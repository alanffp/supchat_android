package com.example.supchat.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.supchat.R
import com.example.supchat.models.response.messageprivate.ConversationMessage
import com.example.supchat.models.response.messageprivate.isReadBy
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.Locale

class ConversationsAdapter(
    private val context: Context,
    private val currentUserId: String,
    private val onConversationClick: (ConversationMessage) -> Unit
) : RecyclerView.Adapter<ConversationsAdapter.ViewHolder>() {

    private var conversations: List<ConversationMessage> = emptyList()
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())

    fun updateConversations(newConversations: List<ConversationMessage>) {
        conversations = newConversations
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_conversation, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = conversations.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val conversation = conversations[position]

        holder.usernameText.text = if (conversation.expediteurId != currentUserId) {
            "Utilisateur ${conversation.expediteurId.take(8)}"
        } else {
            "Moi"
        }

        holder.lastMessageText.text = conversation.contenu

        try {
            val date = inputFormat.parse(conversation.horodatage)
            holder.timeText.text = date?.let { dateFormat.format(it) } ?: ""
        } catch (e: Exception) {
            holder.timeText.text = ""
        }

        holder.unreadIndicator.visibility = if (!conversation.isReadBy(currentUserId) && conversation.expediteurId != currentUserId) {
            View.VISIBLE
        } else {
            View.GONE
        }

        holder.profileImage.setImageResource(R.drawable.default_profile)

        holder.itemView.setOnClickListener {
            onConversationClick(conversation)
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val profileImage: CircleImageView = view.findViewById(R.id.profile_image)
        val usernameText: TextView = view.findViewById(R.id.username_text)
        val lastMessageText: TextView = view.findViewById(R.id.last_message_text)
        val timeText: TextView = view.findViewById(R.id.time_text)
        val unreadIndicator: View = view.findViewById(R.id.unread_indicator)
    }
}