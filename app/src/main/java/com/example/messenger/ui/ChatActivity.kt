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
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private lateinit var adapter: ChatAdapter
    private var currentUserId: Int = 0
    private var recipientId: Int = 0
    
    companion object {
        private const val REQUEST_PICK_FILE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // Get recipient information from intent
        recipientId = intent.getIntExtra("userId", 0)
        val username = intent.getStringExtra("username") ?: "Unknown"
        
        // Set title to show who we're chatting with
        title = "Чат с $username"

        val rvMessages = findViewById<RecyclerView>(R.id.rvMessages)
        val etMessage = findViewById<EditText>(R.id.etMessage)
        val btnSend = findViewById<Button>(R.id.btnSend)
        val btnAttach = findViewById<Button>(R.id.btnAttach)

        adapter = ChatAdapter()
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
                repo.sendText(recipientId, text)
                etMessage.text.clear()
            }
        }
        
        btnAttach.setOnClickListener {
            openFilePicker()
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
                val repo = MessageRepository.get()
                
                // Save file info to database
                val msg = MessageEntity(
                    senderId = currentUserId,
                    recipientId = recipientId,
                    groupId = null,
                    text = null,
                    filePath = fileName,
                    timestamp = System.currentTimeMillis(),
                    isRead = false
                )
                repo.insertLocal(msg)
                
                // TODO: Upload file to server
                launch(Dispatchers.Main) {
                    Toast.makeText(this@ChatActivity, "Файл прикреплён: $fileName", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(this@ChatActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
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

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
