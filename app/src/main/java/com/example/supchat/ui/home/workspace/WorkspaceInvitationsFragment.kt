package com.example.supchat.ui.home.workspace

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.supchat.R
import com.example.supchat.adapters.InvitationsAdapter
import com.example.supchat.api.ApiClient
import com.example.supchat.models.response.Invitation
import com.example.supchat.models.response.InvitationsResponse
import com.example.supchat.models.response.WorkspacesResponse
import com.google.android.material.floatingactionbutton.FloatingActionButton
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class WorkspaceInvitationsFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var noInvitationsText: TextView
    private lateinit var sendInvitationButton: FloatingActionButton
    private var invitations: MutableList<Invitation> = mutableListOf()
    private lateinit var workspaceId: String

    companion object {
        private const val TAG = "WorkspaceInvitations"
        private const val ARG_WORKSPACE_ID = "workspace_id"

        fun newInstance(workspaceId: String): WorkspaceInvitationsFragment {
            val fragment = WorkspaceInvitationsFragment()
            val args = Bundle()
            args.putString(ARG_WORKSPACE_ID, workspaceId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            workspaceId = it.getString(ARG_WORKSPACE_ID, "")
        }

        if (workspaceId.isEmpty()) {
            Log.e(TAG, "ID du workspace non fourni")
            Toast.makeText(context, "Erreur: ID du workspace manquant", Toast.LENGTH_SHORT).show()
            activity?.supportFragmentManager?.popBackStack()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_workspace_invitations, container, false)

        // Initialiser les vues
        recyclerView = view.findViewById(R.id.recycler_invitations)
        noInvitationsText = view.findViewById(R.id.text_no_invitations)
        sendInvitationButton = view.findViewById(R.id.fab_send_invitation)

        // Configurer le RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Configurer le bouton d'envoi d'invitation
        sendInvitationButton.setOnClickListener {
            showSendInvitationDialog()
        }

        // Charger les invitations
        loadInvitations()

        return view
    }

    private fun loadInvitations() {
        val token = context?.getSharedPreferences("SupChatPrefs", 0)?.getString("auth_token", "") ?: ""
        if (token.isEmpty()) {
            Log.e(TAG, "Token d'authentification manquant")
            (activity as? com.example.supchat.ui.home.HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
            return
        }

        // Afficher un message de chargement
        showLoading(true)

        // Appel à l'API pour récupérer les invitations du workspace
        ApiClient.getWorkspaceInvitations(token, workspaceId).enqueue(object : Callback<InvitationsResponse> {
            override fun onResponse(
                call: Call<InvitationsResponse>,
                response: Response<InvitationsResponse>
            ) {
                showLoading(false)

                if (response.isSuccessful) {
                    val responseBody = response.body()
                    Log.d(TAG, "Réponse API: $responseBody")

                    val invitationsList = responseBody?.data?.invitations
                    if (invitationsList != null && invitationsList.isNotEmpty()) {
                        Log.d(TAG, "Invitations récupérées: ${invitationsList.size}")
                        invitations.clear()
                        invitations.addAll(invitationsList)
                        updateInvitationsList()
                    } else {
                        Log.e(TAG, "Aucune invitation trouvée ou liste vide")
                        showNoInvitations(true)
                    }
                } else if (response.code() == 401) {
                    Log.e(TAG, "Erreur 401: Authentification expirée ou invalide")
                    (activity as? com.example.supchat.ui.home.HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Erreur API: ${response.code()}, message: $errorBody")
                    Toast.makeText(
                        context,
                        "Erreur de récupération des invitations: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                    showNoInvitations(true)
                }
            }

            override fun onFailure(call: Call<InvitationsResponse>, t: Throwable) {
                showLoading(false)
                Log.e(TAG, "Échec de l'appel API", t)

                val errorMessage = when (t) {
                    is IllegalStateException -> "Erreur dans le format des données: ${t.message}"
                    is java.net.UnknownHostException -> "Erreur de connexion: Vérifiez votre connexion Internet"
                    is java.net.SocketTimeoutException -> "Délai d'attente dépassé pour la connexion"
                    else -> "Erreur réseau: ${t.message}"
                }

                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                showNoInvitations(true)
            }
        })
    }

    private fun updateInvitationsList() {
        if (invitations.isEmpty()) {
            showNoInvitations(true)
            return
        }

        showNoInvitations(false)

        // Configurer l'adaptateur avec les invitations récupérées
        val adapter = InvitationsAdapter(
            invitations,
            onRevoke = { invitation ->
                // Afficher la confirmation de révocation
                showRevokeInvitationConfirmation(invitation)
            }
        )

        recyclerView.adapter = adapter
    }

    private fun showLoading(isLoading: Boolean) {
        // Implémenter un indicateur de chargement si nécessaire
    }

    private fun showNoInvitations(show: Boolean) {
        if (show) {
            recyclerView.visibility = View.GONE
            noInvitationsText.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            noInvitationsText.visibility = View.GONE
        }
    }

    private fun showSendInvitationDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_send_invitation, null)
        val emailEditText = dialogView.findViewById<EditText>(R.id.edit_user_id)

        AlertDialog.Builder(requireContext())
            .setTitle("Inviter un utilisateur")
            .setView(dialogView)
            .setPositiveButton("Envoyer") { dialog, _ ->
                val userId = emailEditText.text.toString().trim()

                if (userId.isEmpty()) {
                    Toast.makeText(context, "Veuillez saisir un ID utilisateur", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                sendInvitation(userId)
                dialog.dismiss()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun sendInvitation(userId: String) {
        val token = context?.getSharedPreferences("SupChatPrefs", 0)?.getString("auth_token", "") ?: ""
        if (token.isEmpty()) {
            Log.e(TAG, "Token d'authentification manquant")
            (activity as? com.example.supchat.ui.home.HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
            return
        }

        // Afficher un message de chargement
        showLoading(true)

        // Appeler l'API pour envoyer une invitation
        ApiClient.inviteUserByEmail(token, workspaceId, userId).enqueue(object : Callback<WorkspacesResponse> {
            override fun onResponse(
                call: Call<WorkspacesResponse>,
                response: Response<WorkspacesResponse>
            ) {
                showLoading(false)

                if (response.isSuccessful) {
                    Toast.makeText(context, "Invitation envoyée avec succès", Toast.LENGTH_SHORT).show()
                    // Recharger la liste des invitations
                    loadInvitations()
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Erreur lors de l'envoi de l'invitation: ${response.code()}, message: $errorBody")
                    Toast.makeText(
                        context,
                        "Erreur lors de l'envoi de l'invitation: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<WorkspacesResponse>, t: Throwable) {
                showLoading(false)
                Log.e(TAG, "Échec de l'envoi de l'invitation", t)
                Toast.makeText(context, "Erreur réseau: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showRevokeInvitationConfirmation(invitation: Invitation) {
        AlertDialog.Builder(requireContext())
            .setTitle("Révoquer l'invitation")
            .setMessage("Êtes-vous sûr de vouloir révoquer l'invitation envoyée à ${invitation.email} ?")
            .setPositiveButton("Révoquer") { dialog, _ ->
                revokeInvitation(invitation.token)
                dialog.dismiss()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun revokeInvitation(invitationToken: String) {
        val token = context?.getSharedPreferences("SupChatPrefs", 0)?.getString("auth_token", "") ?: ""
        if (token.isEmpty()) {
            Log.e(TAG, "Token d'authentification manquant")
            (activity as? com.example.supchat.ui.home.HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
            return
        }

        // Afficher un message de chargement
        showLoading(true)

        // Appeler l'API pour révoquer l'invitation
        ApiClient.revokeInvitation(token, workspaceId, invitationToken).enqueue(object : Callback<WorkspacesResponse> {
            override fun onResponse(
                call: Call<WorkspacesResponse>,
                response: Response<WorkspacesResponse>
            ) {
                showLoading(false)

                if (response.isSuccessful) {
                    Toast.makeText(context, "Invitation révoquée avec succès", Toast.LENGTH_SHORT).show()
                    // Recharger la liste des invitations
                    loadInvitations()
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Erreur lors de la révocation: ${response.code()}, message: $errorBody")
                    Toast.makeText(
                        context,
                        "Erreur lors de la révocation de l'invitation: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<WorkspacesResponse>, t: Throwable) {
                showLoading(false)
                Log.e(TAG, "Échec de la révocation de l'invitation", t)
                Toast.makeText(context, "Erreur réseau: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}