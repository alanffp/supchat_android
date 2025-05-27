package com.example.supchat.ui.auth

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.supchat.R
import com.example.supchat.api.ApiClient
import com.example.supchat.models.request.ForgotPasswordRequest
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var resetPasswordButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        // Initialisation des vues
        emailEditText = findViewById(R.id.emailEditText)
        resetPasswordButton = findViewById(R.id.resetPasswordButton)

        resetPasswordButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()

            // Validation de base de l'email
            if (email.isEmpty()) {
                emailEditText.error = "Veuillez saisir votre email"
                return@setOnClickListener
            }

            // Vérification du format de l'email
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailEditText.error = "Format d'email invalide"
                return@setOnClickListener
            }

            // Appel de l'API pour la demande de réinitialisation
            sendPasswordResetRequest(email)
        }
    }

    private fun sendPasswordResetRequest(email: String) {
        // Désactiver le bouton pendant la requête
        resetPasswordButton.isEnabled = false

        // Création de la requête
        val request = ForgotPasswordRequest(email)

        // Appel de l'API
        ApiClient.instance.forgotPassword(request).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                // Réactiver le bouton
                resetPasswordButton.isEnabled = true

                if (response.isSuccessful) {
                    // Succès : afficher un message
                    Toast.makeText(
                        this@ForgotPasswordActivity,
                        "Un email de réinitialisation a été envoyé",
                        Toast.LENGTH_LONG
                    ).show()

                    // Fermer l'activité après l'envoi
                    finish()
                } else {
                    // Gestion des erreurs
                    try {
                        val errorBody = response.errorBody()?.string()
                        Log.e("ForgotPassword", "Erreur: $errorBody")
                        Toast.makeText(
                            this@ForgotPasswordActivity,
                            "Erreur : Impossible d'envoyer la demande de réinitialisation",
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        Log.e("ForgotPassword", "Erreur de traitement: ${e.message}")
                        Toast.makeText(
                            this@ForgotPasswordActivity,
                            "Une erreur est survenue",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                // Réactiver le bouton
                resetPasswordButton.isEnabled = true

                // Gestion de l'échec de la requête
                Log.e("ForgotPassword", "Échec de la requête: ${t.message}")
                Toast.makeText(
                    this@ForgotPasswordActivity,
                    "Échec de la connexion : ${t.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }
}