package com.example.supchat.services

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.supchat.api.ApiClient
import com.example.supchat.models.response.notifications.Notification
import com.example.supchat.models.response.notifications.NotificationsResponse
import com.example.supchat.models.response.notifications.isPrivateMessage
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class NotificationService private constructor() {

    companion object {
        private const val TAG = "NotificationService"

        @Volatile
        private var INSTANCE: NotificationService? = null

        fun getInstance(): NotificationService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NotificationService().also { INSTANCE = it }
            }
        }
    }

    // LiveData pour les notifications
    private val _notifications = MutableLiveData<List<Notification>>(emptyList())
    val notifications: LiveData<List<Notification>> = _notifications

    // LiveData pour le nombre de notifications non lues
    private val _unreadCount = MutableLiveData<Int>(0)
    val unreadCount: LiveData<Int> = _unreadCount

    // LiveData pour les notifications de messages privés non lus
    private val _unreadPrivateMessages = MutableLiveData<Map<String, Int>>(emptyMap())
    val unreadPrivateMessages: LiveData<Map<String, Int>> = _unreadPrivateMessages

    private var currentNotifications = mutableListOf<Notification>()

    // Charger toutes les notifications
    fun loadNotifications(context: Context) {
        val token = getAuthToken(context)
        if (token.isEmpty()) {
            Log.e(TAG, "Token manquant pour charger les notifications")
            return
        }

        Log.d(TAG, "Chargement des notifications...")

        ApiClient.getNotifications(token)
            .enqueue(object : Callback<NotificationsResponse> {
                override fun onResponse(
                    call: Call<NotificationsResponse>,
                    response: Response<NotificationsResponse>
                ) {
                    if (response.isSuccessful) {
                        val notificationsResponse = response.body()
                        val notifications = notificationsResponse?.data?.notifications ?: emptyList()

                        currentNotifications.clear()
                        currentNotifications.addAll(notifications)

                        updateLiveData()

                        Log.d(TAG, "Notifications chargées: ${notifications.size}")
                    } else {
                        Log.e(TAG, "Erreur chargement notifications: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<NotificationsResponse>, t: Throwable) {
                    Log.e(TAG, "Erreur réseau notifications", t)
                }
            })
    }

    // Marquer une notification comme lue
    fun markAsRead(context: Context, notificationId: String) {
        val token = getAuthToken(context)
        if (token.isEmpty()) return

        // Mettre à jour localement d'abord pour la réactivité
        val notification = currentNotifications.find { it.id == notificationId }
        if (notification != null && !notification.lu) {
            val index = currentNotifications.indexOf(notification)
            currentNotifications[index] = notification.copy(lu = true)
            updateLiveData()
        }

        // Puis synchroniser avec l'API
        ApiClient.markNotificationAsRead(token, notificationId)
            .enqueue(object : Callback<NotificationsResponse> {
                override fun onResponse(
                    call: Call<NotificationsResponse>,
                    response: Response<NotificationsResponse>
                ) {
                    if (response.isSuccessful) {
                        Log.d(TAG, "Notification $notificationId marquée comme lue")
                    } else {
                        Log.e(TAG, "Erreur marquage lecture: ${response.code()}")
                        // En cas d'erreur, recharger les notifications
                        loadNotifications(context)
                    }
                }

                override fun onFailure(call: Call<NotificationsResponse>, t: Throwable) {
                    Log.e(TAG, "Erreur réseau marquage lecture", t)
                    // En cas d'erreur, recharger les notifications
                    loadNotifications(context)
                }
            })
    }

    // Marquer toutes les notifications comme lues
    fun markAllAsRead(context: Context) {
        val token = getAuthToken(context)
        if (token.isEmpty()) return

        // Mettre à jour localement d'abord
        currentNotifications.replaceAll { it.copy(lu = true) }
        updateLiveData()

        // Puis synchroniser avec l'API
        ApiClient.markAllNotificationsAsRead(token)
            .enqueue(object : Callback<NotificationsResponse> {
                override fun onResponse(
                    call: Call<NotificationsResponse>,
                    response: Response<NotificationsResponse>
                ) {
                    if (response.isSuccessful) {
                        Log.d(TAG, "Toutes les notifications marquées comme lues")
                    } else {
                        Log.e(TAG, "Erreur marquage toutes lues: ${response.code()}")
                        loadNotifications(context)
                    }
                }

                override fun onFailure(call: Call<NotificationsResponse>, t: Throwable) {
                    Log.e(TAG, "Erreur réseau marquage toutes lues", t)
                    loadNotifications(context)
                }
            })
    }

    // Marquer les notifications de messages privés d'une conversation comme lues
    fun markPrivateConversationAsRead(context: Context, conversationId: String) {
        val notificationsToMark = currentNotifications.filter {
            it.isPrivateMessage() && it.reference == conversationId && !it.lu
        }

        notificationsToMark.forEach { notification ->
            markAsRead(context, notification.id)
        }

        Log.d(TAG, "Marquage de ${notificationsToMark.size} notifications comme lues pour conversation $conversationId")
    }

    // Ajouter une nouvelle notification (pour WebSocket)
    fun addNotification(notification: Notification) {
        currentNotifications.add(0, notification) // Ajouter en premier
        updateLiveData()
        Log.d(TAG, "Nouvelle notification ajoutée: ${notification.message}")
    }

    // Mettre à jour les LiveData
    private fun updateLiveData() {
        _notifications.postValue(currentNotifications.toList())

        // Compter les non lues
        val unreadCount = currentNotifications.count { !it.lu }
        _unreadCount.postValue(unreadCount)

        // Compter les messages privés non lus par conversation
        val unreadPrivateMap = currentNotifications
            .filter { it.isPrivateMessage() && !it.lu }
            .groupBy { it.reference }
            .mapValues { it.value.size }

        _unreadPrivateMessages.postValue(unreadPrivateMap)

        Log.d(TAG, "LiveData mis à jour: $unreadCount non lues, ${unreadPrivateMap.size} conversations avec messages non lus")
    }

    // Obtenir le nombre de notifications non lues pour une conversation
    fun getUnreadCountForConversation(conversationId: String): Int {
        return currentNotifications.count {
            it.isPrivateMessage() && it.reference == conversationId && !it.lu
        }
    }

    private fun getAuthToken(context: Context): String {
        return context.getSharedPreferences("SupChatPrefs", Context.MODE_PRIVATE)
            .getString("auth_token", "") ?: ""
    }
}