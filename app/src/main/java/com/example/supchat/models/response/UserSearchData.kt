package com.example.supchat.models.response

import com.google.gson.annotations.SerializedName

data class UserSearchData(
    @SerializedName("_id")
    val id: String,

    @SerializedName("username")
    val username: String,

    @SerializedName("email")
    val email: String,

    @SerializedName("profilePicture")
    val profilePicture: String? = null,

    @SerializedName("status")
    val status: String? = null,

    @SerializedName("role")
    val role: String? = null,

    @SerializedName("lastLogin")
    val lastLogin: String? = null,

    @SerializedName("isVerified")
    val isVerified: Boolean = false
)