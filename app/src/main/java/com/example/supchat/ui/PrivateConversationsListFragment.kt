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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.supchat.R
import com.example.supchat.adapters.PrivateMessagesAdapter
import com.example.supchat.api.ApiClient
import com.example.supchat.models.response.messageprivate.PrivateMessageItem
import com.example.supchat.models.response.messageprivate.PrivateMessagesResponse
import com.example.supchat.ui.chat.PrivateConversationFragment
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PrivateConversationsListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyTextView: TextView
    private lateinit var conversationsAdapter: PrivateMessagesAdapter

    companion object {
        private const val TAG = "PrivateConversationsList"

        fun newInstance(): PrivateConversationsListFragment {
            return PrivateConversationsListFragment()
        }
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
        loadConversations()

        return view
    }

    private fun setupRecyclerView() {
        val currentUserId = getCurrentUserId()

        conversationsAdapter = PrivateMessagesAdapter(requireContext(), currentUserId) { conversation ->
            navigateToPrivateChat(conversation)
        }

        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = conversationsAdapter
        }
    }

    private fun loadConversations() {
        progressBar.visibility = View.VISIBLE
        emptyTextView.visibility = View.GONE
        recyclerView.visibility = View.GONE

        val token = getAuthToken()
        if (token.isEmpty()) {
            showError("Vous n'êtes pas connecté")
            return
        }

        Log.d(TAG, "Chargement des messages privés...")

        // ✅ CORRIGÉ : Utiliser PrivateMessagesResponse
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

                    // ✅ CORRIGÉ : Récupérer les vraies données
                    val conversations = privateMessagesResponse?.data ?: emptyList()
                    Log.d(TAG, "Nombre de conversations: ${conversations.size}")

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
            conversationsAdapter.updateConversations(conversations)
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

        val validConversationId = if (conversation.conversationId.isNotEmpty()) {
            conversation.conversationId
        } else {
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

    override fun onResume() {
        super.onResume()
        loadConversations()
    }
}