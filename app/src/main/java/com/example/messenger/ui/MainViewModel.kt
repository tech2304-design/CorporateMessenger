package com.example.messenger.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    fun sendMessage(recipient: String, message: String) {
        viewModelScope.launch {
            // call SocketManager.sendMsg...
        }
    }
}
