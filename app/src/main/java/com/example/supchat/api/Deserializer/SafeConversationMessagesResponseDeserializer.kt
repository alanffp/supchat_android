package com.example.supchat.api.deserializer

import android.util.Log
import com.example.supchat.models.response.messageprivate.ConversationMessagesResponse
import com.example.supchat.models.response.messageprivate.ConversationMessage
import com.example.supchat.models.response.messageprivate.MessageLecture
import com.example.supchat.models.response.messageprivate.MessageFichier
import com.example.supchat.models.response.messageprivate.MessageReaction
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

class SafeConversationMessagesResponseDeserializer : JsonDeserializer<ConversationMessagesResponse> {
    private val TAG = "ConvMsgDeserializer"

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): ConversationMessagesResponse {
        try {
            if (json == null || !json.isJsonObject) {
                Log.e(TAG, "JSON null ou pas un objet")
                return ConversationMessagesResponse(
                    success = false,
                    resultats = 0,
                    data = mutableListOf()
                )
            }

            val jsonObject = json.asJsonObject
            Log.d(TAG, "Structure JSON reçue: $jsonObject")

            // ✅ CORRIGÉ : Gérer "status" ET "success"
            val success = jsonObject.get("success")?.asBoolean
                ?: (jsonObject.get("status")?.asString == "success")
            val count = jsonObject.get("count")?.asInt
                ?: jsonObject.get("resultats")?.asInt ?: 0
            val messages = mutableListOf<ConversationMessage>()

            // ✅ CORRIGÉ : Gérer la structure data.messages
            if (jsonObject.has("data") && !jsonObject.get("data").isJsonNull) {
                val dataElement = jsonObject.get("data")

                when {
                    // Si data est un array direct
                    dataElement.isJsonArray -> {
                        val dataArray = dataElement.asJsonArray
                        dataArray.forEach { element ->
                            parseMessage(element)?.let { messages.add(it) }
                        }
                    }
                    // Si data est un objet avec messages
                    dataElement.isJsonObject -> {
                        val dataObject = dataElement.asJsonObject
                        if (dataObject.has("messages")) {
                            val messagesArray = dataObject.getAsJsonArray("messages")
                            messagesArray.forEach { element ->
                                parseMessage(element)?.let { messages.add(it) }
                            }
                        }
                    }
                }
            } else {
                Log.d(TAG, "Pas de données (data) dans la réponse")
            }

            Log.d(TAG, "Messages traités: ${messages.size}")
            return ConversationMessagesResponse(
                success = success,
                resultats = count,
                data = messages
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erreur de désérialisation globale", e)
            return ConversationMessagesResponse(
                success = false,
                resultats = 0,
                data = mutableListOf()
            )
        }
    }

    private fun parseMessage(element: JsonElement): ConversationMessage? {
        return try {
            if (!element.isJsonObject) return null

            val messageObject = element.asJsonObject

            // Extraction des lectures
            val lectures = mutableListOf<MessageLecture>()
            if (messageObject.has("lu") && messageObject.get("lu").isJsonArray) {
                val luArray = messageObject.getAsJsonArray("lu")
                luArray.forEach { luElement ->
                    if (luElement.isJsonObject) {
                        val luObj = luElement.asJsonObject
                        lectures.add(
                            MessageLecture(
                                utilisateur = luObj.get("utilisateur")?.asString ?: "",
                                dateLecture = luObj.get("dateLecture")?.asString ?: ""
                            )
                        )
                    }
                }
            }

            // Extraction des fichiers
            val fichiers = mutableListOf<MessageFichier>()
            if (messageObject.has("fichiers") && messageObject.get("fichiers").isJsonArray) {
                val fichiersArray = messageObject.getAsJsonArray("fichiers")
                fichiersArray.forEach { fichierElement ->
                    if (fichierElement.isJsonObject) {
                        val fichierObj = fichierElement.asJsonObject
                        fichiers.add(
                            MessageFichier(
                                nom = fichierObj.get("nom")?.asString ?: "",
                                type = fichierObj.get("type")?.asString ?: "",
                                url = fichierObj.get("url")?.asString ?: "",
                                urlPreview = fichierObj.get("urlPreview")?.asString,
                                taille = fichierObj.get("taille")?.asLong ?: 0
                            )
                        )
                    }
                }
            }

            // Extraction des réactions
            val reactions = mutableListOf<MessageReaction>()
            if (messageObject.has("reactions") && messageObject.get("reactions").isJsonArray) {
                val reactionsArray = messageObject.getAsJsonArray("reactions")
                reactionsArray.forEach { reactionElement ->
                    if (reactionElement.isJsonObject) {
                        val reactionObj = reactionElement.asJsonObject
                        reactions.add(
                            MessageReaction(
                                utilisateur = reactionObj.get("utilisateur")?.asString ?: "",
                                emoji = reactionObj.get("emoji")?.asString ?: "",
                                date = reactionObj.get("date")?.asString ?: ""
                            )
                        )
                    }
                }
            }

            // Gérer les différents formats d'expéditeur
            val expediteurId = when {
                messageObject.has("expediteur") -> {
                    val expediteurElement = messageObject.get("expediteur")
                    when {
                        expediteurElement.isJsonNull -> ""
                        expediteurElement.isJsonObject -> {
                            // Si expediteur est un objet avec _id
                            expediteurElement.asJsonObject.get("_id")?.asString ?: ""
                        }
                        expediteurElement.isJsonPrimitive -> {
                            // Si expediteur est directement un string
                            expediteurElement.asString
                        }
                        else -> ""
                    }
                }
                else -> ""
            }

            // Gérer les différents formats de conversation
            val conversationId = when {
                messageObject.has("conversation") -> {
                    val conversationElement = messageObject.get("conversation")
                    when {
                        conversationElement.isJsonNull -> ""
                        conversationElement.isJsonObject -> {
                            conversationElement.asJsonObject.get("_id")?.asString ?: ""
                        }
                        conversationElement.isJsonPrimitive -> {
                            conversationElement.asString
                        }
                        else -> ""
                    }
                }
                else -> ""
            }

            // ✅ CORRIGÉ : Gérer les champs null
            val reponseA = if (messageObject.has("reponseA") && !messageObject.get("reponseA").isJsonNull) {
                messageObject.get("reponseA").asString
            } else {
                null
            }

            val dateModification = if (messageObject.has("dateModification") && !messageObject.get("dateModification").isJsonNull) {
                messageObject.get("dateModification").asString
            } else {
                null
            }

            ConversationMessage(
                contenu = messageObject.get("contenu")?.asString ?: "",
                expediteur = expediteurId,
                conversation = conversationId,
                lu = lectures,
                envoye = messageObject.get("envoye")?.asBoolean ?: true,
                reponseA = reponseA,
                horodatage = messageObject.get("horodatage")?.asString
                    ?: messageObject.get("dateCreation")?.asString ?: "",
                modifie = messageObject.get("modifie")?.asBoolean ?: false,
                dateModification = dateModification,
                fichiers = fichiers,
                reactions = reactions
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la désérialisation d'un message", e)
            null
        }
    }
}