package com.example.supchat.models.request

import com.google.gson.annotations.SerializedName

data class AccountDeleteRequest(
    @SerializedName("password")
    val password: String
)