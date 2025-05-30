package com.example.supchat.models.response.messageprivate

import com.google.gson.annotations.SerializedName

data class ConversationParticipant(
    @SerializedName("utilisateur") val utilisateur: String = "",
    @SerializedName("username") val username: String = "",
    @SerializedName("profilePicture") val profilePicture: String? = null,
    @SerializedName("dateAjout") val dateAjout: String = "",
    val role: String = "participant" // "createur" ou "participant" - calculé côté client
)