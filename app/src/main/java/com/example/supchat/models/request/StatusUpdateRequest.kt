package com.example.supchat.models.request

import com.google.gson.annotations.SerializedName

/**
 * Requête pour mettre à jour le statut de l'utilisateur
 */
data class StatusUpdateRequest(
    @SerializedName("status")
    val status: String
)

/**
 * Réponse à la mise à jour du statut
 */
data class StatusResponse(
    @SerializedName("success")
    val success: Boolean = false,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("error")
    val error: String? = null,

    @SerializedName("data")
    val data: StatusData? = null
)

/**
 * Données de statut dans la réponse
 */
data class StatusData(
    @SerializedName("status")
    val status: String? = null,

    @SerializedName("updated_at")
    val updatedAt: String? = null
)