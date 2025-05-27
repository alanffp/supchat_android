package com.example.supchat.ui.chat

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.supchat.R
import com.example.supchat.SupChatApplication
import com.example.supchat.adapters.PrivateChatAdapter
import com.example.supchat.api.ApiClient
import com.example.supchat.models.response.messageprivate.ConversationMessagesResponse
import com.example.supchat.models.response.messageprivate.ConversationMessage
import com.example.supchat.services.NotificationService
import com.example.supchat.socket.WebSocketService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PrivateConversationFragment : Fragment(), WebSocketService.MessageListener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var connectionIndicator: View
    private lateinit var connectionStatusText: TextView
    private lateinit var adapter: PrivateChatAdapter
    private lateinit var webSocketService: WebSocketService
    private lateinit var notificationService: NotificationService

    private var conversationId: String = ""
    private var otherUserId: String = ""
    private var username: String = ""
    private var myUserId: String = ""
    private var profilePicture: String? = null

    companion object {
        private const val TAG = "PrivateConversation"
        private const val ARG_CONVERSATION_ID = "conversationId"
        private const val ARG_OTHER_USER_ID = "otherUserId"
        private const val ARG_USERNAME = "username"
        private const val ARG_MY_USER_ID = "myUserId"
        private const val ARG_PROFILE_PICTURE = "profilePicture"

        fun newInstance(
            conversationId: String,
            otherUserId: String,
            username: String,
            myUserId: String,
            profilePicture: String? = null
        ): PrivateConversationFragment {
            val fragment = PrivateConversationFragment()
            val args = Bundle().apply {
                putString(ARG_CONVERSATION_ID, conversationId)
                putString(ARG_OTHER_USER_ID, otherUserId)
                putString(ARG_USERNAME, username)
                putString(ARG_MY_USER_ID, myUserId)
                putString(ARG_PROFILE_PICTURE, profilePicture)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            conversationId = it.getString(ARG_CONVERSATION_ID, "")
            otherUserId = it.getString(ARG_OTHER_USER_ID, "")
            username = it.getString(ARG_USERNAME, "")
            myUserId = it.getString(ARG_MY_USER_ID, "")
            profilePicture = it.getString(ARG_PROFILE_PICTURE)
        }

        // ✅ NOUVEAU: Debug des paramètres
        Log.d(TAG, "=== PARAMÈTRES DE LA CONVERSATION ===")
        Log.d(TAG, "conversationId: '$conversationId'")
        Log.d(TAG, "otherUserId: '$otherUserId'")
        Log.d(TAG, "myUserId: '$myUserId'")
        Log.d(TAG, "username: '$username'")
        Log.d(TAG, "=====================================")

        // Si myUserId n'est pas fourni en argument, le récupérer des préférences
        if (myUserId.isEmpty()) {
            myUserId = getCurrentUserId()
            Log.d(TAG, "myUserId récupéré des préférences: '$myUserId'")
        }

        // Vérifier que l'ID de conversation est valide
        if (conversationId.isEmpty()) {
            Log.e(TAG, "ERREUR: conversationId est vide !")
        }

        // Initialiser WebSocket et Notifications
        val app = requireActivity().application as SupChatApplication
        webSocketService = app.getWebSocketService() ?: WebSocketService.getInstance()
        webSocketService.addMessageListener(this)

        notificationService = NotificationService.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_private_chat, container, false)

        initViews(view)
        setupRecyclerView()
        setupSendButton()
        setupWebSocketObservers()
        loadMessages()

        return view
    }

    private fun initViews(view: View) {
        recyclerView = view.findViewById(R.id.messages_recycler_view)
        messageInput = view.findViewById(R.id.message_input)
        sendButton = view.findViewById(R.id.send_button)
        progressBar = view.findViewById(R.id.messages_progress_bar)

        connectionIndicator = view.findViewById(R.id.connection_indicator)
        connectionStatusText = view.findViewById(R.id.connection_status_text)

        val usernameTextView: TextView = view.findViewById(R.id.username_text)
        usernameTextView.text = username

        val backButton: ImageButton = view.findViewById(R.id.back_button)
        backButton.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        // Charger l'image de profil si disponible
        val profileImageView: de.hdodenhof.circleimageview.CircleImageView = view.findViewById(R.id.user_profile_image)
        if (!profilePicture.isNullOrEmpty()) {
            // Utiliser Glide pour charger l'image (si vous l'avez)
            // Glide.with(this).load("http://10.0.2.2:3000/uploads/profile-pictures/$profilePicture").into(profileImageView)
        }
    }

    private fun setupRecyclerView() {
        adapter = PrivateChatAdapter(
            context = requireContext(),
            currentUserId = myUserId,
            onMessageLongClick = { message, position ->
                showMessageOptions(message, position)
            },
            onMessageRead = { messageId ->
                webSocketService.markMessageAsRead(messageId)
            },
            onMessageClick = { message, view -> // ✅ NOUVEAU
                showQuickEditPopup(message, view)
            }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = this@PrivateConversationFragment.adapter

            // ✅ NOUVEAU: Masquer popup lors du scroll
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    dismissCurrentPopup()
                }
            })
        }
    }
    private var currentPopupWindow: android.widget.PopupWindow? = null

    private fun showQuickEditPopup(message: ConversationMessage, anchorView: View) {
        // Fermer le popup précédent s'il existe
        dismissCurrentPopup()

        try {
            val inflater = LayoutInflater.from(requireContext())
            val popupView = inflater.inflate(R.layout.popup_message_edit_conversation, null)

            val editButton = popupView.findViewById<android.widget.Button>(R.id.edit_button)
            val deleteButton = popupView.findViewById<android.widget.Button>(R.id.delete_button)

            // Créer le PopupWindow
            currentPopupWindow = android.widget.PopupWindow(
                popupView,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                true
            ).apply {
                // Style du popup
                setBackgroundDrawable(
                    android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
                )
                elevation = 12f
                isOutsideTouchable = true
                isFocusable = true
            }

            // Actions des boutons
            editButton.setOnClickListener {
                editMessage(message)
                dismissCurrentPopup()
            }

            deleteButton.setOnClickListener {
                deleteMessage(message)
                dismissCurrentPopup()
            }

            // Calculer la position du popup
            val location = IntArray(2)
            anchorView.getLocationOnScreen(location)

            // Afficher le popup à côté du message (à gauche du message)
            currentPopupWindow?.showAsDropDown(
                anchorView,
                -popupView.measuredWidth, // Décalage à gauche
                -anchorView.height / 2    // Centré verticalement
            )

            // Auto-fermeture après 5 secondes
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                dismissCurrentPopup()
            }, 5000)

            Log.d(TAG, "Popup d'édition affiché pour message: ${message.messageId}")

        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'affichage du popup", e)
            Toast.makeText(context, "Erreur lors de l'affichage du menu", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dismissCurrentPopup() {
        currentPopupWindow?.let { popup ->
            if (popup.isShowing) {
                popup.dismiss()
            }
        }
        currentPopupWindow = null
    }

    private fun setupSendButton() {
        sendButton.setOnClickListener {
            val messageText = messageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                sendMessageViaWebSocket(messageText)
            }
        }

        // Gérer l'envoi avec la touche Entrée
        messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                val messageText = messageInput.text.toString().trim()
                if (messageText.isNotEmpty()) {
                    sendMessageViaWebSocket(messageText)
                }
                true
            } else {
                false
            }
        }

        // Activer/désactiver le bouton selon le contenu
        messageInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val hasText = !s.isNullOrBlank()
                sendButton.isEnabled = hasText && webSocketService.isConnected()
                sendButton.alpha = if (sendButton.isEnabled) 1.0f else 0.5f
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupWebSocketObservers() {
        // Observer le statut de connexion
        webSocketService.connectionStatus.observe(viewLifecycleOwner, Observer { isConnected ->
            Log.d(TAG, "Statut connexion WebSocket: $isConnected")
            updateConnectionIndicator(isConnected)

            // Activer/désactiver le bouton d'envoi
            val hasText = messageInput.text.toString().trim().isNotEmpty()
            sendButton.isEnabled = isConnected && hasText
            sendButton.alpha = if (sendButton.isEnabled) 1.0f else 0.5f

            // Mettre à jour le placeholder du champ de texte
            messageInput.hint = if (isConnected) {
                "Écrivez un message..."
            } else {
                "Reconnexion en cours..."
            }
        })

        // Observer les nouveaux messages
        webSocketService.newPrivateMessage.observe(viewLifecycleOwner, Observer { message ->
            Log.d(TAG, "Nouveau message reçu via WebSocket: ${message.contenu} de ${message.expediteur}")

            // Vérifier si le message concerne cette conversation
            val isFromOtherUser = message.expediteur == otherUserId
            val isFromMe = message.expediteur == myUserId
            val isForThisConversation = message.conversation == conversationId ||
                    message.expediteur == otherUserId ||
                    (isFromMe && message.conversation == otherUserId)

            if (isForThisConversation) {
                adapter.addMessage(message)
                scrollToBottom()

                // Marquer automatiquement comme lu si c'est un message reçu
                if (isFromOtherUser) {
                    markMessageAsReadViaAPI(message.expediteur)
                }

                Log.d(TAG, "Message ajouté à la conversation")
            } else {
                Log.d(TAG, "Message ignoré - ne concerne pas cette conversation")
            }
        })

        // Observer les messages lus
        webSocketService.messageRead.observe(viewLifecycleOwner, Observer { messageId ->
            Log.d(TAG, "Message marqué comme lu: $messageId")
            // Mettre à jour l'affichage si nécessaire
            adapter.notifyDataSetChanged()
        })

        // Observer les messages modifiés
        webSocketService.messageModified.observe(viewLifecycleOwner, Observer { message ->
            Log.d(TAG, "Message modifié: ${message.contenu}")
            // Recharger les messages pour afficher la modification
            loadMessages()
        })

        // Observer les messages supprimés
        webSocketService.messageDeleted.observe(viewLifecycleOwner, Observer { messageId ->
            Log.d(TAG, "Message supprimé: $messageId")
            // Recharger les messages
            loadMessages()
        })

        // Observer les confirmations d'envoi
        webSocketService.messageSent.observe(viewLifecycleOwner, Observer { messageId ->
            Log.d(TAG, "Message envoyé avec succès: $messageId")
            // Optionnel : afficher une confirmation
        })

        // Observer les erreurs
        webSocketService.error.observe(viewLifecycleOwner, Observer { error ->
            Log.e(TAG, "Erreur WebSocket: $error")
            showError(error)
        })
    }

    private fun updateConnectionIndicator(isConnected: Boolean) {
        if (isConnected) {
            connectionIndicator.backgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#4CAF50") // Vert
                )
            connectionStatusText.text = "En ligne"
            connectionStatusText.setTextColor(android.graphics.Color.parseColor("#CCFFFFFF"))
        } else {
            connectionIndicator.backgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#F44336") // Rouge
                )
            connectionStatusText.text = "Hors ligne"
            connectionStatusText.setTextColor(android.graphics.Color.parseColor("#88FFFFFF"))
        }
    }

    private fun loadMessages() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE

        val token = getAuthToken()
        if (token.isEmpty()) {
            showError("Session expirée")
            return
        }

        if (conversationId.isEmpty()) {
            Log.e(TAG, "conversationId est vide !")
            showError("Erreur: ID de conversation manquant")
            return
        }

        Log.d(TAG, "Chargement des messages pour conversation: $conversationId")

        ApiClient.getConversationMessages(token, conversationId)
            .enqueue(object : Callback<ConversationMessagesResponse> {
                override fun onResponse(
                    call: Call<ConversationMessagesResponse>,
                    response: Response<ConversationMessagesResponse>
                ) {
                    if (!isAdded) return
                    progressBar.visibility = View.GONE

                    if (response.isSuccessful) {
                        val messagesResponse = response.body()
                        Log.d(TAG, "Réponse reçue: $messagesResponse")

                        val messages = messagesResponse?.data ?: emptyList()
                        Log.d(TAG, "Nombre de messages: ${messages.size}")

                        if (messages.isEmpty()) {
                            recyclerView.visibility = View.VISIBLE
                            showEmptyState()
                        } else {
                            recyclerView.visibility = View.VISIBLE
                            adapter.updateMessages(messages)
                            scrollToBottom()
                        }
                    } else {
                        Log.e(TAG, "Erreur API: ${response.code()}")
                        showError("Erreur lors du chargement: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<ConversationMessagesResponse>, t: Throwable) {
                    if (!isAdded) return
                    Log.e(TAG, "Erreur réseau", t)
                    progressBar.visibility = View.GONE
                    showError("Erreur réseau: ${t.message}")
                }
            })
    }

    // ✅ NOUVEAU: Méthode principale d'envoi (simplifié)
    private fun sendMessageViaWebSocket(content: String) {
        // ✅ TOUJOURS utiliser l'API REST pour l'envoi
        Log.d(TAG, "=== ENVOI DE MESSAGE ===")
        Log.d(TAG, "Contenu: '$content'")
        Log.d(TAG, "Vers conversationId: '$conversationId'")
        Log.d(TAG, "========================")

        sendMessageViaAPI(content)
    }

    // ✅ MÉTHODE D'ENVOI VIA API REST (debug amélioré)
    private fun sendMessageViaAPI(content: String) {
        val token = getAuthToken()
        if (token.isEmpty()) {
            Log.e(TAG, "ERREUR: Token vide")
            showError("Session expirée")
            return
        }

        if (conversationId.isEmpty()) {
            Log.e(TAG, "ERREUR: conversationId vide - impossible d'envoyer")
            showError("Erreur: ID de conversation manquant")
            return
        }

        sendButton.isEnabled = false
        messageInput.isEnabled = false

        Log.d(TAG, "=== ENVOI API REST ===")
        Log.d(TAG, "URL: POST /api/v1/conversations/$conversationId/messages")
        Log.d(TAG, "Token: ${token.take(20)}...")
        Log.d(TAG, "Contenu: '$content'")
        Log.d(TAG, "=====================")

        ApiClient.sendConversationMessage(token, conversationId, content)
            .enqueue(object : Callback<ConversationMessagesResponse> {
                override fun onResponse(
                    call: Call<ConversationMessagesResponse>,
                    response: Response<ConversationMessagesResponse>
                ) {
                    if (!isAdded) return

                    Log.d(TAG, "=== RÉPONSE API ===")
                    Log.d(TAG, "Code: ${response.code()}")
                    Log.d(TAG, "Success: ${response.isSuccessful}")

                    if (!response.isSuccessful) {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "Erreur body: $errorBody")
                    }
                    Log.d(TAG, "==================")

                    sendButton.isEnabled = true
                    messageInput.isEnabled = true

                    if (response.isSuccessful) {
                        messageInput.text.clear()

                        val messagesResponse = response.body()
                        Log.d(TAG, "Message envoyé avec succès: ${messagesResponse?.success}")

                        // Recharger les messages pour voir le nouveau
                        loadMessages()

                        Log.d(TAG, "Messages rechargés après envoi")
                    } else {
                        Log.e(TAG, "Erreur envoi message API: ${response.code()}")
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "Détails erreur: $errorBody")
                        Toast.makeText(context, "Erreur lors de l'envoi: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<ConversationMessagesResponse>, t: Throwable) {
                    if (!isAdded) return

                    sendButton.isEnabled = true
                    messageInput.isEnabled = true

                    Log.e(TAG, "Erreur réseau envoi API", t)
                    Toast.makeText(context, "Erreur réseau: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun scrollToBottom() {
        if (adapter.itemCount > 0) {
            recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
        }
    }

    private fun showMessageOptions(message: ConversationMessage, position: Int) {
        // ✅ VÉRIFICATION: seulement pour ses propres messages
        if (message.expediteur != myUserId) {
            Log.d(TAG, "Options non disponibles - message d'un autre utilisateur")
            return
        }

        Log.d(TAG, "Options pour message: id=${message.messageId}, contenu='${message.contenu}'")

        val options = arrayOf("Modifier", "Supprimer", "Répondre")

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Options du message")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> editMessage(message)
                    1 -> deleteMessage(message)
                    2 -> replyToMessage(message)
                }
            }
            .show()
    }

    private fun editMessage(message: ConversationMessage) {
        val input = EditText(requireContext())
        input.setText(message.contenu)

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Modifier le message")
            .setView(input)
            .setPositiveButton("Modifier") { _, _ ->
                val newContent = input.text.toString().trim()
                if (newContent.isNotEmpty() && newContent != message.contenu) {
                    editMessageViaAPI(message, newContent)
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun deleteMessage(message: ConversationMessage) {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Supprimer le message")
            .setMessage("Êtes-vous sûr de vouloir supprimer ce message ?")
            .setPositiveButton("Supprimer") { _, _ ->
                deleteMessageViaAPI(message)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    // ✅ NOUVEAU: Modifier message via API
    private fun editMessageViaAPI(message: ConversationMessage, newContent: String) {
        val token = getAuthToken()
        if (token.isEmpty()) {
            showError("Session expirée")
            return
        }

        if (conversationId.isEmpty()) {
            showError("ID de conversation manquant")
            return
        }

        // ✅ UTILISER le vrai ID du message
        val messageId = message.messageId

        if (messageId.isEmpty()) {
            showError("ID de message manquant")
            return
        }

        Log.d(TAG, "=== MODIFICATION MESSAGE ===")
        Log.d(TAG, "conversationId: '$conversationId'")
        Log.d(TAG, "messageId: '$messageId'")
        Log.d(TAG, "nouveau contenu: '$newContent'")
        Log.d(TAG, "===============================")

        // Désactiver temporairement l'interface
        val progressDialog = android.app.ProgressDialog(requireContext()).apply {
            setMessage("Modification en cours...")
            setCancelable(false)
            show()
        }

        ApiClient.updateConversationMessage(token, conversationId, messageId, newContent)
            .enqueue(object : Callback<ConversationMessagesResponse> {
                override fun onResponse(
                    call: Call<ConversationMessagesResponse>,
                    response: Response<ConversationMessagesResponse>
                ) {
                    progressDialog.dismiss()

                    if (!isAdded) return

                    Log.d(TAG, "=== RÉPONSE MODIFICATION ===")
                    Log.d(TAG, "Code: ${response.code()}")
                    Log.d(TAG, "Success: ${response.isSuccessful}")

                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        Log.d(TAG, "Réponse: $responseBody")

                        Toast.makeText(context, "Message modifié avec succès", Toast.LENGTH_SHORT).show()

                        // Recharger les messages pour voir les changements
                        loadMessages()
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "Erreur modification: Code=${response.code()}, Body=$errorBody")

                        val errorMessage = when (response.code()) {
                            404 -> "Message non trouvé"
                            403 -> "Vous ne pouvez pas modifier ce message"
                            400 -> "Contenu invalide"
                            else -> "Erreur lors de la modification (${response.code()})"
                        }
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<ConversationMessagesResponse>, t: Throwable) {
                    progressDialog.dismiss()

                    if (!isAdded) return

                    Log.e(TAG, "Erreur réseau modification", t)
                    Toast.makeText(context, "Erreur réseau: ${t.message}", Toast.LENGTH_LONG).show()
                }
            })
    }

    // ✅ NOUVEAU: Supprimer message via API
    private fun deleteMessageViaAPI(message: ConversationMessage) {
        val token = getAuthToken()
        if (token.isEmpty()) {
            showError("Session expirée")
            return
        }

        if (conversationId.isEmpty()) {
            showError("ID de conversation manquant")
            return
        }

        // ✅ UTILISER le vrai ID du message
        val messageId = message.messageId

        if (messageId.isEmpty()) {
            showError("ID de message manquant")
            return
        }

        Log.d(TAG, "=== SUPPRESSION MESSAGE ===")
        Log.d(TAG, "conversationId: '$conversationId'")
        Log.d(TAG, "messageId: '$messageId'")
        Log.d(TAG, "==============================")

        val progressDialog = android.app.ProgressDialog(requireContext()).apply {
            setMessage("Suppression en cours...")
            setCancelable(false)
            show()
        }

        ApiClient.deleteConversationMessage(token, conversationId, messageId)
            .enqueue(object : Callback<ConversationMessagesResponse> {
                override fun onResponse(
                    call: Call<ConversationMessagesResponse>,
                    response: Response<ConversationMessagesResponse>
                ) {
                    progressDialog.dismiss()

                    if (!isAdded) return

                    Log.d(TAG, "=== RÉPONSE SUPPRESSION ===")
                    Log.d(TAG, "Code: ${response.code()}")
                    Log.d(TAG, "Success: ${response.isSuccessful}")

                    if (response.isSuccessful) {
                        Toast.makeText(context, "Message supprimé", Toast.LENGTH_SHORT).show()
                        loadMessages() // Recharger pour voir les changements
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "Erreur suppression: Code=${response.code()}, Body=$errorBody")

                        val errorMessage = when (response.code()) {
                            404 -> "Message non trouvé"
                            403 -> "Vous ne pouvez pas supprimer ce message"
                            else -> "Erreur lors de la suppression (${response.code()})"
                        }
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<ConversationMessagesResponse>, t: Throwable) {
                    progressDialog.dismiss()

                    if (!isAdded) return

                    Log.e(TAG, "Erreur réseau suppression", t)
                    Toast.makeText(context, "Erreur réseau: ${t.message}", Toast.LENGTH_LONG).show()
                }
            })
    }

    private fun replyToMessage(message: ConversationMessage) {
        // Implémenter la logique de réponse
        messageInput.setText("@${username} ")
        messageInput.setSelection(messageInput.text.length)
        messageInput.requestFocus()
    }

    private fun showEmptyState() {
        Toast.makeText(context, "Aucun message pour l'instant", Toast.LENGTH_SHORT).show()
    }

    private fun showError(message: String) {
        progressBar.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private fun getAuthToken(): String {
        return requireActivity().getSharedPreferences("SupChatPrefs", Context.MODE_PRIVATE)
            .getString("auth_token", "") ?: ""
    }

    private fun getCurrentUserId(): String {
        return requireActivity().getSharedPreferences("SupChatPrefs", Context.MODE_PRIVATE)
            .getString("user_id", "") ?: ""
    }

    private fun getCurrentUsername(): String {
        return requireActivity().getSharedPreferences("SupChatPrefs", Context.MODE_PRIVATE)
            .getString("username", "") ?: ""
    }

    // Implémentation des callbacks WebSocket (compatibilité)
    override fun onNewPrivateMessage(message: org.json.JSONObject) {
        // Déjà géré par les LiveData observers
    }

    override fun onPrivateMessageSent(messageId: String) {
        // Déjà géré par les LiveData observers
    }

    override fun onPrivateMessageRead(messageId: String) {
        // Déjà géré par les LiveData observers
    }

    override fun onPrivateMessageModified(message: org.json.JSONObject) {
        // Déjà géré par les LiveData observers
    }

    override fun onPrivateMessageDeleted(messageId: String) {
        // Déjà géré par les LiveData observers
    }

    override fun onError(error: String) {
        // Déjà géré par les LiveData observers
    }

    override fun onConnectionChanged(isConnected: Boolean) {
        // Déjà géré par les LiveData observers
    }

    // ✅ NOUVEAU: Marquer message comme lu via API
    private fun markMessageAsReadViaAPI(messageId: String) {
        val token = getAuthToken()
        if (token.isEmpty()) return

        if (conversationId.isEmpty()) {
            Log.e(TAG, "conversationId vide pour marquer comme lu")
            return
        }

        Log.d(TAG, "Marquage lecture: conversationId='$conversationId', messageId='$messageId'")

        // Utiliser WebSocket en premier si connecté
        if (webSocketService.isConnected()) {
            webSocketService.markMessageAsRead(messageId)
            return
        }

        // Fallback sur API REST
        ApiClient.markConversationMessageAsRead(token, conversationId, messageId)
            .enqueue(object : Callback<ConversationMessagesResponse> {
                override fun onResponse(
                    call: Call<ConversationMessagesResponse>,
                    response: Response<ConversationMessagesResponse>
                ) {
                    if (response.isSuccessful) {
                        Log.d(TAG, "Message $messageId marqué comme lu via API")
                    } else {
                        Log.e(TAG, "Erreur marquage lecture: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<ConversationMessagesResponse>, t: Throwable) {
                    Log.e(TAG, "Erreur réseau marquage lecture", t)
                }
            })
    }

    private fun markUnreadMessagesAsRead() {
        // ✅ NOUVEAU: Marquer les notifications comme lues
        notificationService.markPrivateConversationAsRead(requireContext(), conversationId)

        Log.d(TAG, "Notifications marquées comme lues pour conversation: $conversationId")
    }

    override fun onResume() {
        super.onResume()
        // Assurer que WebSocket est connecté
        val token = getAuthToken()
        if (token.isNotEmpty() && !webSocketService.isConnected()) {
            webSocketService.initialize(token)
        }

        // ✅ NOUVEAU: Marquer les messages comme lus quand on entre dans la conversation
        markUnreadMessagesAsRead()
    }

    override fun onPause() {
        super.onPause()
        dismissCurrentPopup() // Fermer le popup si l'utilisateur quitte
        markUnreadMessagesAsRead()
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissCurrentPopup()
        webSocketService.removeMessageListener(this)
    }
}