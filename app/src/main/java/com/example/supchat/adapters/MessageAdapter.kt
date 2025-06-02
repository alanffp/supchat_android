// Version complète corrigée de MessageAdapter.kt

package com.example.supchat.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.supchat.R
import com.example.supchat.models.response.Message
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log
import android.view.animation.AlphaAnimation
import android.view.animation.Animation

/**
 * Adaptateur pour afficher les messages dans un RecyclerView
 * avec gestion des clics pour afficher/masquer les actions
 */
class MessageAdapter(
    private val context: Context,
    private val currentUsername: String,
    private val listener: MessageActionListener
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private val TAG = "MessageAdapter"
    private val messages = mutableListOf<Message>()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    private val inputDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())

    // Pour suivre le message dont les boutons sont actuellement affichés
    private var expandedMessageId: String? = null

    // Interface pour les actions sur les messages
    interface MessageActionListener {
        fun onEditMessage(message: Message)
        fun onDeleteMessage(message: Message)
        fun onReactToMessage(message: Message, emoji: String)
        fun onReplyToMessage(message: Message)
        fun onViewReplies(message: Message)
    }

    /**
     * Mets à jour la liste des messages
     */
    fun updateMessages(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
        Log.d(TAG, "Messages mis à jour: ${newMessages.size} messages")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(message)
    }

    override fun getItemCount(): Int = messages.size

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Containers principaux
        private val otherMessageContainer: LinearLayout =
            itemView.findViewById(R.id.other_message_container)
        private val myMessageContainer: LinearLayout =
            itemView.findViewById(R.id.my_message_container)

        // Éléments pour les messages des autres
        private val userInfo: TextView = itemView.findViewById(R.id.userInfo)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timestampText: TextView = itemView.findViewById(R.id.timestamp)
        private val actionsLayoutOther: LinearLayout =
            itemView.findViewById(R.id.message_actions_other)
        private val reactionsLayoutOther: LinearLayout =
            itemView.findViewById(R.id.reactions_layout_other)
        private val replyInfoContainerOther: LinearLayout =
            itemView.findViewById(R.id.reply_info_container_other)
        private val replyInfoTextOther: TextView = itemView.findViewById(R.id.reply_info_text_other)

        // Éléments pour mes messages
        private val messageTextMy: TextView = itemView.findViewById(R.id.messageText_my)
        private val timestampTextMy: TextView = itemView.findViewById(R.id.timestamp_my_bottom)
        private val actionsLayoutMy: LinearLayout = itemView.findViewById(R.id.message_actions_my)
        private val reactionsLayoutMy: LinearLayout =
            itemView.findViewById(R.id.reactions_layout_my)
        private val replyInfoContainerMy: LinearLayout =
            itemView.findViewById(R.id.reply_info_container_my)
        private val replyInfoTextMy: TextView = itemView.findViewById(R.id.reply_info_text_my)

        // Éléments communs
        private val repliesLayout: LinearLayout = itemView.findViewById(R.id.replies_layout)
        private val repliesCount: TextView = itemView.findViewById(R.id.replies_count)

        // Boutons d'action pour les autres
        private val reactButtonOther: ImageButton = itemView.findViewById(R.id.btn_react_other)
        private val replyButtonOther: ImageButton = itemView.findViewById(R.id.btn_reply_other)

        // Boutons d'action pour mes messages
        private val editButton: ImageButton = itemView.findViewById(R.id.btn_edit)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.btn_delete)
        private val reactButton: ImageButton = itemView.findViewById(R.id.btn_react)
        private val replyButton: ImageButton = itemView.findViewById(R.id.btn_reply)

        fun bind(message: Message) {
            // Déterminer si c'est mon message
            val messageAuthor = message.getNomAuteur().trim()
            val isMyMessage = currentUsername.trim().equals(messageAuthor, ignoreCase = true)

            Log.d(
                TAG,
                "Message ${message.id} - Auteur: '$messageAuthor', Username: '$currentUsername', isMyMessage: $isMyMessage"
            )

            if (isMyMessage) {
                // Afficher mes messages à droite
                otherMessageContainer.visibility = View.GONE
                myMessageContainer.visibility = View.VISIBLE

                // Configurer le contenu de mes messages
                messageTextMy.text = message.contenu

                // Formater et afficher la date
                message.dateCreation?.let {
                    try {
                        val date = inputDateFormat.parse(it)
                        timestampTextMy.text = dateFormat.format(date!!)
                        timestampTextMy.visibility = View.VISIBLE
                    } catch (e: Exception) {
                        timestampTextMy.text = it
                        timestampTextMy.visibility = View.VISIBLE
                    }
                } ?: run {
                    timestampTextMy.visibility = View.GONE
                }

                // Gérer les réponses pour mes messages
                if (message.isReply()) {
                    replyInfoContainerMy.visibility = View.VISIBLE
                    replyInfoTextMy.text = "Réponse à un message"
                } else {
                    replyInfoContainerMy.visibility = View.GONE
                }

                // Afficher les réactions pour mes messages
                displayReactions(message, reactionsLayoutMy, true)

                // Gérer les actions pour mes messages
                actionsLayoutMy.visibility = View.GONE

            } else {
                // Afficher les messages des autres à gauche
                myMessageContainer.visibility = View.GONE
                otherMessageContainer.visibility = View.VISIBLE

                // Configurer le contenu des messages des autres
                userInfo.text = message.getNomAuteur()
                messageText.text = message.contenu

                // Formater et afficher la date
                message.dateCreation?.let {
                    try {
                        val date = inputDateFormat.parse(it)
                        timestampText.text = dateFormat.format(date!!)
                        timestampText.visibility = View.VISIBLE
                    } catch (e: Exception) {
                        timestampText.text = it
                        timestampText.visibility = View.VISIBLE
                    }
                } ?: run {
                    timestampText.visibility = View.GONE
                }

                // Gérer les réponses pour les messages des autres
                if (message.isReply()) {
                    replyInfoContainerOther.visibility = View.VISIBLE
                    replyInfoTextOther.text = "Réponse à un message"
                } else {
                    replyInfoContainerOther.visibility = View.GONE
                }

                // Afficher les réactions pour les messages des autres
                displayReactions(message, reactionsLayoutOther, false)

                // Gérer les actions pour les messages des autres
                actionsLayoutOther.visibility = View.GONE
            }

            // Afficher l'indication des réponses (commun aux deux types)
            if (message.hasReplies()) {
                repliesLayout.visibility = View.VISIBLE
                repliesCount.text = "Voir les réponses (${message.getReplyCount()})"
                repliesLayout.setOnClickListener {
                    listener.onViewReplies(message)
                }
            } else {
                repliesLayout.visibility = View.GONE
            }

            // Vérifier si ce message doit avoir ses actions affichées
            if (message.id == expandedMessageId) {
                showActions(message, isMyMessage)
            }

            // Configurer le clic sur le message pour afficher/masquer les actions
            itemView.setOnClickListener {
                toggleActions(message, isMyMessage)
            }

            // Configurer le clic long sur le message pour afficher les options
            itemView.setOnLongClickListener {
                showContextMenu(message, isMyMessage)
                true
            }
        }

        private fun toggleActions(message: Message, isMyMessage: Boolean) {
            if (message.id == expandedMessageId) {
                // Masquer les actions
                if (isMyMessage) {
                    actionsLayoutMy.visibility = View.GONE
                } else {
                    actionsLayoutOther.visibility = View.GONE
                }
                expandedMessageId = null
            } else {
                // Afficher les actions
                showActions(message, isMyMessage)
                expandedMessageId = message.id
            }

            // ✅ CORRECTION: Ne pas faire notifyDataSetChanged() qui peut causer des problèmes
            // notifyDataSetChanged()  // ← ENLEVER CETTE LIGNE

            // À la place, juste notifier le changement pour cet item spécifique
            val currentPosition = adapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) {
                notifyItemChanged(currentPosition)
            }
        }

        private fun showActions(message: Message, isMyMessage: Boolean) {
            Log.d(TAG, "showActions - messageId: ${message.id}, isMyMessage: $isMyMessage")

            if (isMyMessage) {
                // Afficher les actions pour mes messages
                actionsLayoutMy.visibility = View.VISIBLE

                editButton.setOnClickListener {
                    Log.d(TAG, "Clic sur éditer pour message ${message.id}")
                    listener.onEditMessage(message)
                }
                deleteButton.setOnClickListener {
                    Log.d(TAG, "Clic sur supprimer pour message ${message.id}")
                    listener.onDeleteMessage(message)
                }
                reactButton.setOnClickListener {
                    Log.d(TAG, "Clic sur réagir pour message ${message.id}")
                    showReactionPicker(message)
                }
                replyButton.setOnClickListener {
                    Log.d(TAG, "Clic sur répondre pour message ${message.id}")
                    listener.onReplyToMessage(message)
                }
            } else {
                // Afficher les actions pour les messages des autres
                actionsLayoutOther.visibility = View.VISIBLE

                reactButtonOther.setOnClickListener {
                    Log.d(TAG, "Clic sur réagir pour message ${message.id}")
                    showReactionPicker(message)
                }
                replyButtonOther.setOnClickListener {
                    Log.d(TAG, "Clic sur répondre pour message ${message.id}")
                    listener.onReplyToMessage(message)
                }
            }
        }

        private fun displayReactions(
            message: Message,
            reactionsLayout: LinearLayout,
            isMyMessage: Boolean
        ) {
            Log.d(TAG, "🎨 DISPLAY REACTIONS - Message ID: ${message.id}, isMyMessage: $isMyMessage")

            // Vider le conteneur de réactions
            reactionsLayout.removeAllViews()

            // Récupérer et afficher les réactions
            val reactionsList = message.getReactionsList()
            Log.d(TAG, "🎨 DISPLAY REACTIONS - Liste: $reactionsList")

            if (reactionsList.isEmpty()) {
                reactionsLayout.visibility = View.GONE
                return
            }

            // Afficher chaque réaction
            reactionsLayout.visibility = View.VISIBLE

            for ((emoji, count) in reactionsList) {
                try {
                    val reactionView = LayoutInflater.from(context)
                        .inflate(R.layout.item_reaction, reactionsLayout, false)

                    val emojiText = reactionView.findViewById<TextView>(R.id.emoji_text)
                    val countText = reactionView.findViewById<TextView>(R.id.count_text)

                    emojiText.text = emoji
                    countText.text = count.toString()

                    // Permettre d'ajouter la même réaction en cliquant dessus
                    reactionView.setOnClickListener {
                        listener.onReactToMessage(message, emoji)
                    }

                    reactionsLayout.addView(reactionView)
                    Log.d(TAG, "✅ DISPLAY REACTIONS - Vue ajoutée pour '$emoji'")

                } catch (e: Exception) {
                    Log.e(TAG, "❌ DISPLAY REACTIONS - Erreur création vue", e)
                }
            }
        }

        private fun showContextMenu(message: Message, isMyMessage: Boolean) {
            val options = if (isMyMessage) {
                arrayOf("Modifier", "Supprimer", "Réagir", "Répondre")
            } else {
                arrayOf("Réagir", "Répondre")
            }

            android.app.AlertDialog.Builder(context)
                .setTitle("Options")
                .setItems(options) { _, which ->
                    if (isMyMessage) {
                        when (which) {
                            0 -> listener.onEditMessage(message)
                            1 -> listener.onDeleteMessage(message)
                            2 -> showReactionPicker(message)
                            3 -> listener.onReplyToMessage(message)
                        }
                    } else {
                        when (which) {
                            0 -> showReactionPicker(message)
                            1 -> listener.onReplyToMessage(message)
                        }
                    }
                }
                .show()
        }


        private fun showReactionPicker(message: Message) {
            val emojis = arrayOf("👍", "❤️", "😂", "😮", "😢", "👏", "🔥", "🎉", "🤔", "👎")

            // ✅ CORRECTION: S'assurer que le dialog ne cause pas de navigation
            android.app.AlertDialog.Builder(context)
                .setTitle("Choisir une réaction")
                .setItems(emojis) { dialog, which ->
                    Log.d(TAG, "🎭 Emoji sélectionné: ${emojis[which]}")

                    // ✅ CORRECTION: Appeler directement le listener sans navigation
                    listener.onReactToMessage(message, emojis[which])

                    // Fermer le dialog immédiatement
                    dialog.dismiss()
                }
                .setOnCancelListener { dialog ->
                    Log.d(TAG, "Dialog réactions annulé")
                    dialog.dismiss()
                }
                .show()
        }
    }
}