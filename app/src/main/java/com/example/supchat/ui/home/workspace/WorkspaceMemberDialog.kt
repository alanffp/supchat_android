package com.example.supchat.ui.home.workspace

import android.app.Dialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.supchat.R
import com.example.supchat.api.ApiClient
import com.example.supchat.models.response.WorkspacesResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

data class DialogWorkspace(
    val id: String,
    val nom: String,
    val description: String? = null
)

class WorkspaceMemberDialog(
    private val context: Context,
    private val workspace: DialogWorkspace,
    private val onInviteMember: (DialogWorkspace) -> Unit,
    private val onAddMember: (DialogWorkspace) -> Unit
) {

    private var dialog: Dialog? = null

    fun show() {
        val dialogView = LayoutInflater.from(context).inflate(
            R.layout.dialog_workspace_member_actions,
            null
        )

        setupDialogViews(dialogView)

        dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog?.show()
    }

    private fun setupDialogViews(view: View) {
        // Configuration du header
        val workspaceNameText = view.findViewById<TextView>(R.id.dialog_workspace_name)
        val closeButton = view.findViewById<ImageButton>(R.id.btn_close_dialog)

        workspaceNameText.text = workspace.nom

        // Configuration des options
        val optionInviteMember = view.findViewById<LinearLayout>(R.id.option_invite_member)
        val optionAddMember = view.findViewById<LinearLayout>(R.id.option_add_member)
        val cancelButton = view.findViewById<Button>(R.id.btn_cancel)

        // Listeners
        optionInviteMember.setOnClickListener {
            handleInviteMember()
        }

        optionAddMember.setOnClickListener {
            handleAddMember()
        }

        closeButton.setOnClickListener {
            dismiss()
        }

        cancelButton.setOnClickListener {
            dismiss()
        }
    }

    private fun handleInviteMember() {
        dismiss()
        onInviteMember(workspace)
        // Toast pour confirmation
        Toast.makeText(
            context,
            "📧 Ouverture de l'interface d'invitation pour ${workspace.nom}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun handleAddMember() {
        dismiss()
        showAddMemberDialog()
    }

    private fun showAddMemberDialog() {
        val addMemberView = LayoutInflater.from(context).inflate(
            R.layout.dialog_add_member_form,
            null
        )

        val addMemberDialog = AlertDialog.Builder(context)
            .setView(addMemberView)
            .setCancelable(true)
            .create()

        setupAddMemberForm(addMemberView, addMemberDialog)
        addMemberDialog.show()
    }

    private fun setupAddMemberForm(view: View, dialog: Dialog) {
        val inputUserIdentifier = view.findViewById<EditText>(R.id.input_user_identifier)
        val btnRoleMember = view.findViewById<Button>(R.id.btn_role_member)
        val btnRoleAdmin = view.findViewById<Button>(R.id.btn_role_admin)
        val btnCancelAdd = view.findViewById<Button>(R.id.btn_cancel_add)
        val btnAddMember = view.findViewById<Button>(R.id.btn_add_member)

        var selectedRole = "membre" // Par défaut

        // Gestion des rôles
        btnRoleMember.setOnClickListener {
            selectedRole = "membre"
            btnRoleMember.backgroundTintList = context.getColorStateList(android.R.color.holo_orange_dark)
            btnRoleAdmin.backgroundTintList = context.getColorStateList(android.R.color.darker_gray)
        }

        btnRoleAdmin.setOnClickListener {
            selectedRole = "admin"
            btnRoleAdmin.backgroundTintList = context.getColorStateList(android.R.color.holo_orange_dark)
            btnRoleMember.backgroundTintList = context.getColorStateList(android.R.color.darker_gray)
        }

        // Bouton annuler
        btnCancelAdd.setOnClickListener {
            dialog.dismiss()
        }

        // Bouton ajouter
        btnAddMember.setOnClickListener {
            val userIdentifier = inputUserIdentifier.text.toString().trim()

            if (userIdentifier.isEmpty()) {
                Toast.makeText(context, "⚠️ Veuillez saisir un nom d'utilisateur ou email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isValidUserIdentifier(userIdentifier)) {
                Toast.makeText(context, "⚠️ Format invalide. Utilisez un nom d'utilisateur ou email valide", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Désactiver le bouton pendant la requête
            btnAddMember.isEnabled = false
            btnAddMember.text = "⏳ Ajout..."

            addMemberToWorkspace(workspace.id, userIdentifier, selectedRole) { success ->
                if (success) {
                    dialog.dismiss()
                } else {
                    // Réactiver le bouton en cas d'erreur
                    btnAddMember.isEnabled = true
                    btnAddMember.text = "➕ Ajouter"
                }
            }
        }

        // Validation en temps réel
        inputUserIdentifier.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = s?.toString()?.trim() ?: ""
                btnAddMember.isEnabled = input.isNotEmpty() && isValidUserIdentifier(input)
            }
        })
    }

    private fun isValidUserIdentifier(identifier: String): Boolean {
        return when {
            identifier.contains("@") -> {
                // Validation email basique
                android.util.Patterns.EMAIL_ADDRESS.matcher(identifier).matches()
            }
            else -> {
                // Validation username (lettres, chiffres, underscore, tiret)
                identifier.matches(Regex("^[a-zA-Z0-9_-]{3,30}$"))
            }
        }
    }

    private fun addMemberToWorkspace(
        workspaceId: String,
        userIdentifier: String,
        role: String = "membre",
        onComplete: (Boolean) -> Unit
    ) {
        // Récupérer le token depuis SharedPreferences
        val token = context.getSharedPreferences("SupChatPrefs", 0)?.getString("auth_token", "") ?: ""
        if (token.isEmpty()) {
            Toast.makeText(context, "❌ Token d'authentification manquant", Toast.LENGTH_SHORT).show()
            onComplete(false)
            return
        }

        // ÉTAPE 1: D'abord chercher l'utilisateur pour récupérer son ID
        ApiClient.searchUsers(token, userIdentifier)
            .enqueue(object : Callback<com.example.supchat.models.response.UserSearchResponse> {
                override fun onResponse(
                    call: Call<com.example.supchat.models.response.UserSearchResponse>,
                    response: Response<com.example.supchat.models.response.UserSearchResponse>
                ) {
                    if (response.isSuccessful) {
                        val users = response.body()?.data?.users
                        if (!users.isNullOrEmpty()) {
                            // Chercher l'utilisateur exact
                            val user = users.find {
                                it.username.equals(userIdentifier, ignoreCase = true) ||
                                        it.email.equals(userIdentifier, ignoreCase = true)
                            }

                            if (user != null) {
                                // ÉTAPE 2: Maintenant ajouter l'utilisateur avec son ID
                                addMemberWithUserId(token, workspaceId, user.id, role, onComplete)
                            } else {
                                Toast.makeText(
                                    context,
                                    "❌ Utilisateur '$userIdentifier' non trouvé",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onComplete(false)
                            }
                        } else {
                            Toast.makeText(
                                context,
                                "❌ Aucun utilisateur trouvé avec '$userIdentifier'",
                                Toast.LENGTH_SHORT
                            ).show()
                            onComplete(false)
                        }
                    } else {
                        Toast.makeText(
                            context,
                            "❌ Erreur lors de la recherche de l'utilisateur (${response.code()})",
                            Toast.LENGTH_SHORT
                        ).show()
                        onComplete(false)
                    }
                }

                override fun onFailure(call: Call<com.example.supchat.models.response.UserSearchResponse>, t: Throwable) {
                    Toast.makeText(
                        context,
                        "❌ Erreur de connexion lors de la recherche: ${t.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    onComplete(false)
                }
            })
    }

    private fun addMemberWithUserId(
        token: String,
        workspaceId: String,
        utilisateurId: String,
        role: String,
        onComplete: (Boolean) -> Unit
    ) {
        // Appel API POST /api/v1/workspaces/{id}/membres avec l'ID utilisateur
        ApiClient.addWorkspaceMember(token, workspaceId, utilisateurId, role)
            .enqueue(object : Callback<WorkspacesResponse> {
                override fun onResponse(call: Call<WorkspacesResponse>, response: Response<WorkspacesResponse>) {
                    if (response.isSuccessful) {
                        Toast.makeText(
                            context,
                            "✅ Membre ajouté avec succès comme $role dans ${workspace.nom}!",
                            Toast.LENGTH_LONG
                        ).show()

                        // Callback pour actualiser la liste
                        onAddMember(workspace)
                        onComplete(true)
                    } else {
                        val errorMessage = when (response.code()) {
                            404 -> "Utilisateur non trouvé dans le système"
                            409 -> "L'utilisateur est déjà membre de ce workspace"
                            403 -> "Vous n'avez pas les permissions pour ajouter des membres"
                            400 -> "Données invalides"
                            422 -> "Format de données incorrect"
                            500 -> "Erreur serveur lors de l'ajout du membre"
                            else -> "Erreur lors de l'ajout du membre (${response.code()})"
                        }

                        Toast.makeText(
                            context,
                            "❌ $errorMessage",
                            Toast.LENGTH_LONG
                        ).show()
                        onComplete(false)
                    }
                }

                override fun onFailure(call: Call<WorkspacesResponse>, t: Throwable) {
                    Toast.makeText(
                        context,
                        "❌ Erreur de connexion: ${t.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    onComplete(false)
                }
            })
    }

    private fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }
}