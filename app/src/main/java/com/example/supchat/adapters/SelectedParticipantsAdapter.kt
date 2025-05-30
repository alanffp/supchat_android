package com.example.supchat.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.supchat.R
import com.example.supchat.models.response.UserSearchData
import de.hdodenhof.circleimageview.CircleImageView

class SelectedParticipantsAdapter(
    private val context: Context,
    private val participants: MutableList<UserSearchData>,
    private val onRemoveParticipant: (UserSearchData) -> Unit
) : RecyclerView.Adapter<SelectedParticipantsAdapter.SelectedParticipantViewHolder>() {

    inner class SelectedParticipantViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val participantAvatar: CircleImageView = itemView.findViewById(R.id.selected_participant_avatar)
        val participantName: TextView = itemView.findViewById(R.id.selected_participant_name)
        val removeButton: ImageButton = itemView.findViewById(R.id.remove_selected_participant_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectedParticipantViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_selected_participant, parent, false)
        return SelectedParticipantViewHolder(view)
    }

    override fun onBindViewHolder(holder: SelectedParticipantViewHolder, position: Int) {
        val participant = participants[position]

        holder.participantName.text = participant.username

        // Charger l'avatar si disponible
        if (!participant.profilePicture.isNullOrEmpty()) {
            // Glide.with(context).load("http://10.0.2.2:3000/uploads/profile-pictures/${participant.profilePicture}").into(holder.participantAvatar)
        }

        holder.removeButton.setOnClickListener {
            onRemoveParticipant(participant)
        }
    }

    override fun getItemCount(): Int = participants.size
}