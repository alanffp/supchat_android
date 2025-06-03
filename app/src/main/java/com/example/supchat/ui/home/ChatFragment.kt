package com.example.supchat.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.supchat.R
import com.example.supchat.adapters.MessageAdapter
import com.example.supchat.api.ApiClient
import com.example.supchat.models.response.Message
import com.example.supchat.models.response.MessagesResponse
import com.example.supchat.ui.dialogs.RepliesDialog
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Fragment pour afficher et interagir avec les messages d'un canal
 */
class ChatFragment : Fragment(), MessageAdapter.MessageActionListener {
    private lateinit var messageInput: EditText
    private lateinit var backButton: ImageButton
    private lateinit var sendButton: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var channelNameTextView: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var replyInfoBar: LinearLayout
    private lateinit var replyInfoText: TextView
    private lateinit var cancelReplyButton: ImageButton

    // ✅ AJOUT: Variables pour la gestion des fichiers
    private lateinit var attachButton: ImageButton
    private lateinit var attachmentOptionsContainer: LinearLayout
    private var isAttachmentOptionsVisible = false
    private var currentPhotoPath: String? = null

    // ✅ AJOUT: Vues pour les options de fichiers
    private lateinit var fileButton: Button
    private lateinit var pollButton: Button
    private lateinit var cameraButton: ImageButton
    private lateinit var galleryButton: ImageButton
    private lateinit var videoButton: ImageButton

    private var canalId: String = ""
    private var canalNom: String = ""
    private var workspaceId: String? = null
    private var replyToMessageId: String? = null
    private var replyToMessageAuthor: String? = null

    // Pour le rafraîchissement automatique
    private var autoRefreshHandler = Handler(Looper.getMainLooper())
    private var isAutoRefreshEnabled = true
    private val AUTO_REFRESH_INTERVAL = 30000L // 30 secondes

    // ✅ AJOUT: Lanceurs d'activité pour les fichiers
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
        private const val TAG = "ChatFragment"

        // Clés pour les arguments
        private const val ARG_CANAL_ID = "CHANNEL_ID"
        private const val ARG_CANAL_NOM = "CHANNEL_NAME"
        private const val ARG_WORKSPACE_ID = "WORKSPACE_ID"

        /**
         * Crée une nouvelle instance du fragment avec les paramètres spécifiés
         */
        fun newInstance(canalId: String, canalNom: String, workspaceId: String?): ChatFragment {
            Log.d(
                TAG,
                "newInstance - canalId: $canalId, canalNom: $canalNom, workspaceId: $workspaceId"
            )

            val fragment = ChatFragment()
            val args = Bundle()
            args.putString(ARG_CANAL_ID, canalId)
            args.putString(ARG_CANAL_NOM, canalNom)
            workspaceId?.let { args.putString(ARG_WORKSPACE_ID, it) }
            fragment.arguments = args

            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            Log.d(TAG, "onCreate - arguments: ${arguments?.keySet()?.joinToString()}")
            arguments?.let {
                canalId = it.getString(ARG_CANAL_ID, "")
                canalNom = it.getString(ARG_CANAL_NOM, "Canal")
                workspaceId = it.getString(ARG_WORKSPACE_ID)
            }
            Log.d(
                TAG,
                "Arguments extraits - canalId: $canalId, canalNom: $canalNom, workspaceId: $workspaceId"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'extraction des arguments", e)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView")
        return try {
            val view = inflater.inflate(R.layout.fragment_chat, container, false)

            // Initialiser les vues existantes
            messageInput = view.findViewById(R.id.message_input)
            backButton = view.findViewById(R.id.back_button)
            sendButton = view.findViewById(R.id.send_button)
            progressBar = view.findViewById(R.id.messages_progress_bar)
            channelNameTextView = view.findViewById(R.id.channel_name)
            recyclerView = view.findViewById(R.id.messages_recycler_view)
            replyInfoBar = view.findViewById(R.id.reply_info_bar)
            replyInfoText = view.findViewById(R.id.reply_info_text)
            cancelReplyButton = view.findViewById(R.id.cancel_reply_button)

            // ✅ AJOUT: Initialiser les vues pour les fichiers
            attachButton = view.findViewById(R.id.attach_button)
            attachmentOptionsContainer = view.findViewById(R.id.attachment_options_container)

            // Initialiser les options de fichiers (si elles existent dans le XML)
            try {
                fileButton = view.findViewById(R.id.option_file)
                pollButton = view.findViewById(R.id.option_poll) // ou créer un bouton sondage
                cameraButton = view.findViewById(R.id.option_camera)
                galleryButton = view.findViewById(R.id.option_image)
                videoButton = view.findViewById(R.id.option_video) // ou un autre bouton
            } catch (e: Exception) {
                Log.w(TAG, "Certaines vues de fichiers non trouvées, fonctionnalité limitée")
            }

            view
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la création de la vue", e)
            null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")

        try {
            // Vérifier les IDs nécessaires
            val prefs = requireActivity().getSharedPreferences("SupChatPrefs", Context.MODE_PRIVATE)

            if (canalId.isEmpty()) {
                Log.e(TAG, "ID du canal manquant")
                Toast.makeText(context, "Erreur: ID du canal manquant", Toast.LENGTH_SHORT).show()
                return
            }

            if (workspaceId.isNullOrEmpty()) {
                Log.e(TAG, "ID du workspace manquant")
                Toast.makeText(context, "Erreur: ID du workspace manquant", Toast.LENGTH_SHORT)
                    .show()
                return
            }

            // Récupérer le nom d'utilisateur pour l'adaptateur
            val username = requireActivity().getSharedPreferences(
                "SupChatPrefs",
                Context.MODE_PRIVATE
            ).getString("username", "") ?: ""

            // Initialiser l'adaptateur de messages
            messageAdapter = MessageAdapter(
                requireContext(),
                username,
                this  // On implémente l'interface MessageActionListener
            )

            // Configurer le RecyclerView
            recyclerView.layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            recyclerView.adapter = messageAdapter

            // Afficher le nom du canal
            channelNameTextView.text = "# $canalNom"

            // Configurer le bouton retour
            backButton.setOnClickListener {
                try {
                    requireActivity().supportFragmentManager.popBackStack()
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur lors du retour arrière", e)
                }
            }

            // Configurer l'envoi de message avec le clavier
            messageInput.setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEND ||
                    (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
                ) {
                    sendMessage()
                    return@setOnEditorActionListener true
                }
                return@setOnEditorActionListener false
            }

            // Configurer le bouton d'envoi
            sendButton.setOnClickListener {
                sendMessage()
            }

            // Configurer le bouton d'annulation de réponse
            cancelReplyButton.setOnClickListener {
                cancelReply()
            }

            // ✅ AJOUT: Configurer les boutons de fichiers
            setupFileButtons()

            // Charger les messages
            chargerMessages()

            // Démarrer le rafraîchissement automatique
            startAutoRefresh()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'initialisation de la vue", e)
            Toast.makeText(context, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        // Arrêter le rafraîchissement automatique
        stopAutoRefresh()
        super.onDestroyView()
    }

    // ✅ AJOUT: Configuration des boutons de fichiers
    private fun setupFileButtons() {
        attachButton.setOnClickListener {
            toggleAttachmentOptions()
        }

        // Configurer les options si elles existent
        try {
            fileButton.setOnClickListener {
                openFilePicker()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bouton fichier non trouvé")
        }

        try {
            pollButton.setOnClickListener {
                Toast.makeText(context, "Sondage - À implémenter", Toast.LENGTH_SHORT).show()
                hideAttachmentOptions()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bouton sondage non trouvé")
        }

        try {
            cameraButton.setOnClickListener {
                checkCameraPermissionAndOpen()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bouton caméra non trouvé")
        }

        try {
            galleryButton.setOnClickListener {
                openGallery()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bouton galerie non trouvé")
        }

        try {
            videoButton.setOnClickListener {
                openVideoPicker()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bouton vidéo non trouvé")
        }
    }

    // ✅ AJOUT: Gestion de l'affichage des options d'attachement
    private fun toggleAttachmentOptions() {
        if (isAttachmentOptionsVisible) {
            hideAttachmentOptions()
        } else {
            showAttachmentOptions()
        }
    }

    private fun showAttachmentOptions() {
        isAttachmentOptionsVisible = true

        // Changer l'icône en croix
        attachButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)

        // Animer l'apparition de la zone d'options
        attachmentOptionsContainer.visibility = View.VISIBLE
        attachmentOptionsContainer.alpha = 0f
        attachmentOptionsContainer.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
    }

    private fun hideAttachmentOptions() {
        isAttachmentOptionsVisible = false

        // Remettre l'icône +
        attachButton.setImageResource(android.R.drawable.ic_menu_add)

        // Animer la disparition de la zone d'options
        attachmentOptionsContainer.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                attachmentOptionsContainer.visibility = View.GONE
            }
            .start()
    }

    // ✅ AJOUT: Méthodes pour ouvrir les sélecteurs de fichiers
    private fun openFilePicker() {
        hideAttachmentOptions()
        fileLauncher.launch("*/*")
    }

    private fun openGallery() {
        hideAttachmentOptions()
        galleryLauncher.launch("image/*")
    }

    private fun openVideoPicker() {
        hideAttachmentOptions()
        galleryLauncher.launch("video/*")
    }

    private fun checkCameraPermissionAndOpen() {
        hideAttachmentOptions()
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

    // ✅ AJOUT: Création d'un fichier temporaire pour la photo
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

    // ✅ AJOUT: Gestion des fichiers sélectionnés
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

    // ✅ AJOUT: Récupération du nom de fichier
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

    // ✅ AJOUT: Upload de fichier vers le canal
    private fun uploadFile(file: File, description: String) {
        val token = requireActivity().getSharedPreferences("SupChatPrefs", Context.MODE_PRIVATE)
            .getString("auth_token", "") ?: ""

        if (token.isEmpty()) {
            Toast.makeText(context, "Session expirée", Toast.LENGTH_SHORT).show()
            return
        }

        if (workspaceId.isNullOrEmpty() || canalId.isEmpty()) {
            Toast.makeText(context, "Erreur: Informations manquantes", Toast.LENGTH_SHORT).show()
            return
        }

        val maxSize = 20 * 1024 * 1024
        if (file.length() > maxSize) {
            Toast.makeText(context, "Fichier trop volumineux (max 20MB)", Toast.LENGTH_LONG).show()
            return
        }

        progressBar.visibility = View.VISIBLE

        // ✅ UTILISER LA NOUVELLE MÉTHODE POUR LES CANAUX
        ApiClient.uploadFileToCanal(token, workspaceId!!, canalId, file, description)
            .enqueue(object : Callback<MessagesResponse> {
                override fun onResponse(
                    call: Call<MessagesResponse>,
                    response: Response<MessagesResponse>
                ) {
                    progressBar.visibility = View.GONE
                    if (!isAdded) return

                    if (response.isSuccessful) {
                        Toast.makeText(context, "Fichier envoyé avec succès", Toast.LENGTH_SHORT).show()
                        chargerMessages()

                        if (file.path.contains(requireContext().cacheDir.path)) {
                            file.delete()
                        }
                    } else {
                        val errorMessage = when (response.code()) {
                            413 -> "Fichier trop volumineux"
                            415 -> "Type de fichier non supporté"
                            400 -> "Données invalides"
                            404 -> "Canal non trouvé"
                            else -> "Erreur lors de l'envoi (${response.code()})"
                        }
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<MessagesResponse>, t: Throwable) {
                    progressBar.visibility = View.GONE
                    if (!isAdded) return
                    Toast.makeText(context, "Erreur réseau: ${t.message}", Toast.LENGTH_LONG).show()
                }
            })
    }

    /**
     * Charge les messages du canal depuis l'API
     */
    private fun chargerMessages() {
        try {
            // Vérifier si le fragment est toujours attaché à l'activité
            if (!isAdded) {
                Log.w(TAG, "Fragment n'est plus attaché à l'activité")
                return
            }

            // Afficher l'indicateur de progression
            progressBar.visibility = View.VISIBLE

            // Récupérer le token d'authentification
            val token = requireActivity().getSharedPreferences(
                "SupChatPrefs",
                Context.MODE_PRIVATE
            ).getString("auth_token", "") ?: ""

            // Vérifier les paramètres nécessaires
            if (token.isEmpty()) {
                Log.e(TAG, "Token d'authentification manquant")
                progressBar.visibility = View.GONE
                (activity as? HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
                return
            }

            if (workspaceId.isNullOrEmpty() || canalId.isEmpty()) {
                Log.e(TAG, "IDs manquants - workspaceId: $workspaceId, canalId: $canalId")
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Erreur: Identifiants manquants", Toast.LENGTH_SHORT).show()
                return
            }

            Log.d(TAG, "Chargement des messages - workspaceId: $workspaceId, canalId: $canalId")

            // Appeler l'API pour récupérer les messages
            ApiClient.getMessagesFromCanal(token, workspaceId!!, canalId)
                .enqueue(object : Callback<MessagesResponse> {
                    override fun onResponse(
                        call: Call<MessagesResponse>,
                        response: Response<MessagesResponse>
                    ) {
                        progressBar.visibility = View.GONE

                        if (!isAdded) return // Vérifier si le fragment est encore attaché

                        if (response.isSuccessful) {
                            val messages = response.body()?.data?.messages

                            if (messages != null && messages.isNotEmpty()) {
                                Log.d(TAG, "Messages récupérés: ${messages.size}")
                                messageAdapter.updateMessages(messages)
                                scrollToBottom()
                            } else {
                                Log.d(TAG, "Aucun message disponible")
                                Toast.makeText(
                                    context,
                                    "Aucun message dans ce canal",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            handleApiError(response)
                        }
                    }

                    override fun onFailure(call: Call<MessagesResponse>, t: Throwable) {
                        progressBar.visibility = View.GONE

                        if (!isAdded) return

                        Log.e(TAG, "Erreur lors du chargement des messages", t)
                        Toast.makeText(
                            context,
                            "Erreur de connexion: ${t.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                })
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            Log.e(TAG, "Exception lors du chargement des messages", e)

            if (isAdded) {
                Toast.makeText(context, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Envoie un message ou une réponse
     */
    private fun sendMessage() {
        try {
            val messageText = messageInput.text.toString().trim()
            if (messageText.isEmpty()) return

            // Obtenir le token
            val token = requireActivity().getSharedPreferences(
                "SupChatPrefs",
                Context.MODE_PRIVATE
            ).getString("auth_token", "") ?: ""

            // Vérifier que le token est valide
            if (token.isEmpty()) {
                Log.e(TAG, "Token d'authentification manquant")
                Toast.makeText(
                    context,
                    "Session expirée, veuillez vous reconnecter",
                    Toast.LENGTH_SHORT
                ).show()
                (activity as? HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
                return
            }

            // Vérifier que nous avons les ID nécessaires
            if (workspaceId.isNullOrEmpty() || canalId.isEmpty()) {
                Log.e(TAG, "IDs manquants - workspaceId: $workspaceId, canalId: $canalId")
                Toast.makeText(
                    context,
                    "Erreur: Impossible d'envoyer le message",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            // Afficher un indicateur visuel de chargement
            progressBar.visibility = View.VISIBLE

            // Vider le champ de saisie
            val originalText = messageText
            messageInput.text.clear()

            // Vérifier si c'est une réponse à un message
            if (replyToMessageId != null) {
                // Envoyer la réponse
                ApiClient.replyToMessage(
                    token,
                    workspaceId!!,
                    canalId,
                    replyToMessageId!!,
                    messageText
                )
                    .enqueue(object : Callback<MessagesResponse> {
                        override fun onResponse(
                            call: Call<MessagesResponse>,
                            response: Response<MessagesResponse>
                        ) {
                            progressBar.visibility = View.GONE

                            if (response.isSuccessful) {
                                // Réinitialiser le mode réponse
                                cancelReply()

                                // Rafraîchir les messages
                                chargerMessages()

                                // Notification de succès
                                Toast.makeText(
                                    context,
                                    "Réponse envoyée",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                handleApiError(response)

                                // Remettre le texte en cas d'échec
                                messageInput.setText(originalText)
                            }
                        }

                        override fun onFailure(call: Call<MessagesResponse>, t: Throwable) {
                            progressBar.visibility = View.GONE
                            Log.e(TAG, "Échec de l'envoi de la réponse", t)

                            Toast.makeText(
                                context,
                                "Erreur: ${t.message}",
                                Toast.LENGTH_SHORT
                            ).show()

                            // Remettre le texte en cas d'échec
                            messageInput.setText(originalText)
                        }
                    })
            } else {
                // Envoyer un message normal
                ApiClient.sendMessage(token, workspaceId!!, canalId, messageText)
                    .enqueue(object : Callback<MessagesResponse> {
                        override fun onResponse(
                            call: Call<MessagesResponse>,
                            response: Response<MessagesResponse>
                        ) {
                            progressBar.visibility = View.GONE

                            if (response.isSuccessful) {
                                // Rafraîchir les messages
                                chargerMessages()
                            } else {
                                handleApiError(response)

                                // Remettre le texte en cas d'échec
                                messageInput.setText(originalText)
                            }
                        }

                        override fun onFailure(call: Call<MessagesResponse>, t: Throwable) {
                            progressBar.visibility = View.GONE
                            Log.e(TAG, "Échec de l'envoi du message", t)

                            Toast.makeText(
                                context,
                                "Erreur: ${t.message}",
                                Toast.LENGTH_SHORT
                            ).show()

                            // Remettre le texte en cas d'échec
                            messageInput.setText(originalText)
                        }
                    })
            }
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            Log.e(TAG, "Exception lors de l'envoi du message", e)

            if (isAdded) {
                Toast.makeText(context, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Annule la réponse en cours
     */
    private fun cancelReply() {
        replyToMessageId = null
        replyToMessageAuthor = null
        replyInfoBar.visibility = View.GONE
        messageInput.hint = "Écrivez un message..."
    }

    /**
     * Met à jour l'interface pour montrer qu'on répond à un message
     */
    private fun updateReplyUi(isReplying: Boolean) {
        if (isReplying && replyToMessageAuthor != null) {
            // Afficher la barre d'information de réponse
            replyInfoBar.visibility = View.VISIBLE
            replyInfoText.text = "Réponse à ${replyToMessageAuthor}"

            // Modifier le hint du champ de saisie
            messageInput.hint = "Répondre à ${replyToMessageAuthor}..."
        } else {
            // Masquer la barre d'information et remettre le hint par défaut
            replyInfoBar.visibility = View.GONE
            messageInput.hint = "Écrivez un message..."
        }
    }

    /**
     * Fait défiler la liste jusqu'au dernier message
     */
    private fun scrollToBottom() {
        try {
            if (recyclerView.adapter?.itemCount ?: 0 > 0) {
                recyclerView.smoothScrollToPosition((recyclerView.adapter?.itemCount ?: 1) - 1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du défilement", e)
        }
    }

    // Méthodes pour le rafraîchissement automatique
    private fun startAutoRefresh() {
        isAutoRefreshEnabled = true
        scheduleNextRefresh()
    }

    private fun scheduleNextRefresh() {
        if (!isAutoRefreshEnabled || !isAdded) return

        autoRefreshHandler.postDelayed({
            if (isAutoRefreshEnabled && isAdded) {
                chargerMessages()
                scheduleNextRefresh()
            }
        }, AUTO_REFRESH_INTERVAL)
    }

    private fun stopAutoRefresh() {
        isAutoRefreshEnabled = false
        autoRefreshHandler.removeCallbacksAndMessages(null)
    }

    /**
     * Gestion des erreurs API
     */
    private fun handleApiError(response: Response<*>) {
        if (!isAdded) return

        when (response.code()) {
            401 -> {
                // Rediriger vers la page de connexion
                Toast.makeText(context, "Session expirée", Toast.LENGTH_SHORT).show()
                (activity as? HomeActivity)?.redirectToLogin("Session expirée")
            }

            403 -> {
                Toast.makeText(context, "Accès refusé", Toast.LENGTH_SHORT).show()
            }

            404 -> {
                Toast.makeText(context, "Ressource introuvable", Toast.LENGTH_SHORT).show()
            }

            else -> {
                // Tenter de récupérer le message d'erreur du corps de la réponse
                val errorMessage = try {
                    response.errorBody()?.string() ?: "Erreur: ${response.code()}"
                } catch (e: Exception) {
                    "Erreur: ${response.code()}"
                }

                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Implémentation des méthodes de l'interface MessageActionListener

    /**
     * Appelé quand l'utilisateur veut modifier un message
     */
    override fun onEditMessage(message: Message) {
        Log.d(TAG, "Modifier le message: ${message.id}")

        // Créer un EditText pour la saisie
        val editText = EditText(context).apply {
            setText(message.contenu)
            setPadding(30, 20, 30, 20)
            setHint("Modifier votre message")
            setHintTextColor(resources.getColor(android.R.color.darker_gray, null))
            setTextColor(resources.getColor(android.R.color.black, null))
        }

        // Créer le conteneur
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 10, 20, 10)
            addView(
                editText, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        // Afficher la boîte de dialogue
        AlertDialog.Builder(requireContext())
            .setTitle("Modifier le message")
            .setView(container)
            .setPositiveButton("Enregistrer") { _, _ ->
                val newContent = editText.text.toString().trim()
                if (newContent.isNotEmpty() && newContent != message.contenu) {
                    updateMessage(message.id, newContent)
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    /**
     * Appelé quand l'utilisateur veut supprimer un message
     */
    override fun onDeleteMessage(message: Message) {
        Log.d(TAG, "Supprimer le message: ${message.id}")

        AlertDialog.Builder(requireContext())
            .setTitle("Supprimer le message")
            .setMessage("Êtes-vous sûr de vouloir supprimer ce message ?")
            .setPositiveButton("Oui") { _, _ ->
                deleteMessage(message.id)
            }
            .setNegativeButton("Non", null)
            .show()
    }

    override fun onReactToMessage(message: Message, emoji: String) {
        Log.d("ChatFragment", "🎭 Réaction demandée: $emoji sur message ${message.id}")

        val token = requireActivity().getSharedPreferences(
            "SupChatPrefs",
            Context.MODE_PRIVATE
        ).getString("auth_token", "") ?: ""

        if (token.isEmpty()) {
            Toast.makeText(context, "Session expirée", Toast.LENGTH_SHORT).show()
            return
        }

        if (workspaceId.isNullOrEmpty() || canalId.isEmpty()) {
            Toast.makeText(context, "Erreur: Informations manquantes", Toast.LENGTH_SHORT).show()
            return
        }

        ApiClient.addReaction(token, workspaceId!!, canalId, message.id, emoji)
            .enqueue(object : Callback<MessagesResponse> {
                override fun onResponse(call: Call<MessagesResponse>, response: Response<MessagesResponse>) {
                    if (response.isSuccessful) {
                        Log.d("ChatFragment", "✅ Réaction ajoutée avec succès")
                        response.body()?.let { messagesResponse ->
                            Log.d("ChatFragment", "📦 Messages reçus: ${messagesResponse.data.messages.size}")

                            requireActivity().runOnUiThread {
                                messageAdapter.updateMessages(messagesResponse.data.messages)
                                Log.d("ChatFragment", "🔄 Adaptateur mis à jour - réaction ajoutée")
                            }
                        }
                    } else {
                        Log.e("ChatFragment", "❌ Erreur réaction: ${response.code()} - ${response.message()}")

                        requireActivity().runOnUiThread {
                            Toast.makeText(context, "Erreur lors de l'ajout de la réaction", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onFailure(call: Call<MessagesResponse>, t: Throwable) {
                    Log.e("ChatFragment", "❌ Échec réaction", t)

                    requireActivity().runOnUiThread {
                        Toast.makeText(context, "Erreur réseau: ${t.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            })
    }

    override fun onReplyToMessage(message: Message) {
        Log.d(TAG, "Répondre au message: ${message.id}")

        // Enregistrer l'ID du message auquel on répond
        replyToMessageId = message.id
        replyToMessageAuthor = message.getNomAuteur()

        // Mettre à jour l'interface
        updateReplyUi(true)

        // Donner le focus au champ de saisie
        messageInput.requestFocus()

        // Informer l'utilisateur
        Toast.makeText(
            context,
            "Réponse à ${message.getNomAuteur()}",
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * Appelé quand l'utilisateur veut voir les réponses à un message
     */
    override fun onViewReplies(message: Message) {
        Log.d(TAG, "Voir les réponses au message: ${message.id}")

        if (message.hasReplies()) {
            // Récupérer le nom d'utilisateur
            val username = requireActivity().getSharedPreferences(
                "SupChatPrefs",
                Context.MODE_PRIVATE
            ).getString("username", "") ?: ""

            // Afficher le dialogue des réponses
            val dialog = RepliesDialog(
                requireContext(),
                message,
                username,
                this
            )

            dialog.show()
        } else {
            Toast.makeText(
                context,
                "Ce message n'a pas de réponses",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Met à jour un message existant
     */
    private fun updateMessage(messageId: String, newContent: String) {
        // Obtenir le token
        val token = requireActivity().getSharedPreferences(
            "SupChatPrefs",
            Context.MODE_PRIVATE
        ).getString("auth_token", "") ?: ""

        if (token.isEmpty()) {
            Toast.makeText(context, "Session expirée", Toast.LENGTH_SHORT).show()
            return
        }

        // Vérifier les IDs
        if (workspaceId.isNullOrEmpty() || canalId.isEmpty()) {
            Toast.makeText(context, "Erreur: Informations manquantes", Toast.LENGTH_SHORT).show()
            return
        }

        // Appeler l'API
        progressBar.visibility = View.VISIBLE

        ApiClient.updateMessage(token, workspaceId!!, canalId, messageId, newContent)
            .enqueue(object : Callback<MessagesResponse> {
                override fun onResponse(
                    call: Call<MessagesResponse>,
                    response: Response<MessagesResponse>
                ) {
                    progressBar.visibility = View.GONE

                    if (response.isSuccessful) {
                        // Rafraîchir les messages
                        chargerMessages()

                        // Informer l'utilisateur
                        Toast.makeText(
                            context,
                            "Message mis à jour",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        handleApiError(response)
                    }
                }

                override fun onFailure(call: Call<MessagesResponse>, t: Throwable) {
                    progressBar.visibility = View.GONE
                    Log.e(TAG, "Échec de la mise à jour du message", t)

                    Toast.makeText(
                        context,
                        "Erreur: ${t.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    /**
     * Supprime un message existant
     */
    private fun deleteMessage(messageId: String) {
        // Obtenir le token d'authentification
        val token = requireActivity().getSharedPreferences(
            "SupChatPrefs",
            Context.MODE_PRIVATE
        ).getString("auth_token", "") ?: ""

        // Vérifier que le token est valide
        if (token.isEmpty()) {
            Toast.makeText(context, "Session expirée", Toast.LENGTH_SHORT).show()
            return
        }

        // Vérifier que nous avons les ID nécessaires
        if (workspaceId.isNullOrEmpty() || canalId.isEmpty()) {
            Toast.makeText(
                context,
                "Erreur: Impossible de supprimer le message",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Afficher l'indicateur de progression
        progressBar.visibility = View.VISIBLE

        // Appeler l'API pour supprimer le message
        ApiClient.deleteMessage(token, workspaceId!!, canalId, messageId)
            .enqueue(object : Callback<MessagesResponse> {
                override fun onResponse(
                    call: Call<MessagesResponse>,
                    response: Response<MessagesResponse>
                ) {
                    // Masquer l'indicateur de progression
                    progressBar.visibility = View.GONE

                    if (response.isSuccessful) {
                        // Notifier l'utilisateur
                        Toast.makeText(context, "Message supprimé", Toast.LENGTH_SHORT).show()

                        // Rafraîchir la liste des messages
                        chargerMessages()
                    } else {
                        // Gérer les erreurs
                        handleApiError(response)
                    }
                }

                override fun onFailure(call: Call<MessagesResponse>, t: Throwable) {
                    // Masquer l'indicateur de progression
                    progressBar.visibility = View.GONE

                    // Log de l'erreur
                    Log.e(TAG, "Échec de l'appel API pour la suppression", t)

                    // Informer l'utilisateur
                    Toast.makeText(
                        context,
                        "Erreur réseau: ${t.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }
}