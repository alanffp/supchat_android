package com.example.supchat.models.request

// Modèle pour la demande de réinitialisation de mot de passe
data class ForgotPasswordRequest(
    val email: String
)