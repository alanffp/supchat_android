package com.example.supchat.ui.home

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.supchat.R
import com.example.supchat.api.ApiClient
import com.example.supchat.models.response.WorkspacesResponse
import com.google.android.material.floatingactionbutton.FloatingActionButton
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class WelcomeFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_welcome, container, false)

        // Configurer le bouton de création de workspace
        val fabCreateWorkspace = view.findViewById<FloatingActionButton>(R.id.fab_create_workspace)
        fabCreateWorkspace.setOnClickListener {
            showCreateWorkspaceDialog()
        }

        return view
    }

    private fun showCreateWorkspaceDialog() {
        // Créer un dialogue pour saisir les informations du workspace
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_create_workspace, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.edit_workspace_name)
        val descriptionEditText = dialogView.findViewById<EditText>(R.id.edit_workspace_description)
        val visibilityRadioGroup = dialogView.findViewById<RadioGroup>(R.id.visibility_radio_group)

        AlertDialog.Builder(requireContext())
            .setTitle("Créer un workspace")
            .setView(dialogView)
            .setPositiveButton("Créer") { _, _ ->
                val name = nameEditText.text.toString().trim()
                val description = descriptionEditText.text.toString().trim().let {
                    if (it.isEmpty()) null else it
                }

                // Déterminer la visibilité selon le bouton radio sélectionné
                val visibility = when (visibilityRadioGroup.checkedRadioButtonId) {
                    R.id.visibility_private -> "private"
                    else -> "public"
                }

                if (name.isNotEmpty()) {
                    createWorkspace(name, description, visibility)
                } else {
                    Toast.makeText(context, "Le nom ne peut pas être vide", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun createWorkspace(name: String, description: String? = null, visibility: String) {
        // Récupérer le token d'authentification
        val token = requireActivity().getSharedPreferences("SupChatPrefs", Context.MODE_PRIVATE)
            .getString("auth_token", "")
        Log.d("CreateWorkspace", "Token: ${token?.take(10)}...")
        if (token.isNullOrEmpty()) {
            Toast.makeText(context, "Session expirée, veuillez vous reconnecter", Toast.LENGTH_SHORT).show()
            (activity as? HomeActivity)?.redirectToLogin("Session expirée")
            return
        }

        // Afficher un dialogue de progression
        val progressDialog = android.app.ProgressDialog(context)
        progressDialog.setMessage("Création du workspace...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        // Appeler l'API pour créer le workspace
        ApiClient.createWorkspace(token, name, description, visibility)
            .enqueue(object : Callback<WorkspacesResponse> {
                override fun onResponse(call: Call<WorkspacesResponse>, response: Response<WorkspacesResponse>) {
                    progressDialog.dismiss()

                    if (response.isSuccessful) {
                        Toast.makeText(context, "Workspace créé avec succès", Toast.LENGTH_SHORT).show()
                        // Rafraîchir la liste des workspaces
                        (activity as? HomeActivity)?.refreshWorkspaces()
                    }
                }

                override fun onFailure(call: Call<WorkspacesResponse>, t: Throwable) {
                    progressDialog.dismiss()
                    Toast.makeText(context, "Erreur réseau: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }
}