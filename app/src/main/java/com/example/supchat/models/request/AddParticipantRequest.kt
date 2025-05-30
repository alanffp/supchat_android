package com.example.supchat.models.request

import com.google.gson.annotations.SerializedName

data class AddParticipantRequest(
    @SerializedName("userId") val userId: String
)