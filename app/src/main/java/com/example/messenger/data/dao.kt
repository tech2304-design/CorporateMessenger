package com.example.messenger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.messenger.data.entities.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(msg: MessageEntity): Long

    // Используем snake_case (как в серверной схеме/schema.sql)
    @Query("SELECT * FROM messages WHERE (recipient_id = :uid OR sender_id = :uid) ORDER BY timestamp ASC")
    fun getForUserFlow(uid: Int): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAllFlow(): Flow<List<MessageEntity>>
}