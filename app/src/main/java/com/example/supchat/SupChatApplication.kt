package com.example.supchat

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.supchat.socket.WebSocketService

class SupChatApplication : Application() {

    companion object {
        private const val TAG = "SupChatApplication"
        private lateinit var instance: SupChatApplication

        fun getInstance(): SupChatApplication {
            return instance
        }
    }

    private var webSocketService: WebSocketService? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "Application démarrée")

        // Vérifier si l'utilisateur est déjà connecté
        checkExistingSession()
    }

    private fun checkExistingSession() {
        val sharedPrefs = getSharedPreferences("SupChatPrefs", Context.MODE_PRIVATE)
        val authToken = sharedPrefs.getString("auth_token", "")

        if (!authToken.isNullOrEmpty()) {
            Log.d(TAG, "Session existante trouvée, initialisation WebSocket")
            initializeWebSocket(authToken)
        } else {
            Log.d(TAG, "Aucune session existante")
        }
    }

    fun initializeWebSocket(token: String) {
        try {
            Log.d(TAG, "Initialisation WebSocket avec token")
            webSocketService = WebSocketService.getInstance()
            webSocketService?.initialize(token)

            // Sauvegarder le token pour les reconnexions
            val sharedPrefs = getSharedPreferences("SupChatPrefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putString("auth_token", token).apply()

        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'initialisation WebSocket", e)
        }
    }

    fun disconnectWebSocket() {
        try {
            Log.d(TAG, "Déconnexion WebSocket")
            webSocketService?.disconnect()
            webSocketService = null

        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la déconnexion WebSocket", e)
        }
    }

    fun getWebSocketService(): WebSocketService? {
        return webSocketService
    }

    fun isWebSocketConnected(): Boolean {
        return webSocketService?.isConnected() ?: false
    }

    fun reconnectWebSocket() {
        val sharedPrefs = getSharedPreferences("SupChatPrefs", Context.MODE_PRIVATE)
        val authToken = sharedPrefs.getString("auth_token", "")

        if (!authToken.isNullOrEmpty()) {
            Log.d(TAG, "Reconnexion WebSocket")
            webSocketService?.reconnect()
        }
    }
}