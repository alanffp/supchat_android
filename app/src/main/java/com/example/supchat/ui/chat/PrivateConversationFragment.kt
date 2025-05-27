package com.example.supchat.ui.chat

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.supchat.R
import com.example.supchat.adapters.PrivateChatAdapter
import com.example.supchat.api.ApiClient
import com.example.supchat.models.response.messageprivate.ConversationMessagesResponse
import com.example.supchat.models.response.messageprivate.ConversationMessage
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PrivateConversationFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: PrivateChatAdapter

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
        loadMessages()

        return view
    }

    private fun initViews(view: View) {
        recyclerView = view.findViewById(R.id.messages_recycler_view)
        messageInput = view.findViewById(R.id.message_input)
        sendButton = view.findViewById(R.id.send_button)
        progressBar = view.findViewById(R.id.messages_progress_bar)

        val usernameTextView: TextView = view.findViewById(R.id.username_text)
        usernameTextView.text = username

        val backButton: ImageButton = view.findViewById(R.id.back_button)
        backButton.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
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
                markMessageAsRead(messageId)
            }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = this@PrivateConversationFragment.adapter
        }
    }

    private fun setupSendButton() {
        sendButton.setOnClickListener {
            val messageText = messageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                sendMessage(messageText)
            }
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

                        // ✅ CORRECTION : data est déjà la liste de messages
                        val messages = messagesResponse?.data ?: emptyList()
                        Log.d(TAG, "Nombre de messages: ${messages.size}")

                        if (messages.isEmpty()) {
                            recyclerView.visibility = View.VISIBLE
                            showEmptyState()
                        } else {
                            recyclerView.visibility = View.VISIBLE
                            adapter.updateMessages(messages)
                            recyclerView.scrollToPosition(messages.size - 1)
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

    private fun sendMessage(content: String) {
        val token = getAuthToken()
        if (token.isEmpty()) return

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
                        Log.e(TAG, "Erreur envoi message: ${response.code()}")
                        Toast.makeText(context, "Erreur lors de l'envoi", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<ConversationMessagesResponse>, t: Throwable) {
                    if (!isAdded) return

                    sendButton.isEnabled = true
                    messageInput.isEnabled = true

                    Log.e(TAG, "Erreur réseau envoi", t)
                    Toast.makeText(context, "Erreur réseau", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun markMessageAsRead(messageId: String) {
        val token = getAuthToken()
        if (token.isEmpty()) return

        ApiClient.markConversationMessageAsRead(token, conversationId, messageId)
            .enqueue(object : Callback<ConversationMessagesResponse> {
                override fun onResponse(call: Call<ConversationMessagesResponse>, response: Response<ConversationMessagesResponse>) {
                    // Message marqué comme lu
                }

                override fun onFailure(call: Call<ConversationMessagesResponse>, t: Throwable) {
                    Log.e(TAG, "Erreur marquage lecture", t)
                }
            })
    }

    private fun showMessageOptions(message: ConversationMessage, position: Int) {
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
        Toast.makeText(context, "Modification pas encore implémentée", Toast.LENGTH_SHORT).show()
    }

    private fun deleteMessage(message: ConversationMessage) {
        Toast.makeText(context, "Suppression pas encore implémentée", Toast.LENGTH_SHORT).show()
    }

    private fun replyToMessage(message: ConversationMessage) {
        Toast.makeText(context, "Réponse pas encore implémentée", Toast.LENGTH_SHORT).show()
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
}