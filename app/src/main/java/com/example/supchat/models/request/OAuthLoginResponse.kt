package com.example.supchat.models.request

// Modèle pour la réponse d'authentification OAuth
data class OAuthLoginResponse(
    val token: String,
    val userId: String,
    val email: String,
    val username: String
)

// Modèle pour la requête de callback OAuth
data class OAuthCallbackRequest(
    val code: String,
    val state: String? = null
)