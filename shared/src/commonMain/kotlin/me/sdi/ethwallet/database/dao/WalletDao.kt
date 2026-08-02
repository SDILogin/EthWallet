package me.sdi.ethwallet.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.sdi.ethwallet.database.entity.CurrentWalletEntity
import me.sdi.ethwallet.database.entity.WalletEntity

@Dao
interface WalletDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWalletInternal(wallet: WalletEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setActiveWallet(currentWalletEntity: CurrentWalletEntity)

    @Query("DELETE FROM wallets WHERE address = :address")
    suspend fun deleteWalletByAddress(address: String)

    @Query("SELECT * FROM wallets")
    fun observeAllWallets(): Flow<List<WalletEntity>>

    @Query("SELECT encryptedPrivateKeyBase64 FROM wallets WHERE address = :address")
    suspend fun getEncryptedPrivateKeyByAddress(address: String): String

    @Query(
        """
            SELECT wallets.address, wallets.label, wallets.encryptedPrivateKeyBase64 FROM wallets
            INNER JOIN current_wallet ON wallets.address = current_wallet.address
            WHERE current_wallet.id = 1
            LIMIT 1
        """
    )
    fun currentActiveWallet(): Flow<WalletEntity?>

    @Transaction
    suspend fun insertWallet(wallet: WalletEntity) {
        insertWalletInternal(wallet)
        setActiveWallet(CurrentWalletEntity(address = wallet.address))
    }
}
