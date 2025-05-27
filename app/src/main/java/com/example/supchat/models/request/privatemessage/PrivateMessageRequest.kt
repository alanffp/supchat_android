package com.example.supchat.models.request.privatemessage

import com.google.gson.annotations.SerializedName

data class PrivateMessageRequest(
    @SerializedName("contenu")
    val contenu: String,

    @SerializedName("reponseA")
    val reponseA: String? = null
)