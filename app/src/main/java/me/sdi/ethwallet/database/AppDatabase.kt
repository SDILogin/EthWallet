package me.sdi.ethwallet.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import me.sdi.ethwallet.database.dao.WalletDao
import me.sdi.ethwallet.database.entity.WalletEntity

@Database(
    entities = [WalletEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao

    companion object {
        private const val DATABASE_NAME = "ethwallet_db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                val newInstance =
                    Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        DATABASE_NAME,
                    ).build()
                instance = newInstance
                newInstance
            }
        }
    }
}
