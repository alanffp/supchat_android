package com.example.supchat.models.request

import com.google.gson.annotations.SerializedName

/**
 * Requête pour mettre à jour le mot de passe
 */
data class PasswordUpdateRequest(
    @SerializedName("currentPassword")
    val currentPassword: String,

    @SerializedName("newPassword")
    val newPassword: String,

    @SerializedName("confirmPassword")
    val confirmPassword: String
)

/**
 * Réponse à la mise à jour du mot de passe
 */
data class PasswordUpdateResponse(
    @SerializedName("success")
    val success: Boolean = false,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("error")
    val error: String? = null
)