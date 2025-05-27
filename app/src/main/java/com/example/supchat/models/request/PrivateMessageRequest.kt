// PrivateMessageRequest.kt
package com.example.supchat.models.request

import com.google.gson.annotations.SerializedName

data class PrivateMessageRequest(
    @SerializedName("content")
    val content: String
)