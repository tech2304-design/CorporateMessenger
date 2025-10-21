package com.example.messenger.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.messenger.R
import com.example.messenger.data.MessageRepository
import com.example.messenger.data.entities.MessageEntity
import com.example.messenger.network.SocketManager
import com.example.messenger.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

class ChatActivity : AppCompatActivity() {
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private lateinit var adapter: ChatAdapter
    private var currentUserId: Long = 0L
    private var recipientId: Long = 0L
    private var recipientUsername: String = ""
    private var isListening = false
    private var lastMessageId: Long = 0L
    
    companion object {
        private const val REQUEST_PICK_FILE = 1001
        private const val REQUEST_NOTIFICATION_PERMISSION = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // Get user information from intent (use getLongExtra if extras are long)
        recipientId = intent.getLongExtra("userId", intent.getIntExtra("userId", 0).toLong())
        recipientUsername = intent.getStringExtra("username") ?: "Unknown"
        currentUserId = intent.getLongExtra("currentUserId", intent.getIntExtra("currentUserId", 0).toLong())
        
        // Set title to show who we're chatting with
        title = "Чат с $recipientUsername"

        // Initialize notification channel
        NotificationHelper.createNotificationChannel(this)

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            }
        }

        val rvMessages = findViewById<RecyclerView>(R.id.rvMessages)
        val etMessage = findViewById<EditText>(R.id.etMessage)
        val btnSend = findViewById<Button>(R.id.btnSend)
        val btnAttach = findViewById<Button>(R.id.btnAttach)

        adapter = ChatAdapter(currentUserId)
        rvMessages.adapter = adapter
        rvMessages.layoutManager = LinearLayoutManager(this)

        val repo = MessageRepository.get()
        repo.observeLocalMessages(recipientId) { list ->
            runOnUiThread {
                adapter.submitList(list)
                // Scroll to bottom when new message arrives
                if (list.isNotEmpty()) {
                    rvMessages.smoothScrollToPosition(list.size - 1)
                    if (list.isNotEmpty()) {
                        lastMessageId = list.maxOf { it.id }
                    }
                }
            }
        }

        btnSend.setOnClickListener {
            val text = etMessage.text.toString()
            if (text.isNotEmpty()) {
                sendMessage(text)
                etMessage.text.clear()
            }
        }
        
        btnAttach.setOnClickListener {
            openFilePicker()
        }
        
        // Start listening for real-time updates
        startMessageListener()
    }
    
    private fun sendMessage(text: String) {
        scope.launch(Dispatchers.IO) {
            try {
                SocketManager.connect("10.0.2.2", 12345)
                
                // Send message
                val msgData = JSONObject().apply {
                    put("recipient_id", recipientId)
                    put("text", text)
                }
                SocketManager.sendLine("SEND_MSG:$msgData")
                
                val response = SocketManager.readLine()
                if (response?.startsWith("MSG_SENT:") == true) {
                    val msgId = response.substringAfter("MSG_SENT:").toLongOrNull() ?: 0L
                    
                    // Save to local database
                    val msg = MessageEntity(
                        id = msgId,
                        senderId = currentUserId,
                        recipientId = recipientId,
                        groupId = null,
                        text = text,
                        filePath = null,
                        timestamp = System.currentTimeMillis(),
                        isRead = false
                    )
                    MessageRepository.get().insertLocal(msg)
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(this@ChatActivity, "Ошибка отправки: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                SocketManager.disconnect()
            }
        }
    }
    
    private fun startMessageListener() {
        isListening = true
        scope.launch(Dispatchers.IO) {
            while (isListening) {
                try {
                    // Poll for new messages every 3 seconds
                    delay(3000)
                    SocketManager.connect("10.0.2.2", 12345)
                    SocketManager.sendLine("GET_MESSAGES:$recipientId")
                    
                    val response = SocketManager.readLine()
                    if (response?.startsWith("MESSAGES:") == true) {
                        val jsonStr = response.substringAfter("MESSAGES:")
                        val jsonArray = org.json.JSONArray(jsonStr)
                        
                        var hasNewMessage = false
                        for (i in 0 until jsonArray.length()) {
                            val msgObj = jsonArray.getJSONObject(i)
                            val msgId = msgObj.getLong("id")
                            
                            // Check if this is a new message
                            if (msgId > lastMessageId) {
                                hasNewMessage = true
                                val senderId = msgObj.getLong("sender_id")
                                val messageText = msgObj.optString("text", null)
                                
                                val msg = MessageEntity(
                                    id = msgId,
                                    senderId = senderId,
                                    recipientId = msgObj.getLong("recipient_id"),
                                    groupId = null,
                                    text = messageText,
                                    filePath = msgObj.optString("file_path", null),
                                    timestamp = msgObj.getLong("timestamp"),
                                    isRead = false
                                )
                                MessageRepository.get().insertLocal(msg)
                                
                                // Show notification if app is in background and message is from recipient
                                if (senderId == recipientId && !NotificationHelper.isAppInForeground(this@ChatActivity)) {
                                    launch(Dispatchers.Main) {
                                        NotificationHelper.showMessageNotification(
                                            this@ChatActivity,
                                            recipientUsername,
                                            messageText ?: "[Файл]",
                                            recipientId.toInt(),
                                            currentUserId.toInt()
                                        )
                                    }
                                }
                            }
                        }
                        
                        if (hasNewMessage) {
                            lastMessageId = jsonArray.let { arr ->
                                var maxId = lastMessageId
                                for (i in 0 until arr.length()) {
                                    val id = arr.getJSONObject(i).getLong("id")
                                    if (id > maxId) maxId = id
                                }
                                maxId
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore errors during polling
                } finally {
                    SocketManager.disconnect()
                }
            }
        }
    }
    
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "image/*",
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "text/plain"
            ))
        }
        startActivityForResult(Intent.createChooser(intent, "Выберите файл"), REQUEST_PICK_FILE)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                handleFileSelection(uri)
            }
        }
    }
    
    private fun handleFileSelection(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            try {
                val fileName = getFileName(uri)
                val fileSize = getFileSize(uri)
                
                SocketManager.connect("10.0.2.2", 12345)
                
                // Request file upload
                val uploadData = JSONObject().apply {
                    put("recipient_id", recipientId)
                    put("filename", fileName)
                    put("size", fileSize)
                }
                SocketManager.sendLine("UPLOAD_FILE:$uploadData")
                
                val response = SocketManager.readLine()
                if (response == "READY_FOR_FILE") {
                    // Send file data
                    contentResolver.openInputStream(uri)?.use { input ->
                        val socket = SocketManager.getSocket()
                        socket?.getOutputStream()?.let { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                            }
                            output.flush()
                        }
                    }
                    
                    val uploadResponse = SocketManager.readLine()
                    if (uploadResponse?.startsWith("FILE_UPLOADED:") == true) {
                        val msgId = uploadResponse.substringAfter("FILE_UPLOADED:").toLongOrNull() ?: 0L
                        
                        // Save to local database
                        val msg = MessageEntity(
                            id = msgId,
                            senderId = currentUserId,
                            recipientId = recipientId,
                            groupId = null,
                            text = "[File: $fileName]",
                            filePath = fileName,
                            timestamp = System.currentTimeMillis(),
                            isRead = false
                        )
                        MessageRepository.get().insertLocal(msg)
                        
                        launch(Dispatchers.Main) {
                            Toast.makeText(this@ChatActivity, "Файл отправлен: $fileName", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(this@ChatActivity, "Ошибка загрузки файла: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                SocketManager.disconnect()
            }
        }
    }
    
    private fun getFileName(uri: Uri): String {
        var name = "unknown"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
    
    private fun getFileSize(uri: Uri): Long {
        var size = 0L
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIndex >= 0) {
                size = cursor.getLong(sizeIndex)
            }
        }
        return size
    }

    override fun onDestroy() {
        super.onDestroy()
        isListening = false
        job.cancel()
    }
}