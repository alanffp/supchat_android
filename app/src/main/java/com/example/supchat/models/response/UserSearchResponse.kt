package com.example.supchat.models.response

import com.google.gson.annotations.SerializedName

data class UserSearchResponse(
    @SerializedName("success")
    val success: Boolean = false,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("data")
    val data: UserSearchDataWrapper? = null,

    @SerializedName("error")
    val error: String? = null
)

data class UserSearchDataWrapper(
    @SerializedName("users")
    val users: List<UserSearchData>? = null,

    // Ajoutez d'autres champs si l'objet data contient d'autres informations
    @SerializedName("total")
    val total: Int = 0
)