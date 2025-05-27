package com.example.supchat.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.supchat.R
import com.example.supchat.models.response.messageprivate.PrivateMessageItem
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.*

class PrivateMessagesAdapter(
    private val context: Context,
    private val currentUserId: String,
    private val onConversationClick: (PrivateMessageItem) -> Unit
) : RecyclerView.Adapter<PrivateMessagesAdapter.ConversationViewHolder>() {

    private var conversations = mutableListOf<PrivateMessageItem>()

    class ConversationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImageView: CircleImageView = itemView.findViewById(R.id.profile_image)
        val usernameTextView: TextView = itemView.findViewById(R.id.username_text)
        val lastMessageTextView: TextView = itemView.findViewById(R.id.last_message_text)
        val timeTextView: TextView = itemView.findViewById(R.id.time_text)
        val unreadIndicator: View = itemView.findViewById(R.id.unread_indicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_conversation, parent, false)
        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        val conversation = conversations[position]

        // Nom d'utilisateur
        holder.usernameTextView.text = conversation.user.username

        // Dernier message
        holder.lastMessageTextView.text = conversation.lastMessage.contenu

        // Heure du dernier message
        holder.timeTextView.text = formatTime(conversation.lastMessage.horodatage)

        // Photo de profil
        if (!conversation.user.profilePicture.isNullOrEmpty()) {
            Glide.with(context)
                .load("http://10.0.2.2:3000/uploads/profile-pictures/${conversation.user.profilePicture}")
                .placeholder(R.drawable.default_avatar)
                .error(R.drawable.default_avatar)
                .into(holder.profileImageView)
        } else {
            holder.profileImageView.setImageResource(R.drawable.default_avatar)
        }

        // Indicateur de messages non lus
        if (conversation.unreadCount > 0) {
            holder.unreadIndicator.visibility = View.VISIBLE
        } else {
            holder.unreadIndicator.visibility = View.GONE
        }

        // Clic sur la conversation
        holder.itemView.setOnClickListener {
            onConversationClick(conversation)
        }
    }

    override fun getItemCount(): Int = conversations.size

    fun updateConversations(newConversations: List<PrivateMessageItem>) {
        conversations.clear()
        conversations.addAll(newConversations)
        notifyDataSetChanged()
    }

    private fun formatTime(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(timestamp)

            val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            "00:00"
        }
    }
}