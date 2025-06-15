package com.example.supchat.ui.home.workspace

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.supchat.R
import com.example.supchat.adapters.WorkspaceAdapter
import com.example.supchat.api.ApiClient
import com.example.supchat.models.response.LoginResponse
import com.example.supchat.models.response.Workspace
import com.example.supchat.models.response.WorkspacesResponse
import com.google.android.material.floatingactionbutton.FloatingActionButton
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.example.supchat.ui.home.workspace.DialogWorkspace
import com.example.supchat.ui.home.workspace.WorkspaceMemberDialog

class WorkspaceManagementFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var addWorkspaceButton: FloatingActionButton
    private lateinit var searchQueryEditText: EditText
    private lateinit var searchButton: Button
    private lateinit var workspaceFilterGroup: RadioGroup
    private lateinit var myWorkspacesRadio: RadioButton
    private lateinit var publicWorkspacesRadio: RadioButton
    private lateinit var noWorkspacesText: TextView
    private var workspaces: MutableList<Workspace> = mutableListOf()
    private var isShowingPublicWorkspaces = false


    companion object {
        private const val TAG = "WorkspaceManagement"

        fun newInstance(): WorkspaceManagementFragment {
            return WorkspaceManagementFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_workspace_management, container, false)
        debugSharedPrefs()
        // Initialiser les vues
        recyclerView = view.findViewById(R.id.recycler_workspaces)
        noWorkspacesText = view.findViewById(R.id.text_no_workspaces)
        addWorkspaceButton = view.findViewById(R.id.fab_add_workspace)
        searchQueryEditText = view.findViewById(R.id.edit_search_query)
        searchButton = view.findViewById(R.id.btn_search_workspaces)
        workspaceFilterGroup = view.findViewById(R.id.workspace_filter)
        myWorkspacesRadio = view.findViewById(R.id.radio_my_workspaces)
        publicWorkspacesRadio = view.findViewById(R.id.radio_public_workspaces)

        // Configurer le RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Configurer le bouton d'ajout
        addWorkspaceButton.setOnClickListener {
            showCreateWorkspaceDialog()
        }

        // Configurer le bouton de recherche
        searchButton.setOnClickListener {
            val query = searchQueryEditText.text.toString().trim()
            if (query.isNotEmpty()) {
                searchPublicWorkspaces(query)
            } else {
                Toast.makeText(context, "Veuillez saisir un terme de recherche", Toast.LENGTH_SHORT).show()
            }
        }

        // Configurer le changement de filtre
        workspaceFilterGroup.setOnCheckedChangeListener { _, checkedId ->
            isShowingPublicWorkspaces = (checkedId == R.id.radio_public_workspaces)

            // Afficher/masquer les éléments appropriés
            searchQueryEditText.visibility = if (isShowingPublicWorkspaces) View.VISIBLE else View.GONE
            searchButton.visibility = if (isShowingPublicWorkspaces) View.VISIBLE else View.GONE
            addWorkspaceButton.visibility = if (isShowingPublicWorkspaces) View.GONE else View.VISIBLE

            if (isShowingPublicWorkspaces) {
                // Si aucune recherche n'a été faite, afficher un message
                if (searchQueryEditText.text.toString().trim().isEmpty()) {
                    workspaces.clear()
                    showNoWorkspaces(true)
                    noWorkspacesText.text = "Utilisez la barre de recherche pour trouver des workspaces publics"
                }
            } else {
                // Recharger mes workspaces
                noWorkspacesText.text = "Aucun workspace disponible.\nUtilisez + pour créer un nouveau workspace."
                loadWorkspaces()
            }
        }

        // Charger les workspaces par défaut (mes workspaces)
        loadWorkspaces()

        return view
    }

    private fun loadWorkspaces(showLoadingIndicator: Boolean = true) {
        val token = context?.getSharedPreferences("SupChatPrefs", 0)?.getString("auth_token", "") ?: ""
        if (token.isEmpty()) {
            Log.e(TAG, "Token d'authentification manquant")
            (activity as? com.example.supchat.ui.home.HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
            return
        }

        // Afficher l'indicateur de chargement seulement si demandé
        if (showLoadingIndicator) {
            showLoading(true)
        }

        // Paramètre pour éviter le cache et forcer le rafraîchissement des données
        val timestamp = System.currentTimeMillis()

        // Appel à l'API pour récupérer les workspaces
        ApiClient.getWorkspaces(token).enqueue(object : Callback<WorkspacesResponse> {
            override fun onResponse(
                call: Call<WorkspacesResponse>,
                response: Response<WorkspacesResponse>
            ) {
                if (showLoadingIndicator) {
                    showLoading(false)
                }

                if (response.isSuccessful) {
                    val responseBody = response.body()
                    Log.d(TAG, "Réponse API: $responseBody")

                    val workspacesList = responseBody?.data?.workspaces
                    if (workspacesList != null && workspacesList.isNotEmpty()) {
                        Log.d(TAG, "Workspaces récupérés: ${workspacesList.size}")
                        workspaces.clear()
                        workspaces.addAll(workspacesList)
                        updateWorkspacesList(isPublic = isShowingPublicWorkspaces)
                    } else {
                        Log.e(TAG, "Aucun workspace trouvé ou liste vide")
                        showNoWorkspaces(true)
                    }
                } else if (response.code() == 401) {
                    Log.e(TAG, "Erreur 401: Authentification expirée ou invalide")
                    (activity as? com.example.supchat.ui.home.HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Erreur API: ${response.code()}, message: $errorBody")

                    // Afficher un toast seulement si l'indicateur de chargement était affiché
                    // pour éviter de spammer l'utilisateur avec des messages d'erreur
                    if (showLoadingIndicator) {
                        Toast.makeText(
                            context,
                            "Erreur de récupération des workspaces: ${response.code()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    showNoWorkspaces(true)
                }
            }

            override fun onFailure(call: Call<WorkspacesResponse>, t: Throwable) {
                if (showLoadingIndicator) {
                    showLoading(false)
                }

                Log.e(TAG, "Échec de l'appel API", t)

                // Afficher un toast seulement si l'indicateur de chargement était affiché
                if (showLoadingIndicator) {
                    val errorMessage = when (t) {
                        is IllegalStateException -> "Erreur dans le format des données: ${t.message}"
                        is java.net.UnknownHostException -> "Erreur de connexion: Vérifiez votre connexion Internet"
                        is java.net.SocketTimeoutException -> "Délai d'attente dépassé pour la connexion"
                        else -> "Erreur réseau: ${t.message}"
                    }

                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }

                showNoWorkspaces(true)
            }
        })
    }

    private fun searchPublicWorkspaces(query: String) {
        val token = context?.getSharedPreferences("SupChatPrefs", 0)?.getString("auth_token", "") ?: ""
        if (token.isEmpty()) {
            Log.e(TAG, "Token d'authentification manquant")
            (activity as? com.example.supchat.ui.home.HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
            return
        }

        // Afficher un message de chargement
        showLoading(true)

        // Appel à l'API pour rechercher des workspaces publics
        ApiClient.searchPublicWorkspaces(token, query).enqueue(object : Callback<WorkspacesResponse> {
            override fun onResponse(
                call: Call<WorkspacesResponse>,
                response: Response<WorkspacesResponse>
            ) {
                showLoading(false)

                if (response.isSuccessful) {
                    val responseBody = response.body()
                    Log.d(TAG, "Réponse API recherche: $responseBody")

                    val workspacesList = responseBody?.data?.workspaces
                    if (workspacesList != null && workspacesList.isNotEmpty()) {
                        Log.d(TAG, "Workspaces publics trouvés: ${workspacesList.size}")
                        workspaces.clear()
                        workspaces.addAll(workspacesList)
                        updateWorkspacesList(isPublic = true)
                    } else {
                        Log.d(TAG, "Aucun workspace public trouvé pour la requête: $query")
                        workspaces.clear()
                        showNoWorkspaces(true)
                        noWorkspacesText.text = "Aucun workspace public trouvé pour '$query'"
                    }
                } else if (response.code() == 401) {
                    Log.e(TAG, "Erreur 401: Authentification expirée ou invalide")
                    (activity as? com.example.supchat.ui.home.HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Erreur API recherche: ${response.code()}, message: $errorBody")
                    Toast.makeText(
                        context,
                        "Erreur lors de la recherche: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                    showNoWorkspaces(true)
                    noWorkspacesText.text = "Erreur lors de la recherche"
                }
            }

            override fun onFailure(call: Call<WorkspacesResponse>, t: Throwable) {
                showLoading(false)
                Log.e(TAG, "Échec de l'appel API de recherche", t)

                val errorMessage = when (t) {
                    is IllegalStateException -> "Erreur dans le format des données: ${t.message}"
                    is java.net.UnknownHostException -> "Erreur de connexion: Vérifiez votre connexion Internet"
                    is java.net.SocketTimeoutException -> "Délai d'attente dépassé pour la connexion"
                    else -> "Erreur réseau: ${t.message}"
                }

                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                showNoWorkspaces(true)
                noWorkspacesText.text = "Erreur: $errorMessage"
            }
        })
    }

    // Dans WorkspaceManagementFragment.kt
    private fun updateWorkspacesList(isPublic: Boolean = false) {
        if (workspaces.isEmpty()) {
            showNoWorkspaces(true)
            return
        }

        showNoWorkspaces(false)

        // Récupérer l'ID de l'utilisateur actuel depuis les SharedPreferences
        val userId = getUserId()

        Log.d(TAG, "ID utilisateur actuel: $userId")

        // Créer l'adaptateur avec l'ID utilisateur
        val adapter = WorkspaceAdapter(
            workspaces,
            userId, // Passer l'ID utilisateur
            isPublic, // Passer le paramètre isPublic à l'adaptateur
            onItemClick = { workspace ->
                if (isPublic) {
                    showJoinWorkspaceConfirmation(workspace)
                } else {
                    navigateToWorkspaceDetail(workspace)
                }
            },
            onEditClick = { workspace ->
                showEditWorkspaceDialog(workspace)
            },
            onDeleteClick = { workspace ->
                showDeleteWorkspaceConfirmation(workspace)
            },
            onLeaveClick = { workspace ->
                showLeaveWorkspaceConfirmation(workspace)
            }
        )

        recyclerView.adapter = adapter
    }

    private fun getUserId(): String {
        val prefs = requireContext().getSharedPreferences("SupChatPrefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("userid", "") ?: ""

        Log.d(TAG, "getUserId() - UserID récupéré des SharedPreferences: '$userId'")
        return userId
    }
    private fun showJoinWorkspaceConfirmation(workspace: Workspace) {
        AlertDialog.Builder(requireContext())
            .setTitle("Rejoindre le workspace")
            .setMessage("Voulez-vous rejoindre le workspace '${workspace.nom}' ?")
            .setPositiveButton("Rejoindre") { dialog, _ ->
                joinWorkspace(workspace.id)
                dialog.dismiss()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun joinWorkspace(workspaceId: String) {
        val token = context?.getSharedPreferences("SupChatPrefs", 0)?.getString("auth_token", "") ?: ""
        if (token.isEmpty()) {
            Log.e(TAG, "Token d'authentification manquant")
            (activity as? com.example.supchat.ui.home.HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
            return
        }

        // Afficher un message de chargement
        showLoading(true)

        // Appeler l'endpoint GET pour accéder au workspace
        ApiClient.getWorkspaceById(token, workspaceId).enqueue(object : Callback<WorkspacesResponse> {
            override fun onResponse(
                call: Call<WorkspacesResponse>,
                response: Response<WorkspacesResponse>
            ) {
                showLoading(false)

                if (response.isSuccessful) {
                    Log.d(TAG, "Accès au workspace réussi")
                    Toast.makeText(
                        context,
                        "Vous avez rejoint le workspace avec succès",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Basculer vers l'onglet Mes Workspaces
                    myWorkspacesRadio.isChecked = true
                    isShowingPublicWorkspaces = false

                    // Rafraîchir la liste des workspaces sans afficher l'indicateur de chargement
                    // pour une expérience plus fluide
                    loadWorkspaces(showLoadingIndicator = false)
                }
            }

            override fun onFailure(call: Call<WorkspacesResponse>, t: Throwable) {
                showLoading(false)
                Log.e(TAG, "Échec de l'accès au workspace", t)

                Toast.makeText(
                    context,
                    "Erreur réseau: ${t.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }
    private fun showLoading(isLoading: Boolean) {
        // Implémenter un indicateur de chargement si nécessaire
    }

    private fun showNoWorkspaces(show: Boolean) {
        if (show) {
            recyclerView.visibility = View.GONE
            noWorkspacesText.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            noWorkspacesText.visibility = View.GONE
        }
    }

    private fun showCreateWorkspaceDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_create_workspace, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.edit_workspace_name)
        val descriptionEditText = dialogView.findViewById<EditText>(R.id.edit_workspace_description)

        AlertDialog.Builder(requireContext())
            .setTitle("Créer un nouveau workspace")
            .setView(dialogView)
            .setPositiveButton("Créer") { dialog, _ ->
                val name = nameEditText.text.toString().trim()
                val description = descriptionEditText.text.toString().trim()

                if (name.isEmpty()) {
                    Toast.makeText(context, "Veuillez saisir un nom", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                createWorkspace(name, description)
                dialog.dismiss()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun createWorkspace(name: String, description: String) {
        val token = context?.getSharedPreferences("SupChatPrefs", 0)?.getString("auth_token", "") ?: ""
        if (token.isEmpty()) {
            Log.e(TAG, "Token d'authentification manquant")
            (activity as? com.example.supchat.ui.home.HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
            return
        }

        // Afficher un message de chargement
        showLoading(true)

        // Appeler l'API pour créer un nouveau workspace
        ApiClient.createWorkspace(token, name, description).enqueue(object : Callback<WorkspacesResponse> {
            override fun onResponse(
                call: Call<WorkspacesResponse>,
                response: Response<WorkspacesResponse>
            ) {
                showLoading(false)

                if (response.isSuccessful) {
                    Toast.makeText(context, "Workspace créé avec succès", Toast.LENGTH_SHORT).show()
                    // Recharger la liste des workspaces
                    loadWorkspaces()
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Erreur lors de la création: ${response.code()}, message: $errorBody")
                    Toast.makeText(
                        context,
                        "Erreur lors de la création du workspace: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<WorkspacesResponse>, t: Throwable) {
                showLoading(false)
                Log.e(TAG, "Échec de la création du workspace", t)
                Toast.makeText(context, "Erreur réseau: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showEditWorkspaceDialog(workspace: Workspace) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_create_workspace, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.edit_workspace_name)
        val descriptionEditText = dialogView.findViewById<EditText>(R.id.edit_workspace_description)

        // Pré-remplir avec les valeurs existantes
        nameEditText.setText(workspace.nom)
        descriptionEditText.setText(workspace.description)

        AlertDialog.Builder(requireContext())
            .setTitle("Modifier le workspace")
            .setView(dialogView)
            .setPositiveButton("Enregistrer") { dialog, _ ->
                val name = nameEditText.text.toString().trim()
                val description = descriptionEditText.text.toString().trim()

                if (name.isEmpty()) {
                    Toast.makeText(context, "Veuillez saisir un nom", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                updateWorkspace(workspace.id, name, description)
                dialog.dismiss()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun updateWorkspace(workspaceId: String, name: String, description: String) {
        val token = context?.getSharedPreferences("SupChatPrefs", 0)?.getString("auth_token", "") ?: ""
        if (token.isEmpty()) {
            Log.e(TAG, "Token d'authentification manquant")
            (activity as? com.example.supchat.ui.home.HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
            return
        }

        // Afficher un message de chargement
        showLoading(true)

        // Appeler l'API pour mettre à jour le workspace
        ApiClient.updateWorkspace(token, workspaceId, name, description).enqueue(object : Callback<WorkspacesResponse> {
            override fun onResponse(
                call: Call<WorkspacesResponse>,
                response: Response<WorkspacesResponse>
            ) {
                showLoading(false)

                if (response.isSuccessful) {
                    Toast.makeText(context, "Workspace mis à jour avec succès", Toast.LENGTH_SHORT).show()
                    // Recharger la liste des workspaces
                    loadWorkspaces()
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Erreur lors de la mise à jour: ${response.code()}, message: $errorBody")
                    Toast.makeText(
                        context,
                        "Erreur lors de la mise à jour du workspace: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<WorkspacesResponse>, t: Throwable) {
                showLoading(false)
                Log.e(TAG, "Échec de la mise à jour du workspace", t)
                Toast.makeText(context, "Erreur réseau: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showDeleteWorkspaceConfirmation(workspace: Workspace) {
        AlertDialog.Builder(requireContext())
            .setTitle("Supprimer le workspace")
            .setMessage("Êtes-vous sûr de vouloir supprimer le workspace '${workspace.nom}' ? Cette action est irréversible.")
            .setPositiveButton("Supprimer") { dialog, _ ->
                deleteWorkspace(workspace.id)
                dialog.dismiss()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun deleteWorkspace(workspaceId: String) {
        val token = context?.getSharedPreferences("SupChatPrefs", 0)?.getString("auth_token", "") ?: ""
        if (token.isEmpty()) {
            Log.e(TAG, "Token d'authentification manquant")
            (activity as? com.example.supchat.ui.home.HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
            return
        }

        // Afficher un message de chargement
        showLoading(true)

        // Appeler l'API pour supprimer le workspace
        ApiClient.deleteWorkspace(token, workspaceId).enqueue(object : Callback<WorkspacesResponse> {
            override fun onResponse(
                call: Call<WorkspacesResponse>,
                response: Response<WorkspacesResponse>
            ) {
                showLoading(false)

                if (response.isSuccessful) {
                    Toast.makeText(context, "Workspace supprimé avec succès", Toast.LENGTH_SHORT).show()
                    loadWorkspaces()
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Erreur lors de la suppression: ${response.code()}, message: $errorBody")
                    Toast.makeText(
                        context,
                        "Erreur lors de la suppression du workspace: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<WorkspacesResponse>, t: Throwable) {
                showLoading(false)
                Log.e(TAG, "Échec de la suppression du workspace", t)
                Toast.makeText(context, "Erreur réseau: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
    private fun navigateToWorkspaceDetail(workspace: com.example.supchat.models.response.Workspace) {
        // Convertir votre modèle existant vers le modèle de la dialog
        val dialogWorkspace = DialogWorkspace(
            id = workspace.id,
            nom = workspace.nom,
            description = workspace.description
        )

        // Afficher la dialog de choix d'action
        showWorkspaceMemberDialog(dialogWorkspace)
    }

    private fun showWorkspaceMemberDialog(workspace: DialogWorkspace) {
        val memberDialog = WorkspaceMemberDialog(
            context = requireContext(),
            workspace = workspace,
            onInviteMember = { ws ->
                // Naviguer vers votre interface d'invitation existante
                openInvitationInterface(ws)
            },
            onAddMember = { ws ->
                // Actualiser la liste après ajout
                loadWorkspaces(showLoadingIndicator = false)

                // Optionnel: Afficher un message de succès
                Toast.makeText(
                    context,
                    "✅ Membre ajouté au workspace ${ws.nom}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        memberDialog.show()
    }

    private fun openInvitationInterface(workspace: DialogWorkspace) {
        // Naviguer vers votre WorkspaceInvitationsFragment existant
        val invitationsFragment = WorkspaceInvitationsFragment.newInstance(workspace.id)
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_content_container, invitationsFragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Vérifier si nous devons montrer directement la recherche publique
        arguments?.let { args ->
            if (args.getBoolean("showPublicSearch", false)) {
                // Basculer automatiquement vers l'onglet de recherche publique
                publicWorkspacesRadio.isChecked = true

                // Cette action déclenchera le listener OnCheckedChange
                // qui s'occupera de montrer les éléments appropriés
            }
        }
    }
    private fun showLeaveWorkspaceConfirmation(workspace: Workspace) {
        AlertDialog.Builder(requireContext())
            .setTitle("Quitter le workspace")
            .setMessage("Êtes-vous sûr de vouloir quitter le workspace '${workspace.nom}' ?")
            .setPositiveButton("Quitter") { dialog, _ ->
                leaveWorkspace(workspace.id)
                dialog.dismiss()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun leaveWorkspace(workspaceId: String) {
        val token = context?.getSharedPreferences("SupChatPrefs", 0)?.getString("auth_token", "") ?: ""
        if (token.isEmpty()) {
            Log.e(TAG, "Token d'authentification manquant")
            (activity as? com.example.supchat.ui.home.HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
            return
        }

        // Afficher un message de chargement
        showLoading(true)

        // Appeler l'API pour quitter le workspace
        ApiClient.leaveWorkspace(token, workspaceId).enqueue(object : Callback<WorkspacesResponse> {
            override fun onResponse(
                call: Call<WorkspacesResponse>,
                response: Response<WorkspacesResponse>
            ) {
                showLoading(false)

                if (response.isSuccessful) {
                    Toast.makeText(context, "Vous avez quitté le workspace avec succès", Toast.LENGTH_SHORT).show()

                    // Rafraîchir la liste des workspaces
                    loadWorkspaces()
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Erreur lors de la sortie du workspace: ${response.code()}, message: $errorBody")

                    val message = when(response.code()) {
                        400 -> "Requête invalide."
                        403 -> "Vous n'avez pas les droits nécessaires pour quitter ce workspace."
                        404 -> "Workspace introuvable."
                        else -> "Erreur lors de la sortie du workspace (${response.code()})."
                    }

                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<WorkspacesResponse>, t: Throwable) {
                showLoading(false)
                Log.e(TAG, "Échec de la sortie du workspace", t)
                Toast.makeText(context, "Erreur réseau: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
    private fun debugSharedPrefs() {
        val prefs = requireContext().getSharedPreferences("SupChatPrefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("userid", "NON_TROUVÉ")
        val token = prefs.getString("auth_token", "NON_TROUVÉ")

        Log.d(TAG, "DEBUG SHARED PREFS ===========================")
        Log.d(TAG, "userid: '$userId'")
        Log.d(TAG, "auth_token: '${token?.take(10)}...'")
        Log.d(TAG, "=============================================")
    }

}