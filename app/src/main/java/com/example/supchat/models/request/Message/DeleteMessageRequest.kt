package com.example.supchat.models.request.Message

import com.google.gson.annotations.SerializedName

data class DeleteMessageRequest(
    @SerializedName("confirmation")
    val confirmation: Boolean = true
)
