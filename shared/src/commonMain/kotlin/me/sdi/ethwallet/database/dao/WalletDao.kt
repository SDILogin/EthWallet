package me.sdi.ethwallet.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.sdi.ethwallet.database.entity.WalletEntity

@Dao
interface WalletDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallet(wallet: WalletEntity)

    @Query("DELETE FROM wallets WHERE address = :address")
    suspend fun deleteWalletByAddress(address: String)

    @Query("SELECT * FROM wallets")
    fun observeAllWallets(): Flow<List<WalletEntity>>

    @Query("SELECT encryptedPrivateKeyBase64 FROM wallets WHERE address = :address")
    suspend fun getEncryptedPrivateKeyByAddress(address: String): String
}
