package me.sdi.ethwallet.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import me.sdi.ethwallet.database.dao.FiatRateDao
import me.sdi.ethwallet.database.dao.TokenBalanceDao
import me.sdi.ethwallet.database.dao.TransferHistoryDao
import me.sdi.ethwallet.database.dao.WalletDao
import me.sdi.ethwallet.database.entity.FiatRateEntity
import me.sdi.ethwallet.database.entity.TokenBalanceEntity
import me.sdi.ethwallet.database.entity.TransferHistoryEntity
import me.sdi.ethwallet.database.entity.WalletEntity

@Database(
    entities = [
        WalletEntity::class,
        TokenBalanceEntity::class,
        FiatRateEntity::class,
        TransferHistoryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao

    abstract fun tokenBalanceDao(): TokenBalanceDao

    abstract fun fiatRateDao(): FiatRateDao

    abstract fun transferHistoryDao(): TransferHistoryDao
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
