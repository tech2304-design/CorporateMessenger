package com.example.messenger.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    @ColumnInfo(name = "password_hash") val passwordHash: String = "",
    val salt: String = "",
    val isOnline: Boolean = false
)
