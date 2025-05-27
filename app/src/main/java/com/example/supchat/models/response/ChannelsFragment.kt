package com.example.supchat.models.response

data class Channel(
    val id: String,
    val nom: String,
    val description: String?
)

data class ChannelsResponse(
    val status: String,
    val resultats: Int,
    val data: ChannelsData
)

data class ChannelsData(
    val canaux: List<Channel>
)