package com.example.messenger.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,

    @ColumnInfo(name = "sender_id")
    val senderId: Long,

    @ColumnInfo(name = "recipient_id")
    val recipientId: Long?, // nullable, т.к. может быть групповой чат

    @ColumnInfo(name = "group_id")
    val groupId: Long?,     // nullable, если это групповой чат

    @ColumnInfo(name = "message_text")
    val text: String? = null,

    @ColumnInfo(name = "file_path")
    val filePath: String? = null,

    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_read")
    val isRead: Boolean = false
)