package com.example.supchat.models.request.Message

import com.google.gson.annotations.SerializedName

data class ReponseRequest(
    @SerializedName("contenu")
    val contenu: String
)

data class ReactionRequest(
    @SerializedName("emoji")
    val emoji: String
)

data class MessageRequest(
    @SerializedName("contenu")
    val contenu: String
)