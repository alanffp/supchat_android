package com.example.supchat.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import com.example.supchat.api.ApiClient
import com.example.supchat.models.request.OAuthCallbackRequest
import com.example.supchat.models.response.LoginResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class OAuthHelper(
    private val context: Context,
    private val baseUrl: String = "http://10.0.2.2:3000"
) {
    companion object {
        private const val TAG = "OAuthHelper"
        const val GOOGLE_REQUEST_CODE = 1001
        const val FACEBOOK_REQUEST_CODE = 1002
        const val MICROSOFT_REQUEST_CODE = 1003
    }

    interface OAuthCallback {
        fun onSuccess(loginResponse: LoginResponse)
        fun onError(error: String)
    }

    /**
     * Initie l'authentification Google avec paramètres de redirection forcés
     */
    fun initiateGoogleLogin(activity: Activity) {
        val redirectUri = "$baseUrl/api/v1/auth/google/callback"
        val authUrl = "$baseUrl/api/v1/auth/google?redirect_uri=${Uri.encode(redirectUri)}"
        Log.d(TAG, "Google auth URL: $authUrl")
        openCustomTab(activity, authUrl)
    }

    /**
     * Initie l'authentification Facebook avec paramètres de redirection forcés
     */
    fun initiateFacebookLogin(activity: Activity) {
        val redirectUri = "$baseUrl/api/v1/auth/facebook/callback"
        val authUrl = "$baseUrl/api/v1/auth/facebook?redirect_uri=${Uri.encode(redirectUri)}"
        Log.d(TAG, "Facebook auth URL: $authUrl")
        openCustomTab(activity, authUrl)
    }

    /**
     * Initie l'authentification Microsoft avec paramètres de redirection forcés
     */
    fun initiateMicrosoftLogin(activity: Activity) {
        val redirectUri = "$baseUrl/api/v1/auth/microsoft/callback"
        val authUrl = "$baseUrl/api/v1/auth/microsoft?redirect_uri=${Uri.encode(redirectUri)}"
        Log.d(TAG, "Microsoft auth URL: $authUrl")
        openCustomTab(activity, authUrl)
    }

    /**
     * Ouvre une Custom Tab pour l'authentification OAuth
     */
    private fun openCustomTab(activity: Activity, url: String) {
        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setUrlBarHidingEnabled(true)
                .build()

            customTabsIntent.launchUrl(activity, Uri.parse(url))
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du lancement de Custom Tab", e)
            // Fallback vers le navigateur par défaut
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            activity.startActivity(intent)
        }
    }

    /**
     * Traite le callback OAuth pour Google
     */
    fun handleGoogleCallback(code: String, state: String?, callback: OAuthCallback) {
        val request = OAuthCallbackRequest(code, state)

        ApiClient.instance.googleLoginCallback(request)
            .enqueue(object : Callback<LoginResponse> {
                override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                    handleOAuthResponse(response, callback, "Google")
                }

                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    Log.e(TAG, "Erreur callback Google", t)
                    callback.onError("Erreur de connexion Google: ${t.message}")
                }
            })
    }

    /**
     * Traite le callback OAuth pour Facebook
     */
    fun handleFacebookCallback(code: String, state: String?, callback: OAuthCallback) {
        val request = OAuthCallbackRequest(code, state)

        ApiClient.instance.facebookLoginCallback(request)
            .enqueue(object : Callback<LoginResponse> {
                override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                    handleOAuthResponse(response, callback, "Facebook")
                }

                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    Log.e(TAG, "Erreur callback Facebook", t)
                    callback.onError("Erreur de connexion Facebook: ${t.message}")
                }
            })
    }

    /**
     * Traite le callback OAuth pour Microsoft
     */
    fun handleMicrosoftCallback(code: String, state: String?, callback: OAuthCallback) {
        val request = OAuthCallbackRequest(code, state)

        ApiClient.instance.microsoftLoginCallback(request)
            .enqueue(object : Callback<LoginResponse> {
                override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                    handleOAuthResponse(response, callback, "Microsoft")
                }

                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    Log.e(TAG, "Erreur callback Microsoft", t)
                    callback.onError("Erreur de connexion Microsoft: ${t.message}")
                }
            })
    }

    /**
     * Gère la réponse OAuth commune
     */
    private fun handleOAuthResponse(
        response: Response<LoginResponse>,
        callback: OAuthCallback,
        provider: String
    ) {
        if (response.isSuccessful) {
            val loginResponse = response.body()
            if (loginResponse != null && loginResponse.success && !loginResponse.token.isNullOrEmpty()) {
                Log.d(TAG, "Connexion $provider réussie")
                callback.onSuccess(loginResponse)
            } else {
                val errorMessage = loginResponse?.message ?: loginResponse?.error ?: "Erreur inconnue"
                Log.e(TAG, "Échec de la connexion $provider: $errorMessage")
                callback.onError("Échec de la connexion $provider: $errorMessage")
            }
        } else {
            val errorBody = response.errorBody()?.string()
            Log.e(TAG, "Erreur HTTP $provider: ${response.code()}, $errorBody")
            callback.onError("Erreur de connexion $provider: ${response.code()}")
        }
    }

    /**
     * Parse l'URL de callback pour extraire le code et l'état
     */
    fun parseCallbackUrl(url: String): Pair<String?, String?> {
        return try {
            val uri = Uri.parse(url)
            val code = uri.getQueryParameter("code")
            val state = uri.getQueryParameter("state")
            Pair(code, state)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du parsing de l'URL de callback", e)
            Pair(null, null)
        }
    }

    /**
     * Vérifie si l'URL contient une erreur OAuth
     */
    fun hasError(url: String): Boolean {
        return try {
            val uri = Uri.parse(url)
            uri.getQueryParameter("error") != null
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la vérification d'erreur", e)
            false
        }
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
     * Vérifie si l'URL est un callback OAuth valide
     */
    fun isOAuthCallback(url: String): Boolean {
        return url.contains("/callback") &&
                (url.contains("google") || url.contains("facebook") || url.contains("microsoft"))
    }

    /**
     * Détermine le type de provider depuis l'URL
     */
    fun getProviderFromUrl(url: String): String? {
        return when {
            url.contains("google") -> "google"
            url.contains("facebook") -> "facebook"
            url.contains("microsoft") -> "microsoft"
            else -> null
        }
    }
}