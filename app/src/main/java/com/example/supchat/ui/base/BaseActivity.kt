package com.example.supchat.ui.base

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.example.supchat.SupChatApplication
import com.example.supchat.socket.WebSocketService

abstract class BaseActivity : AppCompatActivity() {

    protected lateinit var app: SupChatApplication
    protected var webSocketService: WebSocketService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        app = application as SupChatApplication
        webSocketService = app.getWebSocketService()

        // Observer le statut de connexion WebSocket
        setupWebSocketObserver()

        // Vérifier si l'utilisateur est connecté
        checkUserSession()
    }

    private fun setupWebSocketObserver() {
        webSocketService?.connectionStatus?.observe(this, Observer { isConnected ->
            onWebSocketConnectionChanged(isConnected)
        })
    }

    private fun checkUserSession() {
        val sharedPrefs = getSharedPreferences("SupChatPrefs", Context.MODE_PRIVATE)
        val authToken = sharedPrefs.getString("auth_token", "")

        if (authToken.isNullOrEmpty()) {
            onUserNotLoggedIn()
        } else if (webSocketService == null || !app.isWebSocketConnected()) {
            // Réinitialiser WebSocket si nécessaire
            app.initializeWebSocket(authToken)
            webSocketService = app.getWebSocketService()
        }
    }

    protected open fun onWebSocketConnectionChanged(isConnected: Boolean) {
        // À implémenter dans les activités filles si nécessaire
        supportActionBar?.subtitle = if (isConnected) "En ligne" else "Hors ligne"
    }

    protected open fun onUserNotLoggedIn() {
        // À implémenter dans les activités filles
        // Par défaut, ne rien faire
    }

    protected fun getCurrentUserId(): String {
        return getSharedPreferences("SupChatPrefs", Context.MODE_PRIVATE)
            .getString("user_id", "") ?: ""
    }

    protected fun getCurrentUsername(): String {
        return getSharedPreferences("SupChatPrefs", Context.MODE_PRIVATE)
            .getString("username", "") ?: ""
    }

    protected fun getAuthToken(): String {
        return getSharedPreferences("SupChatPrefs", Context.MODE_PRIVATE)
            .getString("auth_token", "") ?: ""
    }

    override fun onResume() {
        super.onResume()
        // Reconnecter WebSocket si nécessaire
        if (webSocketService?.isConnected() != true) {
            val token = getAuthToken()
            if (token.isNotEmpty()) {
                app.reconnectWebSocket()
            }
        }
    }
}