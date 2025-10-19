package com.example.messenger.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.messenger.R
import com.example.messenger.data.AppDatabase
import com.example.messenger.data.MessageRepository
import com.example.messenger.data.entities.UserEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ContactsActivity : AppCompatActivity() {
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private lateinit var adapter: ContactsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)

        // Initialize MessageRepository
        MessageRepository.init(applicationContext)

        val rvContacts = findViewById<RecyclerView>(R.id.rvContacts)
        adapter = ContactsAdapter { user ->
            // Open chat with selected user
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("userId", user.id)
            intent.putExtra("username", user.username)
            startActivity(intent)
        }

        rvContacts.adapter = adapter
        rvContacts.layoutManager = LinearLayoutManager(this)

        // Load contacts from database
        val db = AppDatabase.getInstance(this)
        scope.launch {
            db.userDao().getAllFlow().collect { users ->
                runOnUiThread {
                    adapter.submitList(users)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
