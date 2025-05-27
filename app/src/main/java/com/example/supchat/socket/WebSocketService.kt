package com.example.supchat.socket

import android.content.Context
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject
import java.net.URISyntaxException

class WebSocketService private constructor() {

    companion object {
        private const val TAG = "WebSocketService"
        private const val SERVER_URL = "http://10.0.2.2:3000"

        @Volatile
        private var INSTANCE: WebSocketService? = null

        fun getInstance(): WebSocketService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WebSocketService().also { INSTANCE = it }
            }
        }
    }

    private var socket: Socket? = null
    private var isConnected = false
    private var authToken: String? = null

    // Callbacks pour les événements
    private val messageListeners = mutableListOf<MessageListener>()

    interface MessageListener {
        fun onNewPrivateMessage(message: JSONObject)
        fun onPrivateMessageSent(messageId: String)
        fun onPrivateMessageRead(messageId: String)
        fun onPrivateMessageModified(message: JSONObject)
        fun onPrivateMessageDeleted(messageId: String)
        fun onError(error: String)
    }

    fun initialize(token: String) {
        this.authToken = token
        connectSocket()
    }

    private fun connectSocket() {
        try {
            Log.d(TAG, "Tentative de connexion WebSocket...")

            val options = IO.Options().apply {
                transports = arrayOf("websocket")
                auth = mapOf("token" to authToken)
            }

            socket = IO.socket(SERVER_URL, options)

            setupEventListeners()
            socket?.connect()

        } catch (e: URISyntaxException) {
            Log.e(TAG, "Erreur URI WebSocket", e)
        }
    }

    private fun setupEventListeners() {
        socket?.apply {
            // Événements de connexion
            on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "WebSocket connecté")
                isConnected = true
            }

            on(Socket.EVENT_DISCONNECT) {
                Log.d(TAG, "WebSocket déconnecté")
                isConnected = false
            }

            on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e(TAG, "Erreur de connexion WebSocket: ${args.contentToString()}")
                isConnected = false
            }

            // Événements des messages privés
            on("nouveau-message-prive") { args ->
                Log.d(TAG, "Nouveau message privé reçu")
                if (args.isNotEmpty()) {
                    val messageData = args[0] as JSONObject
                    messageListeners.forEach { it.onNewPrivateMessage(messageData) }
                }
            }

            on("message-prive-envoye") { args ->
                Log.d(TAG, "Message privé envoyé confirmé")
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    val messageId = data.getString("messageId")
                    messageListeners.forEach { it.onPrivateMessageSent(messageId) }
                }
            }

            on("message-prive-lu") { args ->
                Log.d(TAG, "Message privé marqué comme lu")
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    val messageId = data.getString("messageId")
                    messageListeners.forEach { it.onPrivateMessageRead(messageId) }
                }
            }

            on("message-prive-modifie") { args ->
                Log.d(TAG, "Message privé modifié")
                if (args.isNotEmpty()) {
                    val messageData = args[0] as JSONObject
                    messageListeners.forEach { it.onPrivateMessageModified(messageData) }
                }
            }

            on("message-prive-supprime") { args ->
                Log.d(TAG, "Message privé supprimé")
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    val messageId = data.getString("messageId")
                    messageListeners.forEach { it.onPrivateMessageDeleted(messageId) }
                }
            }

            // Erreurs
            on("erreur-message-prive") { args ->
                Log.e(TAG, "Erreur message privé")
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    val error = data.getString("message")
                    messageListeners.forEach { it.onError(error) }
                }
            }
        }
    }

    // Méthodes pour envoyer des messages
    fun sendPrivateMessage(destinataireId: String, contenu: String, reponseA: String? = null) {
        if (!isConnected) {
            Log.w(TAG, "WebSocket non connecté, impossible d'envoyer le message")
            return
        }

        val data = JSONObject().apply {
            put("destinataireId", destinataireId)
            put("contenu", contenu)
            if (reponseA != null) put("reponseA", reponseA)
        }

        socket?.emit("envoyer-message-prive", data)
        Log.d(TAG, "Message privé envoyé vers $destinataireId")
    }

    fun markMessageAsRead(messageId: String) {
        if (!isConnected) {
            Log.w(TAG, "WebSocket non connecté")
            return
        }

        val data = JSONObject().apply {
            put("messageId", messageId)
        }

        socket?.emit("marquer-message-lu", data)
        Log.d(TAG, "Message $messageId marqué comme lu")
    }

    fun modifyPrivateMessage(messageId: String, nouveauContenu: String) {
        if (!isConnected) {
            Log.w(TAG, "WebSocket non connecté")
            return
        }

        val data = JSONObject().apply {
            put("messageId", messageId)
            put("contenu", nouveauContenu)
        }

        socket?.emit("modifier-message-prive", data)
        Log.d(TAG, "Message $messageId modifié")
    }

    fun deletePrivateMessage(messageId: String) {
        if (!isConnected) {
            Log.w(TAG, "WebSocket non connecté")
            return
        }

        val data = JSONObject().apply {
            put("messageId", messageId)
        }

        socket?.emit("supprimer-message-prive", data)
        Log.d(TAG, "Message $messageId supprimé")
    }

    // Gestion des listeners
    fun addMessageListener(listener: MessageListener) {
        messageListeners.add(listener)
    }

    fun removeMessageListener(listener: MessageListener) {
        messageListeners.remove(listener)
    }

    // Gestion de la connexion
    fun connect() {
        if (!isConnected) {
            connectSocket()
        }
    }

    fun disconnect() {
        socket?.disconnect()
        isConnected = false
        Log.d(TAG, "WebSocket déconnecté manuellement")
    }

    fun isConnected(): Boolean = isConnected
}