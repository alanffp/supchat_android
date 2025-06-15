package com.example.supchat.api.deserializer

import android.util.Log
import com.example.supchat.models.response.notifications.NotificationCountResponse
import com.example.supchat.models.response.notifications.NotificationCountData
import com.google.gson.*
import java.lang.reflect.Type

class SafeNotificationCountResponseDeserializer : JsonDeserializer<NotificationCountResponse> {
    private val TAG = "NotifCountDeserializer"

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): NotificationCountResponse {
        Log.d(TAG, "Tentative désérialisation NotificationCountResponse")

        return try {
            val jsonObject = json?.asJsonObject

            // Vérification stricte : doit avoir "data" avec "count"
            val dataElement = jsonObject?.get("data")
            if (dataElement == null || !dataElement.isJsonObject) {
                Log.d(TAG, "Pas de data object, ce n'est pas pour les notifications")
                throw JsonParseException("Not a notification count response - no data object")
            }

            val dataObject = dataElement.asJsonObject
            val hasCount = dataObject.has("count") || dataObject.has("unreadCount") || dataObject.has("nombre")

            if (!hasCount) {
                Log.d(TAG, "Pas de champ count dans data, ce n'est pas pour les notifications")
                throw JsonParseException("Not a notification count response - no count field")
            }

            // Valeurs par défaut sécurisées selon votre modèle
            val status = jsonObject?.get("status")?.asString ?: "error"

            // Désérialisation sécurisée des données de compteur
            val data = deserializeNotificationCountData(dataElement)

            val result = NotificationCountResponse(
                status = status,
                data = data
            )
            Log.d(TAG, "Notification count désérialisée avec succès: $result")
            result

        } catch (e: JsonParseException) {
            // Re-lancer pour que Gson essaie un autre désérialiseur
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Erreur générale: ${e.message}")
            NotificationCountResponse(
                status = "error",
                data = NotificationCountData(count = 0)
            )
        }
    }

    private fun deserializeNotificationCountData(dataElement: JsonElement): NotificationCountData {
        return try {
            val dataObject = dataElement.asJsonObject

            val count = try {
                dataObject?.get("count")?.asInt ?:
                dataObject?.get("unreadCount")?.asInt ?:
                dataObject?.get("nombre")?.asInt ?: 0
            } catch (e: Exception) {
                Log.e(TAG, "Erreur extraction count: ${e.message}")
                0
            }

            NotificationCountData(count = count)

        } catch (e: Exception) {
            Log.e(TAG, "Erreur NotificationCountData: ${e.message}")
            NotificationCountData(count = 0)
        }
    }
}