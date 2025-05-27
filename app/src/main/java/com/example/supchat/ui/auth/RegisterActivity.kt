package com.example.supchat.ui.auth

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.supchat.R
import com.example.supchat.models.request.User
import com.example.supchat.FieldError
import com.example.supchat.ErrorResponse
import com.example.supchat.api.ApiClient
import com.google.gson.Gson
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RegisterActivity : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var usernameEditText: EditText
    private lateinit var firstNameEditText: EditText
    private lateinit var lastNameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var registerButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Initialisation des vues
        emailEditText = findViewById(R.id.emailEditText)
        usernameEditText = findViewById(R.id.usernameEditText)
        firstNameEditText = findViewById(R.id.firstNameEditText)
        lastNameEditText = findViewById(R.id.lastNameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText)
        registerButton = findViewById(R.id.registerButton)

        registerButton.setOnClickListener {
            // Récupération des valeurs saisies
            val email = emailEditText.text.toString()
            val username = usernameEditText.text.toString()
            val firstName = firstNameEditText.text.toString()
            val lastName = lastNameEditText.text.toString()
            val password = passwordEditText.text.toString()
            val confirmPassword = confirmPasswordEditText.text.toString()

            // Validation des mots de passe
            if (password != confirmPassword) {
                Toast.makeText(this, "Les mots de passe ne correspondent pas", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Création de l'objet utilisateur
            val user = User(
                email = email,
                username = username,
                firstName = firstName,
                lastName = lastName,
                password = password,
                confirmPassword = confirmPassword
            )

            // Appel de l'API d'inscription
            registerUser(user)
        }
    }

    private fun registerUser(user: User) {
        ApiClient.instance.registerUser(user).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    // Inscription réussie
                    Toast.makeText(this@RegisterActivity, "Inscription réussie", Toast.LENGTH_SHORT).show()
                    finish() // Fermer l'activité d'inscription
                } else {
                    try {
                        // Lecture du corps d'erreur
                        val errorBodyString = response.errorBody()?.string()
                        Log.e("Register", "Corps de l'erreur : $errorBodyString")

                        if (errorBodyString != null) {
                            val gson = Gson()
                            try {
                                // Parsing des erreurs
                                val errorsObject = gson.fromJson(errorBodyString, ErrorResponse::class.java)
                                val errors = errorsObject.errors

                                // Affichage des erreurs par champ
                                for (error in errors) {
                                    when (error.champ) {
                                        "email" -> emailEditText.error = error.message
                                        "username" -> usernameEditText.error = error.message
                                        "firstName" -> firstNameEditText.error = error.message
                                        "lastName" -> lastNameEditText.error = error.message
                                        "password" -> passwordEditText.error = error.message
                                        "confirmPassword" -> confirmPasswordEditText.error = error.message
                                        else -> Toast.makeText(
                                            this@RegisterActivity,
                                            error.message,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("Register", "Erreur de parsing JSON : ${e.message}")
                                Log.e("Register", "JSON reçu : $errorBodyString")
                                Toast.makeText(
                                    this@RegisterActivity,
                                    "Format de réponse d'erreur inattendu",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            Toast.makeText(
                                this@RegisterActivity,
                                "Erreur: Corps vide",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        Log.e(
                            "Register",
                            "Exception lors du traitement de l'erreur : ${e.message}",
                            e
                        )
                        Toast.makeText(
                            this@RegisterActivity,
                            "Exception lors du traitement: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Toast.makeText(this@RegisterActivity, "Échec: ${t.message}", Toast.LENGTH_SHORT).show()
                Log.e("API_FAILURE", "Exception: ${t.localizedMessage}")
            }
        })
    }
}