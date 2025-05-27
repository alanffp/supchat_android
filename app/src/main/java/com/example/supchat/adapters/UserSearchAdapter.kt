package com.example.supchat.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.supchat.R
import com.example.supchat.models.response.UserSearchData
import de.hdodenhof.circleimageview.CircleImageView


class UserSearchAdapter(
    private val context: Context,
    private val userList: MutableList<UserSearchData> = mutableListOf(),
    private val onUserClickListener: OnUserClickListener
) : RecyclerView.Adapter<UserSearchAdapter.UserViewHolder>() {

    interface OnUserClickListener {
        fun onUserClick(user: UserSearchData)
        fun onActionButtonClick(user: UserSearchData)
    }

    fun updateUsers(newUsers: List<UserSearchData>) {
        userList.clear()
        userList.addAll(newUsers)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_search, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = userList[position]
        holder.bind(user)
    }

    override fun getItemCount(): Int = userList.size

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val profileImage: CircleImageView = itemView.findViewById(R.id.user_profile_image)
        private val usernameText: TextView = itemView.findViewById(R.id.user_username)
        private val emailText: TextView = itemView.findViewById(R.id.user_email)
        private val statusText: TextView = itemView.findViewById(R.id.user_status)
        private val actionButton: Button = itemView.findViewById(R.id.action_button)

        fun bind(user: UserSearchData) {
            usernameText.text = user.username
            emailText.text = user.email
            statusText.text = user.status ?: "Statut inconnu"

            // Charger l'image de profil si disponible
            if (!user.profilePicture.isNullOrEmpty()) {
                val baseUrl = "http://10.0.2.2:3000/uploads/profile/"
                val imageUrl = baseUrl + user.profilePicture

                Glide.with(context)
                    .load(imageUrl)
                    .apply(
                        RequestOptions()
                            .placeholder(R.drawable.default_avatar)
                            .error(R.drawable.default_avatar)
                    )
                    .into(profileImage)
            } else {
                profileImage.setImageResource(R.drawable.default_avatar)
            }

            // Configurer les listeners
            itemView.setOnClickListener {
                onUserClickListener.onUserClick(user)
            }

            actionButton.setOnClickListener {
                onUserClickListener.onActionButtonClick(user)
            }
        }
    }
}