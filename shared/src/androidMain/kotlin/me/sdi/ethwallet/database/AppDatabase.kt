package me.sdi.ethwallet.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.database.dao.FiatRateDao
import me.sdi.ethwallet.database.dao.TokenBalanceDao
import me.sdi.ethwallet.database.dao.WalletDao
import me.sdi.ethwallet.database.entity.CurrentWalletEntity
import me.sdi.ethwallet.database.entity.FiatRateEntity
import me.sdi.ethwallet.database.entity.TokenBalanceEntity
import me.sdi.ethwallet.database.entity.WalletEntity

@Database(
    entities = [
        WalletEntity::class,
        TokenBalanceEntity::class,
        FiatRateEntity::class,
        CurrentWalletEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao

    abstract fun tokenBalanceDao(): TokenBalanceDao

    abstract fun fiatRateDao(): FiatRateDao

    companion object {
        private const val DATABASE_NAME = "ethwallet_db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                Logger.debug("Db") {
                    "Creating new instance of AppDatabase"
                }
                val builder = Room.databaseBuilder<AppDatabase>(
                    context = context.applicationContext,
                    name = DATABASE_NAME,
                )
                builder.build().also {
                    instance = it
                }
            }
        }
    }
}
