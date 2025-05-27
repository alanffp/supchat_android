package com.example.supchat.models.request

data class User(
    val email: String,
    val password: String,
    val username: String,
    val firstName: String,
    val lastName: String,
    val confirmPassword: String
)