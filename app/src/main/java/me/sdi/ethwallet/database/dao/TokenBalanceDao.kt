package me.sdi.ethwallet.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.sdi.ethwallet.database.entity.TokenBalanceEntity

@Dao
interface TokenBalanceDao {
    @Query("SELECT * FROM token_balances WHERE wallet_address = :walletAddress AND token_address = :tokenAddress")
    suspend fun read(walletAddress: String, tokenAddress: String): TokenBalanceEntity?

    @Query("SELECT * FROM token_balances WHERE wallet_address = :walletAddress")
    fun observe(walletAddress: String): Flow<List<TokenBalanceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tokenBalances: List<TokenBalanceEntity>)
}
