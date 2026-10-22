package me.sdi.ethwallet.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.sdi.ethwallet.database.entity.TransferHistoryEntity

@Dao
interface TransferHistoryDao {
    @Query(
        """
        SELECT * FROM transaction_history
        WHERE chain_id = :chainId AND wallet_address = :walletAddress AND status != 'Failed'
        ORDER BY CASE WHEN status = 'Pending' THEN 0 ELSE 1 END, timestamp DESC, transaction_hash ASC, log_index ASC
        """,
    )
    fun observe(chainId: Long, walletAddress: String): Flow<List<TransferHistoryEntity>>

    @Query(
        """
            SELECT * FROM transaction_history
            WHERE chain_id = :chainId AND wallet_address = :walletAddress AND transaction_hash = :trxHash AND log_index = :logIndex
            LIMIT 1
        """,
    )
    fun observe(chainId: Long, walletAddress: String, trxHash: String, logIndex: Int): Flow<TransferHistoryEntity>

    @Query("SELECT * FROM transaction_history WHERE chain_id = :chainId AND wallet_address = :walletAddress")
    suspend fun readWalletTransfers(chainId: Long, walletAddress: String): List<TransferHistoryEntity>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TransferHistoryEntity>)
}
