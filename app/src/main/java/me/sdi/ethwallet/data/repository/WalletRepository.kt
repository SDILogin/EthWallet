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
     * @param setAsActive Whether to set this wallet as the active wallet
     * @return The mnemonic phrase (seed phrase) for the created wallet
     */
    suspend fun createWallet(label: String = "My Wallet", setAsActive: Boolean = true): List<String> {
        Logger.debug(TAG) { "Creating new wallet" }

        val creationResult: EthCreationResult = walletCreator.createWallet()
        val ethAddress: String = creationResult.address.toString()
        val encryptedKeyBase64 =
            keyStoreEncryptor.encryptPrivateKey(
                privateKey = creationResult.privateKey,
                address = ethAddress,
            )

        Logger.debug(TAG) { "Saving wallet with address $ethAddress and label $label" }

        if (setAsActive) {
            walletDao.clearActiveWallets()
        }

        val walletEntity =
            WalletEntity(
                address = ethAddress,
                encryptedPrivateKeyBase64 = encryptedKeyBase64,
                label = label,
                isActive = setAsActive,
            )

        walletDao.insertWallet(walletEntity)

        Logger.debug(TAG) { "Wallet created and saved with address: $ethAddress" }

        return creationResult.seedPhrase
    }

    /**
     * Retrieves all wallets from the database.
     *
     * @return List of wallet entities
     */
    suspend fun getAllWallets(): List<WalletEntity> {
        return walletDao.getAllWallets()
    }

    /**
     * Sets a wallet as the active wallet.
     *
     * @param address The address of the wallet to set as active
     */
    suspend fun setActiveWallet(address: String) {
        Logger.debug(TAG) { "Setting wallet $address as active" }
        walletDao.clearActiveWallets()
        walletDao.setWalletActive(address)
    }

    /**
     * Deletes a wallet by its address.
     *
     * @param address The address of the wallet to delete
     */
    suspend fun deleteWallet(address: String) {
        Logger.debug(TAG) { "Deleting wallet with address $address" }
        walletDao.deleteWalletByAddress(address)
    }

    /**
     * Checks if any wallets exist in the database.
     *
     * @return True if at least one wallet exists, false otherwise
     */
    suspend fun hasWallets(): Boolean {
        return walletDao.getWalletCount() > 0
    }
}
