package com.example.supchat.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.supchat.R
import com.example.supchat.models.response.Invitation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adaptateur pour afficher la liste des invitations d'un workspace dans un RecyclerView
 */
class InvitationsAdapter(
    private val invitations: List<Invitation>,
    private val onRevoke: (Invitation) -> Unit
) : RecyclerView.Adapter<InvitationsAdapter.ViewHolder>() {

    /**
     * ViewHolder pour les éléments de la liste des invitations
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val emailTextView: TextView = view.findViewById(R.id.text_invitation_email)
        val dateTextView: TextView = view.findViewById(R.id.text_invitation_date)
        val revokeButton: ImageButton = view.findViewById(R.id.btn_revoke_invitation)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_invitation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val invitation = invitations[position]

        // Afficher les informations de l'invitation
        holder.emailTextView.text = invitation.email

        // Formater la date si disponible
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateText = if (invitation.dateInvitation != null) {
            "Envoyée le: ${dateFormat.format(Date(invitation.dateInvitation))}"
        } else {
            "Date d'envoi inconnue"
        }
        holder.dateTextView.text = dateText

        // Configurer le bouton de révocation
        holder.revokeButton.setOnClickListener { onRevoke(invitation) }
    }

    override fun getItemCount() = invitations.size
}