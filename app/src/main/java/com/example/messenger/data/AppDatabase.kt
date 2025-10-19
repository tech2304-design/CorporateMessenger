package com.example.messenger.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.messenger.data.entities.MessageEntity
import com.example.messenger.data.entities.UserEntity

@Database(
    entities = [MessageEntity::class, UserEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "messenger.db"
                ).fallbackToDestructiveMigration()
                    .build()

                // Добавлен лог для отладки пути БД
                Log.d("DB_PATH", "DB created at: ${instance.openHelper.writableDatabase.path}")

                INSTANCE = instance
                instance
            }
        }
    }
}
