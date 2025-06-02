package com.example.supchat.api

import android.util.Log
import com.example.supchat.models.response.Message
import com.example.supchat.models.response.MessagesData
import com.example.supchat.models.response.MessagesResponse
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.lang.reflect.Type

class SafeMessagesResponseDeserializer : JsonDeserializer<MessagesResponse> {
    private val TAG = "MsgResponseDeserializer"

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): MessagesResponse {
        try {
            Log.d(TAG, "🔍 DÉBUT DÉSÉRIALISATION - JSON: $json")
            val jsonObject = json.asJsonObject

            val status = jsonObject.get("status")?.asString ?: "unknown"
            val resultats = jsonObject.get("resultats")?.asInt ?: 0

            val dataElement = jsonObject.get("data")
            if (dataElement == null || dataElement.isJsonNull) {
                Log.e(TAG, "❌ Le champ 'data' est manquant ou null")
                return MessagesResponse(status, resultats, MessagesData(emptyList()))
            }

            val dataObject = dataElement.asJsonObject
            val messagesList = mutableListOf<Message>()

            // Vérifier s'il y a un message unique (réponse d'ajout de réaction)
            val singleMessageElement = dataObject.get("message")
            if (singleMessageElement != null && !singleMessageElement.isJsonNull && singleMessageElement.isJsonObject) {
                Log.d(TAG, "📝 RÉPONSE MESSAGE UNIQUE détectée")
                val message = parseMessage(singleMessageElement.asJsonObject)
                if (message != null) {
                    messagesList.add(message)
                }
            } else {
                // Logique pour array de messages (liste des messages du canal)
                val messagesElement = dataObject.get("messages")
                if (messagesElement != null && !messagesElement.isJsonNull && messagesElement.isJsonArray) {
                    val messagesArray = messagesElement.asJsonArray
                    Log.d(TAG, "📝 ARRAY DE MESSAGES - ${messagesArray.size()} messages")

                    for (i in 0 until messagesArray.size()) {
                        val messageElement = messagesArray.get(i)
                        if (messageElement.isJsonObject) {
                            val message = parseMessage(messageElement.asJsonObject)
                            if (message != null) {
                                messagesList.add(message)
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "❌ Ni 'message' ni 'messages' trouvé")
                }
            }

            Log.d(TAG, "🏁 DÉSÉRIALISATION TERMINÉE: ${messagesList.size} messages")
            return MessagesResponse(status, resultats, MessagesData(messagesList))

        } catch (e: Exception) {
            Log.e(TAG, "💥 EXCEPTION désérialisation", e)
            return MessagesResponse("error", 0, MessagesData(emptyList()))
        }
    }

    private fun parseMessage(messageObj: JsonObject): Message? {
        return try {
            val id = messageObj.get("_id")?.asString ?: ""
            val contenu = messageObj.get("contenu")?.asString ?: ""
            val canal = messageObj.get("canal")?.asString ?: ""

            var dateCreation: String? = null
            if (messageObj.has("dateCreation") && !messageObj.get("dateCreation").isJsonNull) {
                dateCreation = messageObj.get("dateCreation").asString
            }

            // Auteur
            val auteurElement = messageObj.get("auteur")
            val auteur = when {
                auteurElement == null || auteurElement.isJsonNull -> "Utilisateur inconnu"
                auteurElement.isJsonObject -> {
                    val auteurMap = mutableMapOf<String, Any>()
                    val auteurObj = auteurElement.asJsonObject

                    if (auteurObj.has("_id")) auteurMap["_id"] = auteurObj.get("_id").asString
                    if (auteurObj.has("id")) auteurMap["id"] = auteurObj.get("id").asString
                    if (auteurObj.has("username")) auteurMap["username"] = auteurObj.get("username").asString
                    if (auteurObj.has("nom")) auteurMap["nom"] = auteurObj.get("nom").asString
                    if (auteurObj.has("email")) auteurMap["email"] = auteurObj.get("email").asString

                    auteurMap
                }
                auteurElement.isJsonPrimitive -> auteurElement.asString
                else -> "Utilisateur inconnu"
            }

            // ✅ RÉACTIONS AVEC FORMAT API RÉEL
            val reactionsMap = mutableMapOf<String, Int>()
            val reactionsElement = messageObj.get("reactions")

            Log.d(TAG, "🎭 MESSAGE $id - Reactions element: $reactionsElement")

            if (reactionsElement != null && !reactionsElement.isJsonNull && reactionsElement.isJsonArray) {
                val reactionsArray = reactionsElement.asJsonArray
                Log.d(TAG, "🎭 MESSAGE $id - Processing ${reactionsArray.size()} reaction objects")

                for (j in 0 until reactionsArray.size()) {
                    try {
                        val reactionObj = reactionsArray.get(j).asJsonObject

                        val emoji = reactionObj.get("emoji")?.asString
                        val utilisateursElement = reactionObj.get("utilisateurs")
                        val count = if (utilisateursElement != null && utilisateursElement.isJsonArray) {
                            utilisateursElement.asJsonArray.size()
                        } else {
                            1
                        }

                        if (!emoji.isNullOrEmpty() && count > 0) {
                            reactionsMap[emoji] = count
                            Log.d(TAG, "✅ MESSAGE $id - Réaction: '$emoji' = $count utilisateurs")
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erreur réaction individuelle", e)
                    }
                }
            }

            Log.d(TAG, "🎭 MESSAGE $id - RÉACTIONS FINALES: $reactionsMap")

            // Informations de réponse
            var estReponse = false
            var messageParent: String? = null

            if (messageObj.has("estReponse") && !messageObj.get("estReponse").isJsonNull) {
                estReponse = messageObj.get("estReponse").asBoolean
            }

            if (messageObj.has("messageParent") && !messageObj.get("messageParent").isJsonNull) {
                messageParent = messageObj.get("messageParent").asString
            }

            // Créer le message
            val message = Message(
                id,
                contenu,
                auteur,
                canal,
                dateCreation = dateCreation,
                reactions = if (reactionsMap.isEmpty()) null else reactionsMap,
                reponses = null, // Simplifié pour le debug
                estReponse = estReponse,
                messageParent = messageParent
            )

            Log.d(TAG, "✅ MESSAGE CRÉÉ: ${message.id} avec ${message.getTotalReactions()} réactions")
            message

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur parsing message individuel", e)
            null
        }
    }
}