package com.example.supchat.ui.chat

import android.Manifest
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
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
import com.example.supchat.adapters.ParticipantsAdapter
import com.example.supchat.adapters.UserSearchAdapter
import com.example.supchat.api.ApiClient
import com.example.supchat.models.response.messageprivate.ConversationMessagesResponse
import com.example.supchat.models.response.messageprivate.ConversationMessage
import com.example.supchat.models.response.messageprivate.ConversationDetails
import com.example.supchat.models.response.messageprivate.ConversationDetailsResponse
import com.example.supchat.models.response.messageprivate.ConversationParticipant
import com.example.supchat.models.response.UserSearchData
import com.example.supchat.models.response.UserSearchResponse
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

    // ==================== VARIABLES ====================

    // Variables de base
    private var conversationId: String = ""
    private var otherUserId: String = ""
    private var username: String = ""
    private var myUserId: String = ""
    private var profilePicture: String? = null

    // Services
    private lateinit var webSocketService: WebSocketService
    private lateinit var notificationService: NotificationService

    // Vues principales
    private lateinit var recyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var connectionIndicator: View
    private lateinit var connectionStatusText: TextView
    private lateinit var adapter: PrivateChatAdapter

    // Vues pour fichiers
    private lateinit var plusButton: ImageButton
    private lateinit var optionsContainer: LinearLayout
    private lateinit var messageInputContainer: LinearLayout
    private lateinit var fileButton: Button
    private lateinit var pollButton: Button
    private lateinit var cameraButton: ImageButton
    private lateinit var galleryButton: ImageButton
    private lateinit var videoButton: ImageButton

    // Vues pour participants
    private lateinit var participantsMenuButton: ImageButton
    private lateinit var participantsPanel: LinearLayout
    private lateinit var participantsRecyclerView: RecyclerView
    private lateinit var inviteParticipantButton: Button
    private lateinit var leaveConversationButton: Button

    // Variables d'état
    private var isOptionsVisible = false
    private var isParticipantsPanelVisible = false
    private var currentPhotoPath: String? = null
    private var currentPopupWindow: android.widget.PopupWindow? = null

    // Adaptateurs
    private lateinit var participantsAdapter: ParticipantsAdapter
    private var conversationDetails: ConversationDetails? = null

    // Lanceurs d'activité
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

    // ==================== LIFECYCLE ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Récupérer les arguments
        arguments?.let {
            conversationId = it.getString(ARG_CONVERSATION_ID, "")
            otherUserId = it.getString(ARG_OTHER_USER_ID, "")
            username = it.getString(ARG_USERNAME, "")
            myUserId = it.getString(ARG_MY_USER_ID, "")
            profilePicture = it.getString(ARG_PROFILE_PICTURE)
        }

        // Debug
        Log.d(TAG, "=== PARAMÈTRES DE LA CONVERSATION ===")
        Log.d(TAG, "conversationId: '$conversationId'")
        Log.d(TAG, "otherUserId: '$otherUserId'")
        Log.d(TAG, "myUserId: '$myUserId'")
        Log.d(TAG, "username: '$username'")
        Log.d(TAG, "=====================================")

        // Récupérer myUserId si manquant
        if (myUserId.isEmpty()) {
            myUserId = getCurrentUserId()
            Log.d(TAG, "myUserId récupéré des préférences: '$myUserId'")
        }

        // Vérifications
        if (conversationId.isEmpty()) {
            Log.e(TAG, "ERREUR: conversationId est vide !")
        }

        // Initialiser les services
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
        setupFileButtons()
        setupParticipantsManagement()
        setupWebSocketObservers()
        loadMessages()

        return view
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

        if (isOptionsVisible) {
            hideOptionsPanel()
        }
        if (isParticipantsPanelVisible) {
            hideParticipantsPanel()
        }

        markUnreadMessagesAsRead()
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissCurrentPopup()
        webSocketService.removeMessageListener(this)
    }

    // ==================== INITIALISATION ====================

    private fun initViews(view: View) {
        // Vues principales
        recyclerView = view.findViewById(R.id.messages_recycler_view)
        messageInput = view.findViewById(R.id.message_input)
        sendButton = view.findViewById(R.id.send_button)
        progressBar = view.findViewById(R.id.messages_progress_bar)
        connectionIndicator = view.findViewById(R.id.connection_indicator)
        connectionStatusText = view.findViewById(R.id.connection_status_text)

        // Vues pour fichiers
        plusButton = view.findViewById(R.id.plus_button)
        optionsContainer = view.findViewById(R.id.options_container)
        messageInputContainer = view.findViewById(R.id.message_input_container)
        fileButton = view.findViewById(R.id.file_button)
        pollButton = view.findViewById(R.id.poll_button)
        cameraButton = view.findViewById(R.id.camera_button)
        galleryButton = view.findViewById(R.id.gallery_button)
        videoButton = view.findViewById(R.id.video_button)

        // Vues pour participants
        participantsMenuButton = view.findViewById(R.id.participants_menu_button)
        participantsPanel = view.findViewById(R.id.participants_panel)
        participantsRecyclerView = view.findViewById(R.id.participants_recycler_view)
        inviteParticipantButton = view.findViewById(R.id.invite_participant_button)
        leaveConversationButton = view.findViewById(R.id.leave_conversation_button)

        // Configuration du header
        val usernameTextView: TextView = view.findViewById(R.id.username_text)
        usernameTextView.text = username

        val backButton: ImageButton = view.findViewById(R.id.back_button)
        backButton.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        // Image de profil
        val profileImageView: de.hdodenhof.circleimageview.CircleImageView = view.findViewById(R.id.user_profile_image)
        if (!profilePicture.isNullOrEmpty()) {
            // Glide.with(this).load("http://10.0.2.2:3000/uploads/profile-pictures/$profilePicture").into(profileImageView)
        }
    }

    // ==================== GESTION DES PARTICIPANTS ====================

    private fun setupParticipantsManagement() {
        Log.d(TAG, "=== SETUP PARTICIPANTS MANAGEMENT ===")
        Log.d(TAG, "conversationId au setup: '$conversationId'")

        // Configuration de l'adaptateur
        participantsAdapter = ParticipantsAdapter(
            context = requireContext(),
            currentUserId = myUserId,
            conversationCreatorId = "",
            onRemoveParticipant = { participant ->
                showRemoveParticipantConfirmation(participant)
            }
        )

        participantsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = participantsAdapter
        }

        // Listeners avec plus de logs
        participantsMenuButton.setOnClickListener {
            Log.d(TAG, "=== CLIC BOUTON MENU PARTICIPANTS ===")
            Log.d(TAG, "isEnabled: ${participantsMenuButton.isEnabled}")
            Log.d(TAG, "visibility: ${participantsMenuButton.visibility}")
            toggleParticipantsPanel()
        }

        inviteParticipantButton.setOnClickListener {
            Log.d(TAG, "=== CLIC BOUTON INVITER PARTICIPANT ===")
            Log.d(TAG, "isEnabled: ${inviteParticipantButton.isEnabled}")
            Log.d(TAG, "conversationId: '$conversationId'")
            Log.d(TAG, "conversationDetails: $conversationDetails")

            if (conversationId.isEmpty()) {
                Log.e(TAG, "ERREUR: conversationId vide, impossible d'inviter")
                Toast.makeText(context, "Erreur: ID de conversation manquant", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showInviteParticipantDialog()
        }

        leaveConversationButton.setOnClickListener {
            Log.d(TAG, "=== CLIC BOUTON QUITTER CONVERSATION ===")
            Log.d(TAG, "isEnabled: ${leaveConversationButton.isEnabled}")
            Log.d(TAG, "conversationId: '$conversationId'")
            showLeaveConversationConfirmation()
        }
    }

    private fun toggleParticipantsPanel() {
        if (isParticipantsPanelVisible) {
            hideParticipantsPanel()
        } else {
            showParticipantsPanel()
        }
    }

    private fun showParticipantsPanel() {
        Log.d(TAG, "=== SHOW PARTICIPANTS PANEL ===")
        Log.d(TAG, "conversationId: '$conversationId'")
        Log.d(TAG, "conversationDetails avant: $conversationDetails")

        isParticipantsPanelVisible = true

        // ✅ AJOUT: Charger TOUJOURS les détails quand on ouvre le panneau
        if (conversationId.isNotEmpty()) {
            Log.d(TAG, "Chargement des détails de conversation...")
            loadConversationDetails()
        } else {
            Log.e(TAG, "ERREUR: conversationId vide!")
            // ✅ POUR UNE CONVERSATION PRIVÉE, créer une liste avec les 2 participants
            createDummyParticipantsList()
        }

        participantsPanel.visibility = View.VISIBLE
        participantsPanel.translationY = -participantsPanel.height.toFloat()
        participantsPanel.animate()
            .translationY(0f)
            .setDuration(300)
            .setListener(null)
            .start()

        Log.d(TAG, "Panneau participants ouvert")
    }

    private fun createDummyParticipantsList() {
        Log.d(TAG, "=== CRÉATION LISTE PARTICIPANTS DUMMY ===")
        Log.d(TAG, "myUserId: '$myUserId'")
        Log.d(TAG, "otherUserId: '$otherUserId'")
        Log.d(TAG, "username: '$username'")

        val participants = mutableListOf<ConversationParticipant>()

        // Ajouter l'utilisateur actuel
        participants.add(ConversationParticipant(
            utilisateur = myUserId,
            username = getCurrentUsername().ifEmpty { "Vous" },
            profilePicture = null,
            dateAjout = "",
            role = "participant"
        ))

        // Ajouter l'autre utilisateur
        participants.add(ConversationParticipant(
            utilisateur = otherUserId,
            username = username,
            profilePicture = profilePicture,
            dateAjout = "",
            role = "participant"
        ))

        Log.d(TAG, "Participants créés: ${participants.size}")
        participants.forEachIndexed { index, participant ->
            Log.d(TAG, "Participant $index: ${participant.username} (${participant.utilisateur})")
        }

        updateParticipantsList(participants, myUserId)
    }

    private fun hideParticipantsPanel() {
        isParticipantsPanelVisible = false

        participantsPanel.animate()
            .translationY(-participantsPanel.height.toFloat())
            .setDuration(250)
            .withEndAction {
                if (!isParticipantsPanelVisible) {
                    participantsPanel.visibility = View.GONE
                }
            }
            .start()

        Log.d(TAG, "Panneau participants fermé")
    }

    private fun loadConversationDetails() {
        Log.d(TAG, "=== LOAD CONVERSATION DETAILS ===")
        Log.d(TAG, "conversationId: '$conversationId'")

        val token = getAuthToken()
        if (token.isEmpty()) {
            Log.e(TAG, "Token vide")
            showError("Session expirée")
            return
        }

        if (conversationId.isEmpty()) {
            Log.e(TAG, "conversationId vide - création liste dummy")
            createDummyParticipantsList()
            return
        }

        Log.d(TAG, "Appel API getConversationDetails...")

        ApiClient.getConversationDetails(token, conversationId)
            .enqueue(object : Callback<ConversationDetailsResponse> {
                override fun onResponse(
                    call: Call<ConversationDetailsResponse>,
                    response: Response<ConversationDetailsResponse>
                ) {
                    if (!isAdded) return

                    Log.d(TAG, "=== RÉPONSE API CONVERSATION DETAILS ===")
                    Log.d(TAG, "Code: ${response.code()}")
                    Log.d(TAG, "Success: ${response.isSuccessful}")

                    if (response.isSuccessful) {
                        val detailsResponse = response.body()
                        Log.d(TAG, "Response body: $detailsResponse")

                        detailsResponse?.data?.conversation?.let { details ->
                            Log.d(TAG, "=== DÉTAILS CONVERSATION REÇUS ===")
                            Log.d(TAG, "ID: ${details._id}")
                            Log.d(TAG, "Nom: ${details.nom}")
                            Log.d(TAG, "Est groupe: ${details.estGroupe}")
                            Log.d(TAG, "Créateur: ${details.createur}")
                            Log.d(TAG, "Participants: ${details.participants.size}")

                            if (details.participants.isEmpty()) {
                                Log.w(TAG, "⚠️ API a retourné 0 participants!")
                                createDummyParticipantsList()
                            } else {
                                details.participants.forEachIndexed { index, participant ->
                                    Log.d(TAG, "Participant $index: '${participant.username}' (ID: '${participant.utilisateur}')")
                                }

                                conversationDetails = details
                                updateParticipantsList(details.participants, details.createur)
                            }

                            if (details.estGroupe && !details.nom.isNullOrEmpty()) {
                                val usernameTextView: TextView = requireView().findViewById(R.id.username_text)
                                usernameTextView.text = details.nom
                            }
                        } ?: run {
                            Log.e(TAG, "Pas de détails de conversation dans la réponse")
                            createDummyParticipantsList()
                        }
                    } else {
                        Log.e(TAG, "Erreur chargement détails: ${response.code()}")
                        try {
                            val errorBody = response.errorBody()?.string()
                            Log.e(TAG, "Error body: $errorBody")
                        } catch (e: Exception) {
                            Log.e(TAG, "Erreur lecture error body", e)
                        }
                        // En cas d'erreur, créer la liste dummy
                        createDummyParticipantsList()
                    }
                }

                override fun onFailure(call: Call<ConversationDetailsResponse>, t: Throwable) {
                    if (!isAdded) return
                    Log.e(TAG, "=== ÉCHEC APPEL API ===", t)
                    createDummyParticipantsList()
                }
            })
    }
    private fun updateParticipantsList(participants: List<ConversationParticipant>, creatorId: String) {
        Log.d(TAG, "=== UPDATE PARTICIPANTS LIST ===")
        Log.d(TAG, "Nombre de participants reçus: ${participants.size}")
        Log.d(TAG, "CreatorId: '$creatorId'")
        Log.d(TAG, "MyUserId: '$myUserId'")

        participantsAdapter = ParticipantsAdapter(
            context = requireContext(),
            currentUserId = myUserId,
            conversationCreatorId = creatorId,
            onRemoveParticipant = { participant ->
                showRemoveParticipantConfirmation(participant)
            }
        )

        participantsRecyclerView.adapter = participantsAdapter
        participantsAdapter.updateParticipants(participants)

        Log.d(TAG, "Adaptateur mis à jour, itemCount: ${participantsAdapter.itemCount}")
    }

    private fun showInviteParticipantDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_invite_participant, null)

        val searchInput: EditText = dialogView.findViewById(R.id.search_user_input)
        val searchRecyclerView: RecyclerView = dialogView.findViewById(R.id.search_results_recycler_view)
        val cancelButton: Button = dialogView.findViewById(R.id.cancel_invite_button)

        val searchAdapter = UserSearchAdapter(
            context = requireContext(),
            onUserClickListener = object : UserSearchAdapter.OnUserClickListener {
                override fun onUserClick(user: UserSearchData) {}
                override fun onActionButtonClick(user: UserSearchData) {
                    inviteUserToConversation(user)
                }
            }
        )

        searchRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchAdapter
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                if (query.length >= 2) {
                    searchUsers(query, searchAdapter)
                } else {
                    searchAdapter.updateUsers(emptyList())
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }


    private fun searchUsers(query: String, adapter: UserSearchAdapter) {
        val token = getAuthToken()
        if (token.isEmpty()) return

        ApiClient.searchUsers(token, query)
            .enqueue(object : Callback<UserSearchResponse> {
                override fun onResponse(
                    call: Call<UserSearchResponse>,
                    response: Response<UserSearchResponse>
                ) {
                    if (!isAdded) return

                    if (response.isSuccessful) {
                        // ✅ CORRECTION: Accéder à response.body()?.data?.users pour obtenir la liste
                        val users: List<UserSearchData> = response.body()?.data?.users ?: emptyList()
                        val currentParticipantIds = conversationDetails?.participants?.map { it.utilisateur }?.toSet() ?: emptySet()
                        // ✅ CORRECTION: Filtrer la liste d'utilisateurs
                        val filteredUsers = users.filter { user -> !currentParticipantIds.contains(user.id) }
                        adapter.updateUsers(filteredUsers)
                    }
                }

                override fun onFailure(call: Call<UserSearchResponse>, t: Throwable) {
                    if (!isAdded) return
                    Log.e(TAG, "Erreur réseau recherche", t)
                }
            })
    }

    private fun inviteUserToConversation(user: UserSearchData) {
        val token = getAuthToken()
        if (token.isEmpty()) {
            showError("Session expirée")
            return
        }

        val progressDialog = ProgressDialog(requireContext()).apply {
            setMessage("Invitation en cours...")
            setCancelable(false)
            show()
        }

        ApiClient.addParticipantToConversation(token, conversationId, user.id)
            .enqueue(object : Callback<ConversationDetailsResponse> {
                override fun onResponse(
                    call: Call<ConversationDetailsResponse>,
                    response: Response<ConversationDetailsResponse>
                ) {
                    progressDialog.dismiss()
                    if (!isAdded) return

                    if (response.isSuccessful) {
                        Toast.makeText(context, "${user.username} a été invité avec succès", Toast.LENGTH_SHORT).show()
                        loadConversationDetails()
                    } else {
                        val errorMessage = when (response.code()) {
                            404 -> "Utilisateur ou conversation non trouvé"
                            403 -> "Vous n'avez pas l'autorisation d'inviter des participants"
                            409 -> "L'utilisateur est déjà dans la conversation"
                            else -> "Erreur lors de l'invitation (${response.code()})"
                        }
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<ConversationDetailsResponse>, t: Throwable) {
                    progressDialog.dismiss()
                    if (!isAdded) return
                    Toast.makeText(context, "Erreur réseau: ${t.message}", Toast.LENGTH_LONG).show()
                }
            })
    }

    private fun showRemoveParticipantConfirmation(participant: ConversationParticipant) {
        AlertDialog.Builder(requireContext())
            .setTitle("Supprimer le participant")
            .setMessage("Êtes-vous sûr de vouloir supprimer ${participant.username} de cette conversation ?")
            .setPositiveButton("Supprimer") { _, _ ->
                removeParticipantFromConversation(participant)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun removeParticipantFromConversation(participant: ConversationParticipant) {
        val token = getAuthToken()
        if (token.isEmpty()) {
            showError("Session expirée")
            return
        }

        val progressDialog = ProgressDialog(requireContext()).apply {
            setMessage("Suppression en cours...")
            setCancelable(false)
            show()
        }

        ApiClient.removeParticipantFromConversation(token, conversationId, participant.utilisateur)
            .enqueue(object : Callback<ConversationDetailsResponse> {
                override fun onResponse(
                    call: Call<ConversationDetailsResponse>,
                    response: Response<ConversationDetailsResponse>
                ) {
                    progressDialog.dismiss()
                    if (!isAdded) return

                    if (response.isSuccessful) {
                        Toast.makeText(context, "${participant.username} a été supprimé", Toast.LENGTH_SHORT).show()
                        loadConversationDetails()
                    } else {
                        val errorMessage = when (response.code()) {
                            404 -> "Participant ou conversation non trouvé"
                            403 -> "Vous n'avez pas l'autorisation de supprimer ce participant"
                            else -> "Erreur lors de la suppression (${response.code()})"
                        }
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<ConversationDetailsResponse>, t: Throwable) {
                    progressDialog.dismiss()
                    if (!isAdded) return
                    Toast.makeText(context, "Erreur réseau: ${t.message}", Toast.LENGTH_LONG).show()
                }
            })
    }

    private fun showLeaveConversationConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Quitter la conversation")
            .setMessage("Êtes-vous sûr de vouloir quitter cette conversation ? Vous ne pourrez plus voir les messages ni participer.")
            .setPositiveButton("Quitter") { _, _ ->
                leaveConversation()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun leaveConversation() {
        val token = getAuthToken()
        if (token.isEmpty()) {
            showError("Session expirée")
            return
        }

        val progressDialog = ProgressDialog(requireContext()).apply {
            setMessage("Sortie de la conversation...")
            setCancelable(false)
            show()
        }

        ApiClient.leaveConversation(token, conversationId)
            .enqueue(object : Callback<ConversationDetailsResponse> {
                override fun onResponse(
                    call: Call<ConversationDetailsResponse>,
                    response: Response<ConversationDetailsResponse>
                ) {
                    progressDialog.dismiss()
                    if (!isAdded) return

                    if (response.isSuccessful) {
                        Toast.makeText(context, "Vous avez quitté la conversation", Toast.LENGTH_SHORT).show()
                        requireActivity().supportFragmentManager.popBackStack()
                    } else {
                        val errorMessage = when (response.code()) {
                            404 -> "Conversation non trouvée"
                            403 -> "Vous n'avez pas l'autorisation de quitter cette conversation"
                            else -> "Erreur lors de la sortie (${response.code()})"
                        }
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<ConversationDetailsResponse>, t: Throwable) {
                    progressDialog.dismiss()
                    if (!isAdded) return
                    Toast.makeText(context, "Erreur réseau: ${t.message}", Toast.LENGTH_LONG).show()
                }
            })
    }

    // ==================== GESTION DES FICHIERS ====================

    private fun setupFileButtons() {
        plusButton.setOnClickListener {
            toggleOptionsPanel()
        }

        fileButton.setOnClickListener {
            openFilePicker()
        }

        pollButton.setOnClickListener {
            Toast.makeText(context, "Sondage - À implémenter", Toast.LENGTH_SHORT).show()
            hideOptionsPanel()
        }

        cameraButton.setOnClickListener {
            checkCameraPermissionAndOpen()
        }

        galleryButton.setOnClickListener {
            openGallery()
        }

        videoButton.setOnClickListener {
            openVideoPicker()
        }
    }

    private fun toggleOptionsPanel() {
        if (isOptionsVisible) {
            hideOptionsPanel()
        } else {
            showOptionsPanel()
        }
    }

    private fun showOptionsPanel() {
        isOptionsVisible = true
        plusButton.setImageResource(R.drawable.ic_close)

        optionsContainer.visibility = View.VISIBLE
        optionsContainer.alpha = 0f
        optionsContainer.animate()
            .alpha(1f)
            .setDuration(250)
            .setListener(null)
            .start()
    }

    private fun hideOptionsPanel() {
        isOptionsVisible = false
        plusButton.setImageResource(R.drawable.ic_add)

        optionsContainer.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                if (!isOptionsVisible) {
                    optionsContainer.visibility = View.GONE
                }
            }
            .start()
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

        val maxSize = 20 * 1024 * 1024
        if (file.length() > maxSize) {
            Toast.makeText(context, "Fichier trop volumineux (max 20MB)", Toast.LENGTH_LONG).show()
            return
        }

        val progressDialog = ProgressDialog(requireContext()).apply {
            setMessage("Envoi du fichier...")
            setCancelable(false)
            show()
        }

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
                        loadMessages()

                        if (file.path.contains(requireContext().cacheDir.path)) {
                            file.delete()
                        }
                    } else {
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
                    Toast.makeText(context, "Erreur réseau: ${t.message}", Toast.LENGTH_LONG).show()
                }
            })
    }

    // ==================== GESTION DES MESSAGES ====================

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

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                private var scrollDistance = 0

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    dismissCurrentPopup()

                    scrollDistance += Math.abs(dy)
                    if (scrollDistance > 100) {
                        if (isOptionsVisible) {
                            hideOptionsPanel()
                        }
                        if (isParticipantsPanelVisible) {
                            hideParticipantsPanel()
                        }
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

    private fun setupSendButton() {
        sendButton.setOnClickListener {
            val messageText = messageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                sendMessageViaAPI(messageText)
            }
        }

        messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                val messageText = messageInput.text.toString().trim()
                if (messageText.isNotEmpty()) {
                    sendMessageViaAPI(messageText)
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

    private fun loadMessages() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE

        val token = getAuthToken()
        if (token.isEmpty()) {
            showError("Session expirée")
            return
        }

        if (conversationId.isEmpty()) {
            showError("Erreur: ID de conversation manquant")
            return
        }

        ApiClient.getConversationMessages(token, conversationId)
            .enqueue(object : Callback<ConversationMessagesResponse> {
                override fun onResponse(
                    call: Call<ConversationMessagesResponse>,
                    response: Response<ConversationMessagesResponse>
                ) {
                    if (!isAdded) return
                    progressBar.visibility = View.GONE

                    if (response.isSuccessful) {
                        val messages = response.body()?.data ?: emptyList()

                        if (messages.isEmpty()) {
                            recyclerView.visibility = View.VISIBLE
                            showEmptyState()
                        } else {
                            recyclerView.visibility = View.VISIBLE
                            adapter.updateMessages(messages)
                            scrollToBottom()
                        }
                    } else {
                        showError("Erreur lors du chargement: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<ConversationMessagesResponse>, t: Throwable) {
                    if (!isAdded) return
                    progressBar.visibility = View.GONE
                    showError("Erreur réseau: ${t.message}")
                }
            })
    }

    private fun sendMessageViaAPI(content: String) {
        val token = getAuthToken()
        if (token.isEmpty()) {
            showError("Session expirée")
            return
        }

        if (conversationId.isEmpty()) {
            showError("Erreur: ID de conversation manquant")
            return
        }

        sendButton.isEnabled = false
        messageInput.isEnabled = false

        ApiClient.sendConversationMessage(token, conversationId, content)
            .enqueue(object : Callback<ConversationMessagesResponse> {
                override fun onResponse(
                    call: Call<ConversationMessagesResponse>,
                    response: Response<ConversationMessagesResponse>
                ) {
                    if (!isAdded) return

                    sendButton.isEnabled = true
                    messageInput.isEnabled = true

                    if (response.isSuccessful) {
                        messageInput.text.clear()
                        loadMessages()
                    } else {
                        Toast.makeText(context, "Erreur lors de l'envoi: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<ConversationMessagesResponse>, t: Throwable) {
                    if (!isAdded) return

                    sendButton.isEnabled = true
                    messageInput.isEnabled = true
                    Toast.makeText(context, "Erreur réseau: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun scrollToBottom() {
        if (adapter.itemCount > 0) {
            recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
        }
    }

    // ==================== GESTION DES ACTIONS SUR MESSAGES ====================

    private fun showQuickEditPopup(message: ConversationMessage, anchorView: View) {
        dismissCurrentPopup()

        try {
            val inflater = LayoutInflater.from(requireContext())
            val popupView = inflater.inflate(R.layout.popup_message_edit_conversation, null)

            val editButton = popupView.findViewById<android.widget.Button>(R.id.edit_button)
            val deleteButton = popupView.findViewById<android.widget.Button>(R.id.delete_button)

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

            editButton.setOnClickListener {
                editMessage(message)
                dismissCurrentPopup()
            }

            deleteButton.setOnClickListener {
                deleteMessage(message)
                dismissCurrentPopup()
            }

            currentPopupWindow?.showAsDropDown(anchorView, 0, 8)

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                dismissCurrentPopup()
            }, 5000)

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

    private fun showMessageOptions(message: ConversationMessage, position: Int) {
        if (message.expediteur != myUserId) {
            return
        }

        val options = arrayOf("Modifier", "Supprimer", "Répondre")

        AlertDialog.Builder(requireContext())
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

        AlertDialog.Builder(requireContext())
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
        AlertDialog.Builder(requireContext())
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

        val messageId = message.messageId
        if (messageId.isEmpty()) {
            showError("ID de message manquant")
            return
        }

        val progressDialog = ProgressDialog(requireContext()).apply {
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

                    if (response.isSuccessful) {
                        Toast.makeText(context, "Message modifié avec succès", Toast.LENGTH_SHORT).show()
                        loadMessages()
                    } else {
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

        val messageId = message.messageId
        if (messageId.isEmpty()) {
            showError("ID de message manquant")
            return
        }

        val progressDialog = ProgressDialog(requireContext()).apply {
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

                    if (response.isSuccessful) {
                        Toast.makeText(context, "Message supprimé", Toast.LENGTH_SHORT).show()
                        loadMessages()
                    } else {
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
                    Toast.makeText(context, "Erreur réseau: ${t.message}", Toast.LENGTH_LONG).show()
                }
            })
    }

    private fun replyToMessage(message: ConversationMessage) {
        messageInput.setText("@${username} ")
        messageInput.setSelection(messageInput.text.length)
        messageInput.requestFocus()
    }

    // ==================== WEBSOCKET ====================

    private fun setupWebSocketObservers() {
        webSocketService.connectionStatus.observe(viewLifecycleOwner, Observer { isConnected ->
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
            }
        })

        webSocketService.messageRead.observe(viewLifecycleOwner, Observer { messageId ->
            adapter.notifyDataSetChanged()
        })

        webSocketService.messageModified.observe(viewLifecycleOwner, Observer { message ->
            loadMessages()
        })

        webSocketService.messageDeleted.observe(viewLifecycleOwner, Observer { messageId ->
            loadMessages()
        })

        webSocketService.messageSent.observe(viewLifecycleOwner, Observer { messageId ->
            Log.d(TAG, "Message envoyé avec succès: $messageId")
        })

        webSocketService.error.observe(viewLifecycleOwner, Observer { error ->
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

    // ==================== WEBSOCKET CALLBACKS ====================

    override fun onNewPrivateMessage(message: org.json.JSONObject) {
        // Géré par les LiveData observers
    }

    override fun onPrivateMessageSent(messageId: String) {
        // Géré par les LiveData observers
    }

    override fun onPrivateMessageRead(messageId: String) {
        // Géré par les LiveData observers
    }

    override fun onPrivateMessageModified(message: org.json.JSONObject) {
        // Géré par les LiveData observers
    }

    override fun onPrivateMessageDeleted(messageId: String) {
        // Géré par les LiveData observers
    }

    override fun onError(error: String) {
        // Géré par les LiveData observers
    }

    override fun onConnectionChanged(isConnected: Boolean) {
        // Géré par les LiveData observers
    }

    // ==================== UTILITAIRES ====================

    private fun markMessageAsReadViaAPI(messageId: String) {
        val token = getAuthToken()
        if (token.isEmpty()) return

        if (conversationId.isEmpty()) {
            return
        }

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
                    }
                }

                override fun onFailure(call: Call<ConversationMessagesResponse>, t: Throwable) {
                    Log.e(TAG, "Erreur réseau marquage lecture", t)
                }
            })
    }

    private fun markUnreadMessagesAsRead() {
        notificationService.markPrivateConversationAsRead(requireContext(), conversationId)
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
}