package com.example.supchat.models.request.Workspace

import com.google.gson.annotations.SerializedName

data class WorkspaceAddMemberRequest(
    @SerializedName("utilisateurId") val utilisateurId: String,
    @SerializedName("role") val role: String = "membre"
)
