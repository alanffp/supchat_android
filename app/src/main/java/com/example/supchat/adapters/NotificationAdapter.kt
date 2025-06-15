package com.example.supchat.ui.notifications

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.supchat.R
import com.example.supchat.models.response.notifications.Notification
import com.example.supchat.models.response.notifications.isChannelMessage
import com.example.supchat.models.response.notifications.isPrivateMessage
import com.example.supchat.models.response.notifications.isWorkspaceInvite
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class NotificationAdapter(
    private var notifications: MutableList<Notification>,
    private val onNotificationClick: (Notification) -> Unit,
    private val onMarkAsRead: (Notification) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    class NotificationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconNotification: ImageView = view.findViewById(R.id.icon_notification)
        val titleNotification: TextView = view.findViewById(R.id.title_notification)
        val messageNotification: TextView = view.findViewById(R.id.message_notification)
        val timeNotification: TextView = view.findViewById(R.id.time_notification)
        val unreadIndicator: View = view.findViewById(R.id.unread_indicator)
        val markAsReadButton: ImageView = view.findViewById(R.id.mark_as_read_button)
        val typeBadge: TextView = view.findViewById(R.id.notification_type_badge)
        val readStatus: TextView = view.findViewById(R.id.read_status)
        val menuButton: ImageView? = view.findViewById(R.id.notification_menu_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]
        val context = holder.itemView.context

        // ===== CONFIGURATION DU TYPE ET ICÔNE =====
        when {
            notification.isPrivateMessage() -> {
                holder.iconNotification.setImageResource(android.R.drawable.ic_dialog_email)
                holder.titleNotification.text = "Message privé"
                holder.typeBadge.text = "💬 PRIVÉ"
                holder.typeBadge.setTextColor(context.getColor(android.R.color.holo_green_light))
            }
            notification.isChannelMessage() -> {
                holder.iconNotification.setImageResource(android.R.drawable.ic_menu_view)
                holder.titleNotification.text = "Message canal"
                holder.typeBadge.text = "📺 CANAL"
                holder.typeBadge.setTextColor(context.getColor(android.R.color.holo_blue_light))
            }
            notification.isWorkspaceInvite() -> {
                holder.iconNotification.setImageResource(android.R.drawable.ic_menu_add)
                holder.titleNotification.text = "Invitation workspace"
                holder.typeBadge.text = "🏢 WORKSPACE"
                holder.typeBadge.setTextColor(context.getColor(android.R.color.holo_orange_light))
            }
            else -> {
                holder.iconNotification.setImageResource(android.R.drawable.ic_popup_reminder)
                holder.titleNotification.text = getNotificationTitle(notification)
                holder.typeBadge.text = "🔔 ${notification.type.uppercase()}"
                holder.typeBadge.setTextColor(context.getColor(android.R.color.holo_purple))
            }
        }

        // ===== MESSAGE DE LA NOTIFICATION =====
        holder.messageNotification.text = notification.message

        // ===== FORMATAGE DE LA DATE/HEURE =====
        val formattedTime = formatNotificationTime(notification.createdAt)
        holder.timeNotification.text = formattedTime

        // ===== INDICATEUR DE LECTURE =====
        val isUnread = !notification.lu

        // Barre indicatrice
        holder.unreadIndicator.visibility = if (isUnread) View.VISIBLE else View.GONE

        // Statut de lecture
        if (isUnread) {
            holder.readStatus.text = "📬 Non lu"
            holder.readStatus.setTextColor(context.getColor(android.R.color.holo_orange_dark))
            holder.readStatus.visibility = View.VISIBLE
        } else {
            holder.readStatus.text = "✅ Lu"
            holder.readStatus.setTextColor(context.getColor(android.R.color.holo_green_dark))
            holder.readStatus.visibility = View.VISIBLE
        }

        // ===== BOUTON MARQUER COMME LU =====
        if (isUnread) {
            holder.markAsReadButton.visibility = View.VISIBLE
            holder.markAsReadButton.setImageResource(android.R.drawable.ic_menu_view)
            holder.markAsReadButton.imageTintList =
                android.content.res.ColorStateList.valueOf(context.getColor(android.R.color.holo_orange_dark))
        } else {
            holder.markAsReadButton.visibility = View.VISIBLE
            // Remplacé ic_menu_done par ic_dialog_info (icône checkmark alternative)
            holder.markAsReadButton.setImageResource(android.R.drawable.ic_dialog_info)
            holder.markAsReadButton.imageTintList =
                android.content.res.ColorStateList.valueOf(context.getColor(android.R.color.holo_green_dark))
        }

        // ===== STYLE GLOBAL DE L'ITEM =====
        if (isUnread) {
            // Style pour notification non lue
            holder.itemView.setBackgroundColor(context.getColor(android.R.color.background_dark))
            holder.itemView.alpha = 1.0f
            holder.titleNotification.setTextColor(context.getColor(android.R.color.white))
            holder.messageNotification.setTextColor(context.getColor(android.R.color.background_light))
        } else {
            // Style pour notification lue
            holder.itemView.setBackgroundColor(context.getColor(android.R.color.darker_gray))
            holder.itemView.alpha = 0.7f
            holder.titleNotification.setTextColor(context.getColor(android.R.color.tertiary_text_dark))
            holder.messageNotification.setTextColor(context.getColor(android.R.color.secondary_text_dark))
        }

        // ===== LISTENERS =====
        holder.itemView.setOnClickListener {
            onNotificationClick(notification)
        }

        holder.markAsReadButton.setOnClickListener {
            if (isUnread) {
                onMarkAsRead(notification)
            }
        }

        // Menu optionnel (si présent) - Version simplifiée sans menu XML
        holder.menuButton?.setOnClickListener {
            showNotificationMenu(holder.itemView, notification)
        }
    }

    override fun getItemCount(): Int = notifications.size

    // ===== MÉTHODES UTILITAIRES =====

    private fun getNotificationTitle(notification: Notification): String {
        return when (notification.type) {
            "friend_request" -> "Demande d'ami"
            "system" -> "Notification système"
            "mention" -> "Vous êtes mentionné"
            "reaction" -> "Réaction à votre message"
            else -> "Notification"
        }
    }

    private fun formatNotificationTime(createdAt: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            val date = inputFormat.parse(createdAt)

            if (date != null) {
                val now = Date()
                val diffInMillis = now.time - date.time
                val diffInHours = diffInMillis / (1000 * 60 * 60)
                val diffInDays = diffInHours / 24

                when {
                    diffInMillis < 60000 -> "À l'instant" // < 1 minute
                    diffInMillis < 3600000 -> "${diffInMillis / 60000}m" // < 1 heure
                    diffInHours < 24 -> "${diffInHours}h" // < 1 jour
                    diffInDays < 7 -> "${diffInDays}j" // < 1 semaine
                    else -> dateFormat.format(date) // Date complète
                }
            } else {
                createdAt
            }
        } catch (e: Exception) {
            // Fallback en cas d'erreur de parsing
            createdAt.take(10) // Juste la date
        }
    }

    private fun showNotificationMenu(view: View, notification: Notification) {
        // Version simplifiée avec PopupMenu créé programmatiquement
        android.widget.PopupMenu(view.context, view).apply {
            // Ajouter les éléments de menu programmatiquement
            menu.add(0, 1, 0, "Marquer comme lu")
            menu.add(0, 2, 0, "Supprimer")

            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> { // Marquer comme lu
                        onMarkAsRead(notification)
                        true
                    }
                    2 -> { // Supprimer
                        // Logique pour supprimer (à implémenter)
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    // ===== MÉTHODES PUBLIQUES POUR GESTION =====

    fun updateNotifications(newNotifications: List<Notification>) {
        notifications.clear()
        notifications.addAll(newNotifications.sortedByDescending { it.createdAt })
        notifyDataSetChanged()
    }

    fun markNotificationAsRead(notificationId: String) {
        val index = notifications.indexOfFirst { it.id == notificationId }
        if (index != -1) {
            // Créer une nouvelle notification avec lu = true
            val updatedNotification = notifications[index].copy(lu = true)
            notifications[index] = updatedNotification
            notifyItemChanged(index)
        }
    }

    fun addNotification(notification: Notification) {
        notifications.add(0, notification) // Ajouter en premier
        notifyItemInserted(0)
    }

    fun removeNotification(notificationId: String) {
        val index = notifications.indexOfFirst { it.id == notificationId }
        if (index != -1) {
            notifications.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun getUnreadCount(): Int {
        return notifications.count { !it.lu }
    }

    fun getNotificationById(id: String): Notification? {
        return notifications.find { it.id == id }
    }

    // Filtrer par type
    fun filterByType(type: String): List<Notification> {
        return notifications.filter { it.type == type }
    }

    // Obtenir seulement les non lues
    fun getUnreadNotifications(): List<Notification> {
        return notifications.filter { !it.lu }
    }
}