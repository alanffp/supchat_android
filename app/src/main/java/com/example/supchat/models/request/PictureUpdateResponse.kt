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
    val data: PictureData? = null
)

/**
 * Données de la photo dans la réponse
 */
data class PictureData(
    @SerializedName("profilePicture")
    val profilePicture: String = ""
)