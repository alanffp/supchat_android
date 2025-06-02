package com.example.supchat.auth

import android.net.Uri
import android.util.Log

object OAuthErrorHandler {
    private const val TAG = "OAuthErrorHandler"

    /**
     * Vérifie si l'URL contient une erreur OAuth
     */
    fun hasError(url: String): Boolean {
        val uri = Uri.parse(url)
        return uri.getQueryParameter("error") != null
    }

    /**
     * Extrait et formate le message d'erreur depuis l'URL de callback
     */
    fun getErrorMessage(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val error = uri.getQueryParameter("error")
            val errorDescription = uri.getQueryParameter("error_description")

            when (error) {
                "access_denied" -> "Accès refusé par l'utilisateur"
                "invalid_request" -> "Requête invalide"
                "unauthorized_client" -> "Client non autorisé"
                "unsupported_response_type" -> "Type de réponse non supporté"
                "invalid_scope" -> "Portée invalide"
                "server_error" -> "Erreur du serveur"
                "temporarily_unavailable" -> "Service temporairement indisponible"
                else -> errorDescription ?: "Erreur OAuth inconnue: $error"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du parsing de l'erreur OAuth", e)
            "Erreur lors du traitement de la réponse OAuth"
        }
    }

    /**
     * Log les détails de l'erreur pour le débogage
     */
    fun logError(url: String) {
        try {
            val uri = Uri.parse(url)
            val error = uri.getQueryParameter("error")
            val errorDescription = uri.getQueryParameter("error_description")
            val errorUri = uri.getQueryParameter("error_uri")

            Log.e(TAG, "Erreur OAuth détectée:")
            Log.e(TAG, "  Error: $error")
            Log.e(TAG, "  Description: $errorDescription")
            Log.e(TAG, "  URI: $errorUri")
            Log.e(TAG, "  URL complète: $url")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du logging de l'erreur OAuth", e)
        }
    }
}