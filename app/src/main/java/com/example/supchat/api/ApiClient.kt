package com.example.supchat.api

import com.example.supchat.api.deserializer.SafeConversationDetailsResponseDeserializer
import com.example.supchat.api.deserializer.SafeConversationMessagesResponseDeserializer
import com.example.supchat.api.deserializer.SafeNotificationCountResponseDeserializer
import com.example.supchat.api.deserializer.SafeNotificationsResponseDeserializer
import com.example.supchat.api.deserializer.SafePrivateMessagesResponseDeserializer
import com.example.supchat.models.request.AccountDeleteRequest
import com.example.supchat.models.request.AddParticipantRequest
import com.example.supchat.models.request.CreateConversationRequest
import com.example.supchat.models.request.MemberRoleUpdateRequest
import com.example.supchat.models.request.Message.MessageRequest
import com.example.supchat.models.request.Message.ReactionRequest
import com.example.supchat.models.request.PasswordUpdateRequest
import com.example.supchat.models.request.PasswordUpdateResponse
import com.example.supchat.models.request.privatemessage.PrivateMessageRequest
import com.example.supchat.models.request.ProfileUpdateRequest
import com.example.supchat.models.request.ProfileUpdateResponse
import com.example.supchat.models.request.Message.ReponseRequest
import com.example.supchat.models.request.OAuthCallbackRequest
import com.example.supchat.models.request.SendConversationMessageRequest
import com.example.supchat.models.request.StatusResponse
import com.example.supchat.models.request.StatusUpdateRequest
import com.example.supchat.models.request.ThemeUpdateRequest
import com.example.supchat.models.request.Workspace.WorkspaceAddMemberRequest
import com.example.supchat.models.request.Workspace.WorkspaceCreateRequest
import com.example.supchat.models.request.Workspace.WorkspaceUpdateRequest
import com.example.supchat.models.response.AccountDeleteResponse
import com.example.supchat.models.response.CanauxResponse
import com.example.supchat.models.response.DeconnexionResponse
import com.example.supchat.models.response.InvitationsResponse
import com.example.supchat.models.response.LoginResponse
import com.example.supchat.models.response.MembersResponse
import com.example.supchat.models.response.MessagesResponse
import com.example.supchat.models.response.PictureUpdateResponse
import com.example.supchat.models.response.ThemeResponse
import com.example.supchat.models.response.UserProfileResponse
import com.example.supchat.models.response.UserSearchResponse
import com.example.supchat.models.response.WorkspacesResponse
import com.example.supchat.models.response.messageprivate.ConversationDetailsResponse
import com.example.supchat.models.response.messageprivate.ConversationMessagesResponse
import com.example.supchat.models.response.messageprivate.PrivateMessagesResponse
import com.example.supchat.models.response.notifications.NotificationCountResponse
import com.example.supchat.models.response.notifications.NotificationsResponse
import com.google.gson.GsonBuilder
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val TAG = "ApiClient"
    private const val BASE_URL = "http://10.0.2.2:3000"

    val instance: ApiService by lazy {
        // ✅ Configuration Gson avec tous les désérialiseurs
        val gson = GsonBuilder()
            .setLenient()
            .registerTypeAdapter(
                ConversationMessagesResponse::class.java,
                SafeConversationMessagesResponseDeserializer()
            )
            .registerTypeAdapter(
                PrivateMessagesResponse::class.java,
                SafePrivateMessagesResponseDeserializer()
            )
            .registerTypeAdapter(
                ConversationDetailsResponse::class.java,
                SafeConversationDetailsResponseDeserializer()
            )
            // ✅ AJOUT: Désérialiseur pour les workspaces
            .registerTypeAdapter(
                WorkspacesResponse::class.java,
                SafeWorkspacesResponseDeserializer()
            )
            // ✅ Désérialiseurs pour les notifications (si vous les gardez)
            .registerTypeAdapter(
                NotificationsResponse::class.java,
                SafeNotificationsResponseDeserializer()
            )
            .registerTypeAdapter(
                NotificationCountResponse::class.java,
                SafeNotificationCountResponseDeserializer()
            )
            .create()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(okHttpClient())
            .build()
            .create(ApiService::class.java)
    }

    private fun okHttpClient(): OkHttpClient {
        val loggingInterceptor = okhttp3.logging.HttpLoggingInterceptor().apply {
            level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithAuth = originalRequest.newBuilder()
                    .header("Content-Type", "application/json")
                    .build()
                chain.proceed(requestWithAuth)
            }
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // === WORKSPACES ===
    fun getWorkspaces(token: String): Call<WorkspacesResponse> {
        return instance.getWorkspacesFromApi("Bearer $token")
    }

    fun getCanauxForWorkspace(token: String, workspaceId: String): Call<CanauxResponse> {
        return instance.getCanauxForWorkspace("Bearer $token", workspaceId)
    }

    fun createWorkspace(
        token: String,
        name: String,
        description: String? = null,
        visibilite: String = "public"
    ): Call<WorkspacesResponse> {
        val workspaceRequest = WorkspaceCreateRequest(name, description, visibilite)
        return instance.createWorkspace("Bearer $token", workspaceRequest)
    }

    fun updateWorkspace(
        token: String,
        workspaceId: String,
        name: String? = null,
        description: String? = null
    ): Call<WorkspacesResponse> {
        val workspaceRequest = WorkspaceUpdateRequest(name, description)
        return instance.updateWorkspace("Bearer $token", workspaceId, workspaceRequest)
    }

    fun deleteWorkspace(token: String, workspaceId: String): Call<WorkspacesResponse> {
        return instance.deleteWorkspace("Bearer $token", workspaceId)
    }

    fun addWorkspaceMember(
        token: String,
        workspaceId: String,
        utilisateurId: String,
        role: String = "membre"
    ): Call<WorkspacesResponse> {
        val addMemberRequest = WorkspaceAddMemberRequest(utilisateurId, role)
        return instance.addWorkspaceMember("Bearer $token", workspaceId, addMemberRequest)
    }

    fun removeWorkspaceMember(
        token: String,
        workspaceId: String,
        membreId: String
    ): Call<WorkspacesResponse> {
        return instance.removeWorkspaceMember("Bearer $token", workspaceId, membreId)
    }

    fun updateMemberRole(
        token: String,
        workspaceId: String,
        membreId: String,
        role: String
    ): Call<WorkspacesResponse> {
        val roleUpdateRequest = MemberRoleUpdateRequest(role)
        return instance.updateMemberRole("Bearer $token", workspaceId, membreId, roleUpdateRequest)
    }

    fun getWorkspaceMembers(token: String, workspaceId: String): Call<MembersResponse> {
        return instance.getWorkspaceMembers("Bearer $token", workspaceId)
    }

    fun getWorkspaceInvitations(token: String, workspaceId: String): Call<InvitationsResponse> {
        return instance.getWorkspaceInvitations("Bearer $token", workspaceId)
    }

    fun revokeInvitation(
        token: String,
        workspaceId: String,
        invitationToken: String
    ): Call<WorkspacesResponse> {
        return instance.revokeInvitation("Bearer $token", workspaceId, invitationToken)
    }

    fun inviteUserByEmail(
        token: String,
        workspaceId: String,
        userId: String
    ): Call<WorkspacesResponse> {
        return instance.inviteUserByEmail("Bearer $token", workspaceId, userId)
    }

    fun searchPublicWorkspaces(token: String, query: String): Call<WorkspacesResponse> {
        return instance.searchPublicWorkspaces("Bearer $token", query)
    }

    fun getWorkspaceById(token: String, workspaceId: String): Call<WorkspacesResponse> {
        return instance.getWorkspaceById("Bearer $token", workspaceId)
    }

    fun leaveWorkspace(token: String, workspaceId: String): Call<WorkspacesResponse> {
        return instance.leaveWorkspace("Bearer $token", workspaceId)
    }

    // === MESSAGES (CANAUX) ===
    fun getMessagesFromCanal(
        token: String,
        workspaceId: String,
        canalId: String
    ): Call<MessagesResponse> {
        return instance.getMessagesFromCanal("Bearer $token", workspaceId, canalId)
    }

    fun sendMessage(
        token: String,
        workspaceId: String,
        canalId: String,
        messageContent: String
    ): Call<MessagesResponse> {
        val messageRequest = MessageRequest(messageContent)
        return instance.sendMessage("Bearer $token", workspaceId, canalId, messageRequest)
    }

    fun updateMessage(
        token: String,
        workspaceId: String,
        canalId: String,
        messageId: String,
        newContent: String
    ): Call<MessagesResponse> {
        val messageRequest = MessageRequest(newContent)
        return instance.updateMessage(
            "Bearer $token",
            workspaceId,
            canalId,
            messageId,
            messageRequest
        )
    }

    fun deleteMessage(
        token: String,
        workspaceId: String,
        canalId: String,
        messageId: String
    ): Call<MessagesResponse> {
        return instance.deleteMessage("Bearer $token", workspaceId, canalId, messageId)
    }

    fun addReaction(
        token: String,
        workspaceId: String,
        canalId: String,
        messageId: String,
        emoji: String
    ): Call<MessagesResponse> {
        val reactionRequest = ReactionRequest(emoji)
        return instance.addReaction(
            "Bearer $token",
            workspaceId,
            canalId,
            messageId,
            reactionRequest
        )
    }

    fun replyToMessage(
        token: String,
        workspaceId: String,
        canalId: String,
        messageId: String,
        content: String
    ): Call<MessagesResponse> {
        val reponseRequest = ReponseRequest(content)
        return instance.replyToMessage(
            "Bearer $token",
            workspaceId,
            canalId,
            messageId,
            reponseRequest
        )
    }

    // === CONVERSATIONS ===
    fun getConversationMessages(
        token: String,
        conversationId: String
    ): Call<ConversationMessagesResponse> {
        return instance.getConversationMessages("Bearer $token", conversationId)
    }

    fun sendConversationMessage(
        token: String,
        conversationId: String,
        contenu: String,
        reponseA: String? = null
    ): Call<ConversationMessagesResponse> {
        val request = SendConversationMessageRequest(contenu, reponseA)
        return instance.sendConversationMessage("Bearer $token", conversationId, request)
    }

    fun updateConversationMessage(
        token: String,
        conversationId: String,
        messageId: String,
        contenu: String
    ): Call<ConversationMessagesResponse> {
        val request = SendConversationMessageRequest(contenu)
        return instance.updateConversationMessage(
            "Bearer $token",
            conversationId,
            messageId,
            request
        )
    }

    fun deleteConversationMessage(
        token: String,
        conversationId: String,
        messageId: String
    ): Call<ConversationMessagesResponse> {
        return instance.deleteConversationMessage("Bearer $token", conversationId, messageId)
    }

    fun markConversationMessageAsRead(
        token: String,
        conversationId: String,
        messageId: String
    ): Call<ConversationMessagesResponse> {
        return instance.markConversationMessageAsRead("Bearer $token", conversationId, messageId)
    }

    // === MESSAGES PRIVÉS ===
    fun getPrivateMessages(token: String): Call<PrivateMessagesResponse> {
        return instance.getPrivateMessages("Bearer $token")
    }

    fun sendPrivateMessage(
        token: String,
        userId: String,
        content: String
    ): Call<ConversationMessagesResponse> {
        val messageRequest = PrivateMessageRequest(content)
        return instance.sendPrivateMessage("Bearer $token", userId, messageRequest)
    }

    fun getPrivateMessagesWithUser(
        token: String,
        userId: String
    ): Call<ConversationMessagesResponse> {
        return instance.getPrivateMessagesWithUser("Bearer $token", userId)
    }

    fun markMessageAsRead(token: String, messageId: String): Call<ConversationMessagesResponse> {
        return instance.markMessageAsRead("Bearer $token", messageId)
    }

    fun updatePrivateMessage(
        token: String,
        messageId: String,
        content: String
    ): Call<ConversationMessagesResponse> {
        val messageRequest = PrivateMessageRequest(content)
        return instance.updatePrivateMessage("Bearer $token", messageId, messageRequest)
    }

    fun deletePrivateMessage(token: String, messageId: String): Call<ConversationMessagesResponse> {
        return instance.deletePrivateMessage("Bearer $token", messageId)
    }

    fun getConversationFiles(
        token: String,
        conversationId: String
    ): Call<ConversationMessagesResponse> {
        return instance.getConversationFiles("Bearer $token", conversationId)
    }

    // === UTILISATEURS ===
    fun getUserProfile(token: String): Call<UserProfileResponse> {
        return instance.getUserProfile("Bearer $token")
    }

    fun updateUserTheme(token: String, theme: String): Call<ThemeResponse> {
        val themeUpdateRequest = ThemeUpdateRequest(theme)
        return instance.updateUserTheme("Bearer $token", themeUpdateRequest)
    }

    fun updateUserStatus(token: String, status: String): Call<StatusResponse> {
        val statusUpdateRequest = StatusUpdateRequest(status)
        return instance.updateUserStatus("Bearer $token", statusUpdateRequest)
    }

    fun updateUserProfile(
        token: String,
        username: String? = null,
        email: String? = null
    ): Call<ProfileUpdateResponse> {
        val profileUpdateRequest = ProfileUpdateRequest(username, email)
        return instance.updateUserProfile("Bearer $token", profileUpdateRequest)
    }

    fun updateUserPassword(
        token: String,
        currentPassword: String,
        newPassword: String,
        confirmPassword: String
    ): Call<PasswordUpdateResponse> {
        val passwordUpdateRequest =
            PasswordUpdateRequest(currentPassword, newPassword, confirmPassword)
        return instance.updateUserPassword("Bearer $token", passwordUpdateRequest)
    }

    fun updateProfilePicture(token: String, imageFile: File): Call<PictureUpdateResponse> {
        // Validation du fichier
        if (!imageFile.exists()) {
            throw IllegalArgumentException("Le fichier n'existe pas")
        }

        if (imageFile.length() == 0L) {
            throw IllegalArgumentException("Le fichier est vide")
        }

        // Taille maximale: 5MB
        if (imageFile.length() > 5 * 1024 * 1024) {
            throw IllegalArgumentException("Le fichier est trop volumineux (max 5MB)")
        }

        // Déterminer le type MIME basé sur l'extension
        val mimeType = when (imageFile.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "image/*"
        }

        val requestFile = imageFile.asRequestBody(mimeType.toMediaTypeOrNull())
        val imagePart = MultipartBody.Part.createFormData(
            "profilePicture", // Nom du champ attendu par l'API
            imageFile.name,
            requestFile
        )

        return instance.updateProfilePicture("Bearer $token", imagePart)
    }

    fun deleteUserProfile(token: String, password: String): Call<AccountDeleteResponse> {
        val deleteRequest = AccountDeleteRequest(password)
        return instance.deleteUserProfile("Bearer $token", deleteRequest)
    }

    fun searchUsers(token: String, query: String): Call<UserSearchResponse> {
        return instance.searchUsers("Bearer $token", query)
    }

    fun deconnexion(token: String): Call<DeconnexionResponse> {
        return instance.deconnexion("Bearer $token")
    }

    // === NOTIFICATIONS ===
    fun getUnreadNotificationCount(token: String): Call<NotificationCountResponse> {
        return instance.getUnreadNotificationCount("Bearer $token")
    }

    fun getNotifications(token: String): Call<NotificationsResponse> {
        return instance.getNotifications("Bearer $token")
    }

    fun markNotificationAsRead(token: String, notificationId: String): Call<NotificationsResponse> {
        return instance.markNotificationAsRead("Bearer $token", notificationId)
    }

    fun markAllNotificationsAsRead(token: String): Call<NotificationsResponse> {
        return instance.markAllNotificationsAsRead("Bearer $token")
    }

    // === FICHIERS POUR CONVERSATIONS ===
    fun uploadFileToConversation(
        token: String,
        conversationId: String,
        file: File,
        content: String? = null,
        messageId: String? = null
    ): Call<ConversationMessagesResponse> {

        val requestFile = file.asRequestBody("*/*".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("fichier", file.name, requestFile)

        val contentPart = content?.let {
            RequestBody.create("text/plain".toMediaTypeOrNull(), it)
        }

        val messageIdPart = messageId?.let {
            RequestBody.create("text/plain".toMediaTypeOrNull(), it)
        }

        return instance.uploadFileToConversation(
            "Bearer $token",
            conversationId,
            filePart,
            contentPart,
            messageIdPart
        )
    }

    fun getConversationDetails(
        token: String,
        conversationId: String
    ): Call<ConversationDetailsResponse> {
        return instance.getConversationDetails("Bearer $token", conversationId)
    }

    fun addParticipantToConversation(
        token: String,
        conversationId: String,
        userId: String
    ): Call<ConversationDetailsResponse> {
        val request = AddParticipantRequest(userId)
        return instance.addParticipantToConversation("Bearer $token", conversationId, request)
    }

    fun removeParticipantFromConversation(
        token: String,
        conversationId: String,
        userId: String
    ): Call<ConversationDetailsResponse> {
        return instance.removeParticipantFromConversation("Bearer $token", conversationId, userId)
    }

    fun leaveConversation(
        token: String,
        conversationId: String
    ): Call<ConversationDetailsResponse> {
        return instance.leaveConversation("Bearer $token", conversationId)
    }

    fun createConversation(
        token: String,
        nom: String? = null,
        participants: List<String>,
        estGroupe: Boolean = false
    ): Call<ConversationDetailsResponse> {
        val request = CreateConversationRequest(nom, participants, estGroupe)
        return instance.createConversation("Bearer $token", request)
    }

    fun getAllConversations(token: String): Call<PrivateMessagesResponse> {
        return instance.getAllConversations("Bearer $token")
    }

    // === OAUTH AUTHENTICATION ===
    fun googleLoginCallback(code: String, state: String? = null): Call<LoginResponse> {
        val request = OAuthCallbackRequest(code, state)
        return instance.googleLoginCallback(request)
    }

    fun facebookLoginCallback(code: String, state: String? = null): Call<LoginResponse> {
        val request = OAuthCallbackRequest(code, state)
        return instance.facebookLoginCallback(request)
    }

    fun microsoftLoginCallback(code: String, state: String? = null): Call<LoginResponse> {
        val request = OAuthCallbackRequest(code, state)
        return instance.microsoftLoginCallback(request)
    }

    // === FICHIERS POUR CANAUX ===
    fun uploadFileToCanal(
        token: String,
        workspaceId: String,
        canalId: String,
        file: File,
        content: String? = null,
        messageId: String? = null
    ): Call<MessagesResponse> {
        val requestFile = file.asRequestBody("*/*".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("fichier", file.name, requestFile)

        val contentPart = content?.let {
            RequestBody.create("text/plain".toMediaTypeOrNull(), it)
        }

        val messageIdPart = messageId?.let {
            RequestBody.create("text/plain".toMediaTypeOrNull(), it)
        }

        return instance.uploadFileToCanal(
            "Bearer $token",
            workspaceId,
            canalId,
            filePart,
            contentPart,
            messageIdPart
        )
    }

    fun getCanalFiles(
        token: String,
        workspaceId: String,
        canalId: String
    ): Call<MessagesResponse> {
        return instance.getCanalFiles("Bearer $token", workspaceId, canalId)
    }
}