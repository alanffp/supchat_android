package com.example.supchat

data class FieldError(
    val champ: String,
    val message: String
)

data class ErrorResponse(
    val errors: List<FieldError>

)