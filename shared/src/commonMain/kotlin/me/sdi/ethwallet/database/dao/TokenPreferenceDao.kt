package me.sdi.ethwallet.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.sdi.ethwallet.database.entity.TokenPreferenceEntity

@Dao
interface TokenPreferenceDao {
    @Query("SELECT * FROM token_preferences WHERE wallet_address = :walletAddress ORDER BY position")
    fun observe(walletAddress: String): Flow<List<TokenPreferenceEntity>>

    @Query("DELETE FROM token_preferences WHERE wallet_address = :walletAddress")
    suspend fun delete(walletAddress: String)

    @Insert
    suspend fun insertAll(preferences: List<TokenPreferenceEntity>)

    @Transaction
    suspend fun replace(walletAddress: String, preferences: List<TokenPreferenceEntity>) {
        delete(walletAddress)
        insertAll(preferences)
    }
}
