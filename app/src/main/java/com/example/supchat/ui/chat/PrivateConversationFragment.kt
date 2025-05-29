package com.example.supchat.ui.chat

import android.Manifest
import android.app.ProgressDialog
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class PrivateConversationFragment : Fragment(), WebSocketService.MessageListener {

    // Vues existantes
    private lateinit var recyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var connectionIndicator: View
    private lateinit var connectionStatusText: TextView
    private lateinit var adapter: PrivateChatAdapter
    private lateinit var webSocketService: WebSocketService
    private lateinit var notificationService: NotificationService

    // ✅ NOUVELLES vues pour fichiers
    private lateinit var plusButton: ImageButton
    private lateinit var optionsContainer: LinearLayout
    private lateinit var messageInputContainer: LinearLayout
    private lateinit var fileButton: Button
    private lateinit var pollButton: Button
    private lateinit var cameraButton: ImageButton
    private lateinit var galleryButton: ImageButton
    private lateinit var videoButton: ImageButton

    private var conversationId: String = ""
    private var otherUserId: String = ""
    private var username: String = ""
    private var myUserId: String = ""
    private var profilePicture: String? = null

    // ✅ NOUVELLES variables pour gestion fichiers
    private var isOptionsVisible = false
    private var currentPhotoPath: String? = null

    // ✅ NOUVEAUX lanceurs pour résultats d'activité
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleSelectedFile(it, "Image de la galerie") }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            currentPhotoPath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    uploadFile(file, "Photo prise")
                }
            }
        }
    }

    private val fileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleSelectedFile(it, "Fichier sélectionné") }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(context, "Permission caméra refusée", Toast.LENGTH_SHORT).show()
        }
    }

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

        // Debug des paramètres
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
        setupFileButtons() // ✅ NOUVEAU
        setupWebSocketObservers()
        loadMessages()

        return view
    }

    private fun initViews(view: View) {
        // Vues existantes
        recyclerView = view.findViewById(R.id.messages_recycler_view)
        messageInput = view.findViewById(R.id.message_input)
        sendButton = view.findViewById(R.id.send_button)
        progressBar = view.findViewById(R.id.messages_progress_bar)
        connectionIndicator = view.findViewById(R.id.connection_indicator)
        connectionStatusText = view.findViewById(R.id.connection_status_text)

        // ✅ NOUVELLES vues pour fichiers
        plusButton = view.findViewById(R.id.plus_button)
        optionsContainer = view.findViewById(R.id.options_container)
        messageInputContainer = view.findViewById(R.id.message_input_container)
        fileButton = view.findViewById(R.id.file_button)
        pollButton = view.findViewById(R.id.poll_button)
        cameraButton = view.findViewById(R.id.camera_button)
        galleryButton = view.findViewById(R.id.gallery_button)
        videoButton = view.findViewById(R.id.video_button)

        val usernameTextView: TextView = view.findViewById(R.id.username_text)
        usernameTextView.text = username

        val backButton: ImageButton = view.findViewById(R.id.back_button)
        backButton.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        // Charger l'image de profil si disponible
        val profileImageView: de.hdodenhof.circleimageview.CircleImageView = view.findViewById(R.id.user_profile_image)
        if (!profilePicture.isNullOrEmpty()) {
            // Utiliser Glide pour charger l'image si nécessaire
            // Glide.with(this).load("http://10.0.2.2:3000/uploads/profile-pictures/$profilePicture").into(profileImageView)
        }
    }

    private fun setupFileButtons() {
        // Bouton + / X
        plusButton.setOnClickListener {
            Log.d(TAG, "Clic sur bouton plus/croix")
            toggleOptionsPanel()
        }

        // Bouton fichier
        fileButton.setOnClickListener {
            Log.d(TAG, "Clic sur bouton fichier")
            openFilePicker()
        }

        // Bouton sondage
        pollButton.setOnClickListener {
            Log.d(TAG, "Clic sur bouton sondage")
            Toast.makeText(context, "Sondage - À implémenter", Toast.LENGTH_SHORT).show()
            hideOptionsPanel()
        }

        // Bouton caméra
        cameraButton.setOnClickListener {
            Log.d(TAG, "Clic sur bouton caméra")
            checkCameraPermissionAndOpen()
        }

        // Bouton galerie
        galleryButton.setOnClickListener {
            Log.d(TAG, "Clic sur bouton galerie")
            openGallery()
        }

        // Bouton vidéo
        videoButton.setOnClickListener {
            Log.d(TAG, "Clic sur bouton vidéo")
            openVideoPicker()
        }
    }

    private fun toggleOptionsPanel() {
        Log.d(TAG, "Toggle options panel - État actuel: $isOptionsVisible")

        if (isOptionsVisible) {
            hideOptionsPanel()
        } else {
            showOptionsPanel()
        }
    }

    private fun showOptionsPanel() {
        isOptionsVisible = true

        // Changer l'icône + en X
        plusButton.setImageResource(R.drawable.ic_close)

        // ✅ CORRECTION: Animation plus stable
        optionsContainer.visibility = View.VISIBLE
        optionsContainer.alpha = 0f

        // Animation douce
        optionsContainer.animate()
            .alpha(1f)
            .setDuration(250)
            .setListener(null)
            .start()

        Log.d(TAG, "Panneau d'options ouvert")
    }

    private fun hideOptionsPanel() {
        isOptionsVisible = false

        // Changer l'icône X en +
        plusButton.setImageResource(R.drawable.ic_add)

        // ✅ CORRECTION: Animation plus stable
        optionsContainer.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                if (!isOptionsVisible) { // ✅ Vérifier qu'on veut toujours fermer
                    optionsContainer.visibility = View.GONE
                }
            }
            .start()

        Log.d(TAG, "Panneau d'options fermé")
    }

    private fun openFilePicker() {
        fileLauncher.launch("*/*")
    }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    private fun openVideoPicker() {
        galleryLauncher.launch("video/*")
    }

    private fun checkCameraPermissionAndOpen() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            else -> {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun openCamera() {
        val photoFile = createImageFile()
        photoFile?.let { file ->
            currentPhotoPath = file.absolutePath
            val photoURI = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            cameraLauncher.launch(photoURI)
        }
    }

    private fun createImageFile(): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFileName = "JPEG_${timeStamp}_"
            val storageDir = File(requireContext().cacheDir, "images")
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }
            File.createTempFile(imageFileName, ".jpg", storageDir)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur création fichier image", e)
            null
        }
    }

    private fun handleSelectedFile(uri: Uri, description: String) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val fileName = getFileName(uri) ?: "fichier_${System.currentTimeMillis()}"

            // Créer un fichier temporaire
            val tempFile = File(requireContext().cacheDir, fileName)
            val outputStream = FileOutputStream(tempFile)

            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            uploadFile(tempFile, description)

        } catch (e: Exception) {
            Log.e(TAG, "Erreur traitement fichier", e)
            Toast.makeText(context, "Erreur lors du traitement du fichier", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        result = it.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result
    }

    private fun uploadFile(file: File, description: String) {
        val token = getAuthToken()
        if (token.isEmpty()) {
            showError("Session expirée")
            return
        }

        if (conversationId.isEmpty()) {
            showError("ID de conversation manquant")
            return
        }

        // Vérifier la taille du fichier (max 20MB)
        val maxSize = 20 * 1024 * 1024 // 20MB en bytes
        if (file.length() > maxSize) {
            Toast.makeText(context, "Fichier trop volumineux (max 20MB)", Toast.LENGTH_LONG).show()
            return
        }

        // Afficher un indicateur de progression
        val progressDialog = ProgressDialog(requireContext()).apply {
            setMessage("Envoi du fichier...")
            setCancelable(false)
            show()
        }

        Log.d(TAG, "=== UPLOAD FICHIER ===")
        Log.d(TAG, "Fichier: ${file.name} (${file.length()} bytes)")
        Log.d(TAG, "Description: $description")
        Log.d(TAG, "ConversationId: $conversationId")
        Log.d(TAG, "======================")

        ApiClient.uploadFileToConversation(token, conversationId, file, description)
            .enqueue(object : Callback<ConversationMessagesResponse> {
                override fun onResponse(
                    call: Call<ConversationMessagesResponse>,
                    response: Response<ConversationMessagesResponse>
                ) {
                    progressDialog.dismiss()

                    if (!isAdded) return

                    if (response.isSuccessful) {
                        Toast.makeText(context, "Fichier envoyé avec succès", Toast.LENGTH_SHORT).show()

                        // Recharger les messages pour voir le nouveau fichier
                        loadMessages()

                        // Nettoyer le fichier temporaire
                        if (file.path.contains(requireContext().cacheDir.path)) {
                            file.delete()
                        }

                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "Erreur upload: Code=${response.code()}, Body=$errorBody")

                        val errorMessage = when (response.code()) {
                            413 -> "Fichier trop volumineux"
                            415 -> "Type de fichier non supporté"
                            400 -> "Données invalides"
                            404 -> "Conversation non trouvée"
                            else -> "Erreur lors de l'envoi (${response.code()})"
                        }
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<ConversationMessagesResponse>, t: Throwable) {
                    progressDialog.dismiss()

                    if (!isAdded) return

                    Log.e(TAG, "Erreur réseau upload", t)
                    Toast.makeText(context, "Erreur réseau: ${t.message}", Toast.LENGTH_LONG).show()
                }
            })
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
            onMessageClick = { message, view ->
                if (message.expediteurId == myUserId) {
                    showQuickEditPopup(message, view)
                }
            }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = this@PrivateConversationFragment.adapter

            // ✅ CORRECTION: Scroll listener moins agressif
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                private var scrollDistance = 0

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    // Fermer le popup d'édition
                    dismissCurrentPopup()

                    // ✅ NOUVEAU: Fermer les options seulement après un scroll significatif
                    scrollDistance += Math.abs(dy)
                    if (scrollDistance > 100 && isOptionsVisible) { // 100px de scroll avant fermeture
                        hideOptionsPanel()
                        scrollDistance = 0
                    }
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        scrollDistance = 0
                    }
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

            // Afficher le popup en dessous du message
            currentPopupWindow?.showAsDropDown(
                anchorView,
                0,
                8
            )

            // Auto-fermeture après 5 secondes
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                dismissCurrentPopup()
            }, 5000)

            Log.d(TAG, "Popup d'édition affiché en dessous du message: ${message.id}")

        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'affichage du popup", e)
            android.widget.Toast.makeText(context, "Erreur lors de l'affichage du menu", android.widget.Toast.LENGTH_SHORT).show()
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
        webSocketService.connectionStatus.observe(viewLifecycleOwner, Observer { isConnected ->
            Log.d(TAG, "Statut connexion WebSocket: $isConnected")
            updateConnectionIndicator(isConnected)

            val hasText = messageInput.text.toString().trim().isNotEmpty()
            sendButton.isEnabled = isConnected && hasText
            sendButton.alpha = if (sendButton.isEnabled) 1.0f else 0.5f

            messageInput.hint = if (isConnected) {
                "Écrivez un message..."
            } else {
                "Reconnexion en cours..."
            }
        })

        webSocketService.newPrivateMessage.observe(viewLifecycleOwner, Observer { message ->
            Log.d(TAG, "Nouveau message reçu via WebSocket: ${message.contenu} de ${message.expediteur}")

            val isFromOtherUser = message.expediteur == otherUserId
            val isFromMe = message.expediteur == myUserId
            val isForThisConversation = message.conversation == conversationId ||
                    message.expediteur == otherUserId ||
                    (isFromMe && message.conversation == otherUserId)

            if (isForThisConversation) {
                adapter.addMessage(message)
                scrollToBottom()

                if (isFromOtherUser) {
                    markMessageAsReadViaAPI(message.expediteur)
                }

                Log.d(TAG, "Message ajouté à la conversation")
            } else {
                Log.d(TAG, "Message ignoré - ne concerne pas cette conversation")
            }
        })

        webSocketService.messageRead.observe(viewLifecycleOwner, Observer { messageId ->
            Log.d(TAG, "Message marqué comme lu: $messageId")
            adapter.notifyDataSetChanged()
        })

        webSocketService.messageModified.observe(viewLifecycleOwner, Observer { message ->
            Log.d(TAG, "Message modifié: ${message.contenu}")
            loadMessages()
        })

        webSocketService.messageDeleted.observe(viewLifecycleOwner, Observer { messageId ->
            Log.d(TAG, "Message supprimé: $messageId")
            loadMessages()
        })

        webSocketService.messageSent.observe(viewLifecycleOwner, Observer { messageId ->
            Log.d(TAG, "Message envoyé avec succès: $messageId")
        })

        webSocketService.error.observe(viewLifecycleOwner, Observer { error ->
            Log.e(TAG, "Erreur WebSocket: $error")
            showError(error)
        })
    }

    private fun updateConnectionIndicator(isConnected: Boolean) {
        if (isConnected) {
            connectionIndicator.backgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#4CAF50")
                )
            connectionStatusText.text = "En ligne"
            connectionStatusText.setTextColor(android.graphics.Color.parseColor("#CCFFFFFF"))
        } else {
            connectionIndicator.backgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#F44336")
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

    private fun sendMessageViaWebSocket(content: String) {
        Log.d(TAG, "=== ENVOI DE MESSAGE ===")
        Log.d(TAG, "Contenu: '$content'")
        Log.d(TAG, "Vers conversationId: '$conversationId'")
        Log.d(TAG, "========================")

        sendMessageViaAPI(content)
    }

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
                        loadMessages()
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

    // Callbacks WebSocket (compatibilité)
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

    private fun markMessageAsReadViaAPI(messageId: String) {
        val token = getAuthToken()
        if (token.isEmpty()) return

        if (conversationId.isEmpty()) {
            Log.e(TAG, "conversationId vide pour marquer comme lu")
            return
        }

        Log.d(TAG, "Marquage lecture: conversationId='$conversationId', messageId='$messageId'")

        if (webSocketService.isConnected()) {
            webSocketService.markMessageAsRead(messageId)
            return
        }

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
        notificationService.markPrivateConversationAsRead(requireContext(), conversationId)
        Log.d(TAG, "Notifications marquées comme lues pour conversation: $conversationId")
    }

    override fun onResume() {
        super.onResume()
        val token = getAuthToken()
        if (token.isNotEmpty() && !webSocketService.isConnected()) {
            webSocketService.initialize(token)
        }
        markUnreadMessagesAsRead()
    }

    override fun onPause() {
        super.onPause()
        dismissCurrentPopup()
        // ✅ NOUVEAU: Masquer les options quand on quitte
        if (isOptionsVisible) {
            hideOptionsPanel()
        }
        markUnreadMessagesAsRead()
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissCurrentPopup()
        webSocketService.removeMessageListener(this)
    }
}