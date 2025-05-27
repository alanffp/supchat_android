package com.example.supchat.models.response

data class CanauxResponse(
    val status: String,
    val resultats: Int,
    val data: CanauxData
)

data class CanauxData(
    val canaux: List<Canal>
)