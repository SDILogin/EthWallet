package me.sdi.ethwallet.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.sdi.ethwallet.database.entity.TransactionHistoryEntity

@Dao
interface TransactionHistoryDao {
    @Query(
        """
        SELECT * FROM transaction_history
        WHERE chain_id = :chainId AND wallet_address = :walletAddress
        ORDER BY CASE WHEN status = 'Pending' THEN 0 ELSE 1 END, timestamp DESC
        """,
    )
    fun observe(chainId: Long, walletAddress: String): Flow<List<TransactionHistoryEntity>>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TransactionHistoryEntity>)
}
