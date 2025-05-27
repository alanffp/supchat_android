package com.example.supchat.ui.home

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.supchat.R
import com.example.supchat.api.ApiClient
import com.example.supchat.models.request.PasswordUpdateResponse
import com.example.supchat.models.request.ProfileUpdateResponse
import com.example.supchat.models.request.StatusResponse
import com.example.supchat.models.response.AccountDeleteResponse
import com.example.supchat.models.response.PictureUpdateResponse
import com.example.supchat.models.response.ThemeResponse
import com.example.supchat.models.response.UserProfileData
import com.example.supchat.models.response.UserProfileResponse
import de.hdodenhof.circleimageview.CircleImageView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.net.SocketTimeoutException
import java.net.UnknownHostException


class ProfileFragment : Fragment() {
    private lateinit var profileImage: CircleImageView
    private lateinit var usernameText: TextView
    private lateinit var emailText: TextView
    private lateinit var roleText: TextView
    private lateinit var statusText: TextView
    private lateinit var connectionStatusText: TextView
    private lateinit var connectionStatusIndicator: View
    private lateinit var themeRadioGroup: RadioGroup
    private lateinit var themeDarkRadio: RadioButton
    private lateinit var themeLightRadio: RadioButton
    private lateinit var saveThemeButton: Button
    private lateinit var statusSpinner: Spinner
    private lateinit var updateStatusButton: Button
    private lateinit var editProfileButton: Button
    private lateinit var changePasswordButton: Button
    private lateinit var deleteAccountButton: Button

    private val statusOptions =
        arrayOf("en ligne", "absent", "occupé", "ne pas déranger", "invisible")
    private var selectedImageUri: Uri? = null
    private val PICK_IMAGE_REQUEST = 1

    companion object {
        private const val TAG = "ProfileFragment"

        fun newInstance(): ProfileFragment {
            return ProfileFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        // Initialiser les vues
        profileImage = view.findViewById(R.id.profile_image)
        usernameText = view.findViewById(R.id.username_text)
        emailText = view.findViewById(R.id.email_text)
        roleText = view.findViewById(R.id.role_text)
        statusText = view.findViewById(R.id.status_text)
        connectionStatusText = view.findViewById(R.id.connection_status_text)
        connectionStatusIndicator = view.findViewById(R.id.connection_status_indicator)
        themeRadioGroup = view.findViewById(R.id.theme_radio_group)
        themeDarkRadio = view.findViewById(R.id.theme_dark)
        themeLightRadio = view.findViewById(R.id.theme_light)
        saveThemeButton = view.findViewById(R.id.save_theme_button)
        statusSpinner = view.findViewById(R.id.status_spinner)
        updateStatusButton = view.findViewById(R.id.update_status_button)
        editProfileButton = view.findViewById(R.id.edit_profile_button)
        changePasswordButton = view.findViewById(R.id.change_password_button)
        deleteAccountButton = view.findViewById(R.id.delete_account_button)

        // Configurer le bouton de suppression de compte
        deleteAccountButton.setOnClickListener {
            showDeleteAccountConfirmation()
        }

        // Configurer le spinner avec les options de statut
        val statusAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            statusOptions
        )
        statusSpinner.adapter = statusAdapter

        // Configurer les autres boutons
        saveThemeButton.setOnClickListener {
            val selectedTheme = when (themeRadioGroup.checkedRadioButtonId) {
                R.id.theme_dark -> "sombre"
                R.id.theme_light -> "clair"
                else -> "sombre" // Valeur par défaut
            }
            updateUserTheme(selectedTheme)
        }

        updateStatusButton.setOnClickListener {
            val selectedStatus = statusSpinner.selectedItem.toString()
            updateUserStatus(selectedStatus)
        }

        editProfileButton.setOnClickListener {
            showEditProfileDialog()
        }

        changePasswordButton.setOnClickListener {
            showChangePasswordDialog()
        }

        // Configurer l'image de profil pour permettre la modification
        profileImage.setOnClickListener {
            selectProfileImage()
        }

        // Charger les données du profil
        loadUserProfile()

        return view
    }

    private fun loadUserProfile() {
        // Récupérer le token d'authentification
        val token = requireActivity().getSharedPreferences(
            "SupChatPrefs",
            Context.MODE_PRIVATE
        ).getString("auth_token", "")

        if (token.isNullOrEmpty()) {
            Log.e(TAG, "Token d'authentification manquant")
            Toast.makeText(
                context,
                "Session expirée, veuillez vous reconnecter",
                Toast.LENGTH_SHORT
            ).show()
            (activity as? HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
            return
        }

        // Afficher un message de chargement
        showLoadingState()

        // Appeler l'API pour récupérer le profil utilisateur
        ApiClient.getUserProfile(token)
            .enqueue(object : Callback<UserProfileResponse> {
                override fun onResponse(
                    call: Call<UserProfileResponse>,
                    response: Response<UserProfileResponse>
                ) {
                    if (!isAdded) return  // Vérifier si le fragment est toujours attaché

                    if (response.isSuccessful) {
                        val userData = response.body()?.data
                        if (userData != null) {
                            updateUIWithUserData(userData)
                        } else {
                            showError("Données utilisateur vides ou invalides")
                        }
                    } else if (response.code() == 401) {
                        // Gérer l'erreur d'authentification
                        Log.e(TAG, "Erreur 401: Authentification expirée ou invalide")
                        Toast.makeText(
                            context,
                            "Session expirée, veuillez vous reconnecter",
                            Toast.LENGTH_SHORT
                        ).show()
                        (activity as? HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "Erreur API: ${response.code()}, message: $errorBody")
                        showError("Erreur lors de la récupération du profil: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<UserProfileResponse>, t: Throwable) {
                    if (!isAdded) return

                    Log.e(TAG, "Échec de l'appel API", t)
                    val errorMessage = when (t) {
                        is IllegalStateException -> "Erreur dans le format des données: ${t.message}"
                        is UnknownHostException -> "Erreur de connexion: Vérifiez votre connexion Internet"
                        is SocketTimeoutException -> "Délai d'attente dépassé pour la connexion"
                        else -> "Erreur réseau: ${t.message}"
                    }
                    showError(errorMessage)
                }
            })
    }

    private fun updateUIWithUserData(userData: UserProfileData) {
        // Mettre à jour les textes
        usernameText.text = userData.username
        emailText.text = userData.email
        roleText.text = userData.role
        statusText.text = userData.status

        // Mettre à jour le spinner avec le statut actuel
        val statusPosition = statusOptions.indexOf(userData.status)
        if (statusPosition != -1) {
            statusSpinner.setSelection(statusPosition)
        }

        // Mettre à jour l'indicateur de connexion
        if (userData.estConnecte) {
            connectionStatusIndicator.setBackgroundResource(R.drawable.status_indicator_connected)
            connectionStatusText.text = "En ligne"
        } else {
            connectionStatusIndicator.setBackgroundResource(R.drawable.status_indicator_disconnected)
            connectionStatusText.text = "Hors ligne"
        }

        // Mettre à jour la sélection du thème
        when (userData.theme) {
            "clair" -> themeLightRadio.isChecked = true
            else -> themeDarkRadio.isChecked = true // "sombre" par défaut
        }

        // Charger l'image de profil si disponible
        if (!userData.profilePicture.isNullOrEmpty()) {
            val baseUrl = "http://10.0.2.2:3000/uploads/profile/" // Adaptez selon votre API
            val imageUrl = baseUrl + userData.profilePicture

            Glide.with(this)
                .load(imageUrl)
                .apply(
                    RequestOptions()
                        .placeholder(R.drawable.default_avatar)
                        .error(R.drawable.default_avatar)
                )
                .into(profileImage)
        } else {
            // Image par défaut
            profileImage.setImageResource(R.drawable.default_avatar)
        }
    }

    private fun showLoadingState() {
        // Mettre des placeholders ou un état de chargement
        usernameText.text = "Chargement..."
        emailText.text = "Chargement..."
        roleText.text = "Chargement..."
        statusText.text = "Chargement..."
    }

    private fun showError(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        // Afficher un état d'erreur dans l'UI
        usernameText.text = "Non disponible"
        emailText.text = "Non disponible"
        roleText.text = "Non disponible"
        statusText.text = "Non disponible"
    }

    private fun updateUserTheme(theme: String) {
        // Récupérer le token d'authentification
        val token = requireActivity().getSharedPreferences(
            "SupChatPrefs",
            Context.MODE_PRIVATE
        ).getString("auth_token", "")

        if (token.isNullOrEmpty()) {
            Toast.makeText(
                context,
                "Session expirée, veuillez vous reconnecter",
                Toast.LENGTH_SHORT
            ).show()
            (activity as? HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
            return
        }

        val progressDialog = ProgressDialog(context)
        progressDialog.setMessage("Mise à jour du thème...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        // Appeler l'API client pour mettre à jour le thème
        ApiClient.updateUserTheme(token, theme)
            .enqueue(object : Callback<ThemeResponse> {
                override fun onResponse(
                    call: Call<ThemeResponse>,
                    response: Response<ThemeResponse>
                ) {
                    progressDialog.dismiss()

                    if (!isAdded) return

                    if (response.isSuccessful) {
                        Toast.makeText(
                            context,
                            "Thème mis à jour avec succès",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Vous pourriez également appliquer le thème ici
                        // applyTheme(theme)
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "Erreur API: ${response.code()}, message: $errorBody")
                        Toast.makeText(
                            context,
                            "Erreur lors de la mise à jour du thème: ${response.code()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<ThemeResponse>, t: Throwable) {
                    progressDialog.dismiss()
                    if (!isAdded) return

                    Log.e(TAG, "Échec de l'appel API", t)
                    Toast.makeText(
                        context,
                        "Erreur réseau: ${t.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
    }

    private fun updateUserStatus(status: String) {
        // Récupérer le token d'authentification
        val token = requireActivity().getSharedPreferences(
            "SupChatPrefs",
            Context.MODE_PRIVATE
        ).getString("auth_token", "")

        if (token.isNullOrEmpty()) {
            Toast.makeText(
                context,
                "Session expirée, veuillez vous reconnecter",
                Toast.LENGTH_SHORT
            ).show()
            (activity as? HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
            return
        }

        val progressDialog = ProgressDialog(context)
        progressDialog.setMessage("Mise à jour du statut...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        // Appeler l'API client pour mettre à jour le statut
        ApiClient.updateUserStatus(token, status)
            .enqueue(object : Callback<StatusResponse> {
                override fun onResponse(
                    call: Call<StatusResponse>,
                    response: Response<StatusResponse>
                ) {
                    progressDialog.dismiss()

                    if (!isAdded) return

                    if (response.isSuccessful) {
                        // Mettre à jour l'UI
                        statusText.text = status
                        Toast.makeText(
                            context,
                            "Statut mis à jour avec succès",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "Erreur API: ${response.code()}, message: $errorBody")
                        Toast.makeText(
                            context,
                            "Erreur lors de la mise à jour du statut: ${response.code()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<StatusResponse>, t: Throwable) {
                    progressDialog.dismiss()
                    if (!isAdded) return

                    Log.e(TAG, "Échec de l'appel API", t)
                    Toast.makeText(
                        context,
                        "Erreur réseau: ${t.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
    }

    private fun showEditProfileDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_profile, null)
        val usernameEditText = dialogView.findViewById<EditText>(R.id.edit_username)
        val emailEditText = dialogView.findViewById<EditText>(R.id.edit_email)

        // Pré-remplir avec les valeurs actuelles
        usernameEditText.setText(usernameText.text)
        emailEditText.setText(emailText.text)

        AlertDialog.Builder(requireContext())
            .setTitle("Modifier le profil")
            .setView(dialogView)
            .setPositiveButton("Enregistrer") { _, _ ->
                val newUsername = usernameEditText.text.toString()
                val newEmail = emailEditText.text.toString()

                if (newUsername.isBlank() || newEmail.isBlank()) {
                    Toast.makeText(context, "Tous les champs sont obligatoires", Toast.LENGTH_SHORT)
                        .show()
                    return@setPositiveButton
                }

                updateUserProfile(newUsername, newEmail)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showChangePasswordDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_change_password, null)
        val currentPasswordEditText = dialogView.findViewById<EditText>(R.id.current_password)
        val newPasswordEditText = dialogView.findViewById<EditText>(R.id.new_password)
        val confirmPasswordEditText = dialogView.findViewById<EditText>(R.id.confirm_password)

        AlertDialog.Builder(requireContext())
            .setTitle("Changer le mot de passe")
            .setView(dialogView)
            .setPositiveButton("Enregistrer") { _, _ ->
                val currentPassword = currentPasswordEditText.text.toString()
                val newPassword = newPasswordEditText.text.toString()
                val confirmPassword = confirmPasswordEditText.text.toString()

                if (currentPassword.isBlank() || newPassword.isBlank() || confirmPassword.isBlank()) {
                    Toast.makeText(context, "Tous les champs sont obligatoires", Toast.LENGTH_SHORT)
                        .show()
                    return@setPositiveButton
                }

                if (newPassword != confirmPassword) {
                    Toast.makeText(
                        context,
                        "Les mots de passe ne correspondent pas",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                updateUserPassword(currentPassword, newPassword, confirmPassword)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun updateUserProfile(username: String, email: String) {
        // Récupérer le token d'authentification
        val token = requireActivity().getSharedPreferences(
            "SupChatPrefs",
            Context.MODE_PRIVATE
        ).getString("auth_token", "")

        if (token.isNullOrEmpty()) {
            Toast.makeText(
                context,
                "Session expirée, veuillez vous reconnecter",
                Toast.LENGTH_SHORT
            ).show()
            (activity as? HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
            return
        }

        val progressDialog = ProgressDialog(context)
        progressDialog.setMessage("Mise à jour du profil...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        // Appeler l'API client pour mettre à jour le profil
        ApiClient.updateUserProfile(token, username, email)
            .enqueue(object : Callback<ProfileUpdateResponse> {
                override fun onResponse(
                    call: Call<ProfileUpdateResponse>,
                    response: Response<ProfileUpdateResponse>
                ) {
                    progressDialog.dismiss()

                    if (!isAdded) return

                    if (response.isSuccessful) {
                        // Mettre à jour l'UI
                        usernameText.text = username
                        emailText.text = email
                        Toast.makeText(
                            context,
                            "Profil mis à jour avec succès",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "Erreur API: ${response.code()}, message: $errorBody")
                        Toast.makeText(
                            context,
                            "Erreur lors de la mise à jour du profil: ${response.code()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<ProfileUpdateResponse>, t: Throwable) {
                    progressDialog.dismiss()
                    if (!isAdded) return

                    Log.e(TAG, "Échec de l'appel API", t)
                    Toast.makeText(
                        context,
                        "Erreur réseau: ${t.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
    }

    private fun updateUserPassword(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String
    ) {
        // Récupérer le token d'authentification
        val token = requireActivity().getSharedPreferences(
            "SupChatPrefs",
            Context.MODE_PRIVATE
        ).getString("auth_token", "")

        if (token.isNullOrEmpty()) {
            Toast.makeText(
                context,
                "Session expirée, veuillez vous reconnecter",
                Toast.LENGTH_SHORT
            ).show()
            (activity as? HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
            return
        }

        val progressDialog = ProgressDialog(context)
        progressDialog.setMessage("Mise à jour du mot de passe...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        // Appeler l'API client pour mettre à jour le mot de passe
        ApiClient.updateUserPassword(token, currentPassword, newPassword, confirmPassword)
            .enqueue(object : Callback<PasswordUpdateResponse> {
                override fun onResponse(
                    call: Call<PasswordUpdateResponse>,
                    response: Response<PasswordUpdateResponse>
                ) {
                    progressDialog.dismiss()

                    if (!isAdded) return

                    if (response.isSuccessful) {
                        Toast.makeText(
                            context,
                            "Mot de passe mis à jour avec succès",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "Erreur API: ${response.code()}, message: $errorBody")
                        Toast.makeText(
                            context,
                            "Erreur lors de la mise à jour du mot de passe: ${response.code()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<PasswordUpdateResponse>, t: Throwable) {
                    progressDialog.dismiss()
                    if (!isAdded) return

                    Log.e(TAG, "Échec de l'appel API", t)
                    Toast.makeText(
                        context,
                        "Erreur réseau: ${t.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
    }

    private fun selectProfileImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.data != null) {
            selectedImageUri = data.data

            // Afficher l'image sélectionnée
            Glide.with(this)
                .load(selectedImageUri)
                .apply(RequestOptions().centerCrop())
                .into(profileImage)

            // Demander confirmation avant d'envoyer l'image
            AlertDialog.Builder(requireContext())
                .setTitle("Mise à jour de la photo de profil")
                .setMessage("Voulez-vous utiliser cette image comme photo de profil ?")
                .setPositiveButton("Oui") { _, _ ->
                    uploadProfileImage()
                }
                .setNegativeButton("Non", null)
                .show()
        }
    }

    private fun uploadProfileImage() {
        if (selectedImageUri == null) return

        // Récupérer le token d'authentification
        val token = requireActivity().getSharedPreferences(
            "SupChatPrefs",
            Context.MODE_PRIVATE
        ).getString("auth_token", "")

        if (token.isNullOrEmpty()) {
            Toast.makeText(
                context,
                "Session expirée, veuillez vous reconnecter",
                Toast.LENGTH_SHORT
            ).show()
            (activity as? HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
            return
        }

        val progressDialog = ProgressDialog(context)
        progressDialog.setMessage("Mise à jour de la photo de profil...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        try {
            // Convertir l'URI en fichier
            val inputStream = requireContext().contentResolver.openInputStream(selectedImageUri!!)
            val file = File(requireContext().cacheDir, "profile_image.jpg")
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()

            // Envoyer le fichier
            ApiClient.updateProfilePicture(token, file)
                .enqueue(object : Callback<PictureUpdateResponse> {
                    override fun onResponse(
                        call: Call<PictureUpdateResponse>,
                        response: Response<PictureUpdateResponse>
                    ) {
                        progressDialog.dismiss()

                        if (!isAdded) return

                        if (response.isSuccessful) {
                            Toast.makeText(
                                context,
                                "Photo de profil mise à jour avec succès",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            val errorBody = response.errorBody()?.string()
                            Log.e(TAG, "Erreur API: ${response.code()}, message: $errorBody")
                            Toast.makeText(
                                context,
                                "Erreur lors de la mise à jour de la photo: ${response.code()}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onFailure(call: Call<PictureUpdateResponse>, t: Throwable) {
                        progressDialog.dismiss()
                        if (!isAdded) return

                        Log.e(TAG, "Échec de l'appel API", t)
                        Toast.makeText(
                            context,
                            "Erreur réseau: ${t.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                })
        } catch (e: Exception) {
            progressDialog.dismiss()
            Log.e(TAG, "Erreur lors de la préparation du fichier", e)
            Toast.makeText(
                context,
                "Erreur lors de la préparation de l'image: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }


    private fun showDeleteAccountConfirmation() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_confirm_delete_account, null)
        val passwordEditText = view.findViewById<EditText>(R.id.password_edit_text)

        AlertDialog.Builder(requireContext())
            .setTitle("Supprimer le compte")
            .setMessage("Cette action est irréversible. Pour confirmer, veuillez entrer votre mot de passe.")
            .setView(view)
            .setPositiveButton("Supprimer") { _, _ ->
                val password = passwordEditText.text.toString()
                if (password.isEmpty()) {
                    Toast.makeText(context, "Veuillez entrer votre mot de passe", Toast.LENGTH_SHORT).show()
                } else {
                    deleteUserAccount(password)
                }
            }
            .setNegativeButton("Annuler", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun deleteUserAccount(password: String) {
        // Obtenir le token
        val token = requireActivity().getSharedPreferences(
            "SupChatPrefs",
            android.content.Context.MODE_PRIVATE
        ).getString("auth_token", "") ?: ""

        if (token.isEmpty()) {
            Toast.makeText(
                context,
                "Session expirée, veuillez vous reconnecter",
                Toast.LENGTH_SHORT
            ).show()
            (activity as? HomeActivity)?.redirectToLogin("Session expirée")
            return
        }

        // Afficher un dialogue de progression
        val progressDialog = ProgressDialog(context)
        progressDialog.setMessage("Suppression du compte en cours...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        // Appel API pour supprimer le compte avec le mot de passe
        ApiClient.deleteUserProfile(token, password)
            .enqueue(object : Callback<AccountDeleteResponse> {
                // Reste du callback inchangé
                override fun onResponse(
                    call: Call<AccountDeleteResponse>,
                    response: Response<AccountDeleteResponse>
                ) {
                    progressDialog.dismiss()

                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        Log.d(TAG, "Compte supprimé avec succès: ${responseBody?.message}")

                        Toast.makeText(
                            context,
                            responseBody?.message ?: "Votre compte a été supprimé avec succès",
                            Toast.LENGTH_LONG
                        ).show()

                        // Rediriger vers l'écran de connexion
                        (activity as? HomeActivity)?.redirectToLogin("Compte supprimé avec succès")
                    } else if (response.code() == 401) {
                        Log.e(TAG, "Erreur 401: Authentification expirée ou invalide")
                        (activity as? HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e(
                            TAG,
                            "Erreur de suppression du compte: ${response.code()}, $errorBody"
                        )

                        // Message d'erreur spécifique si le mot de passe est incorrect
                        val errorMessage = if (response.code() == 403) {
                            "Mot de passe incorrect. Veuillez réessayer."
                        } else {
                            "Erreur lors de la suppression du compte: ${response.code()}"
                        }

                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<AccountDeleteResponse>, t: Throwable) {
                    // Callback d'échec inchangé
                    progressDialog.dismiss()
                    Log.e(TAG, "Échec de l'appel API pour la suppression du compte", t)

                    val errorMessage = when (t) {
                        is java.net.UnknownHostException -> "Erreur de connexion: Vérifiez votre connexion Internet"
                        is java.net.SocketTimeoutException -> "Délai d'attente dépassé pour la connexion"
                        else -> "Erreur réseau: ${t.message}"
                    }

                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }
            })
    }
}
