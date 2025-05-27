package com.example.supchat.models.request

import com.google.gson.annotations.SerializedName

data class SendConversationMessageRequest(
    @SerializedName("contenu") val contenu: String,
    @SerializedName("reponseA") val reponseA: String? = null
)