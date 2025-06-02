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
import com.example.supchat.SupChatApplication
import com.example.supchat.api.ApiClient
import com.example.supchat.socket.WebSocketService
import com.example.supchat.ui.conversation.CreateConversationFragment
import android.app.AlertDialog
import android.view.LayoutInflater
import android.widget.Button
import com.google.android.material.textfield.TextInputEditText





class HomeActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var menuButton: ImageButton
    private lateinit var serverListContainer: LinearLayout
    private lateinit var mainContentContainer: FrameLayout

    // ✅ CORRECTION: Changer de TextView vers LinearLayout
    private lateinit var logoutButton: LinearLayout
    private lateinit var profileButton: LinearLayout

    private var currentWorkspaceName: String? = null
    private var workspaces: List<Workspace> = emptyList()
    private var currentWorkspaceId: String? = null
    private lateinit var themeToggleButton: TextView

    // ✅ CORRECTION: Changer de TextView vers LinearLayout
    private lateinit var searchUsersButton: LinearLayout

    // ✅ Propriétés WebSocket
    private lateinit var app: SupChatApplication
    private var webSocketService: WebSocketService? = null

    companion object {
        private const val TAG = "HomeActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_home)

            // ✅ Vérification du token AVANT l'initialisation WebSocket
            val token = getSharedPreferences("SupChatPrefs", MODE_PRIVATE).getString("auth_token", "")
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token d'authentification manquant")
                redirectToLogin("Session expirée, veuillez vous reconnecter")
                return
            }

            // ✅ Initialiser WebSocket seulement si le token existe
            initializeWebSocket()

            // ✅ Initialisation sécurisée des vues
            initializeViews()

            // ✅ Configuration des listeners
            setupListeners()

            // Charger les workspaces depuis l'API
            fetchWorkspaces()

            // Afficher l'écran d'accueil en attendant
            showWelcomeScreen()

        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans onCreate", e)
            Toast.makeText(this, "Erreur d'initialisation: ${e.message}", Toast.LENGTH_LONG).show()
            redirectToLogin("Erreur d'initialisation")
        }
    }

    private fun initializeViews() {
        try {
            drawerLayout = findViewById(R.id.drawer_layout)
            menuButton = findViewById(R.id.menu_button)
            serverListContainer = findViewById(R.id.server_list)
            mainContentContainer = findViewById(R.id.main_content_container)

            // ✅ CORRECTION: Utiliser LinearLayout au lieu de TextView
            logoutButton = findViewById(R.id.logout_text)
            profileButton = findViewById(R.id.profile_text)
            searchUsersButton = findViewById(R.id.search_users_text)

            Log.d(TAG, "Vues initialisées avec succès")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'initialisation des vues", e)
            throw e
        }
    }
    private fun setupListeners() {
        try {
            // ✅ CORRECTION: Utiliser LinearLayout au lieu de TextView
            // Configuration du bouton "Créer une conversation" avec dialogue
            val createConversationText = findViewById<LinearLayout>(R.id.create_conversation_text)
            createConversationText?.setOnClickListener {
                drawerLayout.closeDrawer(GravityCompat.END)
                showConversationTypeDialog()
            }

            // ✅ CORRECTION: Utiliser LinearLayout au lieu de TextView
            // Initialiser le bouton de gestion des workspaces
            val manageWorkspacesButton = findViewById<LinearLayout>(R.id.manage_workspaces_text)
            manageWorkspacesButton?.setOnClickListener {
                drawerLayout.closeDrawer(GravityCompat.END)
                openWorkspaceManagement()
            }

            // ✅ CORRECTION: Utiliser LinearLayout au lieu de TextView
            // Initialiser le bouton de recherche de workspaces publics
            val searchPublicWorkspacesButton = findViewById<LinearLayout>(R.id.search_public_workspaces_text)
            searchPublicWorkspacesButton?.setOnClickListener {
                drawerLayout.closeDrawer(GravityCompat.END)
                openPublicWorkspaceSearch()
            }

            // ✅ CORRECTION: Utiliser LinearLayout au lieu de TextView
            val privateMessagesText = findViewById<LinearLayout>(R.id.private_messages_text)
            privateMessagesText?.setOnClickListener {
                drawerLayout.closeDrawer(GravityCompat.END)
                val fragment = PrivateConversationsListFragment()
                supportFragmentManager.beginTransaction()
                    .replace(R.id.main_content_container, fragment)
                    .addToBackStack(null)
                    .commit()
            }

            // Configuration du bouton menu pour ouvrir/fermer le drawer
            menuButton?.setOnClickListener {
                if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                    drawerLayout.closeDrawer(GravityCompat.END)
                } else {
                    drawerLayout.openDrawer(GravityCompat.END)
                }
            }

            // ✅ CORRECTION: Utiliser LinearLayout au lieu de TextView
            // Configuration du bouton de déconnexion
            val logoutButton = findViewById<LinearLayout>(R.id.logout_text)
            logoutButton?.setOnClickListener {
                deconnexionUtilisateur()
            }

            // ✅ CORRECTION: Utiliser LinearLayout au lieu de TextView
            // Configuration du bouton de profil
            val profileButton = findViewById<LinearLayout>(R.id.profile_text)
            profileButton?.setOnClickListener {
                drawerLayout.closeDrawer(GravityCompat.END)
                openProfile()
            }

            // ✅ CORRECTION: Utiliser LinearLayout au lieu de TextView
            // Configuration du bouton de recherche d'utilisateurs
            val searchUsersButton = findViewById<LinearLayout>(R.id.search_users_text)
            searchUsersButton?.setOnClickListener {
                drawerLayout.closeDrawer(GravityCompat.END)
                openUserSearch()
            }

            Log.d(TAG, "Listeners configurés avec succès")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la configuration des listeners", e)
            throw e
        }
    }

    private fun showConversationTypeDialog() {
        // ✅ NOUVEAU: Dialogue direct pour saisir le nom de la conversation
        showConversationNameDialog()
    }

    private fun showConversationNameDialog() {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_group_name, null)

        val conversationNameInput: TextInputEditText = dialogView.findViewById(R.id.group_name_input)
        val confirmButton: Button = dialogView.findViewById(R.id.confirm_group_name_button)
        val cancelButton: Button = dialogView.findViewById(R.id.cancel_group_name_button)

        // ✅ Modifier les textes pour être plus génériques
        val titleText = dialogView.findViewById<TextView>(R.id.dialog_title) // Si vous avez un titre dans le dialogue
        titleText?.text = "💬 Créer une conversation"

        val descriptionText = dialogView.findViewById<TextView>(R.id.dialog_description) // Si vous avez une description
        descriptionText?.text = "Donnez un nom à votre conversation.\nVous pourrez ensuite ajouter des participants."

        // Changer le hint du champ de saisie
        conversationNameInput.hint = "Nom de la conversation"

        // Changer le texte du bouton
        confirmButton.text = "✓ Créer"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        confirmButton.setOnClickListener {
            val conversationName = conversationNameInput.text.toString().trim()
            if (conversationName.isNotEmpty()) {
                dialog.dismiss()
                // ✅ Toujours créer comme un groupe avec le nom donné
                openCreateConversationFragment(isGroup = true, groupName = conversationName)
            } else {
                Toast.makeText(this, "Veuillez saisir un nom de conversation", Toast.LENGTH_SHORT).show()
            }
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun openCreateConversationFragment(isGroup: Boolean, groupName: String?) {
        val fragment = CreateConversationFragment.newInstance(isGroup, groupName)

        supportFragmentManager.beginTransaction()
            .replace(R.id.main_content_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    // ✅ Méthode pour initialiser WebSocket
    private fun initializeWebSocket() {
        try {
            app = application as? SupChatApplication ?: run {
                Log.e(TAG, "Application n'est pas une instance de SupChatApplication")
                return
            }

            val token = getSharedPreferences("SupChatPrefs", MODE_PRIVATE).getString("auth_token", "")
            if (token.isNullOrEmpty()) {
                Log.w(TAG, "Token manquant pour WebSocket")
                return
            }

            webSocketService = app.getWebSocketService()

            // Si WebSocket n'est pas connecté, l'initialiser
            if (!app.isWebSocketConnected()) {
                app.initializeWebSocket(token)
                webSocketService = app.getWebSocketService()
            }

            // Observer le statut de connexion
            webSocketService?.connectionStatus?.observe(this) { isConnected ->
                onWebSocketConnectionChanged(isConnected)
            }

            Log.d(TAG, "WebSocket initialisé: ${app.isWebSocketConnected()}")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur initialisation WebSocket", e)
            // Ne pas faire planter l'app si WebSocket échoue
        }
    }

    // ✅ Gérer les changements de connexion WebSocket
    private fun onWebSocketConnectionChanged(isConnected: Boolean) {
        runOnUiThread {
            if (isConnected) {
                Log.d(TAG, "WebSocket connecté")
                // Optionnel: afficher un indicateur de connexion
            } else {
                Log.w(TAG, "WebSocket déconnecté")
                // Optionnel: afficher un indicateur de déconnexion
            }
        }
    }

    // ✅ Obtenir l'ID de l'utilisateur actuel
    fun getCurrentUserId(): String {
        return getSharedPreferences("SupChatPrefs", MODE_PRIVATE)
            .getString("user_id", "") ?: ""
    }

    // ✅ Obtenir le nom d'utilisateur actuel
    fun getCurrentUsername(): String {
        return getSharedPreferences("SupChatPrefs", MODE_PRIVATE)
            .getString("username", "") ?: ""
    }

    private fun openPublicWorkspaceSearch() {
        val workspaceManagementFragment = WorkspaceManagementFragment.newInstance()
        val args = Bundle()
        args.putBoolean("showPublicSearch", true)
        workspaceManagementFragment.arguments = args
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_content_container, workspaceManagementFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun showWelcomeScreen() {
        val welcomeFragment = WelcomeFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_content_container, welcomeFragment)
            .commit()
    }

    private fun loadChannels(workspaceName: String) {
        if (workspaceName == currentWorkspaceName &&
            supportFragmentManager.findFragmentById(R.id.main_content_container) is CanauxFragment
        ) {
            return
        }

        currentWorkspaceName = workspaceName
        val workspace = workspaces.find { it.nom == workspaceName }
        val workspaceId = workspace?.id

        if (workspaceId.isNullOrEmpty()) {
            Log.e(TAG, "ID du workspace non trouvé pour $workspaceName")
            Toast.makeText(this, "Erreur: ID du workspace non trouvé", Toast.LENGTH_SHORT).show()
            return
        }

        currentWorkspaceId = workspaceId
        updateWorkspaceSelection(workspaceName)

        Log.d(TAG, "Chargement des canaux pour le workspace $workspaceName (ID: $workspaceId)")

        val canauxFragment = CanauxFragment.newInstance(workspaceName, workspaceId)
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_content_container, canauxFragment)
            .commit()
    }

    private fun updateWorkspaceSelection(workspaceName: String) {
        for (i in 0 until serverListContainer.childCount) {
            val serverButton = serverListContainer.getChildAt(i) as? ImageButton
            if (serverButton != null) {
                val isSelected = serverButton.tag == workspaceName
                if (isSelected) {
                    serverButton.setBackgroundResource(R.drawable.selected_rounded_button)
                } else {
                    serverButton.setBackgroundResource(R.drawable.rounded_button)
                }
            }
        }
    }

    fun openChat(canalId: String, canalNom: String) {
        try {
            Log.d(TAG, "Ouverture du chat: canal=$canalId, nom=$canalNom, workspace=$currentWorkspaceId")

            if (canalId.isEmpty()) {
                Log.e(TAG, "Erreur: canalId est vide")
                Toast.makeText(this, "Erreur: ID du canal invalide", Toast.LENGTH_SHORT).show()
                return
            }

            if (currentWorkspaceId.isNullOrEmpty()) {
                Log.e(TAG, "Erreur: currentWorkspaceId est null ou vide")
                Toast.makeText(this, "Erreur: ID du workspace manquant", Toast.LENGTH_SHORT).show()
                return
            }

            val token = getSharedPreferences("SupChatPrefs", MODE_PRIVATE).getString("auth_token", "")
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Token manquant avant ouverture du chat")
                redirectToLogin("Session expirée, veuillez vous reconnecter")
                return
            }

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

                            workspacesList.forEachIndexed { index, workspace ->
                                Log.d(TAG, "Workspace[$index]: id=${workspace.id}, nom=${workspace.nom}, " +
                                        "proprietaire=${workspace.proprietaire}, visibilite=${workspace.visibilite}")
                            }

                            workspaces = workspacesList
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
        serverListContainer.removeAllViews()

        if (workspaces.isEmpty()) {
            Log.d(TAG, "Aucun workspace à afficher")
            return
        }

        Log.d(TAG, "Affichage de ${workspaces.size} workspaces")

        for (workspace in workspaces) {
            try {
                val serverButton = layoutInflater.inflate(
                    R.layout.item_server,
                    serverListContainer,
                    false
                ) as ImageButton

                serverButton.tag = workspace.nom
                Log.d(TAG, "Ajout du bouton workspace: id=${workspace.id}, nom=${workspace.nom}")

                serverButton.setOnClickListener {
                    val workspaceName = it.tag as String
                    Log.d(TAG, "Clic sur workspace: $workspaceName")
                    loadChannels(workspaceName)
                }

                serverButton.contentDescription = "Serveur ${workspace.nom}"
                serverListContainer.addView(serverButton)
            } catch (e: Exception) {
                Log.e(TAG, "Erreur lors de l'ajout du workspace ${workspace.nom}", e)
            }
        }

        currentWorkspaceName?.let { updateWorkspaceSelection(it) }
    }

    override fun onBackPressed() {
        when {
            drawerLayout.isDrawerOpen(GravityCompat.END) -> {
                drawerLayout.closeDrawer(GravityCompat.END)
            }
            supportFragmentManager.backStackEntryCount > 0 -> {
                supportFragmentManager.popBackStack()
            }
            currentWorkspaceName != null -> {
                currentWorkspaceName = null
                showWelcomeScreen()
            }
            else -> {
                super.onBackPressed()
            }
        }
    }

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

        // ✅ Déconnecter WebSocket avant l'API
        app.disconnectWebSocket()

        ApiClient.deconnexion(token)
            .enqueue(object : Callback<DeconnexionResponse> {
                override fun onResponse(call: Call<DeconnexionResponse>, response: Response<DeconnexionResponse>) {
                    progressDialog.dismiss()

                    if (response.isSuccessful) {
                        getSharedPreferences("SupChatPrefs", MODE_PRIVATE)
                            .edit()
                            .clear()
                            .apply()

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

    // ✅ Méthode redirectToLogin modifiée
    fun redirectToLogin(message: String = "") {
        try {
            if (message.isNotEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }

            // Déconnecter WebSocket seulement s'il existe
            if (::app.isInitialized) {
                app.disconnectWebSocket()
            }

            getSharedPreferences("SupChatPrefs", MODE_PRIVATE)
                .edit()
                .clear()
                .apply()

            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans redirectToLogin", e)
            finish() // Fermer l'activité même en cas d'erreur
        }
    }

    override fun onResume() {
        super.onResume()
        // ✅ Reconnecter WebSocket si nécessaire
        if (!app.isWebSocketConnected()) {
            val token = getSharedPreferences("SupChatPrefs", MODE_PRIVATE).getString("auth_token", "")
            if (!token.isNullOrEmpty()) {
                app.reconnectWebSocket()
            }
        }
    }

    fun applyTheme(isDarkMode: Boolean) {
        getSharedPreferences("SupChatPrefs", MODE_PRIVATE)
            .edit()
            .putBoolean("dark_mode", isDarkMode)
            .apply()

        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        recreate()
    }

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
        val token = getSharedPreferences("SupChatPrefs", MODE_PRIVATE).getString("auth_token", "")
        if (token.isNullOrEmpty()) {
            Log.e(TAG, "Token d'authentification manquant pour la recherche")
            redirectToLogin("Session expirée, veuillez vous reconnecter")
            return
        }

        val searchFragment = UserSearchFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_content_container, searchFragment)
            .addToBackStack(null)
            .commit()

        Log.d(TAG, "Fragment de recherche d'utilisateurs chargé")
    }

    private fun openProfile() {
        if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
            drawerLayout.closeDrawer(GravityCompat.END)
        }

        val profileFragment = ProfileFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_content_container, profileFragment)
            .addToBackStack(null)
            .commit()

        Log.d(TAG, "Fragment de profil chargé")
    }

    fun openWorkspaceManagement() {
        val workspaceManagementFragment = WorkspaceManagementFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_content_container, workspaceManagementFragment)
            .addToBackStack(null)
            .commit()
    }

    fun openWorkspaceMembers(workspaceId: String) {
        val workspaceMembersFragment = WorkspaceMembersFragment.newInstance(workspaceId)
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_content_container, workspaceMembersFragment)
            .addToBackStack(null)
            .commit()
    }

    fun openWorkspaceInvitations(workspaceId: String) {
        val workspaceInvitationsFragment = WorkspaceInvitationsFragment.newInstance(workspaceId)
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_content_container, workspaceInvitationsFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun showCreateConversationFragment() {
        // Cette méthode est maintenant remplacée par showConversationTypeDialog()
        // Gardez-la pour compatibilité si elle est utilisée ailleurs, sinon supprimez-la
        showConversationTypeDialog()
    }
}