package com.example.supchat.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.supchat.R
import com.example.supchat.SupChatApplication
import com.example.supchat.adapters.PrivateMessagesAdapter
import com.example.supchat.api.ApiClient
import com.example.supchat.models.response.messageprivate.PrivateMessageItem
import com.example.supchat.models.response.messageprivate.PrivateMessagesResponse
import com.example.supchat.socket.WebSocketService
import com.example.supchat.ui.chat.PrivateConversationFragment
import com.example.supchat.services.NotificationService
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PrivateMessagesListFragment : Fragment(), WebSocketService.MessageListener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyTextView: TextView
    private lateinit var messagesAdapter: PrivateMessagesAdapter

    // ✅ NOUVEAU: WebSocket et Notifications
    private lateinit var webSocketService: WebSocketService
    private lateinit var notificationService: NotificationService
    private var conversations = mutableListOf<PrivateMessageItem>()

    companion object {
        private const val TAG = "PrivateMessagesList"

        fun newInstance(): PrivateMessagesListFragment {
            return PrivateMessagesListFragment()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Initialiser WebSocket et Notifications
        val app = requireActivity().application as SupChatApplication
        webSocketService = app.getWebSocketService() ?: WebSocketService.getInstance()
        webSocketService.addMessageListener(this)

        notificationService = NotificationService.getInstance()

        Log.d(TAG, "Services initialisés")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_private_messages_list, container, false)

        recyclerView = view.findViewById(R.id.conversations_recycler_view)
        progressBar = view.findViewById(R.id.conversations_progress_bar)
        emptyTextView = view.findViewById(R.id.empty_conversations_text)

        setupRecyclerView()
        setupWebSocketObservers() // ✅ NOUVEAU
        loadPrivateMessages()

        return view
    }

    // ✅ NOUVEAU: Observer les événements WebSocket en temps réel
    private fun setupWebSocketObservers() {
        // Observer les nouveaux messages privés
        webSocketService.newPrivateMessage.observe(viewLifecycleOwner, Observer { message ->
            Log.d(TAG, "Nouveau message WebSocket reçu: de ${message.expediteur}")

            // Mettre à jour la liste des conversations
            updateConversationWithNewMessage(message.expediteur, message.contenu, message.horodatage)
        })

        // Observer les messages lus
        webSocketService.messageRead.observe(viewLifecycleOwner, Observer { messageId ->
            Log.d(TAG, "Message marqué comme lu: $messageId")

            // Recharger les conversations pour mettre à jour les compteurs
            refreshConversationsQuietly()
        })

        // Observer les messages envoyés (pour mise à jour immédiate)
        webSocketService.messageSent.observe(viewLifecycleOwner, Observer { messageId ->
            Log.d(TAG, "Message envoyé confirmé: $messageId")

            // Optionnel: recharger pour s'assurer de la cohérence
            refreshConversationsQuietly()
        })
    }

    // ✅ NOUVEAU: Mettre à jour une conversation avec un nouveau message
    private fun updateConversationWithNewMessage(senderId: String, content: String, timestamp: String) {
        val currentUserId = getCurrentUserId()

        // Trouver la conversation existante
        val existingConversationIndex = conversations.indexOfFirst {
            it.user.id == senderId
        }

        if (existingConversationIndex != -1) {
            // Mettre à jour la conversation existante
            val conversation = conversations[existingConversationIndex]

            // Mettre à jour le dernier message
            val updatedLastMessage = conversation.lastMessage.copy(
                contenu = content,
                horodatage = timestamp,
                isFromMe = senderId == currentUserId
            )

            // Incrémenter le compteur si ce n'est pas de nous
            val updatedUnreadCount = if (senderId != currentUserId) {
                conversation.unreadCount + 1
            } else {
                conversation.unreadCount
            }

            val updatedConversation = conversation.copy(
                lastMessage = updatedLastMessage,
                unreadCount = updatedUnreadCount
            )

            // Remplacer et déplacer en haut
            conversations.removeAt(existingConversationIndex)
            conversations.add(0, updatedConversation)

            // Notifier l'adapter
            messagesAdapter.updateConversations(conversations)

            Log.d(TAG, "Conversation mise à jour: ${conversation.user.username}, nouveaux non lus: $updatedUnreadCount")

        } else {
            // Nouvelle conversation - recharger complètement
            Log.d(TAG, "Nouvelle conversation détectée, rechargement complet")
            loadPrivateMessages()
        }
    }

    // ✅ NOUVEAU: Observer les notifications
    private fun setupNotificationObservers() {
        // Observer les compteurs de messages non lus
        notificationService.unreadPrivateMessages.observe(viewLifecycleOwner, Observer { unreadMap ->
            Log.d(TAG, "Mise à jour des compteurs non lus: $unreadMap")
            updateConversationsWithUnreadCounts(unreadMap)
        })

        // Observer les nouvelles notifications via WebSocket
        webSocketService.newNotification.observe(viewLifecycleOwner, Observer { notificationJson ->
            Log.d(TAG, "Nouvelle notification WebSocket reçue")
            // Recharger les notifications pour mettre à jour les compteurs
            notificationService.loadNotifications(requireContext())
        })
    }

    // ✅ NOUVEAU: Mettre à jour les conversations avec les compteurs
    private fun updateConversationsWithUnreadCounts(unreadMap: Map<String, Int>) {
        var hasChanges = false

        conversations.forEachIndexed { index, conversation ->
            val unreadCount = unreadMap[conversation.conversationId] ?: 0
            if (conversation.unreadCount != unreadCount) {
                conversations[index] = conversation.copy(unreadCount = unreadCount)
                hasChanges = true
            }
        }

        if (hasChanges) {
            messagesAdapter.updateConversations(conversations)
            Log.d(TAG, "Conversations mises à jour avec nouveaux compteurs")
        }
    }
    private fun refreshConversationsQuietly() {
        val token = getAuthToken()
        if (token.isEmpty()) return

        ApiClient.getPrivateMessages(token).enqueue(object : Callback<PrivateMessagesResponse> {
            override fun onResponse(
                call: Call<PrivateMessagesResponse>,
                response: Response<PrivateMessagesResponse>
            ) {
                if (!isAdded) return

                if (response.isSuccessful) {
                    val privateMessagesResponse = response.body()
                    val newConversations = privateMessagesResponse?.data ?: emptyList()

                    // Mettre à jour sans changer la visibilité
                    conversations.clear()
                    conversations.addAll(newConversations)
                    messagesAdapter.updateConversations(conversations)

                    Log.d(TAG, "Conversations mises à jour discrètement: ${conversations.size}")
                }
            }

            override fun onFailure(call: Call<PrivateMessagesResponse>, t: Throwable) {
                Log.e(TAG, "Erreur lors du rafraîchissement discret", t)
            }
        })
    }

    private fun setupRecyclerView() {
        val currentUserId = getCurrentUserId()

        messagesAdapter = PrivateMessagesAdapter(requireContext(), currentUserId) { conversation ->
            navigateToPrivateChat(conversation)
        }

        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = messagesAdapter
        }
    }

    private fun loadPrivateMessages() {
        progressBar.visibility = View.VISIBLE
        emptyTextView.visibility = View.GONE
        recyclerView.visibility = View.GONE

        val token = getAuthToken()
        if (token.isEmpty()) {
            showError("Vous n'êtes pas connecté")
            return
        }

        Log.d(TAG, "Chargement des conversations privées et groupes...")

        // ✅ NOUVEAU : Faire 2 appels en parallèle
        loadPrivateMessagesAndGroups(token)
    }

    private fun updateUI(conversations: List<PrivateMessageItem>) {
        if (conversations.isEmpty()) {
            emptyTextView.visibility = View.VISIBLE
            emptyTextView.text = "Aucune conversation privée"
            recyclerView.visibility = View.GONE
            Log.d(TAG, "Aucune conversation à afficher")
        } else {
            emptyTextView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            messagesAdapter.updateConversations(conversations)
            Log.d(TAG, "Affichage de ${conversations.size} conversations")
        }
    }

    private fun showError(message: String) {
        progressBar.visibility = View.GONE
        emptyTextView.text = message
        emptyTextView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        Log.e(TAG, "Erreur: $message")
    }

    private fun navigateToPrivateChat(conversation: PrivateMessageItem) {
        val currentUserId = getCurrentUserId()

        Log.d(TAG, "=== NAVIGATION VERS CHAT ===")
        Log.d(TAG, "conversation.conversationId: '${conversation.conversationId}'")
        Log.d(TAG, "conversation.user.id: '${conversation.user.id}'")
        Log.d(TAG, "conversation.user.username: '${conversation.user.username}'")
        Log.d(TAG, "currentUserId: '$currentUserId'")
        Log.d(TAG, "===============================")

        // Utiliser l'ID de conversation fourni par l'API
        val validConversationId = conversation.conversationId.ifEmpty {
            Log.w(TAG, "ConversationId vide, utilisation de l'userId comme fallback")
            conversation.user.id
        }

        val chatFragment = PrivateConversationFragment.newInstance(
            conversationId = validConversationId,
            otherUserId = conversation.user.id,
            username = conversation.user.username,
            myUserId = currentUserId,
            profilePicture = conversation.user.profilePicture
        )

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, chatFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun getCurrentUserId(): String {
        return requireContext().getSharedPreferences("SupChatPrefs", Context.MODE_PRIVATE)
            .getString("user_id", "") ?: ""
    }

    private fun getAuthToken(): String {
        return requireContext().getSharedPreferences("SupChatPrefs", Context.MODE_PRIVATE)
            .getString("auth_token", "") ?: ""
    }

    // ✅ NOUVEAU: Callbacks WebSocket
    override fun onNewPrivateMessage(message: JSONObject) {
        // Déjà géré par les LiveData observers
        Log.d(TAG, "Callback WebSocket: nouveau message privé")
    }

    override fun onPrivateMessageSent(messageId: String) {
        // Déjà géré par les LiveData observers
        Log.d(TAG, "Callback WebSocket: message envoyé")
    }

    override fun onPrivateMessageRead(messageId: String) {
        // Déjà géré par les LiveData observers
        Log.d(TAG, "Callback WebSocket: message lu")
    }

    override fun onPrivateMessageModified(message: JSONObject) {
        Log.d(TAG, "Callback WebSocket: message modifié")
        refreshConversationsQuietly()
    }

    override fun onPrivateMessageDeleted(messageId: String) {
        Log.d(TAG, "Callback WebSocket: message supprimé")
        refreshConversationsQuietly()
    }

    override fun onError(error: String) {
        Log.e(TAG, "Erreur WebSocket: $error")
    }

    override fun onConnectionChanged(isConnected: Boolean) {
        Log.d(TAG, "Statut connexion WebSocket: $isConnected")
    }

    override fun onResume() {
        super.onResume()
        // ✅ Recharger conversations et notifications
        Log.d(TAG, "Fragment resumé, rafraîchissement des données")
        refreshConversationsQuietly()
        notificationService.loadNotifications(requireContext())
    }

    override fun onDestroy() {
        super.onDestroy()
        // ✅ NOUVEAU: Supprimer le listener WebSocket
        webSocketService.removeMessageListener(this)
        Log.d(TAG, "WebSocket listener supprimé")
    }

    private fun loadPrivateMessagesAndGroups(token: String) {
        var privateMessagesLoaded = false
        var groupsLoaded = false
        var privateConversations = listOf<PrivateMessageItem>()
        var groupConversations = listOf<PrivateMessageItem>()

        // Fonction pour combiner les résultats quand les 2 appels sont terminés
        fun combineResults() {
            if (privateMessagesLoaded && groupsLoaded) {
                val allConversations = mutableListOf<PrivateMessageItem>()
                allConversations.addAll(privateConversations)
                allConversations.addAll(groupConversations)

                // ✅ TRIER par date du dernier message
                allConversations.sortByDescending { conversation ->
                    conversation.lastMessage.horodatage
                }

                conversations.clear()
                conversations.addAll(allConversations)

                progressBar.visibility = View.GONE
                updateUI(allConversations)

                Log.d(TAG, "Total conversations: ${allConversations.size} (${privateConversations.size} privées + ${groupConversations.size} groupes)")
            }
        }

        // ✅ APPEL 1 : Messages privés existants
        ApiClient.getPrivateMessages(token).enqueue(object : Callback<PrivateMessagesResponse> {
            override fun onResponse(
                call: Call<PrivateMessagesResponse>,
                response: Response<PrivateMessagesResponse>
            ) {
                if (!isAdded) return

                if (response.isSuccessful) {
                    privateConversations = response.body()?.data ?: emptyList()
                    Log.d(TAG, "Conversations privées chargées: ${privateConversations.size}")
                } else {
                    Log.e(TAG, "Erreur chargement conversations privées: ${response.code()}")
                }

                privateMessagesLoaded = true
                combineResults()
            }

            override fun onFailure(call: Call<PrivateMessagesResponse>, t: Throwable) {
                if (!isAdded) return
                Log.e(TAG, "Erreur réseau conversations privées", t)
                privateMessagesLoaded = true
                combineResults()
            }
        })

        // ✅ APPEL 2 : Groupes créés
        ApiClient.getAllConversations(token).enqueue(object : Callback<PrivateMessagesResponse> {
            override fun onResponse(
                call: Call<PrivateMessagesResponse>,
                response: Response<PrivateMessagesResponse>
            ) {
                if (!isAdded) return

                if (response.isSuccessful) {
                    val allConversations = response.body()?.data ?: emptyList()
                    // ✅ FILTRER seulement les groupes
                    groupConversations = allConversations.filter { conversation ->
                        // Supposons qu'il y ait un champ pour identifier les groupes
                        // ou plus de 2 participants = groupe
                        isGroupConversation(conversation)
                    }
                    Log.d(TAG, "Groupes chargés: ${groupConversations.size}")
                } else {
                    Log.e(TAG, "Erreur chargement groupes: ${response.code()}")
                }

                groupsLoaded = true
                combineResults()
            }

            override fun onFailure(call: Call<PrivateMessagesResponse>, t: Throwable) {
                if (!isAdded) return
                Log.e(TAG, "Erreur réseau groupes", t)
                groupsLoaded = true
                combineResults()
            }
        })
    }

    private fun isGroupConversation(conversation: PrivateMessageItem): Boolean {
        // ✅ UTILISER LE CHAMP isGroup EXISTANT
        return conversation.isGroup
    }

    fun refreshConversations() {
        Log.d(TAG, "Rafraîchissement des conversations demandé")
        loadPrivateMessages()
    }
}