package com.example.supchat.api

import android.util.Log
import com.example.supchat.models.response.Message
import com.example.supchat.models.response.MessagesData
import com.example.supchat.models.response.MessagesResponse
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

/**
 * Désérialiseur personnalisé pour gérer en toute sécurité les réponses de messages
 * qui pourraient avoir des formats incohérents
 */
class SafeMessagesResponseDeserializer : JsonDeserializer<MessagesResponse> {
    private val TAG = "MsgResponseDeserializer"

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): MessagesResponse {
        try {
            val jsonObject = json.asJsonObject

            // Extraire les informations de base
            val status = jsonObject.get("status")?.asString ?: "unknown"
            val resultats = jsonObject.get("resultats")?.asInt ?: 0

            // Vérifier si data existe et n'est pas null
            val dataElement = jsonObject.get("data")
            if (dataElement == null || dataElement.isJsonNull) {
                Log.e(TAG, "Le champ 'data' est manquant ou null")
                return MessagesResponse(status, resultats, MessagesData(emptyList()))
            }

            // Vérifier si messages existe
            val dataObject = dataElement.asJsonObject
            val messagesElement = dataObject.get("messages")
            if (messagesElement == null || messagesElement.isJsonNull) {
                Log.e(TAG, "Le champ 'messages' est manquant ou null")
                return MessagesResponse(status, resultats, MessagesData(emptyList()))
            }

            // Extraire les messages et gérer les erreurs individuellement
            val messagesList = mutableListOf<Message>()
            val messagesArray = messagesElement.asJsonArray

            for (i in 0 until messagesArray.size()) {
                try {
                    val messageElement = messagesArray.get(i)
                    val messageObj = messageElement.asJsonObject

                    // Extraire les champs obligatoires avec gestion d'erreur
                    val id = messageObj.get("_id")?.asString ?: ""
                    val contenu = messageObj.get("contenu")?.asString ?: ""
                    val canal = messageObj.get("canal")?.asString ?: ""

                    // Extraire dateCreation si présent
                    var dateCreation: String? = null
                    if (messageObj.has("dateCreation") && !messageObj.get("dateCreation").isJsonNull) {
                        dateCreation = messageObj.get("dateCreation").asString
                    }

                    // Extraire auteur avec la bonne gestion de type
                    val auteurElement = messageObj.get("auteur")
                    val auteur = when {
                        auteurElement == null || auteurElement.isJsonNull -> "Utilisateur inconnu"
                        auteurElement.isJsonObject -> {
                            // Convertir en Map
                            val auteurMap = mutableMapOf<String, Any>()
                            val auteurObj = auteurElement.asJsonObject

                            // Extraire tous les champs importants
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

                    // Extraire réactions (si présent)
                    val reactionsMap = mutableMapOf<String, Int>()
                    val reactionsElement = messageObj.get("reactions")
                    if (reactionsElement != null && !reactionsElement.isJsonNull && reactionsElement.isJsonObject) {
                        val reactionsObj = reactionsElement.asJsonObject
                        reactionsObj.keySet().forEach { emoji ->
                            val count = reactionsObj.get(emoji)?.asInt ?: 0
                            reactionsMap[emoji] = count
                        }
                    }

                    // Extraire les informations de réponse
                    var estReponse = false
                    var messageParent: String? = null

                    if (messageObj.has("estReponse") && !messageObj.get("estReponse").isJsonNull) {
                        estReponse = messageObj.get("estReponse").asBoolean
                    }

                    if (messageObj.has("messageParent") && !messageObj.get("messageParent").isJsonNull) {
                        messageParent = messageObj.get("messageParent").asString
                    }

                    // Extraire les réponses de manière récursive si présentes
                    val reponsesList = mutableListOf<Message>()
                    val reponsesElement = messageObj.get("reponses")
                    if (reponsesElement != null && !reponsesElement.isJsonNull && reponsesElement.isJsonArray) {
                        val reponsesArray = reponsesElement.asJsonArray

                        for (j in 0 until reponsesArray.size()) {
                            try {
                                val reponseElement = reponsesArray.get(j)
                                val reponseObj = reponseElement.asJsonObject

                                // Extraire les champs basiques de la réponse
                                val reponseId = reponseObj.get("_id")?.asString ?: ""
                                val reponseContenu = reponseObj.get("contenu")?.asString ?: ""
                                val reponseCanal = reponseObj.get("canal")?.asString ?: ""

                                // Extraire l'auteur de la réponse
                                val reponseAuteurElement = reponseObj.get("auteur")
                                val reponseAuteur = if (reponseAuteurElement != null && !reponseAuteurElement.isJsonNull) {
                                    if (reponseAuteurElement.isJsonObject) {
                                        val reponseAuteurMap = mutableMapOf<String, Any>()
                                        val reponseAuteurObj = reponseAuteurElement.asJsonObject

                                        if (reponseAuteurObj.has("_id")) reponseAuteurMap["_id"] = reponseAuteurObj.get("_id").asString
                                        if (reponseAuteurObj.has("username")) reponseAuteurMap["username"] = reponseAuteurObj.get("username").asString

                                        reponseAuteurMap
                                    } else {
                                        reponseAuteurElement.asString
                                    }
                                } else {
                                    "Utilisateur inconnu"
                                }

                                // Créer l'objet réponse
                                val reponse = Message(
                                    reponseId,
                                    reponseContenu,
                                    reponseAuteur,
                                    reponseCanal,
                                    dateCreation = null,
                                    reactions = null,
                                    reponses = null,
                                    estReponse = true,
                                    messageParent = id
                                )

                                reponsesList.add(reponse)
                            } catch (e: Exception) {
                                Log.e(TAG, "Erreur lors de la désérialisation d'une réponse", e)
                                // Continuer avec les autres réponses
                            }
                        }
                    }

                    // Créer le message avec tous les champs
                    val message = Message(
                        id,
                        contenu,
                        auteur,
                        canal,
                        dateCreation = dateCreation,
                        reactions = if (reactionsMap.isEmpty()) null else reactionsMap,
                        reponses = if (reponsesList.isEmpty()) null else reponsesList,
                        estReponse = estReponse,
                        messageParent = messageParent
                    )

                    messagesList.add(message)
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur lors de la désérialisation d'un message individuel", e)
                    // Continuer avec les autres messages
                }
            }

            return MessagesResponse(status, resultats, MessagesData(messagesList))
        } catch (e: Exception) {
            Log.e(TAG, "Exception lors de la désérialisation de MessagesResponse", e)
            // Retourner une réponse vide mais valide en cas d'erreur
            return MessagesResponse("error", 0, MessagesData(emptyList()))
        }
    }
}