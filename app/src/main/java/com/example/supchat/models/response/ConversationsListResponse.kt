package com.example.supchat.models.response

import com.example.supchat.models.response.messageprivate.ConversationParticipant
import com.google.gson.annotations.SerializedName

data class ConversationsListResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: List<ConversationSummary>? = null,
    @SerializedName("message") val message: String? = null
)

data class ConversationSummary(
    @SerializedName("_id") val id: String,
    @SerializedName("nom") val nom: String? = null,
    @SerializedName("participants") val participants: List<ConversationParticipant>,
    @SerializedName("estGroupe") val estGroupe: Boolean = false,
    @SerializedName("createur") val createur: String = "",
    @SerializedName("dernierMessage") val dernierMessage: String? = null,
    @SerializedName("dernierMessageDate") val dernierMessageDate: String? = null,
    @SerializedName("messagesNonLus") val messagesNonLus: Int = 0
) {
    // ✅ MÉTHODES UTILITAIRES
    fun getDisplayName(currentUserId: String): String {
        return when {
            estGroupe -> nom ?: "Groupe sans nom"
            else -> {
                val otherParticipant = participants.find { it.utilisateur != currentUserId }
                otherParticipant?.username ?: "Utilisateur inconnu"
            }
        }
    }

    fun getOtherUserId(currentUserId: String): String {
        return if (!estGroupe) {
            participants.find { it.utilisateur != currentUserId }?.utilisateur ?: ""
        } else {
            ""
        }
    }

    fun getDisplayPicture(currentUserId: String): String? {
        return if (!estGroupe) {
            participants.find { it.utilisateur != currentUserId }?.profilePicture
        } else {
            null // Les groupes n'ont pas de photo de profil unique
        }
    }
}