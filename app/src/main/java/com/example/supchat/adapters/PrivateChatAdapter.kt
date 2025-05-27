// PrivateChatAdapter.kt - Version corrigée
package com.example.supchat.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.supchat.R
import com.example.supchat.models.response.messageprivate.ConversationMessage
import com.example.supchat.models.response.messageprivate.isReadBy
import java.text.SimpleDateFormat
import java.util.*

class PrivateChatAdapter(
    private val context: Context,
    private val currentUserId: String,
    private val onMessageLongClick: (ConversationMessage, Int) -> Unit,
    private val onMessageRead: (String) -> Unit // Callback pour marquer comme lu
) : RecyclerView.Adapter<PrivateChatAdapter.MessageViewHolder>() {

    private var messages = mutableListOf<ConversationMessage>()

    fun updateMessages(newMessages: List<ConversationMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()

        // Marquer les messages non lus comme lus
        markUnreadMessagesAsRead()
    }

    fun addMessage(message: ConversationMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_private_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount(): Int = messages.size

    private fun markUnreadMessagesAsRead() {
        messages.forEachIndexed { index, message ->
            // Vérifier si le message n'est pas lu par l'utilisateur actuel
            // et que ce n'est pas son propre message
            if (message.expediteur != currentUserId && !message.isReadBy(currentUserId)) {
                // Utiliser l'index comme identifiant pour le moment
                onMessageRead(index.toString())
            }
        }
    }

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val sentMessageLayout: LinearLayout = itemView.findViewById(R.id.sent_message_layout)
        private val receivedMessageLayout: LinearLayout = itemView.findViewById(R.id.received_message_layout)
        private val sentMessageText: TextView = itemView.findViewById(R.id.sent_message_text)
        private val receivedMessageText: TextView = itemView.findViewById(R.id.received_message_text)
        private val sentMessageTime: TextView = itemView.findViewById(R.id.sent_message_time)
        private val receivedMessageTime: TextView = itemView.findViewById(R.id.received_message_time)

        fun bind(message: ConversationMessage) {
            val isMyMessage = message.expediteur == currentUserId

            if (isMyMessage) {
                // Message envoyé par l'utilisateur actuel
                showSentMessage(message)
            } else {
                // Message reçu d'un autre utilisateur
                showReceivedMessage(message)
            }

            // Afficher les réactions si présentes
            displayReactions(message, isMyMessage)

            // Configurer le long click uniquement sur ses propres messages
            configureLongClick(message, isMyMessage)
        }

        private fun showSentMessage(message: ConversationMessage) {
            sentMessageLayout.visibility = View.VISIBLE
            receivedMessageLayout.visibility = View.GONE

            // Gérer le contenu du message
            val messageText = getMessageDisplayText(message)
            sentMessageText.text = if (message.modifie) "$messageText (modifié)" else messageText

            // Ajouter l'indicateur de lecture et l'heure
            val timeText = formatTime(message.horodatage)
            val readIndicator = if (message.lu.isNotEmpty()) " ✓✓" else " ✓"
            sentMessageTime.text = timeText + readIndicator
        }

        private fun showReceivedMessage(message: ConversationMessage) {
            sentMessageLayout.visibility = View.GONE
            receivedMessageLayout.visibility = View.VISIBLE

            // Gérer le contenu du message
            val messageText = getMessageDisplayText(message)
            receivedMessageText.text = if (message.modifie) "$messageText (modifié)" else messageText

            // Afficher l'heure
            receivedMessageTime.text = formatTime(message.horodatage)
        }

        private fun getMessageDisplayText(message: ConversationMessage): String {
            return when {
                message.fichiers.isNotEmpty() -> {
                    val file = message.fichiers.first()
                    when {
                        file.type.startsWith("image/") -> "🖼️ ${file.nom}"
                        file.type.startsWith("video/") -> "🎥 ${file.nom}"
                        file.type.startsWith("audio/") -> "🎵 ${file.nom}"
                        file.type.startsWith("application/pdf") -> "📄 ${file.nom}"
                        file.type.startsWith("application/") -> "📎 ${file.nom}"
                        else -> "📎 ${file.nom}"
                    }
                }
                message.contenu.isNotEmpty() -> message.contenu
                else -> "[Message vide]"
            }
        }

        private fun displayReactions(message: ConversationMessage, isMyMessage: Boolean) {
            if (message.reactions.isNotEmpty()) {
                val reactionsText = message.reactions.joinToString(" ") { it.emoji }

                if (isMyMessage) {
                    val currentText = sentMessageText.text.toString()
                    sentMessageText.text = "$currentText\n$reactionsText"
                } else {
                    val currentText = receivedMessageText.text.toString()
                    receivedMessageText.text = "$currentText\n$reactionsText"
                }
            }
        }

        private fun configureLongClick(message: ConversationMessage, isMyMessage: Boolean) {
            if (isMyMessage) {
                itemView.setOnLongClickListener {
                    onMessageLongClick(message, adapterPosition)
                    true
                }
            } else {
                itemView.setOnLongClickListener(null)
                itemView.isLongClickable = false
            }
        }

        private fun formatTime(horodatage: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val date = inputFormat.parse(horodatage)
                outputFormat.format(date ?: Date())
            } catch (e: Exception) {
                "..."
            }
        }
    }
}