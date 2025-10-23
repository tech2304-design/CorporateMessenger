package com.example.messenger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.example.messenger.data.entities.MessageEntity

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    // Возвращаем Flow списка сообщений для пользователя (используем Long)
    @Query("""
        SELECT * FROM messages
        WHERE (sender_id = :userId OR recipient_id = :userId)
        ORDER BY timestamp ASC, id ASC
    """)
    fun getForUserFlow(userId: Long): Flow<List<MessageEntity>>
    
    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteById(messageId: Long)
}