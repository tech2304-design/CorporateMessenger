package com.example.messenger.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.messenger.R
import com.example.messenger.data.MessageRepository
import com.example.messenger.data.entities.MessageEntity
import com.example.messenger.network.SocketManager
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
    private var currentUserId: Int = 0
    private var recipientId: Int = 0
    private var isListening = false
    
    companion object {
        private const val REQUEST_PICK_FILE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // Get user information from intent
        recipientId = intent.getIntExtra("userId", 0)
        val username = intent.getStringExtra("username") ?: "Unknown"
        currentUserId = intent.getIntExtra("currentUserId", 0)
        
        // Set title to show who we're chatting with
        title = "Чат с $username"

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
                    val msgId = response.substringAfter("MSG_SENT:").toLongOrNull() ?: 0
                    
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
                        
                        for (i in 0 until jsonArray.length()) {
                            val msgObj = jsonArray.getJSONObject(i)
                            val msg = MessageEntity(
                                id = msgObj.getLong("id"),
                                senderId = msgObj.getInt("sender_id"),
                                recipientId = msgObj.getInt("recipient_id"),
                                groupId = null,
                                text = msgObj.optString("text", null),
                                filePath = msgObj.optString("file_path", null),
                                timestamp = msgObj.getLong("timestamp"),
                                isRead = false
                            )
                            MessageRepository.get().insertLocal(msg)
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
                        val msgId = uploadResponse.substringAfter("FILE_UPLOADED:").toLongOrNull() ?: 0
                        
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
