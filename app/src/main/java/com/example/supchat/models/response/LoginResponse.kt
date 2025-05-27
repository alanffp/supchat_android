package com.example.supchat.models.response

import com.google.gson.annotations.SerializedName


data class LoginResponse(
    val token: String?,
    @SerializedName("success")
    val success: Boolean = false,

    @SerializedName("message")
    val message: String?,

    @SerializedName("error")
    val error: String?,

    @SerializedName("data")
    val data: Responsedata
)

data class Responsedata(
    @SerializedName("user")
    val user: UserData
)
data class UserData(
    @SerializedName("_id")
    val userId: String,
    val email: String,
    val username: String,
    val role: String
)

