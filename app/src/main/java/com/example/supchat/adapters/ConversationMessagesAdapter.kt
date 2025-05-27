package com.example.supchat.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.supchat.R
import com.example.supchat.models.response.messageprivate.ConversationMessage
import java.text.SimpleDateFormat
import java.util.Locale

class ConversationMessagesAdapter(
    private val currentUserId: String,
    private val onMessageClickListener: OnMessageClickListener? = null
) : RecyclerView.Adapter<ConversationMessagesAdapter.MessageViewHolder>() {

    interface OnMessageClickListener {
        fun onMessageClick(message: ConversationMessage)
        fun onMessageLongClick(message: ConversationMessage, position: Int): Boolean
    }

    private val messages: MutableList<ConversationMessage> = mutableListOf()
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun updateMessages(newMessages: List<ConversationMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    fun addMessage(message: ConversationMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_private_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(message, currentUserId)
    }

    override fun getItemCount(): Int = messages.size

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val sentMessageLayout: LinearLayout =
            itemView.findViewById(R.id.sent_message_layout)
        private val sentMessageText: TextView = itemView.findViewById(R.id.sent_message_text)
        private val sentMessageTime: TextView = itemView.findViewById(R.id.sent_message_time)

        private val receivedMessageLayout: LinearLayout =
            itemView.findViewById(R.id.received_message_layout)
        private val receivedMessageText: TextView =
            itemView.findViewById(R.id.received_message_text)
        private val receivedMessageTime: TextView =
            itemView.findViewById(R.id.received_message_time)

        fun bind(message: ConversationMessage, currentUserId: String) {
            // CORRECTION: Utiliser expediteurId au lieu de senderId
            val isSentByMe = message.expediteurId == currentUserId

            // Configurer la visibilité des layouts
            sentMessageLayout.visibility = if (isSentByMe) View.VISIBLE else View.GONE
            receivedMessageLayout.visibility = if (isSentByMe) View.GONE else View.VISIBLE

            // Formater la date - CORRECTION: Utiliser horodatage au lieu de createdAt
            val formattedDate = try {
                val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                    .parse(message.horodatage)
                date?.let { dateFormat.format(it) } ?: "..."
            } catch (e: Exception) {
                "..."
            }

            // Gérer différents types de contenu
            val messageText = when {
                message.fichiers.isNotEmpty() -> {
                    val file = message.fichiers.first()
                    when {
                        file.type.startsWith("image/") -> "🖼️ ${file.nom}"
                        file.type.startsWith("video/") -> "🎥 ${file.nom}"
                        file.type.startsWith("audio/") -> "🎵 ${file.nom}"
                        else -> "📎 ${file.nom}"
                    }
                }
                message.contenu.isNotEmpty() -> message.contenu // CORRECTION: Utiliser contenu au lieu de content
                else -> "[Message vide]"
            }

            // Remplir les données du message
            if (isSentByMe) {
                sentMessageText.text = messageText

                // Ajouter l'indicateur de lecture pour les messages envoyés
                val readIndicator = if (message.lu.isNotEmpty()) " ✓✓" else " ✓"
                sentMessageTime.text = formattedDate + readIndicator

                // Indicateur de modification
                if (message.modifie) {
                    sentMessageText.text = "$messageText (modifié)"
                }
            } else {
                receivedMessageText.text = messageText
                receivedMessageTime.text = formattedDate

                // Indicateur de modification
                if (message.modifie) {
                    receivedMessageText.text = "$messageText (modifié)"
                }
            }

            // Afficher les réactions si présentes
            if (message.reactions.isNotEmpty()) {
                val reactionsText = message.reactions.joinToString(" ") { it.emoji }
                val currentText = if (isSentByMe) sentMessageText.text else receivedMessageText.text
                if (isSentByMe) {
                    sentMessageText.text = "$currentText\n$reactionsText"
                } else {
                    receivedMessageText.text = "$currentText\n$reactionsText"
                }
            }

            // Configurer les listeners
            itemView.setOnClickListener {
                onMessageClickListener?.onMessageClick(message)
            }

            itemView.setOnLongClickListener {
                onMessageClickListener?.onMessageLongClick(message, adapterPosition) ?: false
            }
        }
    }
}