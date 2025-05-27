package com.example.supchat.ui.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.supchat.R
import com.example.supchat.models.response.DeconnexionResponse
import com.example.supchat.models.response.Workspace
import com.example.supchat.models.response.WorkspacesResponse
import com.example.supchat.ui.PrivateConversationsListFragment
import com.example.supchat.ui.auth.LoginActivity
import com.example.supchat.ui.home.workspace.WorkspaceInvitationsFragment
import com.example.supchat.ui.home.workspace.WorkspaceManagementFragment
import com.example.supchat.ui.home.workspace.WorkspaceMembersFragment
import com.example.supchat.ui.search.UserSearchFragment
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import androidx.core.content.edit
import com.example.supchat.api.ApiClient


class HomeActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var menuButton: ImageButton
    private lateinit var serverListContainer: LinearLayout
    private lateinit var mainContentContainer: FrameLayout
    private lateinit var logoutButton: TextView
    private lateinit var profileButton: TextView
    private var currentWorkspaceName: String? = null
    private var workspaces: List<Workspace> = emptyList()
    private var currentWorkspaceId: String? = null
    private lateinit var themeToggleButton: TextView
    private lateinit var searchUsersButton: TextView

    companion object {
        private const val TAG = "HomeActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Initialisation des vues
        drawerLayout = findViewById(R.id.drawer_layout)
        menuButton = findViewById(R.id.menu_button)
        serverListContainer = findViewById(R.id.server_list)
        mainContentContainer = findViewById(R.id.main_content_container)
        logoutButton = findViewById(R.id.logout_text)
        profileButton = findViewById(R.id.profile_text)
        searchUsersButton = findViewById(R.id.search_users_text)

        // Initialiser le bouton de gestion des workspaces
        val manageWorkspacesButton = findViewById<TextView>(R.id.manage_workspaces_text)
        manageWorkspacesButton.setOnClickListener {
            // Fermer le drawer
            drawerLayout.closeDrawer(GravityCompat.END)

            // Ouvrir le fragment de gestion des workspaces
            openWorkspaceManagement()
        }

        // Initialiser le bouton de recherche de workspaces publics
        val searchPublicWorkspacesButton = findViewById<TextView>(R.id.search_public_workspaces_text)
        searchPublicWorkspacesButton.setOnClickListener {
            // Fermer le drawer
            drawerLayout.closeDrawer(GravityCompat.END)

            // Ouvrir directement la recherche de workspaces publics
            openPublicWorkspaceSearch()
        }

        val privateMessagesText = findViewById<TextView>(R.id.private_messages_text)
        privateMessagesText.setOnClickListener {
            // Fermer le drawer
            drawerLayout.closeDrawer(GravityCompat.END)

            // Afficher le fragment de liste de conversations
            val fragment = PrivateConversationsListFragment()

            supportFragmentManager.beginTransaction()
                .replace(R.id.main_content_container, fragment)
                .addToBackStack(null)
                .commit()
        }

        // Configuration du bouton menu pour ouvrir/fermer le drawer
        menuButton.setOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                drawerLayout.closeDrawer(GravityCompat.END)
            } else {
                drawerLayout.openDrawer(GravityCompat.END)
            }
        }

        // Configuration du bouton de déconnexion
        logoutButton.setOnClickListener {
            deconnexionUtilisateur()
        }

        // Configuration du bouton de profil
        profileButton.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.END)
            openProfile()
        }

        // Configuration du bouton de recherche d'utilisateurs
        searchUsersButton.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.END)
            openUserSearch()
        }

        // Vérifier si le token est présent
        val token = getSharedPreferences("SupChatPrefs", MODE_PRIVATE).getString("auth_token", "")
        if (token.isNullOrEmpty()) {
            Log.e(TAG, "Token d'authentification manquant")
            redirectToLogin("Session expirée, veuillez vous reconnecter")
            return
        }

        // Charger les workspaces depuis l'API
        fetchWorkspaces()

        // Afficher l'écran d'accueil en attendant
        showWelcomeScreen()
    }
    private fun openPublicWorkspaceSearch() {
        val workspaceManagementFragment = WorkspaceManagementFragment.newInstance()

        // Passer l'argument pour indiquer qu'il faut afficher directement la recherche publique
        val args = Bundle()
        args.putBoolean("showPublicSearch", true)
        workspaceManagementFragment.arguments = args

        supportFragmentManager.beginTransaction()
            .replace(R.id.main_content_container, workspaceManagementFragment)
            .addToBackStack(null)
            .commit()
    }
    private fun showWelcomeScreen() {
        // Afficher un message "Sélectionnez un serveur"
        val welcomeFragment = WelcomeFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_content_container, welcomeFragment)
            .commit()
    }

    private fun loadChannels(workspaceName: String) {
        // Si on clique sur le même workspace, ne rien faire
        if (workspaceName == currentWorkspaceName &&
            supportFragmentManager.findFragmentById(R.id.main_content_container) is CanauxFragment
        ) {
            return
        }

        // Enregistrer le workspace actuellement sélectionné
        currentWorkspaceName = workspaceName

        // Trouver l'ID du workspace basé sur son nom
        val workspace = workspaces.find { it.nom == workspaceName }
        val workspaceId = workspace?.id

        if (workspaceId.isNullOrEmpty()) {
            Log.e(TAG, "ID du workspace non trouvé pour $workspaceName")
            Toast.makeText(this, "Erreur: ID du workspace non trouvé", Toast.LENGTH_SHORT).show()
            return
        }

        // Stocker l'ID du workspace actuel
        currentWorkspaceId = workspaceId

        // Mettre en évidence visuellement le workspace sélectionné
        updateWorkspaceSelection(workspaceName)

        // Charger les canaux pour le workspace et les afficher
        Log.d(TAG, "Chargement des canaux pour le workspace $workspaceName (ID: $workspaceId)")

        val canauxFragment = CanauxFragment.newInstance(workspaceName, workspaceId)
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_content_container, canauxFragment)
            .commit()
    }

    private fun updateWorkspaceSelection(workspaceName: String) {
        // Parcourir tous les boutons de workspace et mettre à jour leur apparence
        for (i in 0 until serverListContainer.childCount) {
            val serverButton = serverListContainer.getChildAt(i) as? ImageButton
            if (serverButton != null) {
                val isSelected = serverButton.tag == workspaceName

                // Appliquer un style différent au bouton sélectionné
                if (isSelected) {
                    serverButton.setBackgroundResource(R.drawable.selected_rounded_button)
                } else {
                    serverButton.setBackgroundResource(R.drawable.rounded_button)
                }
            }
        }
    }

    // Cette fonction sera appelée depuis le fragment des canaux
    fun openChat(canalId: String, canalNom: String) {
        try {
            Log.d(TAG, "Ouverture du chat: canal=$canalId, nom=$canalNom, workspace=$currentWorkspaceId")

            // Vérifier si l'ID du canal est valide
            if (canalId.isEmpty()) {
                Log.e(TAG, "Erreur: canalId est vide")
                Toast.makeText(this, "Erreur: ID du canal invalide", Toast.LENGTH_SHORT).show()
                return
            }

            // Vérifier si l'ID du workspace est disponible
            if (currentWorkspaceId.isNullOrEmpty()) {
                Log.e(TAG, "Erreur: currentWorkspaceId est null ou vide")
                Toast.makeText(this, "Erreur: ID du workspace manquant", Toast.LENGTH_SHORT).show()
                return
            }

            // Vérifier que le token est valide
            val token = getSharedPreferences("SupChatPrefs", MODE_PRIVATE).getString("auth_token", "")
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token manquant avant ouverture du chat")
                redirectToLogin("Session expirée, veuillez vous reconnecter")
                return
            }

            // Utiliser l'ID du workspace actuel
            val chatFragment = ChatFragment.newInstance(canalId, canalNom, currentWorkspaceId)
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_content_container, chatFragment)
                .addToBackStack(null)
                .commit()

            Log.d(TAG, "Fragment de chat créé et ajouté avec succès")
        } catch (e: Exception) {
            Log.e(TAG, "Exception lors de l'ouverture du chat", e)
            Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchWorkspaces() {
        val token = getSharedPreferences("SupChatPrefs", MODE_PRIVATE).getString("auth_token", "") ?: ""
        if (token.isEmpty()) {
            Log.e(TAG, "Token manquant pour fetchWorkspaces")
            redirectToLogin("Session expirée, veuillez vous reconnecter")
            return
        }

        Log.d(TAG, "Début de la récupération des workspaces avec token: ${token.take(10)}...")

        ApiClient.getWorkspaces(token)
            .enqueue(object : Callback<WorkspacesResponse> {
                override fun onResponse(
                    call: Call<WorkspacesResponse>,
                    response: Response<WorkspacesResponse>
                ) {
                    Log.d(TAG, "Réponse reçue. Code: ${response.code()}")

                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        Log.d(TAG, "Réponse API brute: $responseBody")

                        val workspacesList = responseBody?.data?.workspaces
                        if (workspacesList != null && workspacesList.isNotEmpty()) {
                            Log.d(TAG, "Workspaces récupérés: ${workspacesList.size}")

                            // Log de chaque workspace pour débogage
                            workspacesList.forEachIndexed { index, workspace ->
                                Log.d(TAG, "Workspace[$index]: id=${workspace.id}, nom=${workspace.nom}, " +
                                        "proprietaire=${workspace.proprietaire}, visibilite=${workspace.visibilite}")
                            }

                            // Mettre à jour la liste des workspaces
                            workspaces = workspacesList

                            // Afficher les workspaces
                            displayWorkspaces(workspacesList)
                        } else {
                            Log.e(TAG, "Aucun workspace trouvé ou liste vide")
                            Log.e(TAG, "Données reçues: ${responseBody?.data}")

                            Toast.makeText(
                                this@HomeActivity,
                                "Aucun serveur disponible",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        // Log du corps de l'erreur si disponible
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "Erreur API: ${response.code()}, message: $errorBody")

                        if (response.code() == 401) {
                            redirectToLogin("Session expirée, veuillez vous reconnecter")
                        } else {
                            Toast.makeText(
                                this@HomeActivity,
                                "Erreur de récupération des serveurs: ${response.code()}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                override fun onFailure(call: Call<WorkspacesResponse>, t: Throwable) {
                    Log.e(TAG, "Échec de l'appel API", t)

                    val errorMessage = when (t) {
                        is IllegalStateException -> "Erreur dans le format des données: ${t.message}"
                        is java.net.UnknownHostException -> "Erreur de connexion: Vérifiez votre connexion Internet"
                        is java.net.SocketTimeoutException -> "Délai d'attente dépassé pour la connexion"
                        else -> "Erreur réseau: ${t.message}"
                    }

                    Toast.makeText(this@HomeActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            })
    }

    private fun displayWorkspaces(workspaces: List<Workspace>) {
        // Vider le conteneur des serveurs
        serverListContainer.removeAllViews()

        if (workspaces.isEmpty()) {
            Log.d(TAG, "Aucun workspace à afficher")
            return
        }

        Log.d(TAG, "Affichage de ${workspaces.size} workspaces")

        // Ajouter chaque workspace
        for (workspace in workspaces) {
            try {
                val serverButton = layoutInflater.inflate(
                    R.layout.item_server,
                    serverListContainer,
                    false
                ) as ImageButton

                // Configurer le bouton avec les données du workspace
                serverButton.tag = workspace.nom

                // Log des détails pour débogage
                Log.d(TAG, "Ajout du bouton workspace: id=${workspace.id}, nom=${workspace.nom}")

                // Configurer le clic sur le workspace
                serverButton.setOnClickListener {
                    val workspaceName = it.tag as String
                    Log.d(TAG, "Clic sur workspace: $workspaceName")
                    loadChannels(workspaceName)
                }

                // Ajouter une description accessible pour le bouton
                serverButton.contentDescription = "Serveur ${workspace.nom}"

                // Ajouter le bouton au conteneur
                serverListContainer.addView(serverButton)
            } catch (e: Exception) {
                Log.e(TAG, "Erreur lors de l'ajout du workspace ${workspace.nom}", e)
            }
        }

        // Si nous avons un workspace actif, mettre à jour la sélection visuelle
        currentWorkspaceName?.let { updateWorkspaceSelection(it) }
    }

    // Gérer le bouton retour pour une navigation plus fluide
    override fun onBackPressed() {
        when {
            drawerLayout.isDrawerOpen(GravityCompat.END) -> {
                // Fermer le drawer s'il est ouvert
                drawerLayout.closeDrawer(GravityCompat.END)
            }

            supportFragmentManager.backStackEntryCount > 0 -> {
                // Revenir au fragment précédent si possible
                supportFragmentManager.popBackStack()
            }

            currentWorkspaceName != null -> {
                // Si on est dans un workspace, revenir à l'écran d'accueil
                currentWorkspaceName = null
                showWelcomeScreen()
            }

            else -> {
                // Comportement par défaut (quitter l'app)
                super.onBackPressed()
            }
        }
    }

    // Méthode pour rafraîchir les workspaces (pourrait être appelée par un bouton de rafraîchissement)
    fun refreshWorkspaces() {
        fetchWorkspaces()
    }

    private fun deconnexionUtilisateur() {
        val token = getSharedPreferences("SupChatPrefs", MODE_PRIVATE).getString("auth_token", "") ?: ""

        android.app.AlertDialog.Builder(this)
            .setTitle("Déconnexion")
            .setMessage("Êtes-vous sûr de vouloir vous déconnecter ?")
            .setPositiveButton("Oui") { _, _ -> effectuerDeconnexion(token) }
            .setNegativeButton("Non", null)
            .show()
    }

    private fun effectuerDeconnexion(token: String) {
        val progressDialog = android.app.ProgressDialog(this)
        progressDialog.setMessage("Déconnexion en cours...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        // Appel à l'API de déconnexion
        ApiClient.deconnexion(token)
            .enqueue(object : Callback<DeconnexionResponse> {
                override fun onResponse(call: Call<DeconnexionResponse>, response: Response<DeconnexionResponse>) {
                    progressDialog.dismiss()

                    if (response.isSuccessful) {
                        // Effacer le token d'authentification
                        getSharedPreferences("SupChatPrefs", MODE_PRIVATE)
                            .edit {
                                remove("auth_token")
                            }

                        // Rediriger vers l'activité de connexion
                        redirectToLogin("Vous êtes déconnecté")
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "Erreur lors de la déconnexion: ${response.code()}, $errorBody")

                        Toast.makeText(
                            this@HomeActivity,
                            "Erreur lors de la déconnexion: ${response.code()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<DeconnexionResponse>, t: Throwable) {
                    progressDialog.dismiss()
                    Log.e(TAG, "Échec de la déconnexion", t)

                    Toast.makeText(
                        this@HomeActivity,
                        "Erreur réseau: ${t.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    fun redirectToLogin(message: String = "") {
        // Afficher un message Toast si fourni
        if (message.isNotEmpty()) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }

        // Supprimer le token
        getSharedPreferences("SupChatPrefs", MODE_PRIVATE)
            .edit()
            .remove("auth_token")
            .apply()

        // Créer l'intent pour aller vers LoginActivity
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun openProfile() {
        // Fermer le drawer si ouvert
        if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
            drawerLayout.closeDrawer(GravityCompat.END)
        }

        // Charger le fragment de profil
        val profileFragment = ProfileFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_content_container, profileFragment)
            .addToBackStack(null)
            .commit()

        Log.d(TAG, "Fragment de profil chargé")
    }
    fun applyTheme(isDarkMode: Boolean) {
        // Enregistrer la préférence
        getSharedPreferences("SupChatPrefs", MODE_PRIVATE)
            .edit()
            .putBoolean("dark_mode", isDarkMode)
            .apply()

        // Appliquer le thème
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        // Recréer l'activité pour appliquer le thème
        recreate()
    }
    // Mettre à jour le texte du bouton en fonction du thème actuel
    private fun updateThemeButtonText() {
        val isDarkMode = getSharedPreferences("SupChatPrefs", MODE_PRIVATE)
            .getBoolean("dark_mode", false)

        themeToggleButton.text = if (isDarkMode) {
            "☀️ Thème Clair"
        } else {
            "🌙 Thème Sombre"
        }
    }
    private fun openUserSearch() {
        // Vérifier si le token est présent
        val token = getSharedPreferences("SupChatPrefs", MODE_PRIVATE).getString("auth_token", "")
        if (token.isNullOrEmpty()) {
            Log.e(TAG, "Token d'authentification manquant pour la recherche")
            redirectToLogin("Session expirée, veuillez vous reconnecter")
            return
        }

        // Charger le fragment de recherche
        val searchFragment = UserSearchFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_content_container, searchFragment)
            .addToBackStack(null)
            .commit()

        Log.d(TAG, "Fragment de recherche d'utilisateurs chargé")
    }

    fun openWorkspaceManagement() {
        val workspaceManagementFragment = WorkspaceManagementFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_content_container, workspaceManagementFragment)
            .addToBackStack(null)
            .commit()
    }

    // Ouvrir la gestion des membres d'un workspace
    fun openWorkspaceMembers(workspaceId: String) {
        val workspaceMembersFragment = WorkspaceMembersFragment.newInstance(workspaceId)
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_content_container, workspaceMembersFragment)
            .addToBackStack(null)
            .commit()
    }

    // Ouvrir la gestion des invitations
    fun openWorkspaceInvitations(workspaceId: String) {
        val workspaceInvitationsFragment = WorkspaceInvitationsFragment.newInstance(workspaceId)
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_content_container, workspaceInvitationsFragment)
            .addToBackStack(null)
            .commit()
    }

}