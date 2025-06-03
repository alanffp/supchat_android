package com.example.supchat.models.response.messageprivate

import com.google.gson.annotations.SerializedName

// Réponse de l'API conversations/{id}/messages
data class ConversationMessagesResponse(
    val success: Boolean,
    @SerializedName("resultats") val resultats: Int = 0,
    @SerializedName("data") val data: MutableList<ConversationMessage> = mutableListOf()
)

// Classe pour la structure API actuelle (si nécessaire)
data class ConversationMessagesData(
    @SerializedName("messages") val messages: List<ConversationMessage> = emptyList()
)

data class ConversationMessage(
    @SerializedName("_id") val id: String = "", // ✅ AJOUTÉ: ID unique du message
    @SerializedName("contenu") val contenu: String,
    @SerializedName("expediteur") val expediteur: String,
    @SerializedName("conversation") val conversation: String,
    @SerializedName("lu") val lu: List<MessageLecture> = emptyList(),
    @SerializedName("envoye") val envoye: Boolean = true,
    @SerializedName("reponseA") val reponseA: String? = null,
    @SerializedName("horodatage") val horodatage: String,
    @SerializedName("modifie") val modifie: Boolean = false,
    @SerializedName("dateModification") val dateModification: String? = null,
    @SerializedName("fichiers") val fichiers: List<MessageFichier> = emptyList(),
    @SerializedName("reactions") val reactions: List<MessageReaction> = emptyList()
) {
    // Pour compatibilité avec votre code existant
    val expediteurId: String get() = expediteur
    val messageId: String get() = id.ifEmpty { expediteur }
}

data class MessageLecture(
    @SerializedName("utilisateur") val utilisateur: String,
    @SerializedName("dateLecture") val dateLecture: String
)


data class MessageReaction(
    @SerializedName("utilisateur") val utilisateur: String = "",
    @SerializedName("emoji") val emoji: String = "",
    @SerializedName("date") val date: String = ""
)

// Extension function pour vérifier si un message est lu par un utilisateur
fun ConversationMessage.isReadBy(userId: String): Boolean {
    return lu.any { it.utilisateur == userId }
}