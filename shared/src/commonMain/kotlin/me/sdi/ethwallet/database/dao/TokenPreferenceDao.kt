package me.sdi.ethwallet.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.sdi.ethwallet.database.entity.TokenPreferenceEntity

@Dao
interface TokenPreferenceDao {
    @Query(
        "SELECT * FROM token_preferences " +
            "WHERE chain_id = :chainId AND wallet_address = :walletAddress ORDER BY position",
    )
    fun observe(chainId: Long, walletAddress: String): Flow<List<TokenPreferenceEntity>>

    @Query("DELETE FROM token_preferences WHERE chain_id = :chainId AND wallet_address = :walletAddress")
    suspend fun delete(chainId: Long, walletAddress: String)

    @Insert
    suspend fun insertAll(preferences: List<TokenPreferenceEntity>)

    @Transaction
    suspend fun replace(chainId: Long, walletAddress: String, preferences: List<TokenPreferenceEntity>) {
        delete(chainId = chainId, walletAddress = walletAddress)
        insertAll(preferences)
    }
}
