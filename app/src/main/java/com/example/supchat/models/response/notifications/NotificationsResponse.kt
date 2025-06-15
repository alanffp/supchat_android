package com.example.supchat.models.response.notifications

import com.google.gson.annotations.SerializedName

// Réponse de l'API notifications
data class NotificationsResponse(
    @SerializedName("status") val status: String,
    @SerializedName("results") val results: Int,
    @SerializedName("data") val data: NotificationsData
)

data class NotificationsData(
    @SerializedName("notifications") val notifications: List<Notification>
)

data class Notification(
    @SerializedName("_id") val id: String,
    @SerializedName("utilisateur") val utilisateur: String,
    @SerializedName("type") val type: String, // "canal", "message_prive", etc.
    @SerializedName("reference") val reference: String, // ID du canal/conversation
    @SerializedName("onModel") val onModel: String, // "Canal", "ConversationPrivee", etc.
    @SerializedName("message") val message: String,
    @SerializedName("lu") val lu: Boolean,
    @SerializedName("createdAt") val createdAt: String
)

// Réponse pour le compteur de notifications
data class NotificationCountResponse(
    @SerializedName("status") val status: String,
    @SerializedName("data") val data: NotificationCountData
)

data class NotificationCountData(
    @SerializedName("count") val count: Int
)

// Extension pour vérifier si c'est une notification de message privé
fun Notification.isPrivateMessage(): Boolean = type == "message_prive" || onModel == "ConversationPrivee"

// Extension pour vérifier si c'est une notification de canal
fun Notification.isChannelMessage(): Boolean = type == "canal" || onModel == "Canal"

// Extension pour vérifier si c'est une invitation workspace
fun Notification.isWorkspaceInvite(): Boolean = type == "workspace_invite" || type == "invitation_workspace"