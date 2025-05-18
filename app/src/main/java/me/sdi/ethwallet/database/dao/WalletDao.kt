package me.sdi.ethwallet.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import me.sdi.ethwallet.database.entity.WalletEntity

@Dao
interface WalletDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallet(wallet: WalletEntity)

    @Update
    suspend fun updateWallet(wallet: WalletEntity)

    @Query("DELETE FROM wallets WHERE address = :address")
    suspend fun deleteWalletByAddress(address: String)

    @Query("SELECT * FROM wallets")
    suspend fun getAllWallets(): List<WalletEntity>
}
