package com.example.supchat.models.response.messageprivate

import com.google.gson.annotations.SerializedName

data class PrivateMessageItem(
    @SerializedName("_id") val conversationId: String = "", // ✅ AJOUTÉ
    @SerializedName("user") val user: User = User(), // ✅ AJOUTÉ
    @SerializedName("lastMessage") val lastMessage: LastMessage = LastMessage(), // ✅ AJOUTÉ
    @SerializedName("unreadCount") val unreadCount: Int = 0,
    @SerializedName("isGroup") val isGroup: Boolean = false,
    @SerializedName("dateCreation") val dateCreation: String = "" // ✅ AJOUTÉ
)

data class User(
    @SerializedName("_id") val id: String = "",
    @SerializedName("username") val username: String = "",
    @SerializedName("profilePicture") val profilePicture: String? = null
    // ✅ SUPPRIMÉ : prenom et nom car ils n'existent pas dans l'API
)

data class LastMessage(
    @SerializedName("_id") val id: String = "",
    @SerializedName("contenu") val contenu: String = "",
    @SerializedName("horodatage") val horodatage: String = "",
    @SerializedName("lu") val lu: List<PrivateMessageLecture> = emptyList(), // ✅ RENOMMÉ
    @SerializedName("envoye") val envoye: Boolean = true,
    @SerializedName("isFromMe") val isFromMe: Boolean = false
) {
    // ✅ AJOUTÉ : Helper pour savoir si le message est lu (comme un Boolean)
    val isRead: Boolean get() = lu.isNotEmpty()
}

data class PrivateMessageLecture( // ✅ RENOMMÉ
    @SerializedName("utilisateur") val utilisateur: String = "",
    @SerializedName("dateLecture") val dateLecture: String = ""
)

data class PrivateMessagesResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("count") val count: Int = 0,
    @SerializedName("data") val data: List<PrivateMessageItem> = emptyList()
)

data class PrivateMessage(
    @SerializedName("contenu") val contenu: String = "", // ✅ AJOUTÉ
    @SerializedName("expediteur") val expediteur: User = User(), // ✅ AJOUTÉ
    @SerializedName("destinataire") val destinataire: User = User(), // ✅ AJOUTÉ
    @SerializedName("dateCreation") val dateCreation: String = "", // ✅ AJOUTÉ
    @SerializedName("lu") val lu: Boolean = false,
    @SerializedName("modifie") val modifie: Boolean = false,
    @SerializedName("type") val type: String = "text",
    @SerializedName("fileUrl") val fileUrl: String? = null,
    @SerializedName("fileName") val fileName: String? = null,
    @SerializedName("fileSize") val fileSize: Long? = null
)

data class ConversationFilesResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("count") val count: Int = 0,
    @SerializedName("data") val data: List<ConversationFile> = emptyList()
)

data class ConversationFile(
    @SerializedName("fileName") val fileName: String = "", // ✅ AJOUTÉ
    @SerializedName("fileType") val fileType: String = "", // ✅ AJOUTÉ
    @SerializedName("fileUrl") val fileUrl: String = "", // ✅ AJOUTÉ
    @SerializedName("fileSize") val fileSize: Long = 0
)