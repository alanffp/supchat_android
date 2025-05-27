package com.example.supchat.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.supchat.R
import com.example.supchat.models.response.Member

class MembersAdapter(
    private val members: List<Member>,
    private val onRoleUpdateClick: (Member) -> Unit,
    private val onRemoveClick: (Member) -> Unit
) : RecyclerView.Adapter<MembersAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val usernameTextView: TextView = view.findViewById(R.id.text_member_name)
        val roleTextView: TextView = view.findViewById(R.id.text_member_role)
        val roleButton: ImageButton = view.findViewById(R.id.btn_change_role)
        val removeButton: ImageButton = view.findViewById(R.id.btn_remove_member)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_member, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val member = members[position]

        // Utilise directement username au lieu de member.utilisateur.username
        holder.usernameTextView.text = member.username
        holder.roleTextView.text = "Rôle: ${member.role}"

        // Configurer les listeners
        holder.roleButton.setOnClickListener { onRoleUpdateClick(member) }
        holder.removeButton.setOnClickListener { onRemoveClick(member) }
    }

    override fun getItemCount() = members.size
}