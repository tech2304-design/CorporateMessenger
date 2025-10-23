package com.example.messenger.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.messenger.R
import com.example.messenger.data.AppDatabase
import com.example.messenger.data.MessageRepository
import com.example.messenger.data.entities.UserEntity
import com.example.messenger.network.ServerConfig
import com.example.messenger.network.SocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONArray

class ContactsActivity : AppCompatActivity() {
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private lateinit var adapter: ContactsAdapter
    private var currentUserId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)

        // Initialize MessageRepository
        MessageRepository.init(applicationContext)
        
        // Get current user info
        currentUserId = intent.getLongExtra("userId", intent.getIntExtra("userId", 0).toLong())
        val currentUsername = intent.getStringExtra("username") ?: ""
        val currentPassword = intent.getStringExtra("password") ?: ""

        val rvContacts = findViewById<RecyclerView>(R.id.rvContacts)
        adapter = ContactsAdapter { user ->
            // Open chat with selected user
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("userId", user.id)
            intent.putExtra("username", user.username)
            intent.putExtra("currentUserId", currentUserId)
            intent.putExtra("currentUsername", currentUsername)
            intent.putExtra("currentPassword", currentPassword)
            startActivity(intent)
        }

        rvContacts.adapter = adapter
        rvContacts.layoutManager = LinearLayoutManager(this)

        // Load contacts from server
        loadContactsFromServer()
    }
    
    private fun loadContactsFromServer() {
        scope.launch(Dispatchers.IO) {
            try {
                SocketManager.connect(ServerConfig.SERVER_HOST, ServerConfig.SERVER_PORT)
                
                // Authenticate first
                val currentUsername = intent.getStringExtra("username") ?: ""
                val currentPassword = intent.getStringExtra("password") ?: ""
                if (currentUsername.isNotEmpty() && currentPassword.isNotEmpty()) {
                    SocketManager.sendLine("AUTH:$currentUsername:$currentPassword")
                    val authResponse = SocketManager.readLine()
                    if (authResponse?.startsWith("AUTH_OK:") != true) {
                        launch(Dispatchers.Main) {
                            Toast.makeText(this@ContactsActivity, "Ошибка аутентификации", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                }
                
                SocketManager.sendLine("LIST_USERS")
                val response = SocketManager.readLine()
                
                if (response?.startsWith("USERS:") == true) {
                    val jsonStr = response.substringAfter("USERS:")
                    val jsonArray = JSONArray(jsonStr)
                    val users = mutableListOf<UserEntity>()
                    
                    for (i in 0 until jsonArray.length()) {
                        val userObj = jsonArray.getJSONObject(i)
                        val userId = userObj.getLong("id")
                        val username = userObj.getString("username")
                        val isOnline = userObj.optBoolean("isOnline", false)
                        
                        // Don't include current user in contacts list
                        if (userId != currentUserId) {
                            users.add(UserEntity(id = userId, username = username, isOnline = isOnline))
                        }
                    }
                    
                    // Save to database
                    val db = AppDatabase.getInstance(applicationContext)
                    db.userDao().insertAll(users)
                    
                    launch(Dispatchers.Main) {
                        adapter.submitList(users)
                    }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(this@ContactsActivity, "Ошибка загрузки контактов: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                SocketManager.disconnect()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}