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
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PrivateMessagesListFragment : Fragment(), WebSocketService.MessageListener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyTextView: TextView
    private lateinit var messagesAdapter: PrivateMessagesAdapter

    // ✅ NOUVEAU: WebSocket
    private lateinit var webSocketService: WebSocketService
    private var conversations = mutableListOf<PrivateMessageItem>()

    companion object {
        private const val TAG = "PrivateMessagesList"

        fun newInstance(): PrivateMessagesListFragment {
            return PrivateMessagesListFragment()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ NOUVEAU: Initialiser WebSocket listener
        val app = requireActivity().application as SupChatApplication
        webSocketService = app.getWebSocketService() ?: WebSocketService.getInstance()
        webSocketService.addMessageListener(this)

        Log.d(TAG, "WebSocket listener ajouté")
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

    // ✅ NOUVEAU: Recharger discrètement sans spinner
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

        Log.d(TAG, "Chargement des messages privés...")

        ApiClient.getPrivateMessages(token).enqueue(object : Callback<PrivateMessagesResponse> {
            override fun onResponse(
                call: Call<PrivateMessagesResponse>,
                response: Response<PrivateMessagesResponse>
            ) {
                if (!isAdded) return

                progressBar.visibility = View.GONE

                if (response.isSuccessful) {
                    val privateMessagesResponse = response.body()
                    Log.d(TAG, "Réponse reçue: $privateMessagesResponse")

                    val newConversations = privateMessagesResponse?.data ?: emptyList()
                    Log.d(TAG, "Nombre de conversations: ${newConversations.size}")

                    // ✅ NOUVEAU: Sauvegarder dans la liste locale
                    conversations.clear()
                    conversations.addAll(newConversations)

                    updateUI(conversations)
                } else {
                    Log.e(TAG, "Erreur API: ${response.code()}")
                    showError("Erreur: ${response.code()} - ${response.message()}")
                }
            }

            override fun onFailure(call: Call<PrivateMessagesResponse>, t: Throwable) {
                if (!isAdded) return
                progressBar.visibility = View.GONE
                Log.e(TAG, "Erreur de connexion API", t)
                showError("Erreur de connexion: ${t.message}")
            }
        })
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

        Log.d(TAG, "Navigation vers chat avec: ${conversation.user.username}")

        val chatFragment = PrivateConversationFragment.newInstance(
            conversationId = conversation.conversationId,
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
        // ✅ NOUVEAU: Recharger discrètement à chaque retour
        Log.d(TAG, "Fragment resumé, rafraîchissement des conversations")
        refreshConversationsQuietly()
    }

    override fun onDestroy() {
        super.onDestroy()
        // ✅ NOUVEAU: Supprimer le listener WebSocket
        webSocketService.removeMessageListener(this)
        Log.d(TAG, "WebSocket listener supprimé")
    }
}