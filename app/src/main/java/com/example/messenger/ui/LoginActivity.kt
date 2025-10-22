package com.example.messenger.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.messenger.R
import com.example.messenger.network.ServerConfig
import com.example.messenger.network.SocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString()
            val password = etPassword.text.toString()
            if (username.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Введите логин и пароль", Toast.LENGTH_SHORT).show()
            } else {
                scope.launch(Dispatchers.IO) {
                    try {
                        SocketManager.connect(ServerConfig.SERVER_HOST, ServerConfig.SERVER_PORT)
                        SocketManager.sendLine("AUTH:$username:$password")
                        val response = SocketManager.readLine()
                        if (response?.startsWith("AUTH_OK:") == true) {
                            val userId = response.substringAfter("AUTH_OK:").toIntOrNull() ?: 0
                            launch(Dispatchers.Main) {
                                Toast.makeText(this@LoginActivity, "Авторизация успешна", Toast.LENGTH_SHORT).show()
                                // Navigate to contacts
                                val intent = Intent(this@LoginActivity, ContactsActivity::class.java)
                                intent.putExtra("userId", userId)
                                intent.putExtra("username", username)
                                intent.putExtra("password", password)
                                startActivity(intent)
                                finish()
                            }
                        } else {
                            launch(Dispatchers.Main) {
                                Toast.makeText(this@LoginActivity, "Ошибка авторизации", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        launch(Dispatchers.Main) {
                            Toast.makeText(this@LoginActivity, "Ошибка подключения: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
