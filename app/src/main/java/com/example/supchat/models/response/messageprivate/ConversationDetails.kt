package com.example.supchat.models.response.messageprivate

import com.google.gson.annotations.SerializedName

data class ConversationDetails(
    @SerializedName("_id") val _id: String = "",
    @SerializedName("nom") val nom: String? = null,
    @SerializedName("participants") val participants: List<ConversationParticipant> = emptyList(),
    @SerializedName("createur") val createur: String = "",
    @SerializedName("dateCreation") val dateCreation: String = "",
    @SerializedName("dernierMessage") val dernierMessage: String? = null,
    @SerializedName("estGroupe") val estGroupe: Boolean = false
)