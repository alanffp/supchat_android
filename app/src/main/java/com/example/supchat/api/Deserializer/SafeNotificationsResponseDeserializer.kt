package com.example.supchat.api.deserializer

import android.util.Log
import com.example.supchat.models.response.notifications.NotificationsResponse
import com.example.supchat.models.response.notifications.NotificationsData
import com.example.supchat.models.response.notifications.Notification
import com.google.gson.*
import java.lang.reflect.Type

class SafeNotificationsResponseDeserializer : JsonDeserializer<NotificationsResponse> {
    private val TAG = "NotificationsDeserializer"

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): NotificationsResponse {
        Log.d(TAG, "Tentative désérialisation NotificationsResponse")

        return try {
            val jsonObject = json?.asJsonObject

            // Vérification stricte : doit avoir "data" avec "notifications"
            val dataElement = jsonObject?.get("data")
            if (dataElement == null || !dataElement.isJsonObject) {
                Log.d(TAG, "Pas de data object, ce n'est pas pour les notifications")
                throw JsonParseException("Not a notifications response - no data object")
            }

            val dataObject = dataElement.asJsonObject
            if (!dataObject.has("notifications")) {
                Log.d(TAG, "Pas de champ notifications dans data, ce n'est pas pour les notifications")
                throw JsonParseException("Not a notifications response - no notifications field")
            }

            // Si on arrive ici, c'est bien une réponse de notifications
            Log.d(TAG, "Réponse de notifications détectée, traitement...")

            // Valeurs par défaut sécurisées selon votre modèle
            val status = jsonObject?.get("status")?.asString ?: "error"
            val results = jsonObject?.get("results")?.asInt ?: 0

            // Désérialisation sécurisée des données
            val data = deserializeNotificationsData(dataElement, context)

            val result = NotificationsResponse(
                status = status,
                results = results,
                data = data
            )
            Log.d(TAG, "Notifications désérialisées avec succès: ${result.data.notifications.size} notifications")
            result

        } catch (e: JsonParseException) {
            // Re-lancer pour que Gson essaie un autre désérialiseur
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Erreur générale: ${e.message}")
            NotificationsResponse(
                status = "error",
                results = 0,
                data = NotificationsData(notifications = emptyList())
            )
        }
    }

    private fun deserializeNotificationsData(
        dataElement: JsonElement,
        context: JsonDeserializationContext?
    ): NotificationsData {
        return try {
            val dataObject = dataElement.asJsonObject

            // Désérialiser la liste des notifications
            val notifications = try {
                val notificationsArray = dataObject?.get("notifications")?.asJsonArray
                notificationsArray?.mapNotNull { notificationElement ->
                    deserializeNotification(notificationElement, context)
                } ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Erreur liste notifications: ${e.message}")
                emptyList<Notification>()
            }

            NotificationsData(notifications = notifications)

        } catch (e: Exception) {
            Log.e(TAG, "Erreur NotificationsData: ${e.message}")
            NotificationsData(notifications = emptyList())
        }
    }

    private fun deserializeNotification(
        notificationElement: JsonElement,
        context: JsonDeserializationContext?
    ): Notification? {
        return try {
            val notificationObject = notificationElement.asJsonObject

            // Extraction sécurisée selon votre modèle Notification
            val id = notificationObject?.get("_id")?.asString ?:
            notificationObject?.get("id")?.asString ?: ""

            val utilisateur = notificationObject?.get("utilisateur")?.asString ?: ""
            val type = notificationObject?.get("type")?.asString ?: ""
            val reference = notificationObject?.get("reference")?.asString ?: ""
            val onModel = notificationObject?.get("onModel")?.asString ?: ""
            val message = notificationObject?.get("message")?.asString ?: ""
            val lu = notificationObject?.get("lu")?.asBoolean ?: false
            val createdAt = notificationObject?.get("createdAt")?.asString ?: ""

            Notification(
                id = id,
                utilisateur = utilisateur,
                type = type,
                reference = reference,
                onModel = onModel,
                message = message,
                lu = lu,
                createdAt = createdAt
            )

        } catch (e: Exception) {
            Log.e(TAG, "Erreur notification individuelle: ${e.message}")
            null
        }
    }
}