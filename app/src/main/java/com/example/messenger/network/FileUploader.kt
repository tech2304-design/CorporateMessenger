package com.example.messenger.network

import java.io.File

object FileUploader {
    fun upload(
        recipient: String,
        filename: String,
        bytes: ByteArray,
        progress: (Int) -> Unit
    ) {
        // Здесь можно реализовать логику загрузки файла
        // Например, через HTTP или сокеты
    }
}
