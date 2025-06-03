
package com.example.supchat.models.response.messageprivate

import com.google.gson.annotations.SerializedName

data class MessageFichier(
    @SerializedName("nom") val nom: String = "",
    @SerializedName("type") val type: String = "",
    @SerializedName("url") val url: String = "",
    @SerializedName("urlPreview") val urlPreview: String? = null,
    @SerializedName("taille") val taille: Long = 0
)
