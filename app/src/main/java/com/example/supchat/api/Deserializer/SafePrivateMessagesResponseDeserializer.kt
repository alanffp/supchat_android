package com.example.supchat.api.deserializer

import android.util.Log
import com.example.supchat.models.response.messageprivate.PrivateMessagesResponse
import com.example.supchat.models.response.messageprivate.PrivateMessageItem
import com.example.supchat.models.response.messageprivate.User
import com.example.supchat.models.response.messageprivate.LastMessage
import com.example.supchat.models.response.messageprivate.PrivateMessageLecture
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

class SafePrivateMessagesResponseDeserializer : JsonDeserializer<PrivateMessagesResponse> {
    private val TAG = "PMResponseDeserializer"

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): PrivateMessagesResponse {
        try {
            if (json == null || !json.isJsonObject) {
                Log.e(TAG, "JSON null ou pas un objet")
                return PrivateMessagesResponse(false, 0, emptyList())
            }

            val jsonObject = json.asJsonObject
            Log.d(TAG, "Structure JSON reçue: $json")

            // Extraction manuelle pour éviter la boucle infinie
            val success = jsonObject.get("success")?.asBoolean ?: false
            val count = jsonObject.get("count")?.asInt ?: 0
            val dataItems = mutableListOf<PrivateMessageItem>()

            // Extraction des données
            if (jsonObject.has("data") && !jsonObject.get("data").isJsonNull) {
                val dataArray = jsonObject.getAsJsonArray("data")

                dataArray.forEach { element ->
                    try {
                        if (element.isJsonObject) {
                            val itemObject = element.asJsonObject

                            // Extraire l'objet user
                            val userObj = itemObject.getAsJsonObject("user")
                            val user = if (userObj != null) {
                                User(
                                    id = userObj.get("_id")?.asString ?: "",
                                    username = userObj.get("username")?.asString ?: "",
                                    profilePicture = userObj.get("profilePicture")?.asString
                                )
                            } else {
                                User()
                            }

                            // Extraire l'objet lastMessage
                            val lastMessageObj = itemObject.getAsJsonObject("lastMessage")
                            val lastMessage = if (lastMessageObj != null) {
                                // Traiter le tableau "lu"
                                val lectures = mutableListOf<PrivateMessageLecture>()
                                if (lastMessageObj.has("lu") && lastMessageObj.get("lu").isJsonArray) {
                                    val luArray = lastMessageObj.getAsJsonArray("lu")
                                    luArray.forEach { luElement ->
                                        if (luElement.isJsonObject) {
                                            val luObj = luElement.asJsonObject
                                            lectures.add(
                                                PrivateMessageLecture(
                                                    utilisateur = luObj.get("utilisateur")?.asString ?: "",
                                                    dateLecture = luObj.get("dateLecture")?.asString ?: ""
                                                )
                                            )
                                        }
                                    }
                                }

                                LastMessage(
                                    id = lastMessageObj.get("_id")?.asString ?: "",
                                    contenu = lastMessageObj.get("contenu")?.asString ?: "",
                                    horodatage = lastMessageObj.get("horodatage")?.asString ?: "",
                                    lu = lectures,
                                    envoye = lastMessageObj.get("envoye")?.asBoolean ?: false,
                                    isFromMe = lastMessageObj.get("isFromMe")?.asBoolean ?: false
                                )
                            } else {
                                LastMessage()
                            }

                            val conversationId = itemObject.get("_id")?.asString ?: ""
                            val unreadCount = itemObject.get("unreadCount")?.asInt ?: 0
                            val isGroup = itemObject.get("isGroup")?.asBoolean ?: false
                            val dateCreation = itemObject.get("dateCreation")?.asString ?: ""

                            val item = PrivateMessageItem(
                                conversationId = conversationId,
                                user = user,
                                lastMessage = lastMessage,
                                unreadCount = unreadCount,
                                isGroup = isGroup,
                                dateCreation = dateCreation
                            )
                            dataItems.add(item)
                            Log.d(TAG, "Item ajouté: ${user.username}, message: ${lastMessage.contenu}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erreur lors de la désérialisation d'un élément", e)
                    }
                }
            }

            Log.d(TAG, "Désérialisation réussie: ${dataItems.size} éléments")
            return PrivateMessagesResponse(success, count, dataItems)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur de désérialisation: ${e.message}", e)
            return PrivateMessagesResponse(false, 0, emptyList())
        }
    }
}