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
import android.view.ViewGroup
import android.widget.Button
import com.google.android.material.textfield.TextInputEditText
import android.graphics.Typeface
import android.view.View
import com.example.supchat.ui.notifications.NotificationsFragment
import com.example.supchat.models.response.notifications.NotificationCountResponse
import android.widget.EditText

class HomeActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var menuButton: ImageButton
    private lateinit var serverListContainer: LinearLayout
    private lateinit var mainContentContainer: FrameLayout
    private lateinit var logoutButton: LinearLayout
    private lateinit var profileButton: LinearLayout
    private var currentWorkspaceName: String? = null
    private var workspaces: List<Workspace> = emptyList()
    private var currentWorkspaceId: String? = null
    private lateinit var themeToggleButton: TextView
    private lateinit var notificationsButton: ImageButton
    private lateinit var notificationBadge: TextView
    private lateinit var searchUsersButton: LinearLayout
    private lateinit var app: SupChatApplication
    private lateinit var supChatTitle: TextView
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

            initializeWebSocket()
            initializeViews()
            setupListeners()
            fetchWorkspaces()
            loadUnreadNotificationCount()
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
            notificationsButton = findViewById(R.id.notifications_button)
            logoutButton = findViewById(R.id.logout_text)
            profileButton = findViewById(R.id.profile_text)
            searchUsersButton = findViewById(R.id.search_users_text)
            supChatTitle = findViewById(R.id.supchat_title)

            // Créer le badge de notification
            notificationBadge = TextView(this).apply {
                layoutParams = ViewGroup.LayoutParams(24, 24)
                textSize = 10f
                setTextColor(getColor(android.R.color.white))
                setBackgroundResource(R.drawable.notification_badge_background)
                gravity = android.view.Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                visibility = View.GONE
            }

            // Ajouter le badge au conteneur parent du bouton
            val notificationContainer = notificationsButton.parent as ViewGroup
            notificationContainer.addView(notificationBadge)

            // Positionner le badge en haut à droite du bouton
            notificationBadge.translationX = 30f
            notificationBadge.translationY = -10f

            Log.d(TAG, "Vues initialisées avec succès")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'initialisation des vues", e)
            throw e
        }
    }

    private fun setupListeners() {
        try {
            supChatTitle.setOnClickListener {
                Log.d(TAG, "Clic sur le titre SupChat - retour à l'accueil")

                // Fermer le drawer s'il est ouvert
                if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                    drawerLayout.closeDrawer(GravityCompat.END)
                }

                // Retourner à l'écran d'accueil (WelcomeFragment)
                currentWorkspaceName = null
                currentWorkspaceId = null
                showWelcomeScreen()

                // Réinitialiser la sélection des workspaces
                updateWorkspaceSelection("")
            }

            // Configuration du bouton notifications
            notificationsButton?.setOnClickListener {
                openNotifications()
            }

            // Configuration du bouton notifications
            notificationsButton?.setOnClickListener {
                openNotifications()
            }

            val createConversationText = findViewById<LinearLayout>(R.id.create_conversation_text)
            createConversationText?.setOnClickListener {
                drawerLayout.closeDrawer(GravityCompat.END)
                showConversationTypeDialog()
            }

            val manageWorkspacesButton = findViewById<LinearLayout>(R.id.manage_workspaces_text)
            manageWorkspacesButton?.setOnClickListener {
                drawerLayout.closeDrawer(GravityCompat.END)
                openWorkspaceManagement()
            }

            val searchPublicWorkspacesButton = findViewById<LinearLayout>(R.id.search_public_workspaces_text)
            searchPublicWorkspacesButton?.setOnClickListener {
                drawerLayout.closeDrawer(GravityCompat.END)
                openPublicWorkspaceSearch()
            }

            val privateMessagesText = findViewById<LinearLayout>(R.id.private_messages_text)
            privateMessagesText?.setOnClickListener {
                drawerLayout.closeDrawer(GravityCompat.END)
                val fragment = PrivateConversationsListFragment()
                supportFragmentManager.beginTransaction()
                    .replace(R.id.main_content_container, fragment)
                    .addToBackStack(null)
                    .commit()
            }

            menuButton?.setOnClickListener {
                if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                    drawerLayout.closeDrawer(GravityCompat.END)
                } else {
                    drawerLayout.openDrawer(GravityCompat.END)
                }
            }

            val logoutButton = findViewById<LinearLayout>(R.id.logout_text)
            logoutButton?.setOnClickListener {
                deconnexionUtilisateur()
            }

            val profileButton = findViewById<LinearLayout>(R.id.profile_text)
            profileButton?.setOnClickListener {
                drawerLayout.closeDrawer(GravityCompat.END)
                openProfile()
            }

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

    private fun openNotifications() {
        try {
            val notificationsFragment = NotificationsFragment.newInstance()
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_content_container, notificationsFragment)
                .addToBackStack(null)
                .commit()

            Log.d(TAG, "Fragment de notifications ouvert")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'ouverture des notifications", e)
            Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateNotificationBadge(unreadCount: Int) {
        runOnUiThread {
            if (unreadCount > 0) {
                notificationBadge.text = if (unreadCount > 99) "99+" else unreadCount.toString()
                notificationBadge.visibility = View.VISIBLE
            } else {
                notificationBadge.visibility = View.GONE
            }

            Log.d(TAG, "Badge de notification mis à jour: $unreadCount")
        }
    }

    private fun loadUnreadNotificationCount() {
        val token = getSharedPreferences("SupChatPrefs", MODE_PRIVATE).getString("auth_token", "")
        if (token.isNullOrEmpty()) {
            Log.w(TAG, "Token manquant pour charger le compteur de notifications")
            return
        }

        ApiClient.getUnreadNotificationCount(token)
            .enqueue(object : Callback<NotificationCountResponse> {
                override fun onResponse(
                    call: Call<NotificationCountResponse>,
                    response: Response<NotificationCountResponse>
                ) {
                    if (response.isSuccessful) {
                        val count = response.body()?.data?.count ?: 0
                        updateNotificationBadge(count)
                        Log.d(TAG, "Compteur de notifications non lues: $count")
                    } else {
                        Log.e(TAG, "Erreur lors du chargement du compteur: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<NotificationCountResponse>, t: Throwable) {
                    Log.e(TAG, "Erreur réseau pour le compteur de notifications", t)
                }
            })
    }

    private fun showConversationTypeDialog() {
        showConversationNameDialog()
    }

    private fun showConversationNameDialog() {
        try {
            Log.d(TAG, "Début de showConversationNameDialog")

            val dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_group_name, null)

            val conversationNameInput = dialogView.findViewById<EditText>(R.id.group_name_input)
            val confirmButton = dialogView.findViewById<Button>(R.id.confirm_group_name_button)
            val cancelButton = dialogView.findViewById<Button>(R.id.cancel_group_name_button)

            if (conversationNameInput == null || confirmButton == null || cancelButton == null) {
                Log.e(TAG, "Erreur: Une ou plusieurs vues sont null")
                Toast.makeText(this, "Erreur dans le layout du dialog", Toast.LENGTH_SHORT).show()
                return
            }

            val titleText = dialogView.findViewById<TextView>(R.id.dialog_title)
            titleText?.text = "💬 Créer une conversation"

            val descriptionText = dialogView.findViewById<TextView>(R.id.dialog_description)
            descriptionText?.text = "Donnez un nom à votre conversation.\nVous pourrez ensuite ajouter des participants."

            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create()

            // Rendre le fond transparent (optionnel)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            confirmButton.setOnClickListener {
                try {
                    val conversationName = conversationNameInput.text.toString().trim()
                    if (conversationName.isNotEmpty()) {
                        dialog.dismiss()
                        // ✅ CHANGEMENT: Utiliser la méthode sécurisée
                        openCreateConversationFragmentSafe(conversationName)
                    } else {
                        Toast.makeText(this, "Veuillez saisir un nom de conversation", Toast.LENGTH_SHORT).show()
                        conversationNameInput.requestFocus()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur dans confirmButton click", e)
                    Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            cancelButton.setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()
            Log.d(TAG, "Dialog affiché avec succès")

        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans showConversationNameDialog", e)
            Toast.makeText(this, "Erreur lors de l'ouverture du dialog: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openCreateConversationFragmentSafe(groupName: String) {
        try {
            Log.d(TAG, "Début de openCreateConversationFragmentSafe avec nom: $groupName")

            // Vérifier que le fragment manager est disponible
            if (supportFragmentManager.isDestroyed || supportFragmentManager.isStateSaved) {
                Log.w(TAG, "FragmentManager non disponible")
                Toast.makeText(this, "Impossible d'ouvrir la conversation maintenant", Toast.LENGTH_SHORT).show()
                return
            }

            // Créer le fragment de manière sécurisée
            val fragment = CreateConversationFragment.newInstance(true, groupName)

            Log.d(TAG, "Fragment CreateConversationFragment créé")

            // Lancer la transaction de manière sécurisée
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_content_container, fragment)
                .addToBackStack(null)
                .commitAllowingStateLoss() // Plus sûr que commit()

            Log.d(TAG, "Transaction de fragment réalisée avec succès")

        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans openCreateConversationFragmentSafe", e)
            Toast.makeText(this, "Erreur lors de l'ouverture: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

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

            if (!app.isWebSocketConnected()) {
                app.initializeWebSocket(token)
                webSocketService = app.getWebSocketService()
            }

            webSocketService?.connectionStatus?.observe(this) { isConnected ->
                onWebSocketConnectionChanged(isConnected)
            }

            Log.d(TAG, "WebSocket initialisé: ${app.isWebSocketConnected()}")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur initialisation WebSocket", e)
        }
    }

    private fun onWebSocketConnectionChanged(isConnected: Boolean) {
        runOnUiThread {
            if (isConnected) {
                Log.d(TAG, "WebSocket connecté")
            } else {
                Log.w(TAG, "WebSocket déconnecté")
            }
        }
    }

    fun getCurrentUserId(): String {
        return getSharedPreferences("SupChatPrefs", MODE_PRIVATE)
            .getString("user_id", "") ?: ""
    }

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

    fun redirectToLogin(message: String = "") {
        try {
            if (message.isNotEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }

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
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::app.isInitialized && !app.isWebSocketConnected()) {
            val token = getSharedPreferences("SupChatPrefs", MODE_PRIVATE).getString("auth_token", "")
            if (!token.isNullOrEmpty()) {
                app.reconnectWebSocket()
            }
        }
        loadUnreadNotificationCount()
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
}