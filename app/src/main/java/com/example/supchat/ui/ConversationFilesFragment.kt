package com.example.supchat.ui.chat

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.supchat.R
import com.example.supchat.adapters.ConversationFilesAdapter
import com.example.supchat.api.ApiClient
import com.example.supchat.models.response.messageprivate.ConversationMessagesResponse
import com.example.supchat.models.response.messageprivate.MessageFichier
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ConversationFilesFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyTextView: TextView
    private lateinit var filesAdapter: ConversationFilesAdapter

    private var conversationId: String = ""

    companion object {
        private const val TAG = "ConversationFiles"
        private const val ARG_CONVERSATION_ID = "conversationId"

        fun newInstance(conversationId: String): ConversationFilesFragment {
            val fragment = ConversationFilesFragment()
            val args = Bundle()
            args.putString(ARG_CONVERSATION_ID, conversationId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            conversationId = it.getString(ARG_CONVERSATION_ID, "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_conversation_files, container, false)

        recyclerView = view.findViewById(R.id.files_recycler_view)
        progressBar = view.findViewById(R.id.files_progress_bar)
        emptyTextView = view.findViewById(R.id.empty_files_text)

        setupRecyclerView()
        loadFiles()

        return view
    }

    private fun setupRecyclerView() {
        filesAdapter = ConversationFilesAdapter(requireContext()) { file ->
            // Handle file click
        }

        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = filesAdapter
        }
    }

    private fun loadFiles() {
        progressBar.visibility = View.VISIBLE
        emptyTextView.visibility = View.GONE
        recyclerView.visibility = View.GONE

        val token = getAuthToken()
        if (token.isEmpty()) {
            showError("Session expirée")
            return
        }

        // ✅ CORRECTION : Utiliser ConversationMessagesResponse comme attendu par ApiClient
        ApiClient.getConversationFiles(token, conversationId)
            .enqueue(object : Callback<ConversationMessagesResponse> {
                override fun onResponse(
                    call: Call<ConversationMessagesResponse>,
                    response: Response<ConversationMessagesResponse>
                ) {
                    if (!isAdded) return

                    progressBar.visibility = View.GONE

                    if (response.isSuccessful) {
                        val messagesResponse = response.body()

                        // Extraire les fichiers des messages
                        val files = mutableListOf<MessageFichier>()
                        messagesResponse?.data?.forEach { message ->
                            files.addAll(message.fichiers)
                        }

                        if (files.isEmpty()) {
                            emptyTextView.visibility = View.VISIBLE
                            emptyTextView.text = "Aucun fichier partagé"
                        } else {
                            recyclerView.visibility = View.VISIBLE
                            filesAdapter.updateFiles(files)
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

    private fun showError(message: String) {
        progressBar.visibility = View.GONE
        emptyTextView.text = message
        emptyTextView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }

    private fun getAuthToken(): String {
        return requireActivity().getSharedPreferences("SupChatPrefs", Context.MODE_PRIVATE)
            .getString("auth_token", "") ?: ""
    }
}