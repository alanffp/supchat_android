package com.example.supchat.models.response


// Le modèle de réponse WorkspacesResponse doit correspondre à la structure exacte renvoyée par l'API
data class WorkspacesResponse(
    val status: String,
    val resultats: Int,
    val data: WorkspacesData
)

data class WorkspacesData(
    val workspaces: List<Workspace>
)