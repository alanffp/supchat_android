package com.example.supchat.models.request

import com.example.supchat.models.response.UserProfileData
import com.google.gson.annotations.SerializedName

/**
 * Requête pour mettre à jour le profil utilisateur
 */
data class ProfileUpdateRequest(
    @SerializedName("username")
    val username: String? = null,

    @SerializedName("email")
    val email: String? = null
)

/**
 * Réponse à la mise à jour du profil
 */
data class ProfileUpdateResponse(
    @SerializedName("success")
    val success: Boolean = false,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("error")
    val error: String? = null,

    @SerializedName("data")
    val data: UserProfileData? = null
)