package com.example.supchat.adapters

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.supchat.R
import com.example.supchat.models.response.Workspace


class WorkspaceAdapter(
    private val workspaces: List<Workspace>,
    private val userId: String,
    private val isPublicList: Boolean = false,
    private val onItemClick: (Workspace) -> Unit,
    private val onEditClick: (Workspace) -> Unit,
    private val onDeleteClick: (Workspace) -> Unit,
    private val onLeaveClick: (Workspace) -> Unit
) : RecyclerView.Adapter<WorkspaceAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.text_workspace_name)
        val descriptionTextView: TextView = view.findViewById(R.id.text_workspace_description)
        val editButton: Button = view.findViewById(R.id.btn_edit_workspace)
        val deleteButton: Button = view.findViewById(R.id.btn_delete_workspace)
        val leaveButton: Button = view.findViewById(R.id.btn_leave_workspace)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_workspace, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val workspace = workspaces[position]

        // Configurer les textes
        holder.nameTextView.text = workspace.nom
        holder.descriptionTextView.text = workspace.description ?: "Aucune description"

        // Configurer l'action au clic sur l'élément
        holder.itemView.setOnClickListener { onItemClick(workspace) }

        // Si c'est une liste publique, masquer tous les boutons d'action
        if (isPublicList) {
            Log.d("WorkspaceAdapter", "Liste publique: masquage des boutons pour ${workspace.nom}")
            holder.editButton.visibility = View.GONE
            holder.deleteButton.visibility = View.GONE
            holder.leaveButton.visibility = View.VISIBLE
            return
        }

        // Forcer isOwner à true pour tous les workspaces
        val isOwner = true

        Log.d("WorkspaceAdapter", "L'utilisateur est-il propriétaire? $isOwner")

        // Configurer la visibilité des boutons en fonction du statut de propriétaire
        if (isOwner) {
            // L'utilisateur est le propriétaire - Montrer les boutons d'édition et de suppression
            Log.d("WorkspaceAdapter", "PROPRIÉTAIRE: Afficher boutons modifier/supprimer")
            holder.editButton.visibility = View.VISIBLE
            holder.deleteButton.visibility = View.VISIBLE
            holder.leaveButton.visibility = View.VISIBLE

            // Configurer les actions des boutons
            holder.editButton.setOnClickListener { onEditClick(workspace) }
            holder.deleteButton.setOnClickListener { onDeleteClick(workspace) }
        } else {
            // L'utilisateur n'est pas le propriétaire - Montrer uniquement le bouton quitter
            Log.d("WorkspaceAdapter", "NON PROPRIÉTAIRE: Afficher seulement bouton quitter")
            holder.editButton.visibility = View.GONE
            holder.deleteButton.visibility = View.GONE
            holder.leaveButton.visibility = View.VISIBLE

            // Configurer l'action du bouton Quitter
            holder.leaveButton.setOnClickListener { onLeaveClick(workspace) }
        }
    }
    override fun getItemCount() = workspaces.size

}