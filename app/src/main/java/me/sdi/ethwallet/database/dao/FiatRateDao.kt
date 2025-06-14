package me.sdi.ethwallet.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.sdi.ethwallet.database.entity.FiatRateEntity

@Dao
interface FiatRateDao {
    @Query("SELECT * FROM fiat_rates WHERE token_address IN (:tokenAddresses)")
    suspend fun readAll(tokenAddresses: List<String>): List<FiatRateEntity>

    @Query("SELECT * FROM fiat_rates WHERE token_address IN (:tokenAddresses)")
    fun observeAll(tokenAddresses: List<String>): Flow<List<FiatRateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(fiatRates: List<FiatRateEntity>)
}
