package me.sdi.ethwallet.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.sdi.ethwallet.database.entity.WalletEntity

@Dao
interface WalletDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallet(wallet: WalletEntity)

    @Query("DELETE FROM wallets WHERE address = :address")
    suspend fun deleteWalletByAddress(address: String)

    @Query("SELECT * FROM wallets")
    suspend fun getAllWallets(): List<WalletEntity>

    @Query("UPDATE wallets SET isActive = 0")
    suspend fun clearActiveWallets()

    @Query("UPDATE wallets SET isActive = 1 WHERE address = :address")
    suspend fun setWalletActive(address: String)
}
