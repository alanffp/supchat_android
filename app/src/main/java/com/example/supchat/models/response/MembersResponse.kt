package com.example.supchat.models.response

data class MembersResponse(
    val status: String,
    val resultats: Int,
    val data: MembersData
)

data class MembersData(
    val membres: List<Member>
)
