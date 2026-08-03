package me.sdi.ethwallet.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthCreationResult
import me.sdi.ethwallet.core.EthManager
import me.sdi.ethwallet.core.security.PrivateKeyEncryptor
import me.sdi.ethwallet.database.dao.WalletDao
import me.sdi.ethwallet.database.entity.WalletEntity

private const val TAG = "WalletRepository"

/**
 * Repository for wallet-related database operations.
 */
class WalletRepository(
    private val walletDao: WalletDao,
    private val ethManager: EthManager,
    private val privateKeyEncryptor: PrivateKeyEncryptor,
) {
    suspend fun saveWallet(creationResult: EthCreationResult, label: String) {
        val encryptedKeyBase64 = privateKeyEncryptor.encryptPrivateKey(
            privateKey = creationResult.privateKey,
            address = creationResult.address.toString(),
        )
        walletDao.insertWallet(
            WalletEntity(
                address = creationResult.address.toString(),
                encryptedPrivateKeyBase64 = encryptedKeyBase64,
                label = label,
            ),
        )
    }

    suspend fun importWallet(mnemonic: String, label: String) {
        Logger.debug(TAG) { "Importing wallet" }

        val creationResult: EthCreationResult = ethManager.importWallet(mnemonic)
        val encryptedKeyBase64 = privateKeyEncryptor.encryptPrivateKey(
            privateKey = creationResult.privateKey,
            address = creationResult.address.toString(),
        )
        walletDao.insertWallet(
            WalletEntity(
                address = creationResult.address.toString(),
                encryptedPrivateKeyBase64 = encryptedKeyBase64,
                label = label,
            ),
        )
    }

    fun observeAllWallets(): Flow<List<EthWallet>> = walletDao.observeAllWallets().map { dbEntities ->
        dbEntities.map { it.toEthWallet() }
    }

    /**
     * Deletes a wallet by its address.
     *
     * @param address The address of the wallet to delete
     */
    suspend fun deleteWallet(address: EthAddress) {
        Logger.debug(TAG) { "Deleting wallet with address $address" }
        walletDao.deleteWalletByAddress(address.toString())
    }

    suspend fun getEncryptedPrivateKey(address: EthAddress) = walletDao
        .getEncryptedPrivateKeyByAddress(address = address.toString())

    private fun WalletEntity.toEthWallet() = EthWallet(
        label = label,
        address = EthAddress(address),
    )
}
