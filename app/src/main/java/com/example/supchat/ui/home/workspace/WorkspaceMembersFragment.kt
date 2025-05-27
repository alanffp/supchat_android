package com.example.supchat.ui.home.workspace

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.supchat.R
import com.example.supchat.adapters.MembersAdapter
import com.example.supchat.api.ApiClient
import com.example.supchat.models.response.Member
import com.example.supchat.models.response.MembersResponse
import com.example.supchat.models.response.WorkspacesResponse
import com.example.supchat.ui.home.HomeActivity
import com.example.supchat.ui.search.UserSearchFragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class WorkspaceMembersFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var noMembersText: TextView
    private lateinit var addMemberButton: FloatingActionButton
    private lateinit var invitationsButton: Button
    private var members: MutableList<Member> = mutableListOf()
    private lateinit var workspaceId: String

    companion object {
        private const val TAG = "WorkspaceMembers"
        private const val ARG_WORKSPACE_ID = "workspace_id"

        fun newInstance(workspaceId: String): WorkspaceMembersFragment {
            val fragment = WorkspaceMembersFragment()
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
        val view = inflater.inflate(R.layout.fragment_workspace_members, container, false)

        // Initialiser les vues
        recyclerView = view.findViewById(R.id.recycler_members)
        noMembersText = view.findViewById(R.id.text_no_members)
        addMemberButton = view.findViewById(R.id.fab_add_member)
        invitationsButton = view.findViewById(R.id.button_invitations)

        // Configurer le RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Configurer le bouton d'ajout de membre
        addMemberButton.setOnClickListener {
            openUserSearch()
        }

        // Configurer le bouton d'invitations
        invitationsButton.setOnClickListener {
            openInvitations()
        }

        // Charger les membres
        loadMembers()

        return view
    }

    private fun loadMembers() {
        val token = context?.getSharedPreferences("SupChatPrefs", 0)?.getString("auth_token", "") ?: ""
        if (token.isEmpty()) {
            Log.e(TAG, "Token d'authentification manquant")
            (activity as? HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
            return
        }

        // Afficher un message de chargement
        showLoading(true)

        // Appel à l'API pour récupérer les membres du workspace
        ApiClient.getWorkspaceMembers(token, workspaceId).enqueue(object : Callback<MembersResponse> {
            override fun onResponse(
                call: Call<MembersResponse>,
                response: Response<MembersResponse>
            ) {
                showLoading(false)

                if (response.isSuccessful) {
                    val responseBody = response.body()
                    Log.d(TAG, "Réponse API: $responseBody")

                    val membersList = responseBody?.data?.membres
                    if (membersList != null && membersList.isNotEmpty()) {
                        Log.d(TAG, "Membres récupérés: ${membersList.size}")
                        members.clear()
                        members.addAll(membersList)
                        updateMembersList()
                    } else {
                        Log.e(TAG, "Aucun membre trouvé ou liste vide")
                        showNoMembers(true)
                    }
                } else if (response.code() == 401) {
                    Log.e(TAG, "Erreur 401: Authentification expirée ou invalide")
                    (activity as? HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Erreur API: ${response.code()}, message: $errorBody")
                    Toast.makeText(
                        context,
                        "Erreur de récupération des membres: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                    showNoMembers(true)
                }
            }

            override fun onFailure(call: Call<MembersResponse>, t: Throwable) {
                showLoading(false)
                Log.e(TAG, "Échec de l'appel API", t)

                val errorMessage = when (t) {
                    is IllegalStateException -> "Erreur dans le format des données: ${t.message}"
                    is java.net.UnknownHostException -> "Erreur de connexion: Vérifiez votre connexion Internet"
                    is java.net.SocketTimeoutException -> "Délai d'attente dépassé pour la connexion"
                    else -> "Erreur réseau: ${t.message}"
                }

                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                showNoMembers(true)
            }
        })
    }

    private fun updateMembersList() {
        if (members.isEmpty()) {
            showNoMembers(true)
            return
        }

        showNoMembers(false)

        // Configurer l'adaptateur avec les membres récupérés
        val adapter = MembersAdapter(
            members,
            onRoleUpdateClick = { member ->
                showRoleUpdateDialog(member)
            },
            onRemoveClick = { member ->
                showRemoveMemberConfirmation(member)
            }
        )

        recyclerView.adapter = adapter
    }

    private fun showLoading(isLoading: Boolean) {
        // Implémenter un indicateur de chargement si nécessaire
    }

    private fun showNoMembers(show: Boolean) {
        if (show) {
            recyclerView.visibility = View.GONE
            noMembersText.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            noMembersText.visibility = View.GONE
        }
    }

    private fun openUserSearch() {
        // Créer et configurer le fragment de recherche d'utilisateurs
        val searchFragment = UserSearchFragment.newInstance()

        // Configurer le mode de sélection d'utilisateur
        searchFragment.setOnUserSelectedListener { userId ->
            addMemberToWorkspace(userId)
        }

        // Remplacer le fragment actuel par le fragment de recherche
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_content_container, searchFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun openInvitations() {
        // Ouvrir le fragment de gestion des invitations
        (activity as? HomeActivity)?.openWorkspaceInvitations(workspaceId)
    }

    private fun addMemberToWorkspace(userId: String) {
        val token = context?.getSharedPreferences("SupChatPrefs", 0)?.getString("auth_token", "") ?: ""
        if (token.isEmpty()) {
            Log.e(TAG, "Token d'authentification manquant")
            (activity as? HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
            return
        }

        // Afficher un message de chargement
        showLoading(true)

        // Appeler l'API pour ajouter un membre
        ApiClient.addWorkspaceMember(token, workspaceId, userId).enqueue(object : Callback<WorkspacesResponse> {
            override fun onResponse(
                call: Call<WorkspacesResponse>,
                response: Response<WorkspacesResponse>
            ) {
                showLoading(false)

                if (response.isSuccessful) {
                    Toast.makeText(context, "Membre ajouté avec succès", Toast.LENGTH_SHORT).show()
                    // Recharger la liste des membres
                    loadMembers()
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Erreur lors de l'ajout: ${response.code()}, message: $errorBody")
                    Toast.makeText(
                        context,
                        "Erreur lors de l'ajout du membre: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<WorkspacesResponse>, t: Throwable) {
                showLoading(false)
                Log.e(TAG, "Échec de l'ajout du membre", t)
                Toast.makeText(context, "Erreur réseau: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showRoleUpdateDialog(member: Member) {
        val roles = arrayOf("membre", "admin")
        val currentRoleIndex = roles.indexOf(member.role)

        AlertDialog.Builder(requireContext())
            .setTitle("Changer le rôle de ${member.username}")
            .setSingleChoiceItems(roles, currentRoleIndex) { dialog, which ->
                val newRole = roles[which]
                updateMemberRole(member.id, newRole)
                dialog.dismiss()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun updateMemberRole(membreId: String, role: String) {
        val token = context?.getSharedPreferences("SupChatPrefs", 0)?.getString("auth_token", "") ?: ""
        if (token.isEmpty()) {
            Log.e(TAG, "Token d'authentification manquant")
            (activity as? HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
            return
        }

        // Afficher un message de chargement
        showLoading(true)

        // Appeler l'API pour mettre à jour le rôle
        ApiClient.updateMemberRole(token, workspaceId, membreId, role).enqueue(object : Callback<WorkspacesResponse> {
            override fun onResponse(
                call: Call<WorkspacesResponse>,
                response: Response<WorkspacesResponse>
            ) {
                showLoading(false)

                if (response.isSuccessful) {
                    Toast.makeText(context, "Rôle mis à jour avec succès", Toast.LENGTH_SHORT).show()
                    // Recharger la liste des membres
                    loadMembers()
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Erreur lors de la mise à jour: ${response.code()}, message: $errorBody")
                    Toast.makeText(
                        context,
                        "Erreur lors de la mise à jour du rôle: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<WorkspacesResponse>, t: Throwable) {
                showLoading(false)
                Log.e(TAG, "Échec de la mise à jour du rôle", t)
                Toast.makeText(context, "Erreur réseau: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showRemoveMemberConfirmation(member: Member) {
        AlertDialog.Builder(requireContext())
            .setTitle("Supprimer le membre")
            .setMessage("Êtes-vous sûr de vouloir supprimer ${member.username} du workspace ?")
            .setPositiveButton("Supprimer") { dialog, _ ->
                removeMember(member.id)
                dialog.dismiss()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun removeMember(membreId: String) {
        val token = context?.getSharedPreferences("SupChatPrefs", 0)?.getString("auth_token", "") ?: ""
        if (token.isEmpty()) {
            Log.e(TAG, "Token d'authentification manquant")
            (activity as? HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
            return
        }

        // Afficher un message de chargement
        showLoading(true)

        // Appeler l'API pour supprimer le membre
        ApiClient.removeWorkspaceMember(token, workspaceId, membreId).enqueue(object : Callback<WorkspacesResponse> {
            override fun onResponse(
                call: Call<WorkspacesResponse>,
                response: Response<WorkspacesResponse>
            ) {
                showLoading(false)

                if (response.isSuccessful) {
                    Toast.makeText(context, "Membre supprimé avec succès", Toast.LENGTH_SHORT).show()
                    // Recharger la liste des membres
                    loadMembers()
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Erreur lors de la suppression: ${response.code()}, message: $errorBody")
                    Toast.makeText(
                        context,
                        "Erreur lors de la suppression du membre: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<WorkspacesResponse>, t: Throwable) {
                showLoading(false)
                Log.e(TAG, "Échec de la suppression du membre", t)
                Toast.makeText(context, "Erreur réseau: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}