package com.example.supchat.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.supchat.R
import com.example.supchat.models.response.messageprivate.ConversationParticipant
import de.hdodenhof.circleimageview.CircleImageView

class ParticipantsAdapter(
    private val context: Context,
    private val currentUserId: String,
    private val conversationCreatorId: String,
    private val onRemoveParticipant: (ConversationParticipant) -> Unit
) : RecyclerView.Adapter<ParticipantsAdapter.ParticipantViewHolder>() {

    private var participants = mutableListOf<ConversationParticipant>()
    private var expandedPosition = -1 // Position de l'élément actuellement étendu

    inner class ParticipantViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val participantAvatar: CircleImageView = itemView.findViewById(R.id.participant_avatar)
        val participantUsername: TextView = itemView.findViewById(R.id.participant_username)
        val participantRole: TextView = itemView.findViewById(R.id.participant_role)
        val participantStatus: TextView = itemView.findViewById(R.id.participant_status)
        val expandIcon: ImageView = itemView.findViewById(R.id.participant_expand_icon)
        val mainLayout: LinearLayout = itemView.findViewById(R.id.participant_main_layout)
        val actionsLayout: LinearLayout = itemView.findViewById(R.id.participant_actions_layout)
        val removeButton: Button = itemView.findViewById(R.id.remove_participant_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParticipantViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_participant, parent, false)
        return ParticipantViewHolder(view)
    }

    override fun onBindViewHolder(holder: ParticipantViewHolder, position: Int) {
        val participant = participants[position]
        val isExpanded = position == expandedPosition
        val isCreator = participant.utilisateur == conversationCreatorId
        val isCurrentUser = participant.utilisateur == currentUserId
        val canRemove = (currentUserId == conversationCreatorId) && !isCurrentUser

        // Informations du participant
        holder.participantUsername.text = participant.username.ifEmpty { "Utilisateur inconnu" }

        // Statut du participant
        if (isCreator) {
            holder.participantStatus.visibility = View.VISIBLE
            holder.participantStatus.text = "Créateur"
            holder.participantStatus.setBackgroundColor(
                android.graphics.Color.parseColor("#F1C40F")
            )
        } else if (isCurrentUser) {
            holder.participantStatus.visibility = View.VISIBLE
            holder.participantStatus.text = "Vous"
            holder.participantStatus.setBackgroundColor(
                android.graphics.Color.parseColor("#3498DB")
            )
        } else {
            holder.participantStatus.visibility = View.GONE
        }

        // Icône d'expansion (seulement si on peut supprimer)
        if (canRemove) {
            holder.expandIcon.visibility = View.VISIBLE
            holder.expandIcon.rotation = if (isExpanded) 180f else 0f
        } else {
            holder.expandIcon.visibility = View.GONE
        }

        // Actions étendues
        holder.actionsLayout.visibility = if (isExpanded && canRemove) View.VISIBLE else View.GONE

        // Clic sur le participant principal
        holder.mainLayout.setOnClickListener {
            if (canRemove) {
                val previousExpandedPosition = expandedPosition
                expandedPosition = if (isExpanded) -1 else position

                // Notifier les changements
                if (previousExpandedPosition != -1) {
                    notifyItemChanged(previousExpandedPosition)
                }
                if (expandedPosition != -1) {
                    notifyItemChanged(expandedPosition)
                }
            }
        }

        // Bouton de suppression
        holder.removeButton.setOnClickListener {
            onRemoveParticipant(participant)
            // Rétracter après suppression
            expandedPosition = -1
            notifyItemChanged(position)
        }

        // Charger l'avatar si disponible
        if (!participant.profilePicture.isNullOrEmpty()) {
            // Utiliser Glide ou Picasso pour charger l'image
            // Glide.with(context).load("http://10.0.2.2:3000/uploads/profile-pictures/${participant.profilePicture}").into(holder.participantAvatar)
        }
    }

    override fun getItemCount(): Int = participants.size

    fun updateParticipants(newParticipants: List<ConversationParticipant>) {
        participants.clear()
        participants.addAll(newParticipants)
        expandedPosition = -1 // Réinitialiser l'expansion
        notifyDataSetChanged()
    }

    fun addParticipant(participant: ConversationParticipant) {
        participants.add(participant)
        notifyItemInserted(participants.size - 1)
    }

    fun removeParticipant(participantId: String) {
        val index = participants.indexOfFirst { it.utilisateur == participantId }
        if (index != -1) {
            participants.removeAt(index)
            if (expandedPosition == index) {
                expandedPosition = -1
            } else if (expandedPosition > index) {
                expandedPosition--
            }
            notifyItemRemoved(index)
        }
    }
}