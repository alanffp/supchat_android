package com.example.supchat.models.response.messageprivate

import com.google.gson.annotations.SerializedName

data class ConversationDetailsResponse(
    @SerializedName("status") val status: String = "",
    @SerializedName("data") val data: ConversationDetailsData? = null
)

data class ConversationDetailsData(
    @SerializedName("conversation") val conversation: ConversationDetails? = null
)