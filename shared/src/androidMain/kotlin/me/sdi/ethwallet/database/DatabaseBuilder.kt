package me.sdi.ethwallet.database

import android.content.Context
import androidx.room.Room

object DatabaseBuilder {
    @Volatile
    private var instance: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder<AppDatabase>(
            context = context.applicationContext,
            name = "ethwallet_db",
        ).build().also { instance = it }
    }
}
