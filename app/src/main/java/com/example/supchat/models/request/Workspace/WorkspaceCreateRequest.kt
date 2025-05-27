package com.example.supchat.models.request.Workspace

data class WorkspaceCreateRequest(
    val nom: String,
    val description: String?,
    val visibilite: String
)
