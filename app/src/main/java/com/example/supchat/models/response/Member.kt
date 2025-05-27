package com.example.supchat.models.response

import com.google.gson.annotations.SerializedName

data class Member(
    @SerializedName("_id") val id: String,
    val username: String,
    val role: String,
    val email: String? = null
)