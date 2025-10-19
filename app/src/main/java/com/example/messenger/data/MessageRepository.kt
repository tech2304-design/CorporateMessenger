package com.example.messenger.data

import android.content.Context
import com.example.messenger.data.entities.MessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MessageRepository private constructor(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private var incomingListener: ((MessageEntity) -> Unit)? = null

    fun setIncomingListener(cb: (MessageEntity) -> Unit) {
        incomingListener = cb
    }

    // Смена сигнатуры: теперь принимаем uid и используем getForUserFlow(uid)
    fun observeLocalMessages(uid: Int, cb: (List<MessageEntity>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            db.messageDao().getForUserFlow(uid).collect { list ->
                cb(list)
            }
        }
    }

    suspend fun insertLocal(msg: MessageEntity) {
        db.messageDao().insert(msg)
    }

    fun sendText(recipientId: Int, text: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val msg = MessageEntity(
                senderId = 0, // TODO: get current user ID
                recipientId = recipientId,
                groupId = null,
                text = text,
                filePath = null,
                timestamp = System.currentTimeMillis(),
                isRead = false
            )
            insertLocal(msg)
            // TODO: send to server via SocketManager
        }
    }

    companion object {
        @Volatile private var INSTANCE: MessageRepository? = null

        fun init(context: Context) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) INSTANCE = MessageRepository(context)
                }
            }
        }

        fun get(): MessageRepository {
            return INSTANCE ?: throw IllegalStateException("MessageRepository not initialized. Call init(context) first")
        }
    }
}