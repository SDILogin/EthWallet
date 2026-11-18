package me.sdi.ethwallet.database.dao

import androidx.room.Dao
import androidx.room.Delete
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
            WHERE chain_id = :chainId AND transaction_hash = :trxHash
            ORDER BY log_index ASC, fee_wei IS NULL ASC, wallet_address ASC
            LIMIT 1
        """,
    )
    fun observeTransaction(chainId: Long, trxHash: String): Flow<TransferHistoryEntity?>

    @Query("SELECT * FROM transaction_history WHERE chain_id = :chainId AND wallet_address = :walletAddress")
    suspend fun readWalletTransfers(chainId: Long, walletAddress: String): List<TransferHistoryEntity>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TransferHistoryEntity>)

    @Query(
        """
            UPDATE transaction_history
            SET status = :status,
                fee_wei = CASE WHEN fee_wei IS NULL OR :feeWei IS NULL THEN fee_wei ELSE :feeWei END,
                fee_estimated = CASE WHEN fee_wei IS NULL OR :feeWei IS NULL THEN fee_estimated ELSE :feeEstimated END
            WHERE chain_id = :chainId AND transaction_hash = :trxHash
        """,
    )
    suspend fun update(chainId: Long, trxHash: String, status: String, feeWei: String?, feeEstimated: Boolean)

    @Delete
    suspend fun delete(items: List<TransferHistoryEntity>)
}
