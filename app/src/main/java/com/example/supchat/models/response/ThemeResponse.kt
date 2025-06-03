package com.example.supchat.models.response

import com.google.gson.annotations.SerializedName

/**
 * Réponse à la mise à jour du thème
 */
data class ThemeResponse(
    @SerializedName("success")
    val success: Boolean = false,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("error")
    val error: String? = null,

    @SerializedName("data")
    val data: ThemeData? = null
)

/**
 * Données de thème dans la réponse
 */
data class ThemeData(
    @SerializedName("theme")
    val theme: String? = null,

    @SerializedName("updated_at")
    val updatedAt: String? = null
)