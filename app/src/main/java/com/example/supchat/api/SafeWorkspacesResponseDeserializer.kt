// Créer une classe SafeWorkspacesResponseDeserializer.kt
package com.example.supchat.api

import com.example.supchat.models.response.Workspace
import com.example.supchat.models.response.WorkspacesData
import com.example.supchat.models.response.WorkspacesResponse
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import android.util.Log
import java.lang.reflect.Type

class SafeWorkspacesResponseDeserializer : JsonDeserializer<WorkspacesResponse> {
    private val TAG = "WorkspacesDeserializer"

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): WorkspacesResponse {
        try {
            Log.d(TAG, "Début de la désérialisation du JSON: $json")

            if (json == null || !json.isJsonObject) {
                Log.e(TAG, "JSON invalide ou null")
                return createEmptyResponse()
            }

            val jsonObject = json.asJsonObject

            // Extraire les champs principaux avec gestion des erreurs
            val status = getStringOrDefault(jsonObject, "status", "error")
            val resultats = getIntOrDefault(jsonObject, "resultats", 0)

            // Traiter l'objet data
            val dataObject = jsonObject.get("data")
            if (dataObject == null || !dataObject.isJsonObject) {
                Log.e(TAG, "Objet 'data' manquant ou invalide")
                return WorkspacesResponse(status, resultats, WorkspacesData(emptyList()))
            }

            // Extraire la liste des workspaces
            val workspacesList = mutableListOf<Workspace>()
            val dataJsonObject = dataObject.asJsonObject
            val workspacesElement = dataJsonObject.get("workspaces")

            if (workspacesElement != null && workspacesElement.isJsonArray) {
                val workspacesArray = workspacesElement.asJsonArray

                for (workspaceElement in workspacesArray) {
                    if (workspaceElement.isJsonObject) {
                        try {
                            val workspaceObject = workspaceElement.asJsonObject
                            val id = getStringOrDefault(workspaceObject, "_id", "")
                            val nom = getStringOrDefault(workspaceObject, "nom", "")
                            val description = getStringOrNull(workspaceObject, "description")
                            val proprietaire = getStringOrNull(workspaceObject, "proprietaire")
                            val visibilite = getStringOrNull(workspaceObject, "visibilite")

                            val workspace = Workspace(id, nom, description,
                                proprietaire.toString(), visibilite)
                            workspacesList.add(workspace)
                            Log.d(TAG, "Workspace traité: $workspace")
                        } catch (e: Exception) {
                            Log.e(TAG, "Erreur lors du traitement d'un workspace: ${e.message}")
                        }
                    }
                }
            } else {
                Log.e(TAG, "Liste 'workspaces' manquante ou invalide")
            }

            val workspacesData = WorkspacesData(workspacesList)
            return WorkspacesResponse(status, resultats, workspacesData)

        } catch (e: Exception) {
            Log.e(TAG, "Exception lors de la désérialisation: ${e.message}", e)
            return createEmptyResponse()
        }
    }

    private fun createEmptyResponse(): WorkspacesResponse {
        return WorkspacesResponse("error", 0, WorkspacesData(emptyList()))
    }

    private fun getStringOrDefault(jsonObject: JsonObject, key: String, defaultValue: String): String {
        val element = jsonObject.get(key)
        return if (element != null && !element.isJsonNull && element.isJsonPrimitive) {
            try {
                element.asString
            } catch (e: Exception) {
                defaultValue
            }
        } else {
            defaultValue
        }
    }

    private fun getStringOrNull(jsonObject: JsonObject, key: String): String? {
        val element = jsonObject.get(key)
        return if (element != null && !element.isJsonNull && element.isJsonPrimitive) {
            try {
                element.asString
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    private fun getIntOrDefault(jsonObject: JsonObject, key: String, defaultValue: Int): Int {
        val element = jsonObject.get(key)
        return if (element != null && !element.isJsonNull && element.isJsonPrimitive) {
            try {
                element.asInt
            } catch (e: Exception) {
                defaultValue
            }
        } else {
            defaultValue
        }
    }
}