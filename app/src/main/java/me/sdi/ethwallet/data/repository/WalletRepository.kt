package me.sdi.ethwallet.data.repository

import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.EthCreationResult
import me.sdi.ethwallet.core.EthWalletCreator
import me.sdi.ethwallet.core.security.KeyStoreEncryptor
import me.sdi.ethwallet.database.dao.WalletDao
import me.sdi.ethwallet.database.entity.WalletEntity

private const val TAG = "WalletRepository"

/**
 * Repository for wallet-related database operations.
 */
class WalletRepository(
    private val walletDao: WalletDao,
    private val walletCreator: EthWalletCreator,
    private val keyStoreEncryptor: KeyStoreEncryptor,
) {
    /**
     * Creates a new wallet, encrypts the private key, and saves it to the database.
     *
     * @param label Optional friendly name for the wallet
     * @return The mnemonic phrase (seed phrase) for the created wallet
     */
    suspend fun createWallet(label: String = "My Wallet"): List<String> {
        Logger.debug(TAG) { "Creating new wallet" }

        val creationResult: EthCreationResult = walletCreator.createWallet()
        val ethAddress: String = creationResult.address.toString()
        val encryptedKeyBase64 =
            keyStoreEncryptor.encryptPrivateKey(
                privateKey = creationResult.privateKey,
                address = ethAddress,
            )

        Logger.debug(TAG) { "Saving wallet with address $ethAddress and label $label" }

        val walletEntity =
            WalletEntity(
                address = ethAddress,
                encryptedPrivateKeyBase64 = encryptedKeyBase64,
                label = label,
            )

        walletDao.insertWallet(walletEntity)

        Logger.debug(TAG) { "Wallet created and saved with address: $ethAddress" }

        return creationResult.seedPhrase
    }
}
