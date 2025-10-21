package com.example.messenger.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val username: String,
    @ColumnInfo(name = "password_hash")
    val passwordHash: String = "",
    val salt: String = "",
    val isOnline: Boolean = false
)