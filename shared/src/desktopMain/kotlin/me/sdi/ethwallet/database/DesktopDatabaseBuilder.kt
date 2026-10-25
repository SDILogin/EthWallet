package me.sdi.ethwallet.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

object DesktopDatabaseBuilder {
    fun build(directory: File): AppDatabase {
        check(directory.isDirectory || directory.mkdirs()) {
            "Cannot create database directory: $directory"
        }
        return Room.databaseBuilder<AppDatabase>(
            name = File(directory, "ethwallet_db").absolutePath,
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
}
