package com.example.supchat.models.response

import com.google.gson.annotations.SerializedName

data class UserProfileData(
    @SerializedName("email")
    val email: String = "",

    @SerializedName("username")
    val username: String = "",

    @SerializedName("profilePicture")
    val profilePicture: String? = null,

    @SerializedName("role")
    val role: String = "",

    @SerializedName("status")
    val status: String = "",

    @SerializedName("estConnecte")
    val estConnecte: Boolean = false,

    @SerializedName("theme")
    val theme: String = "sombre"
)