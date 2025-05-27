package com.example.supchat.models.response

import com.google.gson.annotations.SerializedName

data class Workspace(
    @SerializedName("_id")
    val id: String,
    val nom: String,
    val description: String?,
    val proprietaire: String,
    val visibilite: String?,
)