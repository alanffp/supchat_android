package com.example.supchat.socket

import android.R.attr.tag
import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.supchat.models.response.messageprivate.ConversationMessage
import com.example.supchat.models.response.messageprivate.PrivateMessageItem
import io.socket.client.IO
import io.socket.client.Socket
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

    // LiveData pour les événements en temps réel
    private val _connectionStatus = MutableLiveData<Boolean>(false)
    val connectionStatus: LiveData<Boolean> = _connectionStatus

    private val _newPrivateMessage = MutableLiveData<ConversationMessage>()
    val newPrivateMessage: LiveData<ConversationMessage> = _newPrivateMessage

    private val _messageRead = MutableLiveData<String>()
    val messageRead: LiveData<String> = _messageRead

    private val _messageModified = MutableLiveData<ConversationMessage>()
    val messageModified: LiveData<ConversationMessage> = _messageModified

    private val _messageDeleted = MutableLiveData<String>()
    val messageDeleted: LiveData<String> = _messageDeleted

    private val _messageSent = MutableLiveData<String>()
    val messageSent: LiveData<String> = _messageSent

    // ✅ NOUVEAU: LiveData pour les notifications
    private val _newNotification = MutableLiveData<org.json.JSONObject>()
    val newNotification: LiveData<org.json.JSONObject> = _newNotification

    private val _notificationRead = MutableLiveData<String>()
    val notificationRead: LiveData<String> = _notificationRead

    // Callbacks pour les événements (gardé pour la compatibilité)
    private val messageListeners = mutableListOf<MessageListener>()

    interface MessageListener {
        fun onNewPrivateMessage(message: JSONObject)
        fun onPrivateMessageSent(messageId: String)
        fun onPrivateMessageRead(messageId: String)
        fun onPrivateMessageModified(message: JSONObject)
        fun onPrivateMessageDeleted(messageId: String)
        fun onError(error: String)
        fun onConnectionChanged(isConnected: Boolean)

        // ✅ NOUVEAU: Méthodes pour les notifications
        fun onNewNotification(notification: JSONObject) {}
        fun onNotificationRead(notificationId: String) {}
    }

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

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
                reconnection = true
                reconnectionDelay = 1000
                reconnectionAttempts = 5
            }

            socket = IO.socket(SERVER_URL, options)
            setupEventListeners()
            socket?.connect()

        } catch (e: URISyntaxException) {
            Log.e(TAG, "Erreur URI WebSocket", e)
            _error.postValue("Erreur de connexion: URL invalide")
        }
    }

    private fun setupEventListeners() {
        socket?.apply {
            // Événements de connexion
            on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "WebSocket connecté")
                isConnected = true
                _connectionStatus.postValue(true)
                messageListeners.forEach { it.onConnectionChanged(true) }
            }

            on(Socket.EVENT_DISCONNECT) {
                Log.d(TAG, "WebSocket déconnecté")
                isConnected = false
                _connectionStatus.postValue(false)
                messageListeners.forEach { it.onConnectionChanged(false) }
            }

            on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e(TAG, "Erreur de connexion WebSocket: ${args.contentToString()}")
                isConnected = false
                _connectionStatus.postValue(false)
                val errorMsg = "Erreur de connexion: ${args.firstOrNull()}"
                _error.postValue(errorMsg)
                messageListeners.forEach { it.onError(errorMsg) }
            }

            // Événements des messages privés
            on("nouveau-message-prive") { args ->
                Log.d(TAG, "Nouveau message privé reçu")
                if (args.isNotEmpty()) {
                    try {
                        val messageData = args[0] as JSONObject
                        Log.d(TAG, "Données du message: $messageData")

                        // Convertir en ConversationMessage pour LiveData
                        val message = parsePrivateMessage(messageData)
                        message?.let { _newPrivateMessage.postValue(it) }

                        // Notifier les listeners existants
                        messageListeners.forEach { it.onNewPrivateMessage(messageData) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erreur parsing nouveau message", e)
                        _error.postValue("Erreur de réception du message")
                    }
                }
            }

            on("message-prive-envoye") { args ->
                Log.d(TAG, "Message privé envoyé confirmé")
                if (args.isNotEmpty()) {
                    try {
                        val data = args[0] as JSONObject
                        val messageId = data.getString("messageId")
                        _messageSent.postValue(messageId)
                        messageListeners.forEach { it.onPrivateMessageSent(messageId) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erreur parsing confirmation envoi", e)
                    }
                }
            }

            on("message-prive-lu") { args ->
                Log.d(TAG, "Message privé marqué comme lu")
                if (args.isNotEmpty()) {
                    try {
                        val data = args[0] as JSONObject
                        val messageId = data.getString("messageId")
                        _messageRead.postValue(messageId)
                        messageListeners.forEach { it.onPrivateMessageRead(messageId) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erreur parsing message lu", e)
                    }
                }
            }

            on("message-prive-modifie") { args ->
                Log.d(TAG, "Message privé modifié")
                if (args.isNotEmpty()) {
                    try {
                        val messageData = args[0] as JSONObject
                        val message = parsePrivateMessage(messageData)
                        message?.let { _messageModified.postValue(it) }
                        messageListeners.forEach { it.onPrivateMessageModified(messageData) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erreur parsing message modifié", e)
                    }
                }
            }

            on("message-prive-supprime") { args ->
                Log.d(TAG, "Message privé supprimé")
                if (args.isNotEmpty()) {
                    try {
                        val data = args[0] as JSONObject
                        val messageId = data.getString("messageId")
                        _messageDeleted.postValue(messageId)
                        messageListeners.forEach { it.onPrivateMessageDeleted(messageId) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erreur parsing message supprimé", e)
                    }
                }
            }

            // ✅ NOUVEAU: Événements de notifications
            on("nouvelle-notification") { args ->
                Log.d(tag.toString(), "Nouvelle notification reçue")
                if (args.isNotEmpty()) {
                    try {
                        val notificationData = args[0] as JSONObject
                        _newNotification.postValue(notificationData)
                        messageListeners.forEach { it.onNewNotification(notificationData) }
                    } catch (e: Exception) {
                        Log.e(tag.toString(), "Erreur parsing nouvelle notification", e)
                    }
                }
            }

            on("notification-lue") { args ->
                Log.d(tag.toString(), "Notification marquée comme lue")
                if (args.isNotEmpty()) {
                    try {
                        val data = args[0] as JSONObject
                        val notificationId = data.getString("notificationId")
                        _notificationRead.postValue(notificationId)
                        messageListeners.forEach { it.onNotificationRead(notificationId) }
                    } catch (e: Exception) {
                        Log.e(tag.toString(), "Erreur parsing notification lue", e)
                    }
                }
            }

            // Erreurs
            on("erreur-message-prive") { args ->
                Log.e(TAG, "Erreur message privé")
                if (args.isNotEmpty()) {
                    try {
                        val data = args[0] as JSONObject
                        val error = data.getString("message")
                        _error.postValue(error)
                        messageListeners.forEach { it.onError(error) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erreur parsing erreur message", e)
                    }
                }
            }
        }
    }

    private fun parsePrivateMessage(jsonMessage: JSONObject): ConversationMessage? {
        return try {
            // Extraire les données du message selon votre format API
            val contenu = jsonMessage.optString("contenu", "")
            val horodatage = jsonMessage.optString("dateCreation", "")

            // Gérer l'expéditeur (peut être un objet ou un string)
            val expediteurElement = jsonMessage.opt("expediteur")
            val expediteur = when (expediteurElement) {
                is JSONObject -> expediteurElement.optString("_id", "")
                is String -> expediteurElement
                else -> ""
            }

            // Gérer le destinataire
            val destinataireElement = jsonMessage.opt("destinataire")
            val destinataire = when (destinataireElement) {
                is JSONObject -> destinataireElement.optString("_id", "")
                is String -> destinataireElement
                else -> ""
            }

            ConversationMessage(
                contenu = contenu,
                expediteur = expediteur,
                conversation = destinataire, // Pour les messages privés, on utilise l'ID du destinataire
                lu = emptyList(), // Sera géré par les événements de lecture
                envoye = jsonMessage.optBoolean("envoye", true),
                reponseA = jsonMessage.optString("reponseA", null),
                horodatage = horodatage,
                modifie = jsonMessage.optBoolean("modifie", false),
                dateModification = jsonMessage.optString("dateModification", null),
                fichiers = emptyList(), // À implémenter si nécessaire
                reactions = emptyList() // À implémenter si nécessaire
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erreur parsing message privé", e)
            null
        }
    }

    // Méthodes pour envoyer des messages
    fun sendPrivateMessage(destinataireId: String, contenu: String, reponseA: String? = null) {
        if (!isConnected) {
            Log.w(TAG, "WebSocket non connecté, impossible d'envoyer le message")
            _error.postValue("Non connecté au serveur")
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
            _error.postValue("Non connecté au serveur")
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
            _error.postValue("Non connecté au serveur")
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
            _error.postValue("Non connecté au serveur")
            return
        }

        val data = JSONObject().apply {
            put("messageId", messageId)
        }

        socket?.emit("supprimer-message-prive", data)
        Log.d(TAG, "Message $messageId supprimé")
    }

    // Gestion des listeners (compatibilité)
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
        socket?.off()
        isConnected = false
        _connectionStatus.postValue(false)
        Log.d(TAG, "WebSocket déconnecté manuellement")
    }

    fun isConnected(): Boolean = isConnected

    fun reconnect() {
        disconnect()
        authToken?.let { initialize(it) }
    }
}