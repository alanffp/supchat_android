package com.example.supchat.models.response

data class MessagesResponse(
    val status: String,
    val resultats: Int,
    val data: MessagesData
)

data class MessagesData(
    val messages: List<Message>
)