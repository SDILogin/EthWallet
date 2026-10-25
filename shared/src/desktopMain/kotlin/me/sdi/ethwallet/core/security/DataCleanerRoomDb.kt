package me.sdi.ethwallet.core.security

import androidx.room.execSQL
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import me.sdi.ethwallet.database.AppDatabase

class DataCleanerRoomDb(
    private val database: AppDatabase,
) : DataCleaner {
    override suspend fun clean() {
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                execSQL("DELETE FROM wallets")
                execSQL("DELETE FROM token_balances")
                execSQL("DELETE FROM fiat_rates")
                execSQL("DELETE FROM transaction_history")
            }
        }
    }
}
