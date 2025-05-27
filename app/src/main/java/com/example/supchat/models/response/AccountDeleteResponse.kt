package com.example.supchat.models.response

import com.google.gson.annotations.SerializedName

data class AccountDeleteResponse(
    @SerializedName("success")
    val success: Boolean = false,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("error")
    val error: String? = null
)