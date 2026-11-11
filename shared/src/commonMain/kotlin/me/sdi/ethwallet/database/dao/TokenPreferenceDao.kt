package me.sdi.ethwallet.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.sdi.ethwallet.database.entity.TokenPreferenceEntity

@Dao
interface TokenPreferenceDao {
    @Query("SELECT * FROM token_preferences WHERE wallet_address = :walletAddress ORDER BY position")
    fun observe(walletAddress: String): Flow<List<TokenPreferenceEntity>>

    @Upsert
    suspend fun upsertAll(preferences: List<TokenPreferenceEntity>)
}
