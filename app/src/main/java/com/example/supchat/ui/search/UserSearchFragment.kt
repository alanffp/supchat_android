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
import com.example.supchat.ui.chat.PrivateConversationFragment // CORRECTION: Import correct
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.net.URLEncoder
import java.util.Timer
import java.util.TimerTask

class UserSearchFragment : Fragment(), UserSearchAdapter.OnUserClickListener {
    // Variables existantes
    private lateinit var searchEditText: TextInputEditText
    private lateinit var resultsInfoText: TextView
    private lateinit var searchResultsRecyclerView: RecyclerView
    private lateinit var searchProgress: ProgressBar
    private lateinit var userSearchAdapter: UserSearchAdapter

    private var searchTimer: Timer? = null
    private val SEARCH_DELAY_MS = 500L

    // Ajouter ces deux variables pour la sélection d'utilisateurs
    private var userSelectedCallback: ((String) -> Unit)? = null
    private var selectionMode = false // Mode pour distinguer entre recherche normale et sélection

    companion object {
        private const val TAG = "UserSearchFragment"

        fun newInstance(): UserSearchFragment {
            return UserSearchFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_user_search, container, false)

        // Initialiser les vues
        searchEditText = view.findViewById(R.id.search_edit_text)
        resultsInfoText = view.findViewById(R.id.results_info_text)
        searchResultsRecyclerView = view.findViewById(R.id.search_results_recycler_view)
        searchProgress = view.findViewById(R.id.search_progress)

        // Configurer le RecyclerView
        searchResultsRecyclerView.layoutManager = LinearLayoutManager(context)
        userSearchAdapter = UserSearchAdapter(requireContext(), onUserClickListener = this)
        searchResultsRecyclerView.adapter = userSearchAdapter

        // Configurer les listeners pour la recherche
        setupSearchListeners()

        return view
    }

    private fun setupSearchListeners() {
        // Recherche lorsque l'utilisateur appuie sur "recherche" sur le clavier
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

        // Recherche au fur et à mesure que l'utilisateur tape (avec délai)
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Annuler le timer précédent s'il existe
                searchTimer?.cancel()

                val searchText = s.toString().trim()
                if (searchText.isEmpty()) {
                    resultsInfoText.text = "Entrez un terme de recherche ci-dessus"
                    userSearchAdapter.updateUsers(emptyList())
                    return
                }

                // Initialiser un nouveau timer pour retarder la recherche
                searchTimer = Timer()
                searchTimer?.schedule(object : TimerTask() {
                    override fun run() {
                        activity?.runOnUiThread {
                            performSearch(searchText)
                        }
                    }
                }, SEARCH_DELAY_MS)
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun performSearch(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.length < 2) {
            resultsInfoText.text = "Veuillez entrer au moins 2 caractères"
            userSearchAdapter.updateUsers(emptyList())
            return
        }

        // Récupérer le token d'authentification
        val token = requireActivity().getSharedPreferences(
            "SupChatPrefs",
            Context.MODE_PRIVATE
        ).getString("auth_token", "")

        if (token.isNullOrEmpty()) {
            Toast.makeText(
                context,
                "Session expirée, veuillez vous reconnecter",
                Toast.LENGTH_SHORT
            ).show()
            (activity as? HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
            return
        }

        // Afficher l'indicateur de chargement
        searchProgress.visibility = View.VISIBLE
        resultsInfoText.text = "Recherche en cours..."

        // Ajouter des logs pour déboguer
        Log.d(TAG, "Début de la recherche avec le terme: '$trimmedQuery'")

        // Appeler l'API pour la recherche
        ApiClient.searchUsers(token, trimmedQuery)
            .enqueue(object : Callback<UserSearchResponse> {
                override fun onResponse(
                    call: Call<UserSearchResponse>,
                    response: Response<UserSearchResponse>
                ) {
                    if (!isAdded) return  // Vérifier si le fragment est toujours attaché

                    searchProgress.visibility = View.GONE

                    try {
                        // Log pour le débogage
                        Log.d(TAG, "Code de réponse: ${response.code()}")
                        Log.d(TAG, "URL de la requête: ${call.request().url}")

                        if (response.isSuccessful) {
                            // Log pour déboguer la structure de la réponse
                            Log.d(TAG, "Réponse brute: ${response.body()}")

                            val wrapper = response.body()?.data
                            val userList = wrapper?.users

                            if (userList != null && userList.isNotEmpty()) {
                                userSearchAdapter.updateUsers(userList)
                                resultsInfoText.text = "${userList.size} utilisateur(s) trouvé(s)"
                            } else {
                                userSearchAdapter.updateUsers(emptyList())
                                resultsInfoText.text = "Aucun utilisateur trouvé pour '$trimmedQuery'"
                            }
                        } else {
                            // Log du corps de l'erreur pour déboguer
                            val errorBody = response.errorBody()?.string()
                            Log.e(TAG, "Erreur API: ${response.code()}, message: $errorBody")

                            if (response.code() == 401) {
                                // Gérer l'erreur d'authentification
                                Toast.makeText(
                                    context,
                                    "Session expirée, veuillez vous reconnecter",
                                    Toast.LENGTH_SHORT
                                ).show()
                                (activity as? HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
                            } else {
                                resultsInfoText.text = "Erreur lors de la recherche: ${response.code()}"
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception lors du traitement de la réponse", e)
                        resultsInfoText.text = "Erreur lors du traitement des résultats: ${e.message}"
                    }
                }

                override fun onFailure(call: Call<UserSearchResponse>, t: Throwable) {
                    if (!isAdded) return

                    searchProgress.visibility = View.GONE
                    Log.e(TAG, "Échec de l'appel API", t)

                    val errorMessage = when (t) {
                        is java.net.UnknownHostException -> "Erreur de connexion: Vérifiez votre connexion Internet"
                        is java.net.SocketTimeoutException -> "Délai d'attente dépassé pour la connexion"
                        is java.lang.IllegalStateException -> "Erreur de format de données: ${t.message}"
                        else -> "Erreur réseau: ${t.message}"
                    }

                    resultsInfoText.text = errorMessage
                }
            })
    }

    // Reste du code (onUserClick, openPrivateChat, etc.)
    override fun onUserClick(user: UserSearchData) {
        // Si nous sommes en mode sélection (pour ajouter un utilisateur à un workspace)
        if (selectionMode && userSelectedCallback != null) {
            userSelectedCallback?.invoke(user.id)
            // Retourner à l'écran précédent après la sélection
            activity?.supportFragmentManager?.popBackStack()
            return
        }

        // Sinon, comportement normal existant (afficher les options)
        val options = arrayOf("Voir le profil", "Envoyer un message")

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Options pour ${user.username}")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> {
                        // Option pour voir le profil
                        Toast.makeText(context, "Voir le profil de ${user.username} (à implémenter)", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        // Option pour envoyer un message privé
                        openPrivateChat(user)
                    }
                }
            }
            .show()
    }

    private fun openPrivateChat(user: UserSearchData) {
        // Récupérer l'ID de l'utilisateur actuel
        val myUserId = requireActivity().getSharedPreferences(
            "SupChatPrefs",
            Context.MODE_PRIVATE
        ).getString("user_id", "")

        if (myUserId.isNullOrEmpty()) {
            Toast.makeText(context, "Erreur: ID utilisateur non trouvé", Toast.LENGTH_SHORT).show()
            return
        }

        // CORRECTION: Utiliser PrivateConversationFragment.newInstance
        val privateChatFragment = PrivateConversationFragment.newInstance(
            conversationId = "", // Valeur par défaut pour une nouvelle conversation
            otherUserId = user.id,
            username = user.username,
            myUserId = myUserId,
            profilePicture = user.profilePicture
        )

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_content_container, privateChatFragment)
            .addToBackStack(null)
            .commit()

        Log.d(TAG, "Conversation privée ouverte avec l'utilisateur: ${user.username}")
    }

    override fun onActionButtonClick(user: UserSearchData) {
        // Si nous sommes en mode sélection, utiliser le callback
        if (selectionMode && userSelectedCallback != null) {
            userSelectedCallback?.invoke(user.id)
            // Retourner à l'écran précédent après la sélection
            activity?.supportFragmentManager?.popBackStack()
            return
        }

        // Sinon, comportement normal existant (ouvrir directement le chat)
        val myUserId = requireActivity().getSharedPreferences(
            "SupChatPrefs",
            Context.MODE_PRIVATE
        ).getString("user_id", "")

        if (myUserId.isNullOrEmpty()) {
            Toast.makeText(context, "Erreur: ID utilisateur non trouvé", Toast.LENGTH_SHORT).show()
            return
        }

        // CORRECTION: Utiliser PrivateConversationFragment.newInstance
        val privateChatFragment = PrivateConversationFragment.newInstance(
            conversationId = "", // Valeur par défaut pour une nouvelle conversation
            otherUserId = user.id,
            username = user.username,
            myUserId = myUserId,
            profilePicture = user.profilePicture
        )

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_content_container, privateChatFragment)
            .addToBackStack(null)
            .commit()

        Log.d(TAG, "Conversation privée ouverte avec l'utilisateur: ${user.username}")
    }

    fun setOnUserSelectedListener(listener: (String) -> Unit) {
        userSelectedCallback = listener
        selectionMode = true
    }
}