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
        private val messageContainer: LinearLayout = itemView.findViewById(R.id.message_container)
        private val userInfo: TextView = itemView.findViewById(R.id.userInfo)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timestampText: TextView = itemView.findViewById(R.id.timestamp)
        private val actionsLayout: LinearLayout = itemView.findViewById(R.id.message_actions)
        private val reactionsLayout: LinearLayout = itemView.findViewById(R.id.reactions_layout)
        private val repliesLayout: LinearLayout = itemView.findViewById(R.id.replies_layout)
        private val repliesCount: TextView = itemView.findViewById(R.id.replies_count)
        private val replyInfoContainer: LinearLayout = itemView.findViewById(R.id.reply_info_container)
        private val replyInfoText: TextView = itemView.findViewById(R.id.reply_info_text)

        // Boutons d'action
        private val editButton: ImageButton = itemView.findViewById(R.id.btn_edit)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.btn_delete)
        private val reactButton: ImageButton = itemView.findViewById(R.id.btn_react)
        private val replyButton: ImageButton = itemView.findViewById(R.id.btn_reply)

        fun bind(message: Message) {
            // Afficher les informations de base
            userInfo.text = message.getNomAuteur()
            messageText.text = message.contenu

            // Formater et afficher la date si disponible
            message.dateCreation?.let {
                try {
                    val date = inputDateFormat.parse(it)
                    timestampText.text = dateFormat.format(date!!)
                    timestampText.visibility = View.VISIBLE
                } catch (e: Exception) {
                    timestampText.text = it
                    timestampText.visibility = View.VISIBLE
                    Log.d(TAG, "Erreur de format de date: ${e.message}")
                }
            } ?: run {
                timestampText.visibility = View.GONE
            }

            // Déterminer si c'est mon message - CORRIGÉ
            val messageAuthor = message.getNomAuteur().trim()
            val isMyMessage = currentUsername.trim().equals(messageAuthor, ignoreCase = true)

            // Log pour débogage
            Log.d(TAG, "Message ${message.id} - Auteur: '$messageAuthor', Username: '$currentUsername', isMyMessage: $isMyMessage")

            // Si c'est une réponse, afficher à qui on répond
            if (message.isReply()) {
                replyInfoContainer.visibility = View.VISIBLE
                // Idéalement, vous devriez récupérer le nom de l'auteur du message parent
                replyInfoText.text = "Réponse à un message"
            } else {
                replyInfoContainer.visibility = View.GONE
            }

            // Cacher les actions par défaut, elles seront affichées au clic
            actionsLayout.visibility = View.GONE

            // Afficher les réactions
            displayReactions(message)

            // Afficher l'indication des réponses si nécessaire
            if (message.hasReplies()) {
                repliesLayout.visibility = View.VISIBLE
                repliesCount.text = "Voir les réponses (${message.getReplyCount()})"

                // Configurer le clic pour voir les réponses
                repliesLayout.setOnClickListener {
                    listener.onViewReplies(message)
                }
            } else {
                repliesLayout.visibility = View.GONE
            }

            // Vérifier si ce message doit avoir ses actions affichées
            if (message.id == expandedMessageId) {
                showActions(message, isMyMessage)
            } else {
                actionsLayout.visibility = View.GONE
            }

            // Configurer le clic sur le message pour afficher/masquer les actions
            messageContainer.setOnClickListener {
                toggleActions(message, isMyMessage)
            }

            // Configurer le clic long sur le message pour afficher les options
            messageContainer.setOnLongClickListener {
                showContextMenu(message, isMyMessage)
                true
            }
        }

        /**
         * Affiche ou masque les actions sur le message
         */
        private fun toggleActions(message: Message, isMyMessage: Boolean) {
            if (message.id == expandedMessageId) {
                // Si les actions sont déjà affichées, les masquer
                actionsLayout.visibility = View.GONE
                expandedMessageId = null
            } else {
                // Sinon, afficher les actions
                showActions(message, isMyMessage)
                expandedMessageId = message.id
            }

            // Notifier l'adaptateur que les données ont changé
            // pour masquer les actions des autres messages
            notifyDataSetChanged()
        }

        /**
         * Affiche les actions adaptées au message - CORRIGÉ
         */
        private fun showActions(message: Message, isMyMessage: Boolean) {
            Log.d(TAG, "showActions - messageId: ${message.id}, isMyMessage: $isMyMessage")

            // Rendre visible le layout d'actions
            actionsLayout.visibility = View.VISIBLE

            // Ajouter une animation pour une transition fluide
            val fadeIn = AlphaAnimation(0.0f, 1.0f)
            fadeIn.duration = 200
            actionsLayout.startAnimation(fadeIn)

            // Pour les messages de l'utilisateur courant
            if (isMyMessage) {
                Log.d(TAG, "Affichage des boutons éditer/supprimer car c'est mon message")
                editButton.visibility = View.VISIBLE
                deleteButton.visibility = View.VISIBLE

                // Configurer les listeners des boutons
                editButton.setOnClickListener {
                    Log.d(TAG, "Clic sur éditer pour message ${message.id}")
                    listener.onEditMessage(message)
                }
                deleteButton.setOnClickListener {
                    Log.d(TAG, "Clic sur supprimer pour message ${message.id}")
                    listener.onDeleteMessage(message)
                }
            } else {
                Log.d(TAG, "Masquage des boutons éditer/supprimer car ce n'est pas mon message")
                editButton.visibility = View.GONE
                deleteButton.visibility = View.GONE
            }

            // Ces boutons sont disponibles pour tous les messages
            reactButton.visibility = View.VISIBLE
            replyButton.visibility = View.VISIBLE
            reactButton.setOnClickListener {
                Log.d(TAG, "Clic sur réagir pour message ${message.id}")
                showReactionPicker(message)
            }
            replyButton.setOnClickListener {
                Log.d(TAG, "Clic sur répondre pour message ${message.id}")
                listener.onReplyToMessage(message)
            }
        }

        private fun displayReactions(message: Message) {
            // Vider le conteneur de réactions
            reactionsLayout.removeAllViews()

            // Récupérer et afficher les réactions
            val reactionsList = message.getReactionsList()
            if (reactionsList.isEmpty()) {
                reactionsLayout.visibility = View.GONE
                return
            }

            // Afficher chaque réaction
            reactionsLayout.visibility = View.VISIBLE
            for ((emoji, count) in reactionsList) {
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
            }
        }

        /**
         * Affiche le menu contextuel avec les options appropriées - CORRIGÉ
         */
        private fun showContextMenu(message: Message, isMyMessage: Boolean) {
            // Options disponibles selon si c'est mon message ou pas - CORRIGÉ
            val options = if (isMyMessage) {
                arrayOf("Modifier", "Supprimer", "Réagir", "Répondre")
            } else {
                arrayOf("Réagir", "Répondre") // Seulement ces options pour les messages des autres
            }

            // Afficher le menu contextuel
            android.app.AlertDialog.Builder(context)
                .setTitle("Options")
                .setItems(options) { _, which ->
                    // Traitement des clics - CORRIGÉ
                    if (isMyMessage) {
                        // Pour mes messages
                        when (which) {
                            0 -> listener.onEditMessage(message)
                            1 -> listener.onDeleteMessage(message)
                            2 -> showReactionPicker(message)
                            3 -> listener.onReplyToMessage(message)
                        }
                    } else {
                        // Pour les messages des autres
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

            android.app.AlertDialog.Builder(context)
                .setTitle("Choisir une réaction")
                .setItems(emojis) { _, which ->
                    listener.onReactToMessage(message, emojis[which])
                }
                .show()
        }
    }
}