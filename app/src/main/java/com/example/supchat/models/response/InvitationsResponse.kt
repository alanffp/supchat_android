
package com.example.supchat.models.response

data class InvitationsResponse(
    val status: String,
    val resultats: Int,
    val data: InvitationsData
)

data class InvitationsData(
    val invitations: List<Invitation>
)