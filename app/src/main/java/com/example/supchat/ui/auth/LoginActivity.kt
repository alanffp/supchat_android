package com.example.supchat.ui.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.supchat.R
import com.example.supchat.api.ApiClient
import com.example.supchat.auth.OAuthHelper
import com.example.supchat.models.request.LoginRequest
import com.example.supchat.models.response.LoginResponse
import com.example.supchat.ui.home.HomeActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity(), OAuthHelper.OAuthCallback {
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var forgotPasswordText: TextView
    private lateinit var googleLoginButton: Button
    private lateinit var facebookLoginButton: Button
    private lateinit var microsoftLoginButton: Button
    private lateinit var signUpText: TextView
    private lateinit var oAuthHelper: OAuthHelper

    companion object {
        private const val TAG = "LoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Appliquer le thème sauvegardé
        applyTheme()

        setContentView(R.layout.activity_login)

        // Initialiser les vues
        initializeViews()

        // Initialiser OAuth Helper
        oAuthHelper = OAuthHelper(this)

        // Configurer les listeners
        setupListeners()

        // Gérer les deep links (callbacks OAuth)
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.let { handleDeepLink(it) }
    }

    private fun initializeViews() {
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        forgotPasswordText = findViewById(R.id.forgotPasswordText)
        googleLoginButton = findViewById(R.id.googleLoginButton)
        facebookLoginButton = findViewById(R.id.facebookLoginButton)
        microsoftLoginButton = findViewById(R.id.microsoftLoginButton)
        signUpText = findViewById(R.id.signUpText)
    }

    private fun setupListeners() {
        // Connexion classique
        loginButton.setOnClickListener {
            performLogin()
        }

        // Mot de passe oublié
        forgotPasswordText.setOnClickListener {
            val intent = Intent(this, ForgotPasswordActivity::class.java)
            startActivity(intent)
        }

        signUpText.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        // OAuth Login Buttons
        googleLoginButton.setOnClickListener {
            Log.d(TAG, "Début de l'authentification Google")
            oAuthHelper.initiateGoogleLogin(this)
        }

        facebookLoginButton.setOnClickListener {
            Log.d(TAG, "Début de l'authentification Facebook")
            oAuthHelper.initiateFacebookLogin(this)
        }

        microsoftLoginButton.setOnClickListener {
            Log.d(TAG, "Début de l'authentification Microsoft")
            oAuthHelper.initiateMicrosoftLogin(this)
        }
    }

    private fun handleDeepLink(intent: Intent) {
        val data: Uri? = intent.data
        if (data != null) {
            val url = data.toString()
            Log.d(TAG, "Deep link reçu: $url")

            if (oAuthHelper.isOAuthCallback(url)) {
                // ✅ AJOUT: Vérifier s'il y a une erreur dans l'URL
                if (oAuthHelper.hasError(url)) {
                    val errorMessage = oAuthHelper.getErrorMessage(url)
                    Log.e(TAG, "Erreur OAuth détectée: $errorMessage")
                    onError(errorMessage)
                    return
                }

                val (code, state) = oAuthHelper.parseCallbackUrl(url)

                if (code != null) {
                    val provider = oAuthHelper.getProviderFromUrl(url)

                    when (provider) {
                        "google" -> {
                            Log.d(TAG, "Traitement du callback Google")
                            oAuthHelper.handleGoogleCallback(code, state, this)
                        }
                        "facebook" -> {
                            Log.d(TAG, "Traitement du callback Facebook")
                            oAuthHelper.handleFacebookCallback(code, state, this)
                        }
                        "microsoft" -> {
                            Log.d(TAG, "Traitement du callback Microsoft")
                            oAuthHelper.handleMicrosoftCallback(code, state, this)
                        }
                        else -> {
                            Log.e(TAG, "Provider OAuth non reconnu dans l'URL: $url")
                            Toast.makeText(this, "Erreur: Provider non reconnu", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.e(TAG, "Code d'autorisation manquant dans l'URL de callback")
                    Toast.makeText(this, "Erreur: Code d'autorisation manquant", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun performLogin() {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        if (email.isEmpty()) {
            emailEditText.error = "L'email est requis"
            emailEditText.requestFocus()
            return
        }

        if (password.isEmpty()) {
            passwordEditText.error = "Le mot de passe est requis"
            passwordEditText.requestFocus()
            return
        }

        // Désactiver le bouton pendant la connexion
        loginButton.isEnabled = false
        loginButton.text = "Connexion..."

        val loginRequest = LoginRequest(email, password)

        ApiClient.instance.loginUser(loginRequest)
            .enqueue(object : Callback<LoginResponse> {
                override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                    loginButton.isEnabled = true
                    loginButton.text = "Se connecter"

                    if (response.isSuccessful) {
                        val loginResponse = response.body()
                        if (loginResponse != null && loginResponse.success && !loginResponse.token.isNullOrEmpty()) {
                            onSuccess(loginResponse)
                        } else {
                            val errorMessage = loginResponse?.message ?: loginResponse?.error ?: "Erreur inconnue"
                            onError("Échec de la connexion: $errorMessage")
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "Erreur de connexion: ${response.code()}, $errorBody")
                        onError("Erreur de connexion: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    loginButton.isEnabled = true
                    loginButton.text = "Se connecter"
                    Log.e(TAG, "Erreur réseau lors de la connexion", t)
                    onError("Erreur réseau: ${t.message}")
                }
            })
    }

    // Implémentation de OAuthHelper.OAuthCallback
    override fun onSuccess(loginResponse: LoginResponse) {
        Log.d(TAG, "Connexion réussie")

        // Sauvegarder les informations de l'utilisateur
        val sharedPreferences = getSharedPreferences("SupChatPrefs", MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        editor.putString("auth_token", loginResponse.token)
        editor.putString("user_id", loginResponse.data.user.userId)
        editor.putString("username", loginResponse.data.user.username)
        editor.putString("email", loginResponse.data.user.email)
        editor.putString("role", loginResponse.data.user.role)
        editor.putBoolean("is_logged_in", true)
        editor.apply()

        Log.d(TAG, "Informations utilisateur sauvegardées")

        // Rediriger vers HomeActivity
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onError(error: String) {
        Log.e(TAG, "Erreur de connexion: $error")
        runOnUiThread {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        }
    }

    private fun applyTheme() {
        val sharedPreferences = getSharedPreferences("SupChatPrefs", MODE_PRIVATE)
        val isDarkMode = sharedPreferences.getBoolean("dark_mode", false)

        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
}