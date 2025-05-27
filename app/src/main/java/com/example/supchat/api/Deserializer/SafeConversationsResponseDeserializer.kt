package com.example.supchat.api.deserializer

import android.util.Log
import com.example.supchat.models.response.messageprivate.ConversationMessage
import com.example.supchat.models.response.messageprivate.ConversationMessagesResponse
import com.example.supchat.models.response.messageprivate.MessageFichier
import com.example.supchat.models.response.messageprivate.MessageLecture
import com.example.supchat.models.response.messageprivate.MessageReaction
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

class SafeConversationsResponseDeserializer : JsonDeserializer<ConversationMessagesResponse> {
    private val TAG = "ConversationsDeserializer"

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

            val success = jsonObject.get("success")?.asBoolean ?: false
            val resultats = jsonObject.get("resultats")?.asInt ?: 0
            val messages = mutableListOf<ConversationMessage>()

            if (jsonObject.has("data") && !jsonObject.get("data").isJsonNull) {
                val dataObject = jsonObject.getAsJsonObject("data")

                if (dataObject.has("messages") && !dataObject.get("messages").isJsonNull) {
                    val messagesArray = dataObject.getAsJsonArray("messages")

                    messagesArray.forEach { element ->
                        try {
                            if (element.isJsonObject) {
                                val messageObject = element.asJsonObject

                                // Extraire les lectures
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

                                // Extraire les fichiers
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

                                // Extraire les réactions
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

                                // Créer le message
                                val message = ConversationMessage(
                                    contenu = messageObject.get("contenu")?.asString ?: "",
                                    expediteur = messageObject.get("expediteur")?.asString ?: "",
                                    conversation = messageObject.get("conversation")?.asString ?: "",
                                    lu = lectures,
                                    envoye = messageObject.get("envoye")?.asBoolean ?: false,
                                    reponseA = messageObject.get("reponseA")?.asString,
                                    horodatage = messageObject.get("horodatage")?.asString ?: "",
                                    modifie = messageObject.get("modifie")?.asBoolean ?: false,
                                    dateModification = messageObject.get("dateModification")?.asString,
                                    fichiers = fichiers,
                                    reactions = reactions
                                )

                                messages.add(message)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Erreur lors de la désérialisation d'un message", e)
                        }
                    }
                }
            }

            return ConversationMessagesResponse(
                success = success,
                resultats = resultats,
                data = messages // ✅ Directement la liste, pas ConversationMessagesData
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
}