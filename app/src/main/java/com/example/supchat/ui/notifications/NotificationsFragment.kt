package com.example.supchat.ui.notifications

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.supchat.R
import com.example.supchat.api.ApiClient
import com.example.supchat.models.response.notifications.Notification
import com.example.supchat.models.response.notifications.NotificationsResponse
import com.example.supchat.models.response.notifications.isChannelMessage
import com.example.supchat.models.response.notifications.isPrivateMessage
import com.example.supchat.models.response.notifications.isWorkspaceInvite
import com.example.supchat.ui.home.HomeActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class NotificationsFragment : Fragment() {

    companion object {
        private const val TAG = "NotificationsFragment"

        fun newInstance(): NotificationsFragment {
            return NotificationsFragment()
        }
    }

    // ===== VUES PRINCIPALES =====
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NotificationAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var markAllReadFab: FloatingActionButton
    private lateinit var fabRefresh: FloatingActionButton

    // ===== VUES D'ÉTAT =====
    private lateinit var emptyStateText: LinearLayout
    private lateinit var loadingState: LinearLayout
    private lateinit var errorState: LinearLayout
    private lateinit var progressBar: ProgressBar

    // ===== VUES DE CONTENU =====
    private lateinit var unreadCountContainer: LinearLayout
    private lateinit var unreadCountText: TextView
    private lateinit var refreshButton: Button
    private lateinit var retryButton: Button
    private lateinit var errorMessage: TextView
    private lateinit var menuButton: ImageView

    // ===== DONNÉES =====
    private var notifications: MutableList<Notification> = mutableListOf()

    // ===== LIFECYCLE =====

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "🔄 onCreateView - Création du fragment notifications")
        return inflater.inflate(R.layout.fragment_notifications, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "🔄 onViewCreated - Configuration du fragment")

        try {
            initializeViews(view)
            setupRecyclerView()
            setupSwipeRefresh()
            setupButtons()
            setupFabs()

            // Charger les notifications
            loadNotifications()

            // Test temporaire - supprimez après test
            // addTestNotifications()

            Log.d(TAG, "✅ Fragment notifications configuré avec succès")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur configuration fragment", e)
            showErrorState("Erreur lors de l'initialisation: ${e.message}")
        }
    }

    // ===== INITIALISATION DES VUES =====

    private fun initializeViews(view: View) {
        Log.d(TAG, "🔧 Initialisation des vues...")

        try {
            // Vues principales
            recyclerView = view.findViewById(R.id.recycler_notifications)
            swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_notifications)
            markAllReadFab = view.findViewById(R.id.fab_mark_all_read)
            fabRefresh = view.findViewById(R.id.fab_refresh)

            // États d'affichage
            emptyStateText = view.findViewById(R.id.empty_state_notifications)
            loadingState = view.findViewById(R.id.loading_state)
            errorState = view.findViewById(R.id.error_state)
            progressBar = view.findViewById(R.id.progress_notifications)

            // Contenus
            unreadCountContainer = view.findViewById(R.id.unread_count_container)
            unreadCountText = view.findViewById(R.id.unread_count_text)
            refreshButton = view.findViewById(R.id.refresh_button)
            retryButton = view.findViewById(R.id.retry_button)
            errorMessage = view.findViewById(R.id.error_message)
            menuButton = view.findViewById(R.id.menu_notifications)

            Log.d(TAG, "✅ Vues initialisées avec succès")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur initialisation vues", e)
            throw e
        }
    }

    private fun setupRecyclerView() {
        Log.d(TAG, "🔧 Configuration RecyclerView...")

        adapter = NotificationAdapter(
            notifications = notifications,
            onNotificationClick = { notification -> onNotificationClicked(notification) },
            onMarkAsRead = { notification -> markNotificationAsRead(notification.id) }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@NotificationsFragment.adapter
            setHasFixedSize(true)
        }

        Log.d(TAG, "✅ RecyclerView configuré")
    }

    private fun setupSwipeRefresh() {
        Log.d(TAG, "🔧 Configuration SwipeRefresh...")

        swipeRefreshLayout.apply {
            setOnRefreshListener {
                Log.d(TAG, "🔄 SwipeRefresh déclenché")
                loadNotifications()
            }

            setColorSchemeResources(
                android.R.color.holo_orange_dark,
                android.R.color.holo_blue_dark,
                android.R.color.holo_green_dark,
                android.R.color.holo_red_dark
            )
        }

        Log.d(TAG, "✅ SwipeRefresh configuré")
    }

    private fun setupButtons() {
        Log.d(TAG, "🔧 Configuration des boutons...")

        // Bouton refresh dans l'état vide
        refreshButton.setOnClickListener {
            Log.d(TAG, "🔄 Bouton refresh cliqué")
            loadNotifications()
        }

        // Bouton retry dans l'état d'erreur
        retryButton.setOnClickListener {
            Log.d(TAG, "🔄 Bouton retry cliqué")
            loadNotifications()
        }

        // Menu options
        menuButton.setOnClickListener {
            Log.d(TAG, "📋 Menu notifications cliqué")
            showNotificationsMenu()
        }

        Log.d(TAG, "✅ Boutons configurés")
    }

    private fun setupFabs() {
        Log.d(TAG, "🔧 Configuration des FABs...")

        // FAB marquer toutes comme lues
        markAllReadFab.setOnClickListener {
            Log.d(TAG, "✅ FAB mark all read cliqué")
            markAllNotificationsAsRead()
        }

        // FAB refresh
        fabRefresh.setOnClickListener {
            Log.d(TAG, "🔄 FAB refresh cliqué")
            loadNotifications()
        }

        updateFabVisibility()
        Log.d(TAG, "✅ FABs configurés")
    }

    // ===== GESTION DES ÉTATS D'AFFICHAGE =====

    private fun showLoadingState() {
        Log.d(TAG, "⏳ Affichage état chargement")

        loadingState.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyStateText.visibility = View.GONE
        errorState.visibility = View.GONE
        swipeRefreshLayout.isRefreshing = false
    }

    private fun showErrorState(message: String) {
        Log.e(TAG, "❌ Affichage état erreur: $message")

        errorState.visibility = View.VISIBLE
        errorMessage.text = message
        recyclerView.visibility = View.GONE
        emptyStateText.visibility = View.GONE
        loadingState.visibility = View.GONE
        swipeRefreshLayout.isRefreshing = false
    }

    private fun showContentState() {
        Log.d(TAG, "📋 Affichage état contenu")

        loadingState.visibility = View.GONE
        errorState.visibility = View.GONE
        swipeRefreshLayout.isRefreshing = false

        if (notifications.isEmpty()) {
            emptyStateText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            Log.d(TAG, "📭 Aucune notification - État vide")
        } else {
            emptyStateText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            Log.d(TAG, "📬 ${notifications.size} notifications affichées")
        }
    }

    // ===== CHARGEMENT DES NOTIFICATIONS =====

    private fun loadNotifications() {
        Log.d(TAG, "=== 🔄 DÉBUT CHARGEMENT NOTIFICATIONS ===")

        val token = requireActivity().getSharedPreferences("SupChatPrefs", android.content.Context.MODE_PRIVATE)
            .getString("auth_token", "") ?: ""

        if (token.isEmpty()) {
            Log.e(TAG, "❌ Token manquant")
            (requireActivity() as HomeActivity).redirectToLogin("Session expirée")
            return
        }

        Log.d(TAG, "🔑 Token présent: ${token.take(20)}...")
        showLoadingState()

        ApiClient.getNotifications(token)
            .enqueue(object : Callback<NotificationsResponse> {
                override fun onResponse(
                    call: Call<NotificationsResponse>,
                    response: Response<NotificationsResponse>
                ) {
                    Log.d(TAG, "📡 Réponse API reçue")
                    Log.d(TAG, "📍 URL appelée: ${call.request().url}")
                    Log.d(TAG, "📊 Code réponse: ${response.code()}")

                    if (response.isSuccessful) {
                        handleSuccessfulResponse(response)
                    } else {
                        handleErrorResponse(response)
                    }
                }

                override fun onFailure(call: Call<NotificationsResponse>, t: Throwable) {
                    Log.e(TAG, "❌ Échec appel API", t)
                    Log.e(TAG, "🌐 URL: ${call.request().url}")

                    val errorMsg = when (t) {
                        is java.net.UnknownHostException -> "🌐 Pas de connexion Internet"
                        is java.net.SocketTimeoutException -> "⏱️ Délai d'attente dépassé"
                        is java.net.ConnectException -> "🔌 Impossible de se connecter au serveur"
                        else -> "❌ Erreur réseau: ${t.message}"
                    }

                    showErrorState(errorMsg)
                }
            })
    }

    private fun handleSuccessfulResponse(response: Response<NotificationsResponse>) {
        try {
            val notificationsResponse = response.body()
            val notificationsList = notificationsResponse?.data?.notifications ?: emptyList()

            Log.d(TAG, "✅ ${notificationsList.size} notifications reçues")
            Log.d(TAG, "📊 Body complet: $notificationsResponse")

            // Analyser les notifications reçues
            notificationsList.forEachIndexed { index, notif ->
                Log.d(TAG, "[$index] Notification:")
                Log.d(TAG, "  - ID: ${notif.id}")
                Log.d(TAG, "  - Type: ${notif.type}")
                Log.d(TAG, "  - OnModel: ${notif.onModel}")
                Log.d(TAG, "  - Message: ${notif.message}")
                Log.d(TAG, "  - Lu: ${notif.lu}")
                Log.d(TAG, "  - Créé: ${notif.createdAt}")

                // Vérifications spécifiques
                when {
                    notif.isPrivateMessage() -> Log.d(TAG, "  ✅ MESSAGE PRIVÉ détecté!")
                    notif.isChannelMessage() -> Log.d(TAG, "  📺 Message canal détecté!")
                    notif.isWorkspaceInvite() -> Log.d(TAG, "  🏢 Invitation workspace détectée!")
                    else -> Log.d(TAG, "  ❓ Type inconnu: ${notif.type}")
                }
            }

            // Mettre à jour les données
            notifications.clear()
            notifications.addAll(notificationsList.sortedByDescending { it.createdAt })
            adapter.updateNotifications(notifications)

            showContentState()
            updateUnreadCountDisplay()
            updateFabVisibility()

            val unreadCount = notifications.count { !it.lu }
            val privateCount = notifications.count { it.isPrivateMessage() }
            val channelCount = notifications.count { it.isChannelMessage() }

            Log.d(TAG, "📈 RÉSUMÉ:")
            Log.d(TAG, "  - Total: ${notifications.size}")
            Log.d(TAG, "  - Non lues: $unreadCount")
            Log.d(TAG, "  - Messages privés: $privateCount")
            Log.d(TAG, "  - Messages canaux: $channelCount")

            updateNotificationBadge(unreadCount)

            Log.d(TAG, "=== ✅ CHARGEMENT TERMINÉ AVEC SUCCÈS ===")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur traitement réponse", e)
            showErrorState("Erreur lors du traitement des données: ${e.message}")
        }
    }

    private fun handleErrorResponse(response: Response<NotificationsResponse>) {
        val errorBody = response.errorBody()?.string()
        Log.e(TAG, "❌ Erreur API: ${response.code()}")
        Log.e(TAG, "📄 Error body: $errorBody")
        Log.e(TAG, "📋 Headers: ${response.headers()}")

        val errorMsg = when (response.code()) {
            401 -> {
                Log.e(TAG, "🔐 Session expirée")
                (requireActivity() as HomeActivity).redirectToLogin("Session expirée")
                return
            }
            403 -> "🚫 Accès non autorisé aux notifications"
            404 -> "🔍 Endpoint de notifications non trouvé\nVérifiez l'URL: ${response.raw().request.url}"
            500 -> "🔧 Erreur serveur lors du chargement"
            else -> "❌ Erreur lors du chargement des notifications (${response.code()})"
        }

        showErrorState(errorMsg)
    }

    // ===== GESTION DES NOTIFICATIONS =====

    private fun markNotificationAsRead(notificationId: String) {
        Log.d(TAG, "✅ Marquage notification comme lue: $notificationId")

        val token = requireActivity().getSharedPreferences("SupChatPrefs", android.content.Context.MODE_PRIVATE)
            .getString("auth_token", "") ?: ""

        if (token.isEmpty()) {
            (requireActivity() as HomeActivity).redirectToLogin("Session expirée")
            return
        }

        ApiClient.markNotificationAsRead(token, notificationId)
            .enqueue(object : Callback<NotificationsResponse> {
                override fun onResponse(
                    call: Call<NotificationsResponse>,
                    response: Response<NotificationsResponse>
                ) {
                    if (response.isSuccessful) {
                        Log.d(TAG, "✅ Notification marquée comme lue: $notificationId")

                        adapter.markNotificationAsRead(notificationId)
                        updateUnreadCountDisplay()
                        updateFabVisibility()

                        val unreadCount = adapter.getUnreadCount()
                        updateNotificationBadge(unreadCount)

                        Toast.makeText(requireContext(), "✅ Notification marquée comme lue", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.e(TAG, "❌ Erreur marquage: ${response.code()}")
                        Toast.makeText(requireContext(), "❌ Erreur lors du marquage", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<NotificationsResponse>, t: Throwable) {
                    Log.e(TAG, "❌ Erreur réseau marquage", t)
                    Toast.makeText(requireContext(), "❌ Erreur réseau", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun markAllNotificationsAsRead() {
        Log.d(TAG, "✅ Marquage toutes notifications comme lues")

        val token = requireActivity().getSharedPreferences("SupChatPrefs", android.content.Context.MODE_PRIVATE)
            .getString("auth_token", "") ?: ""

        if (token.isEmpty()) {
            (requireActivity() as HomeActivity).redirectToLogin("Session expirée")
            return
        }

        val unreadNotifications = notifications.filter { !it.lu }
        if (unreadNotifications.isEmpty()) {
            Toast.makeText(requireContext(), "📭 Aucune notification non lue", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "📊 ${unreadNotifications.size} notifications à marquer comme lues")

        ApiClient.markAllNotificationsAsRead(token)
            .enqueue(object : Callback<NotificationsResponse> {
                override fun onResponse(
                    call: Call<NotificationsResponse>,
                    response: Response<NotificationsResponse>
                ) {
                    if (response.isSuccessful) {
                        Log.d(TAG, "✅ Toutes les notifications marquées comme lues")
                        loadNotifications()
                        Toast.makeText(requireContext(), "✅ Toutes les notifications marquées comme lues", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.e(TAG, "❌ Erreur marquage global: ${response.code()}")
                        Toast.makeText(requireContext(), "❌ Erreur lors du marquage", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<NotificationsResponse>, t: Throwable) {
                    Log.e(TAG, "❌ Erreur réseau marquage global", t)
                    Toast.makeText(requireContext(), "❌ Erreur réseau", Toast.LENGTH_SHORT).show()
                }
            })
    }

    // ===== NAVIGATION =====

    private fun onNotificationClicked(notification: Notification) {
        Log.d(TAG, "👆 Notification cliquée: ${notification.id}")
        Log.d(TAG, "  Type: ${notification.type}")
        Log.d(TAG, "  Message: ${notification.message}")

        if (!notification.lu) {
            markNotificationAsRead(notification.id)
        }

        when {
            notification.isPrivateMessage() -> {
                Log.d(TAG, "🔄 Navigation vers message privé")
                navigateToPrivateMessage(notification)
            }
            notification.isChannelMessage() -> {
                Log.d(TAG, "🔄 Navigation vers canal")
                navigateToChannel(notification)
            }
            notification.isWorkspaceInvite() -> {
                Log.d(TAG, "🔄 Navigation vers workspace")
                navigateToWorkspace(notification)
            }
            else -> {
                Log.w(TAG, "❓ Type de notification non géré: ${notification.type}")
                Toast.makeText(requireContext(), "Type de notification non géré: ${notification.type}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToPrivateMessage(notification: Notification) {
        try {
            val homeActivity = requireActivity() as HomeActivity
            homeActivity.supportFragmentManager.beginTransaction()
                .replace(R.id.main_content_container, com.example.supchat.ui.PrivateConversationsListFragment())
                .addToBackStack(null)
                .commit()

            Log.d(TAG, "✅ Navigation vers messages privés réussie")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur navigation message privé", e)
            Toast.makeText(requireContext(), "❌ Erreur lors de l'ouverture", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToChannel(notification: Notification) {
        try {
            Log.d(TAG, "📺 Navigation vers canal: ${notification.reference}")
            Toast.makeText(requireContext(), "📺 Navigation vers le canal en cours de développement", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur navigation canal", e)
            Toast.makeText(requireContext(), "❌ Erreur lors de l'ouverture", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToWorkspace(notification: Notification) {
        try {
            (requireActivity() as HomeActivity).openWorkspaceManagement()
            Log.d(TAG, "✅ Navigation vers workspace réussie")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur navigation workspace", e)
            Toast.makeText(requireContext(), "❌ Erreur lors de l'ouverture", Toast.LENGTH_SHORT).show()
        }
    }

    // ===== MISE À JOUR DE L'INTERFACE =====

    private fun updateUnreadCountDisplay() {
        val unreadCount = notifications.count { !it.lu }

        if (unreadCount > 0) {
            unreadCountContainer.visibility = View.VISIBLE
            unreadCountText.text = unreadCount.toString()
            Log.d(TAG, "📊 Compteur: $unreadCount notifications non lues")
        } else {
            unreadCountContainer.visibility = View.GONE
            Log.d(TAG, "📊 Aucune notification non lue")
        }
    }

    private fun updateFabVisibility() {
        val hasUnreadNotifications = notifications.any { !it.lu }
        markAllReadFab.visibility = if (hasUnreadNotifications) View.VISIBLE else View.GONE

        Log.d(TAG, "🎈 FAB mark all read: ${if (hasUnreadNotifications) "visible" else "masqué"}")
    }

    private fun updateNotificationBadge(unreadCount: Int) {
        try {
            (requireActivity() as? HomeActivity)?.updateNotificationBadge(unreadCount)
            Log.d(TAG, "🔔 Badge mis à jour: $unreadCount")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur mise à jour badge", e)
        }
    }

    // ===== MENU ET OPTIONS =====

    private fun showNotificationsMenu() {
        val popupMenu = android.widget.PopupMenu(requireContext(), menuButton)

        popupMenu.menu.add(0, 1, 0, "✅ Marquer toutes comme lues")
        popupMenu.menu.add(0, 2, 0, "🔄 Actualiser")
        popupMenu.menu.add(0, 3, 0, "🔍 Filtrer")
        popupMenu.menu.add(0, 4, 0, "🧪 Ajouter test")
        popupMenu.menu.add(0, 5, 0, "🗑️ Vider toutes")

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    markAllNotificationsAsRead()
                    true
                }
                2 -> {
                    loadNotifications()
                    true
                }
                3 -> {
                    showFilterOptions()
                    true
                }
                4 -> {
                    addTestNotifications()
                    true
                }
                5 -> {
                    clearAllNotifications()
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
    }

    private fun showFilterOptions() {
        val options = arrayOf("Toutes", "Non lues", "Messages privés", "Canaux", "Invitations")

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Filtrer les notifications")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> filterNotifications("all")
                    1 -> filterNotifications("unread")
                    2 -> filterNotifications("private")
                    3 -> filterNotifications("channel")
                    4 -> filterNotifications("invite")
                }
            }
            .show()
    }

    private fun filterNotifications(type: String) {
        val filteredNotifications = when (type) {
            "unread" -> notifications.filter { !it.lu }
            "private" -> notifications.filter { it.isPrivateMessage() }
            "channel" -> notifications.filter { it.isChannelMessage() }
            "invite" -> notifications.filter { it.isWorkspaceInvite() }
            else -> notifications
        }

        adapter.updateNotifications(filteredNotifications)

        Toast.makeText(
            requireContext(),
            "🔍 Affichage: ${filteredNotifications.size} notification(s)",
            Toast.LENGTH_SHORT
        ).show()

        Log.d(TAG, "🔍 Filtre appliqué: $type -> ${filteredNotifications.size} résultats")
    }

    // ===== MÉTHODES DE TEST =====

    private fun addTestNotifications() {
        Log.d(TAG, "🧪 Ajout de notifications de test...")

        val testNotifications = listOf(
            Notification(
                id = "test_private_${System.currentTimeMillis()}",
                utilisateur = "user1",
                type = "message_prive",
                reference = "conv1",
                onModel = "ConversationPrivee",
                message = "📧 Test: Nouveau message privé de John Doe",
                lu = false,
                createdAt = "2025-06-15T15:40:40.900Z"
            ),
            Notification(
                id = "test_channel_${System.currentTimeMillis()}",
                utilisateur = "user2",
                type = "canal",
                reference = "canal1",
                onModel = "Canal",
                message = "📺 Test: Message dans le canal général",
                lu = false,
                createdAt = "2025-06-15T15:35:40.900Z"
            ),
            Notification(
                id = "test_invite_${System.currentTimeMillis()}",
                utilisateur = "user3",
                type = "workspace_invite",
                reference = "workspace1",
                onModel = "Workspace",
                message = "🏢 Test: Invitation au workspace DevTeam",
                lu = false,
                createdAt = "2025-06-15T15:30:40.900Z"
            ),
            Notification(
                id = "test_read_${System.currentTimeMillis()}",
                utilisateur = "user4",
                type = "message_prive",
                reference = "conv2",
                onModel = "ConversationPrivee",
                message = "📧 Test: Message privé déjà lu",
                lu = true,
                createdAt = "2025-06-15T15:25:40.900Z"
            )
        )

        notifications.addAll(0, testNotifications)
        adapter.updateNotifications(notifications)
        updateUnreadCountDisplay()
        updateFabVisibility()
        showContentState()

        Toast.makeText(requireContext(), "🧪 ${testNotifications.size} notifications de test ajoutées", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "✅ Notifications de test ajoutées: ${testNotifications.size}")
    }

    private fun clearAllNotifications() {
        notifications.clear()
        adapter.updateNotifications(notifications)
        updateUnreadCountDisplay()
        updateFabVisibility()
        showContentState()

        Toast.makeText(requireContext(), "🗑️ Toutes les notifications supprimées", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "🗑️ Notifications supprimées localement")
    }
}