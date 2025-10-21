package com.example.messenger.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.messenger.R
import com.example.messenger.data.entities.UserEntity

class ContactsAdapter(
    private val onItemClick: (UserEntity) -> Unit
) : ListAdapter<UserEntity, ContactsAdapter.ContactViewHolder>(UserDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val user = getItem(position)
        holder.bind(user)
    }

    class ContactViewHolder(
        itemView: View,
        private val onItemClick: (UserEntity) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvUsername: TextView = itemView.findViewById(R.id.tvUsername)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val statusIndicator: View = itemView.findViewById(R.id.statusIndicator)

        fun bind(user: UserEntity) {
            tvUsername.text = user.username
            tvStatus.text = if (user.isOnline) "В сети" else "Не в сети"
            
            // Set status indicator color
            statusIndicator.isSelected = user.isOnline
            
            itemView.setOnClickListener {
                onItemClick(user)
            }
        }
    }

    class UserDiffCallback : DiffUtil.ItemCallback<UserEntity>() {
        override fun areItemsTheSame(oldItem: UserEntity, newItem: UserEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: UserEntity, newItem: UserEntity): Boolean {
            return oldItem == newItem
        }
    }
}
