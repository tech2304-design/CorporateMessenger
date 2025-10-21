package com.example.messenger.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
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
        private val itemViewRoot: View,
        private val onItemClick: (UserEntity) -> Unit
    ) : RecyclerView.ViewHolder(itemViewRoot) {

        // helper: find view by name (safe at runtime; doesn't reference missing R.id.* at compile-time)
        private fun findTextViewByName(view: View, name: String): TextView? {
            val id = view.context.resources.getIdentifier(name, "id", view.context.packageName)
            return if (id != 0) view.findViewById(id) else null
        }

        private val tvUsername: TextView? = findTextViewByName(itemViewRoot, "tvUsername")
            ?: findTextViewByName(itemViewRoot, "tvContactName")

        private val tvStatus: TextView? = findTextViewByName(itemViewRoot, "tvStatus")
            ?: findTextViewByName(itemViewRoot, "tvContactStatus")

        private val statusIndicator: View? = run {
            val id = itemViewRoot.context.resources.getIdentifier("statusIndicator", "id", itemViewRoot.context.packageName)
            if (id != 0) itemViewRoot.findViewById(id) else null
        }

        fun bind(user: UserEntity) {
            // Defensive: only update views that exist in current layout
            tvUsername?.text = user.username
            tvStatus?.text = if (user.isOnline) "В сети" else "Не в сети"

            statusIndicator?.isVisible = statusIndicator != null
            statusIndicator?.isSelected = user.isOnline

            itemViewRoot.setOnClickListener {
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