package com.example.supchat.ui.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.supchat.R
import com.example.supchat.SupChatApplication
import com.example.supchat.api.ApiClient
import com.example.supchat.models.request.LoginRequest
import com.example.supchat.models.response.LoginResponse
import com.example.supchat.ui.home.HomeActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {
    // Composants de l'interface utilisateur
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var forgotPasswordText: TextView
    private lateinit var googleLoginButton: Button
    private lateinit var facebookLoginButton: Button
    private lateinit var microsoftLoginButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialisation des vues
        initializeViews()

        // Configuration des listeners
        setupListeners()
    }

    private fun initializeViews() {
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        forgotPasswordText = findViewById(R.id.forgotPasswordText)
        googleLoginButton = findViewById(R.id.googleLoginButton)
        facebookLoginButton = findViewById(R.id.facebookLoginButton)
        microsoftLoginButton = findViewById(R.id.microsoftLoginButton)
    }

    private fun setupListeners() {
        // Mot de passe oublié
        forgotPasswordText.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        // Connexion standard
        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString()

            if (validateLoginInput(email, password)) {
                performStandardLogin(email, password)
            }
        }

        // Connexions OAuth (à adapter selon votre besoin)
        googleLoginButton.setOnClickListener {
            initiateOAuthLogin("google")
        }
        facebookLoginButton.setOnClickListener {
            initiateOAuthLogin("facebook")
        }
        microsoftLoginButton.setOnClickListener {
            initiateOAuthLogin("microsoft")
        }
    }

    private fun initiateOAuthLogin(provider: String) {
        val loginCall = when (provider) {
            "google" -> ApiClient.instance.initiateGoogleLogin()
            "facebook" -> ApiClient.instance.initiateFacebookLogin()
            "microsoft" -> ApiClient.instance.initiateMicrosoftLogin()
            else -> throw IllegalArgumentException("Provider non supporté")
        }

        loginCall.enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    val authUrl = response.headers().get("Location")
                    if (authUrl != null) {
                        openAuthInBrowser(authUrl)
                    } else {
                        Toast.makeText(
                            this@LoginActivity,
                            "URL de connexion $provider non disponible",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this@LoginActivity,
                        "Erreur de connexion $provider",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Toast.makeText(
                    this@LoginActivity,
                    "Échec de la connexion $provider: ${t.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun openAuthInBrowser(authUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
        startActivity(intent)
    }
    private fun validateLoginInput(email: String, password: String): Boolean {
        return when {
            email.isEmpty() -> {
                emailEditText.error = "Veuillez saisir votre email"
                false
            }
            password.isEmpty() -> {
                passwordEditText.error = "Veuillez saisir votre mot de passe"
                false
            }
            else -> true
        }
    }

    private fun performStandardLogin(email: String, password: String) {
        val loginRequest = LoginRequest(email, password)
        ApiClient.instance.loginUser(loginRequest).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                Log.d("LoginActivity", "Réponse API reçue: ${response.code()}")
                when {
                    response.isSuccessful && response.body() != null -> {
                        Log.d("LoginActivity", "Réponse réussie avec body: ${response.body()}")
                        saveUserSession(response.body()!!)
                        navigateToMainActivity()
                        handleLoginSuccess(response.body())
                    }
                    else -> {
                        Log.d("LoginActivity", "Erreur API: ${response.code()}, ${response.message()}")
                        handleLoginError(response)
                    }
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                handleLoginFailure(t)
            }
        })
    }

    private fun saveUserSession(loginResponse: LoginResponse) {
        // Logs avant la sauvegarde
        Log.d("LoginActivity", "Sauvegarde session - Token: ${loginResponse.token?.take(10)}..., UserID: ${loginResponse.data?.user?.userId}")

        getSharedPreferences("SupChatPrefs", MODE_PRIVATE).edit().apply {
            putString("auth_token", loginResponse.token)
            putString("user_id", loginResponse.data.user.userId) // ✅ CORRIGÉ: user_id au lieu de userid
            putString("username", loginResponse.data.user.username) // Ajouter le username si disponible
            apply()
        }

        // ✅ NOUVEAU: Initialiser WebSocket après connexion réussie
        val app = application as SupChatApplication
        loginResponse.token?.let { token ->
            app.initializeWebSocket(token)
            Log.d("LoginActivity", "WebSocket initialisé après connexion")
        }

        // Vérification immédiate
        val savedUserId = getSharedPreferences("SupChatPrefs", MODE_PRIVATE).getString("user_id", "")
        Log.d("LoginActivity", "Vérification après sauvegarde - UserID sauvegardé: $savedUserId")
    }

    private fun navigateToMainActivity() {
        Toast.makeText(this, "Connexion réussie", Toast.LENGTH_SHORT).show()

        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun handleLoginSuccess(response: LoginResponse?) {
        // Extraire l'ID utilisateur correctement
        val userId = response?.data?.user?.userId
        val username = response?.data?.user?.username
        val token = response?.token

        Log.d("LoginActivity", "Données de connexion: userId=${userId}, username=${username}, token=${token?.take(10)}...")

        if (userId.isNullOrEmpty()) {
            Log.e("LoginActivity", "ERREUR: ID utilisateur manquant ou null!")
            Toast.makeText(this, "Erreur: données utilisateur incomplètes", Toast.LENGTH_SHORT).show()
            return
        }

        // Sauvegarder les données utilisateur
        val editor = getSharedPreferences("SupChatPrefs", Context.MODE_PRIVATE).edit()
        editor.putString("user_id", userId) // ✅ CORRIGÉ
        editor.putString("username", username)
        editor.putString("auth_token", token)
        editor.apply()

        // ✅ NOUVEAU: Initialiser WebSocket
        val app = application as SupChatApplication
        token?.let {
            app.initializeWebSocket(it)
            Log.d("LoginActivity", "WebSocket initialisé avec succès")
        }

        // Vérification immédiate
        val savedUserId = getSharedPreferences("SupChatPrefs", Context.MODE_PRIVATE).getString("user_id", "NON_TROUVÉ")
        Log.d("LoginActivity", "ID utilisateur sauvegardé: $savedUserId")
    }

    private fun handleLoginError(response: Response<LoginResponse>) {
        val errorMessage = try {
            response.errorBody()?.string() ?: "Erreur de connexion inconnue"
        } catch (e: Exception) {
            "Erreur de traitement"
        }
        Toast.makeText(this, "Email ou mot de passe incorrect", Toast.LENGTH_SHORT).show()
    }

    private fun handleLoginFailure(t: Throwable) {
        Toast.makeText(this, "Connexion impossible", Toast.LENGTH_SHORT).show()
    }
}