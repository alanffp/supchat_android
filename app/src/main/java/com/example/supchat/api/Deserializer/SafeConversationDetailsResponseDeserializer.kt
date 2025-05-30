package com.example.supchat.api.deserializer

import android.util.Log
import com.example.supchat.models.response.messageprivate.ConversationDetailsResponse
import com.example.supchat.models.response.messageprivate.ConversationDetailsData
import com.example.supchat.models.response.messageprivate.ConversationDetails
import com.example.supchat.models.response.messageprivate.ConversationParticipant
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

class SafeConversationDetailsResponseDeserializer : JsonDeserializer<ConversationDetailsResponse> {
    private val TAG = "ConvDetailsDeserializer"

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): ConversationDetailsResponse {
        return try {
            if (json == null || !json.isJsonObject) {
                Log.e(TAG, "JSON null ou pas un objet")
                return createErrorResponse()
            }

            val jsonObject = json.asJsonObject
            Log.d(TAG, "Structure JSON reçue: $jsonObject")

            // Extraire le status
            val status = jsonObject.get("status")?.asString
                ?: jsonObject.get("success")?.let { if (it.asBoolean) "success" else "error" }
                ?: "error"

            var conversationDetailsData: ConversationDetailsData? = null

            // Traiter les données si présentes
            if (jsonObject.has("data") && !jsonObject.get("data").isJsonNull) {
                val dataElement = jsonObject.get("data")

                when {
                    // Si data contient directement une conversation
                    dataElement.isJsonObject && dataElement.asJsonObject.has("conversation") -> {
                        val dataObject = dataElement.asJsonObject
                        val conversationObject = dataObject.getAsJsonObject("conversation")
                        val conversation = parseConversation(conversationObject)
                        conversationDetailsData = ConversationDetailsData(conversation = conversation)
                    }

                    // Si data est directement la conversation
                    dataElement.isJsonObject && dataElement.asJsonObject.has("_id") -> {
                        val conversation = parseConversation(dataElement.asJsonObject)
                        conversationDetailsData = ConversationDetailsData(conversation = conversation)
                    }
                }
            }

            Log.d(TAG, "Désérialisation réussie - Status: $status")
            ConversationDetailsResponse(status = status, data = conversationDetailsData)

        } catch (e: Exception) {
            Log.e(TAG, "Erreur de désérialisation: ${e.message}", e)
            createErrorResponse()
        }
    }

    private fun parseConversation(conversationObject: JsonElement): ConversationDetails? {
        return try {
            if (!conversationObject.isJsonObject) return null

            val convObj = conversationObject.asJsonObject

            // Extraire les participants
            val participants = parseParticipants(convObj)

            // Extraire le créateur
            val createur = when {
                convObj.has("createur") -> {
                    val createurElement = convObj.get("createur")
                    when {
                        createurElement.isJsonObject -> {
                            createurElement.asJsonObject.get("_id")?.asString ?: ""
                        }
                        createurElement.isJsonPrimitive -> {
                            createurElement.asString
                        }
                        else -> ""
                    }
                }
                else -> ""
            }

            ConversationDetails(
                _id = convObj.get("_id")?.asString ?: "",
                nom = convObj.get("nom")?.takeIf { !it.isJsonNull }?.asString,
                participants = participants,
                createur = createur,
                dateCreation = convObj.get("dateCreation")?.asString ?: "",
                dernierMessage = convObj.get("dernierMessage")?.takeIf { !it.isJsonNull }?.asString,
                estGroupe = convObj.get("estGroupe")?.asBoolean ?: false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erreur parsing conversation", e)
            null
        }
    }

    private fun parseParticipants(conversationObject: JsonElement): List<ConversationParticipant> {
        val participants = mutableListOf<ConversationParticipant>()

        try {
            if (!conversationObject.isJsonObject) return participants

            val convObj = conversationObject.asJsonObject
            val createur = convObj.get("createur")?.asString ?: ""

            if (convObj.has("participants") && convObj.get("participants").isJsonArray) {
                val participantsArray = convObj.getAsJsonArray("participants")

                participantsArray.forEach { participantElement ->
                    if (participantElement.isJsonObject) {
                        val participantObj = participantElement.asJsonObject

                        // Gérer le champ utilisateur (peut être un objet ou un string)
                        val (utilisateurId, username, profilePicture) = when {
                            participantObj.has("utilisateur") -> {
                                val utilisateurElement = participantObj.get("utilisateur")
                                when {
                                    utilisateurElement.isJsonObject -> {
                                        val userObj = utilisateurElement.asJsonObject
                                        Triple(
                                            userObj.get("_id")?.asString ?: "",
                                            userObj.get("username")?.asString ?: "",
                                            userObj.get("profilePicture")?.takeIf { !it.isJsonNull }?.asString
                                        )
                                    }
                                    utilisateurElement.isJsonPrimitive -> {
                                        Triple(utilisateurElement.asString, "", null)
                                    }
                                    else -> Triple("", "", null)
                                }
                            }
                            else -> Triple("", "", null)
                        }

                        // Déterminer le rôle
                        val role = if (utilisateurId == createur) "createur" else "participant"

                        val participant = ConversationParticipant(
                            utilisateur = utilisateurId,
                            username = username,
                            profilePicture = profilePicture,
                            dateAjout = participantObj.get("dateAjout")?.asString ?: "",
                            role = role
                        )

                        participants.add(participant)
                        Log.d(TAG, "Participant ajouté: $username ($utilisateurId) - $role")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur parsing participants", e)
        }

        Log.d(TAG, "Total participants parsés: ${participants.size}")
        return participants
    }

    private fun createErrorResponse(): ConversationDetailsResponse {
        return ConversationDetailsResponse(status = "error", data = null)
    }
}