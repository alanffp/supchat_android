package com.example.supchat.api

import com.example.supchat.models.request.AccountDeleteRequest
import com.example.supchat.models.request.ForgotPasswordRequest
import com.example.supchat.models.request.LoginRequest
import com.example.supchat.models.response.LoginResponse
import com.example.supchat.models.request.MemberRoleUpdateRequest
import com.example.supchat.models.request.Message.MessageRequest
import com.example.supchat.models.request.Message.ReactionRequest
import com.example.supchat.models.request.PasswordUpdateRequest
import com.example.supchat.models.request.PasswordUpdateResponse
import com.example.supchat.models.request.privatemessage.PrivateMessageRequest
import com.example.supchat.models.request.ProfileUpdateRequest
import com.example.supchat.models.request.ProfileUpdateResponse
import com.example.supchat.models.request.Message.ReponseRequest
import com.example.supchat.models.request.SendConversationMessageRequest
import com.example.supchat.models.request.StatusResponse
import com.example.supchat.models.request.StatusUpdateRequest
import com.example.supchat.models.request.ThemeUpdateRequest
import com.example.supchat.models.request.User
import com.example.supchat.models.request.Workspace.WorkspaceAddMemberRequest
import com.example.supchat.models.request.Workspace.WorkspaceCreateRequest
import com.example.supchat.models.request.Workspace.WorkspaceUpdateRequest
import com.example.supchat.models.response.AccountDeleteResponse
import com.example.supchat.models.response.CanauxResponse
import com.example.supchat.models.response.DeconnexionResponse
import com.example.supchat.models.response.InvitationsResponse
import com.example.supchat.models.response.MembersResponse
import com.example.supchat.models.response.MessagesResponse
import com.example.supchat.models.response.PictureUpdateResponse
import com.example.supchat.models.response.ThemeResponse
import com.example.supchat.models.response.UserProfileResponse
import com.example.supchat.models.response.UserSearchResponse
import com.example.supchat.models.response.WorkspacesResponse
import com.example.supchat.models.response.messageprivate.ConversationMessagesResponse
import com.example.supchat.models.response.messageprivate.PrivateMessagesResponse
import com.example.supchat.models.response.notifications.NotificationsResponse
import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("/api/v1/auth/inscription")
    fun registerUser(@Body user: User): Call<Void>

    @POST("/api/v1/auth/connexion")
    fun loginUser(@Body loginRequest: LoginRequest): Call<LoginResponse>

    @POST("/api/v1/auth/mot-de-passe-oublie")
    fun forgotPassword(@Body request: ForgotPasswordRequest): Call<Void>

    // Méthodes d'authentification OAuth
    @GET("/api/v1/auth/google")
    fun initiateGoogleLogin(): Call<Void>

    @GET("/api/v1/auth/facebook")
    fun initiateFacebookLogin(): Call<Void>

    @GET("/api/v1/auth/microsoft")
    fun initiateMicrosoftLogin(): Call<Void>

    @GET("/api/v1/workspaces")
    fun getWorkspacesFromApi(@Header("Authorization") token: String): Call<WorkspacesResponse>

    @GET("/api/v1/workspaces/{workspaceId}/canaux")
    fun getCanauxForWorkspace(
        @Header("Authorization") token: String,
        @Path("workspaceId") workspaceId: String
    ): Call<CanauxResponse>

    @GET("/api/v1/auth/deconnexion")
    fun deconnexion(@Header("Authorization") token: String): Call<DeconnexionResponse>

    @GET("/api/v1/workspaces/{workspaceId}/canaux/{canalId}/messages")
    fun getMessagesFromCanal(
        @Header("Authorization") token: String,
        @Path("workspaceId") workspaceId: String,
        @Path("canalId") canalId: String
    ): Call<MessagesResponse>

    @POST("/api/v1/workspaces/{workspaceId}/canaux/{canalId}/messages")
    fun sendMessage(
        @Header("Authorization") token: String,
        @Path("workspaceId") workspaceId: String,
        @Path("canalId") canalId: String,
        @Body messageRequest: MessageRequest
    ): Call<MessagesResponse>

    @PATCH("/api/v1/workspaces/{workspaceId}/canaux/{canalId}/messages/{messageId}")
    fun updateMessage(
        @Header("Authorization") token: String,
        @Path("workspaceId") workspaceId: String,
        @Path("canalId") canalId: String,
        @Path("messageId") messageId: String,
        @Body messageRequest: MessageRequest
    ): Call<MessagesResponse>
    // Supprimer un message
    @DELETE("api/v1/workspaces/{workspaceId}/canaux/{canalId}/messages/{messageId}")
    fun deleteMessage(
        @Header("Authorization") token: String,
        @Path("workspaceId") workspaceId: String,
        @Path("canalId") canalId: String,
        @Path("messageId") messageId: String
    ): Call<MessagesResponse>

    // Réagir à un message
    @POST("/api/v1/workspaces/{workspaceId}/canaux/{canalId}/messages/{messageId}/reactions")
    fun addReaction(
        @Header("Authorization") token: String,
        @Path("workspaceId") workspaceId: String,
        @Path("canalId") canalId: String,
        @Path("messageId") messageId: String,
        @Body reactionRequest: ReactionRequest
    ): Call<MessagesResponse>

    // Répondre à un message
    @POST("/api/v1/workspaces/{workspaceId}/canaux/{canalId}/messages/{messageId}/reponses")
    fun replyToMessage(
        @Header("Authorization") token: String,
        @Path("workspaceId") workspaceId: String,
        @Path("canalId") canalId: String,
        @Path("messageId") messageId: String,
        @Body reponseRequest: ReponseRequest
    ): Call<MessagesResponse>

    @GET("/api/v1/users/profile")
    fun getUserProfile(@Header("Authorization") token: String): Call<UserProfileResponse>

    @PUT("/api/v1/users/theme")
    fun updateUserTheme(
        @Header("Authorization") token: String,
        @Body themeUpdateRequest: ThemeUpdateRequest
    ): Call<ThemeResponse>

    /**
     * Met à jour le statut de l'utilisateur
     */
    @PUT("/api/v1/users/status")
    fun updateUserStatus(
        @Header("Authorization") token: String,
        @Body statusUpdateRequest: StatusUpdateRequest
    ): Call<StatusResponse>

    /**
     * Met à jour le profil de l'utilisateur (nom d'utilisateur, email)
     */
    @PUT("/api/v1/users/profile")
    fun updateUserProfile(
        @Header("Authorization") token: String,
        @Body profileUpdateRequest: ProfileUpdateRequest
    ): Call<ProfileUpdateResponse>

    /**
     * Met à jour le mot de passe de l'utilisateur
     */
    @PUT("/api/v1/users/profile/password")
    fun updateUserPassword(
        @Header("Authorization") token: String,
        @Body passwordUpdateRequest: PasswordUpdateRequest
    ): Call<PasswordUpdateResponse>

    /**
     * Met à jour la photo de profil de l'utilisateur
     */
    @Multipart
    @PUT("/api/v1/users/profile/picture")
    fun updateProfilePicture(
        @Header("Authorization") token: String,
        @Part profilePicture: MultipartBody.Part
    ): Call<PictureUpdateResponse>


    @DELETE("/api/v1/users/profile")
    fun deleteUserProfile(
        @Header("Authorization") token: String,
        @Body deleteRequest: AccountDeleteRequest
    ): Call<AccountDeleteResponse>

    @GET("/api/v1/search/users")
    fun searchUsers(
        @Header("Authorization") token: String,
        @Query("q") searchQuery: String
    ): Call<UserSearchResponse>
    @GET("/api/v1/messages/private")
    fun getAllPrivateMessages(
        @Header("Authorization") token: String
    ): Call<ConversationMessagesResponse>

    @GET("/api/v1/messages/private/{userId}")
    fun getPrivateMessagesWithUser(
        @Header("Authorization") token: String,
        @Path("userId") userId: String
    ): Call<ConversationMessagesResponse>

    @POST("/api/v1/messages/private/{userId}")
    fun sendPrivateMessage(
        @Header("Authorization") token: String,
        @Path("userId") userId: String,
        @Body message: PrivateMessageRequest
    ): Call<ConversationMessagesResponse>

    @PATCH("/api/v1/messages/private/{messageId}/read")
    fun markMessageAsRead(
        @Header("Authorization") token: String,
        @Path("messageId") messageId: String
    ): Call<ConversationMessagesResponse>

    @PATCH("/api/v1/messages/private/{messageId}")
    fun updatePrivateMessage(
        @Header("Authorization") token: String,
        @Path("messageId") messageId: String,
        @Body messageRequest: PrivateMessageRequest
    ): Call<ConversationMessagesResponse>

    @DELETE("/api/v1/messages/private/{messageId}")
    fun deletePrivateMessage(
        @Header("Authorization") token: String,
        @Path("messageId") messageId: String
    ): Call<ConversationMessagesResponse>

    @POST("/api/v1/workspaces")
    fun createWorkspace(
        @Header("Authorization") token: String,
        @Body workspaceRequest: WorkspaceCreateRequest
    ): Call<WorkspacesResponse>

    @PATCH("/api/v1/workspaces/{id}")
    fun updateWorkspace(
        @Header("Authorization") token: String,
        @Path("id") workspaceId: String,
        @Body workspaceRequest: WorkspaceUpdateRequest
    ): Call<WorkspacesResponse>

    @DELETE("/api/v1/workspaces/{id}")
    fun deleteWorkspace(
        @Header("Authorization") token: String,
        @Path("id") workspaceId: String
    ): Call<WorkspacesResponse>

    @POST("/api/v1/workspaces/{id}/membres")
    fun addWorkspaceMember(
        @Header("Authorization") token: String,
        @Path("id") workspaceId: String,
        @Body addMemberRequest: WorkspaceAddMemberRequest
    ): Call<WorkspacesResponse>

    @DELETE("/api/v1/workspaces/{id}/membres/{membreId}")
    fun removeWorkspaceMember(
        @Header("Authorization") token: String,
        @Path("id") workspaceId: String,
        @Path("membreId") membreId: String
    ): Call<WorkspacesResponse>

    @PATCH("/api/v1/workspaces/{id}/membres/{membreId}/role")
    fun updateMemberRole(
        @Header("Authorization") token: String,
        @Path("id") workspaceId: String,
        @Path("membreId") membreId: String,
        @Body roleUpdateRequest: MemberRoleUpdateRequest
    ): Call<WorkspacesResponse>

    @DELETE("/api/v1/workspaces/{id}/invitations/{token}")
    fun revokeInvitation(
        @Header("Authorization") token: String,
        @Path("id") workspaceId: String,
        @Path("token") invitationToken: String
    ): Call<WorkspacesResponse>

    @POST("/api/v1/workspaces/{id}/inviter/{userId}")
    fun inviteUserByEmail(
        @Header("Authorization") token: String,
        @Path("id") workspaceId: String,
        @Path("userId") userId: String
    ): Call<WorkspacesResponse>

    @POST("/api/v1/workspaces/{id}/membres")
    fun getWorkspaceMembers(
        @Header("Authorization") token: String,
        @Path("id") workspaceId: String
    ): Call<MembersResponse>

    @GET("/api/v1/workspaces/{Id}/invitations")
    fun getWorkspaceInvitations(
        @Header("Authorization") token: String,
        @Path("Id") workspaceId: String
    ): Call<InvitationsResponse>

    @GET("/api/v1/workspaces/recherche/public")
    fun searchPublicWorkspaces(
        @Header("Authorization") token: String,
        @Query("query") searchQuery: String
    ): Call<WorkspacesResponse>

    @GET("/api/v1/workspaces/{id}")
    fun getWorkspaceById(
        @Header("Authorization") token: String,
        @Path("id") workspaceId: String
    ): Call<WorkspacesResponse>

    @DELETE("/api/v1/workspaces/{id}/quitter")
    fun leaveWorkspace(
        @Header("Authorization") token: String,
        @Path("id") workspaceId: String
    ): Call<WorkspacesResponse>

    @GET("/api/v1/conversations")
    fun getConversations(
        @Header("Authorization") token: String
    ): Call<ConversationMessagesResponse>

    @GET("/api/v1/messages/private")
    fun getPrivateMessages(
        @Header("Authorization") token: String
    ): Call<PrivateMessagesResponse>


    @GET("/api/v1/conversations/{id}/messages")
    fun getConversationMessages(
        @Header("Authorization") token: String,
        @Path("id") conversationId: String
    ): Call<ConversationMessagesResponse>

    @GET("/api/v1/messages/private/files/{conversationId}")
    fun getConversationFiles(
        @Header("Authorization") token: String,
        @Path("conversationId") conversationId: String
    ): Call<ConversationMessagesResponse>

    @POST("/api/v1/conversations/{id}/messages")
    fun sendConversationMessage(
        @Header("Authorization") token: String,
        @Path("id") conversationId: String,
        @Body request: SendConversationMessageRequest
    ): Call<ConversationMessagesResponse>

    @POST("/api/v1/conversations/{id}/messages/{messageId}/read")
    fun markConversationMessageAsRead(
        @Header("Authorization") token: String,
        @Path("id") conversationId: String,
        @Path("messageId") messageId: String
    ): Call<ConversationMessagesResponse>

    @PUT("/api/v1/conversations/{id}/messages/{messageId}")
    fun updateConversationMessage(
        @Header("Authorization") token: String,
        @Path("id") conversationId: String,
        @Path("messageId") messageId: String,
        @Body request: SendConversationMessageRequest
    ): Call<ConversationMessagesResponse>

    @DELETE("/api/v1/conversations/{id}/messages/{messageId}")
    fun deleteConversationMessage(
        @Header("Authorization") token: String,
        @Path("id") conversationId: String,
        @Path("messageId") messageId: String
    ): Call<ConversationMessagesResponse>

    @GET("/api/notifications")
    fun getNotifications(
        @Header("Authorization") token: String
    ): Call<NotificationsResponse>

    @PUT("/api/notifications/{id}/read")
    fun markNotificationAsRead(
        @Header("Authorization") token: String,
        @Path("id") notificationId: String
    ): Call<NotificationsResponse>

    @PUT("/api/notifications/read-all")
    fun markAllNotificationsAsRead(
        @Header("Authorization") token: String
    ): Call<NotificationsResponse>

    @Multipart
    @POST("/api/v1/fichiers/conversation/{conversationId}")
    fun uploadFileToConversation(
        @Header("Authorization") token: String,
        @Path("conversationId") conversationId: String,
        @Part fichier: MultipartBody.Part,
        @Part("contenu") contenu: RequestBody? = null,
        @Part("messageId") messageId: RequestBody? = null
    ): Call<ConversationMessagesResponse>
}

