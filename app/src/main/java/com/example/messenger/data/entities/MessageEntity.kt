package com.example.messenger.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,

    @ColumnInfo(name = "sender_id")
    val senderId: Int,

    @ColumnInfo(name = "recipient_id")
    val recipientId: Int?, // nullable, т.к. может быть групповой чат

    @ColumnInfo(name = "group_id")
    val groupId: Int?,     // nullable, если это групповой чат

    @ColumnInfo(name = "message_text")
    val text: String?,

    @ColumnInfo(name = "file_path")
    val filePath: String?,

    val timestamp: Long,

    @ColumnInfo(name = "is_read")
    val isRead: Boolean
)