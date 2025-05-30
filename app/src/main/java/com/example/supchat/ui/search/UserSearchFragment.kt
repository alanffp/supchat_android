package com.example.supchat.ui.search

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.supchat.R
import com.example.supchat.adapters.UserSearchAdapter
import com.example.supchat.api.ApiClient
import com.example.supchat.models.response.UserSearchData
import com.example.supchat.models.response.UserSearchResponse
import com.example.supchat.ui.home.HomeActivity
import com.example.supchat.ui.chat.PrivateConversationFragment
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Timer
import java.util.TimerTask

class UserSearchFragment : Fragment(), UserSearchAdapter.OnUserClickListener {

    companion object {
        private const val TAG = "UserSearchFragment"
        private const val SEARCH_DELAY_MS = 500L

        fun newInstance(): UserSearchFragment {
            return UserSearchFragment()
        }

        // ✅ NOUVEAU: Pour les invitations avec exclusions
        fun newInstanceForInvitation(excludedUserIds: Set<String>): UserSearchFragment {
            val fragment = UserSearchFragment()
            fragment.excludedUserIds = excludedUserIds
            fragment.isInvitationMode = true
            return fragment
        }
    }

    // ==================== VARIABLES ====================

    // Vues
    private lateinit var searchEditText: TextInputEditText
    private lateinit var resultsInfoText: TextView
    private lateinit var searchResultsRecyclerView: RecyclerView
    private lateinit var searchProgress: ProgressBar
    private lateinit var userSearchAdapter: UserSearchAdapter

    // Logique de recherche
    private var searchTimer: Timer? = null

    // ✅ NOUVEAU: Gestion des modes et callbacks
    private var excludedUserIds: Set<String> = emptySet()
    private var isInvitationMode = false
    private var userSelectedCallback: ((UserSearchData) -> Unit)? = null
    private var userSelectedByIdCallback: ((String) -> Unit)? = null

    // ==================== LIFECYCLE ====================

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_user_search, container, false)

        initViews(view)
        setupRecyclerView()
        setupSearchListeners()
        updateUIForMode()

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchTimer?.cancel()
        searchTimer = null
    }

    // ==================== INITIALISATION ====================

    private fun initViews(view: View) {
        searchEditText = view.findViewById(R.id.search_edit_text)
        resultsInfoText = view.findViewById(R.id.results_info_text)
        searchResultsRecyclerView = view.findViewById(R.id.search_results_recycler_view)
        searchProgress = view.findViewById(R.id.search_progress)
    }

    private fun setupRecyclerView() {
        searchResultsRecyclerView.layoutManager = LinearLayoutManager(context)
        userSearchAdapter = UserSearchAdapter(requireContext(), onUserClickListener = this)
        searchResultsRecyclerView.adapter = userSearchAdapter
    }

    private fun setupSearchListeners() {
        // Recherche à l'appui sur "recherche"
        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val searchText = searchEditText.text.toString().trim()
                if (searchText.isNotEmpty()) {
                    performSearch(searchText)
                }
                return@setOnEditorActionListener true
            }
            false
        }

        // Recherche avec délai pendant la frappe
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchTimer?.cancel()

                val searchText = s.toString().trim()
                if (searchText.isEmpty()) {
                    updateEmptyState()
                    return
                }

                searchTimer = Timer()
                searchTimer?.schedule(object : TimerTask() {
                    override fun run() {
                        activity?.runOnUiThread {
                            if (isAdded) {
                                performSearch(searchText)
                            }
                        }
                    }
                }, SEARCH_DELAY_MS)
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun updateUIForMode() {
        if (isInvitationMode) {
            resultsInfoText.text = "Recherchez un utilisateur à inviter dans la conversation"
        } else {
            resultsInfoText.text = "Entrez un terme de recherche ci-dessus"
        }
    }

    private fun updateEmptyState() {
        if (isInvitationMode) {
            resultsInfoText.text = "Recherchez un utilisateur à inviter dans la conversation"
        } else {
            resultsInfoText.text = "Entrez un terme de recherche ci-dessus"
        }
        userSearchAdapter.updateUsers(emptyList())
    }

    // ==================== MÉTHODES PUBLIQUES ====================

    // ✅ Pour exclure des utilisateurs (utilisateurs déjà dans la conversation)
    fun setExcludedUsers(userIds: Set<String>) {
        excludedUserIds = userIds
        isInvitationMode = true
        updateUIForMode()
    }

    // ✅ Pour définir le callback de sélection (objet complet)
    fun setOnUserSelectedListener(listener: (UserSearchData) -> Unit) {
        userSelectedCallback = listener
        isInvitationMode = true
        updateUIForMode()
    }

    // ✅ Pour définir le callback de sélection (ID seulement)
    fun setOnUserSelectedByIdListener(listener: (String) -> Unit) {
        userSelectedByIdCallback = listener
        isInvitationMode = true
        updateUIForMode()
    }

    // ==================== RECHERCHE ====================

    private fun performSearch(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.length < 2) {
            resultsInfoText.text = "Veuillez entrer au moins 2 caractères"
            userSearchAdapter.updateUsers(emptyList())
            return
        }

        val token = getAuthToken()
        if (token.isNullOrEmpty()) {
            handleAuthError()
            return
        }

        showLoading()

        Log.d(TAG, "Recherche: '$trimmedQuery' (Mode invitation: $isInvitationMode)")

        ApiClient.searchUsers(token, trimmedQuery)
            .enqueue(object : Callback<UserSearchResponse> {
                override fun onResponse(
                    call: Call<UserSearchResponse>,
                    response: Response<UserSearchResponse>
                ) {
                    if (!isAdded) return

                    hideLoading()
                    handleSearchResponse(response, trimmedQuery)
                }

                override fun onFailure(call: Call<UserSearchResponse>, t: Throwable) {
                    if (!isAdded) return

                    hideLoading()
                    handleSearchError(t)
                }
            })
    }


    private fun handleSearchResponse(response: Response<UserSearchResponse>, query: String) {
        try {
            Log.d(TAG, "Code de réponse: ${response.code()}")

            if (response.isSuccessful) {
                // ✅ CORRECTION: Accéder à response.body()?.data?.users pour obtenir la liste
                val userList: List<UserSearchData> = response.body()?.data?.users ?: emptyList()
                Log.d(TAG, "Utilisateurs trouvés: ${userList.size}")

                // ✅ CORRECTION: Vérifier isNotEmpty() sur la liste typée
                if (userList.isNotEmpty()) {
                    val filteredUsers = filterUsers(userList)
                    updateResults(filteredUsers, userList.size, query)
                } else {
                    showNoResults(query)
                }
            } else {
                handleApiError(response)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception lors du traitement de la réponse", e)
            resultsInfoText.text = "Erreur lors du traitement des résultats"
        }
    }

    private fun filterUsers(userList: List<UserSearchData>): List<UserSearchData> {
        return if (excludedUserIds.isEmpty()) {
            userList
        } else {
            userList.filter { user -> !excludedUserIds.contains(user.id) }
        }
    }

    private fun updateResults(filteredUsers: List<UserSearchData>, totalFound: Int, query: String) {
        userSearchAdapter.updateUsers(filteredUsers)

        val message = when {
            filteredUsers.isEmpty() && excludedUserIds.isNotEmpty() -> {
                "Tous les utilisateurs trouvés sont déjà dans la conversation"
            }
            filteredUsers.size < totalFound -> {
                val excludedCount = totalFound - filteredUsers.size
                "${filteredUsers.size} utilisateur(s) disponible(s) ($excludedCount déjà présent(s))"
            }
            else -> {
                "${filteredUsers.size} utilisateur(s) trouvé(s)"
            }
        }

        resultsInfoText.text = message
        Log.d(TAG, "Résultats filtrés: ${filteredUsers.size}/$totalFound")
    }

    private fun showNoResults(query: String) {
        userSearchAdapter.updateUsers(emptyList())
        resultsInfoText.text = "Aucun utilisateur trouvé pour '$query'"
    }

    private fun handleApiError(response: Response<UserSearchResponse>) {
        val errorBody = response.errorBody()?.string()
        Log.e(TAG, "Erreur API: ${response.code()}, message: $errorBody")

        when (response.code()) {
            401 -> handleAuthError()
            else -> resultsInfoText.text = "Erreur lors de la recherche: ${response.code()}"
        }
    }

    private fun handleSearchError(t: Throwable) {
        Log.e(TAG, "Échec de l'appel API", t)

        val errorMessage = when (t) {
            is java.net.UnknownHostException -> "Erreur de connexion: Vérifiez votre connexion Internet"
            is java.net.SocketTimeoutException -> "Délai d'attente dépassé"
            else -> "Erreur réseau: ${t.message}"
        }

        resultsInfoText.text = errorMessage
    }

    private fun showLoading() {
        searchProgress.visibility = View.VISIBLE
        resultsInfoText.text = "Recherche en cours..."
    }

    private fun hideLoading() {
        searchProgress.visibility = View.GONE
    }

    private fun handleAuthError() {
        Toast.makeText(
            context,
            "Session expirée, veuillez vous reconnecter",
            Toast.LENGTH_SHORT
        ).show()
        (activity as? HomeActivity)?.redirectToLogin("Session expirée")
    }

    // ==================== INTERACTIONS UTILISATEUR ====================

    override fun onUserClick(user: UserSearchData) {
        Log.d(TAG, "Clic sur utilisateur: ${user.username}")

        if (isInvitationMode) {
            handleUserSelection(user)
        } else {
            showUserOptions(user)
        }
    }

    override fun onActionButtonClick(user: UserSearchData) {
        Log.d(TAG, "Clic sur bouton d'action: ${user.username}")

        if (isInvitationMode) {
            handleUserSelection(user)
        } else {
            openPrivateChat(user)
        }
    }

    private fun handleUserSelection(user: UserSearchData) {
        // Appeler le callback approprié
        userSelectedCallback?.invoke(user)
        userSelectedByIdCallback?.invoke(user.id)

        // Afficher un feedback
        Toast.makeText(context, "${user.username} sélectionné", Toast.LENGTH_SHORT).show()

        // Retourner à l'écran précédent
        activity?.supportFragmentManager?.popBackStack()
    }

    private fun showUserOptions(user: UserSearchData) {
        val options = arrayOf("Voir le profil", "Envoyer un message")

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Options pour ${user.username}")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> showUserProfile(user)
                    1 -> openPrivateChat(user)
                }
            }
            .show()
    }

    private fun showUserProfile(user: UserSearchData) {
        Toast.makeText(
            context,
            "Voir le profil de ${user.username} (à implémenter)",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun openPrivateChat(user: UserSearchData) {
        val myUserId = getCurrentUserId()
        if (myUserId.isNullOrEmpty()) {
            Toast.makeText(context, "Erreur: ID utilisateur non trouvé", Toast.LENGTH_SHORT).show()
            return
        }

        val privateChatFragment = PrivateConversationFragment.newInstance(
            conversationId = "", // Nouvelle conversation
            otherUserId = user.id,
            username = user.username,
            myUserId = myUserId,
            profilePicture = user.profilePicture
        )

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_content_container, privateChatFragment)
            .addToBackStack(null)
            .commit()

        Log.d(TAG, "Conversation privée ouverte avec: ${user.username}")
    }

    // ==================== UTILITAIRES ====================

    private fun getAuthToken(): String? {
        return requireActivity().getSharedPreferences(
            "SupChatPrefs",
            Context.MODE_PRIVATE
        ).getString("auth_token", "")
    }

    private fun getCurrentUserId(): String? {
        return requireActivity().getSharedPreferences(
            "SupChatPrefs",
            Context.MODE_PRIVATE
        ).getString("user_id", "")
    }
}