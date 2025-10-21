package com.example.messenger.network

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

object SocketManager {
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

    fun connect(host: String, port: Int) {
        try {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            socket = factory.createSocket(host, port) as Socket
            writer = PrintWriter(socket!!.getOutputStream(), true)
            reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
            Log.d("SocketManager", "Connected to $host:$port")
        } catch (e: Exception) {
            Log.e("SocketManager", "Error connecting: $e")
        }
    }

    fun sendLine(line: String) {
        try {
            writer?.println(line)
            writer?.flush()
            Log.d("SocketManager", "Sent: $line")
        } catch (e: Exception) {
            Log.e("SocketManager", "Error sending: $e")
        }
    }

    fun readLine(): String? {
        return try {
            reader?.readLine()
        } catch (e: Exception) {
            Log.e("SocketManager", "Error reading: $e")
            null
        }
    }

    fun disconnect() {
        try {
            writer?.close()
            reader?.close()
            socket?.close()
            socket = null
            Log.d("SocketManager", "Disconnected")
        } catch (e: Exception) {
            Log.e("SocketManager", "Error disconnecting: $e")
        }
    }

    fun isConnected(): Boolean {
        return socket?.isConnected == true && socket?.isClosed == false
    }
    
    fun getSocket(): Socket? {
        return socket
    }
}
