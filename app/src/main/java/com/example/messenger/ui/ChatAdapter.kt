package com.example.messenger.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.messenger.R
import com.example.messenger.data.entities.MessageEntity
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(
    private val currentUserId: Long,
    private val onDeleteMessage: (MessageEntity, Boolean) -> Unit // message, deleteForEveryone
) : ListAdapter<MessageEntity, ChatAdapter.MessageViewHolder>(MessageDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return if (message.senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutId = if (viewType == VIEW_TYPE_SENT) {
            R.layout.item_message_sent
        } else {
            R.layout.item_message_received
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return MessageViewHolder(view, currentUserId, onDeleteMessage)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = getItem(position)
        holder.bind(message)
    }

    class MessageViewHolder(
        itemView: View,
        private val currentUserId: Long,
        private val onDeleteMessage: (MessageEntity, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvMessageText: TextView = itemView.findViewById(R.id.tvMessageText)
        private val tvMessageTime: TextView = itemView.findViewById(R.id.tvMessageTime)
        private val messageContainer: View = itemView.findViewById(R.id.messageContainer)
        private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        private var currentMessage: MessageEntity? = null

        init {
            // Short click - show context menu
            messageContainer.setOnClickListener { view ->
                currentMessage?.let { message ->
                    showContextMenu(view, message)
                }
            }

            // Long click - show delete dialog
            messageContainer.setOnLongClickListener { view ->
                currentMessage?.let { message ->
                    showDeleteDialog(view.context, message)
                }
                true
            }
        }

        fun bind(message: MessageEntity) {
            currentMessage = message
            tvMessageText.text = message.text ?: message.filePath ?: "[Empty message]"
            tvMessageTime.text = dateFormat.format(Date(message.timestamp))
        }

        private fun showContextMenu(view: View, message: MessageEntity) {
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.message_context_menu, popup.menu)
            
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_copy -> {
                        copyToClipboard(view.context, message)
                        true
                    }
                    R.id.action_forward -> {
                        // TODO: Implement forward functionality
                        true
                    }
                    R.id.action_delete -> {
                        showDeleteDialog(view.context, message)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        private fun showDeleteDialog(context: Context, message: MessageEntity) {
            val items = if (message.senderId == currentUserId) {
                arrayOf("Удалить у себя", "Удалить у всех")
            } else {
                arrayOf("Удалить у себя")
            }

            AlertDialog.Builder(context)
                .setTitle("Удалить сообщение")
                .setItems(items) { _, which ->
                    when (which) {
                        0 -> onDeleteMessage(message, false) // Delete locally
                        1 -> onDeleteMessage(message, true)  // Delete for everyone
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        private fun copyToClipboard(context: Context, message: MessageEntity) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("message", message.text ?: "")
            clipboard.setPrimaryClip(clip)
            
            // Show a toast or snackbar to confirm
            android.widget.Toast.makeText(context, "Скопировано", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<MessageEntity>() {
        override fun areItemsTheSame(oldItem: MessageEntity, newItem: MessageEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MessageEntity, newItem: MessageEntity): Boolean {
            return oldItem == newItem
        }
    }
}