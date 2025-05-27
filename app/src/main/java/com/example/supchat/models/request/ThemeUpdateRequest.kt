package com.example.supchat.models.request

import com.google.gson.annotations.SerializedName

data class ThemeUpdateRequest(
    @SerializedName("theme")
    val theme: String
)