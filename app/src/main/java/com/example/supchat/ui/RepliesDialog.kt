package com.example.supchat.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.supchat.R
import com.example.supchat.adapters.MessageAdapter
import com.example.supchat.models.response.Message

/**
 * Dialogue pour afficher les réponses à un message
 */
class RepliesDialog(
    context: Context,
    private val message: Message,
    private val currentUsername: String,
    private val listener: MessageAdapter.MessageActionListener
) : Dialog(context) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var messageAdapter: MessageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Créer la vue du dialogue
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_replies, null)
        setContentView(view)

        // Configurer le titre
        val titleView = view.findViewById<TextView>(R.id.dialog_title)
        titleView.text = "Réponses au message de ${message.getNomAuteur()}"

        // Afficher le message parent
        val parentMessageView = view.findViewById<LinearLayout>(R.id.parent_message_container)
        val userInfoView = parentMessageView.findViewById<TextView>(R.id.parent_user_info)
        val messageTextView = parentMessageView.findViewById<TextView>(R.id.parent_message_text)

        userInfoView.text = message.getNomAuteur()
        messageTextView.text = message.contenu

        // Configurer le RecyclerView pour les réponses
        recyclerView = view.findViewById(R.id.replies_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Initialiser l'adaptateur
        messageAdapter = MessageAdapter(
            context,
            currentUsername,
            listener
        )

        recyclerView.adapter = messageAdapter

        // Afficher les réponses
        val replies = message.reponses ?: emptyList()
        messageAdapter.updateMessages(replies)

        // Configurer le bouton de fermeture
        val closeButton = view.findViewById<TextView>(R.id.btn_close)
        closeButton.setOnClickListener {
            dismiss()
        }

        // Définir la taille du dialogue
        window?.setLayout(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }
}