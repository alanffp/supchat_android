package com.example.supchat.models.request

import com.google.gson.annotations.SerializedName

data class CreateConversationRequest(
    @SerializedName("nom") val nom: String? = null, // Nom de la conversation (optionnel pour conversations privées)
    @SerializedName("participants") val participants: List<String>, // Liste des IDs des participants
    @SerializedName("estGroupe") val estGroupe: Boolean = false // true pour groupe, false pour conversation privée
)