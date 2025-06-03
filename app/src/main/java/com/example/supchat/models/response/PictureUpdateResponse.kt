package com.example.supchat.models.response

import com.google.gson.annotations.SerializedName

/**
 * Réponse à la mise à jour de la photo de profil
 */
data class PictureUpdateResponse(
    @SerializedName("success")
    val success: Boolean = false,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("error")
    val error: String? = null,

    @SerializedName("data")
    val data: PictureUpdateData? = null
)

/**
 * Données de la photo dans la réponse
 */
data class PictureUpdateData(
    @SerializedName("profilePicture")
    val profilePicture: String? = null,

    @SerializedName("profilePictureUrl")
    val profilePictureUrl: String? = null,

    @SerializedName("profile_picture_url")
    val profilePictureUrlAlt: String? = null,

    @SerializedName("url")
    val url: String? = null,

    @SerializedName("filename")
    val filename: String? = null,

    @SerializedName("updated_at")
    val updatedAt: String? = null
)