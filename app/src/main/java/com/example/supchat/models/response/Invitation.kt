package com.example.supchat.models.response

import com.google.gson.annotations.SerializedName

data class Invitation(
    @SerializedName("_id") val id: String,
    val email: String,
    val token: String,
    @SerializedName("dateInvitation") val dateInvitation: Long? = null
)