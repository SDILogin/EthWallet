package me.sdi.ethwallet.data.repository

import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthCreationResult
import me.sdi.ethwallet.core.EthManager
import me.sdi.ethwallet.core.security.PrivateKeyEncryptor
import me.sdi.ethwallet.database.dao.WalletDao
import me.sdi.ethwallet.database.entity.WalletEntity
import java.math.BigInteger

private const val TAG = "WalletRepository"

/**
 * Repository for wallet-related database operations.
 */
class WalletRepository(
    private val walletDao: WalletDao,
    private val ethManager: EthManager,
    private val privateKeyEncryptor: PrivateKeyEncryptor,
) {
    private var pendingWallet: PendingWallet? = null

    /**
     * Creates a new wallet and holds its private key in memory until the PIN is set.
     * Nothing is encrypted or persisted here; call [persistPendingWallet] once the PIN exists.
     *
     * @param label Optional friendly name for the wallet
     * @return The mnemonic phrase (seed phrase) for the created wallet
     */
    suspend fun createWallet(label: String): List<String> {
        Logger.debug(TAG) { "Creating new wallet" }

        val creationResult: EthCreationResult = ethManager.createWallet()
        pendingWallet = PendingWallet(
            address = creationResult.address.toString(),
            privateKey = creationResult.privateKey,
            label = label,
        )

        return creationResult.seedPhrase
    }

    /**
     * Restores a wallet from an existing mnemonic and holds its private key in memory until the
     * PIN is set. Nothing is encrypted or persisted here; call [persistPendingWallet] once the
     * PIN exists.
     *
     * @param mnemonic The recovery phrase to import
     * @param label Optional friendly name for the wallet
     * @throws IllegalArgumentException if the mnemonic is not a valid BIP-39 phrase
     */
    suspend fun importWallet(mnemonic: String, label: String) {
        Logger.debug(TAG) { "Importing wallet" }

        val creationResult: EthCreationResult = ethManager.importWallet(mnemonic)
        pendingWallet = PendingWallet(
            address = creationResult.address.toString(),
            privateKey = creationResult.privateKey,
            label = label,
        )
    }

    /**
     * Encrypts the pending wallet's private key and persists it as the active wallet, then clears
     * the pending state.
     *
     * @throws IllegalStateException if no wallet is pending
     */
    suspend fun persistPendingWallet() {
        val pending = checkNotNull(pendingWallet) { "No pending wallet to persist" }

        Logger.debug(TAG) { "Persisting pending wallet with address ${pending.address}" }

        val encryptedKeyBase64 = privateKeyEncryptor.encryptPrivateKey(
            privateKey = pending.privateKey,
            address = pending.address,
        )

        walletDao.clearActiveWallets()

        walletDao.insertWallet(
            WalletEntity(
                address = pending.address,
                encryptedPrivateKeyBase64 = encryptedKeyBase64,
                label = pending.label,
                isActive = true,
            ),
        )

        pendingWallet = null

        Logger.debug(TAG) { "Pending wallet persisted with address: ${pending.address}" }
    }

    /**
     * Retrieves all wallets from the database.
     *
     * @return List of wallet entities
     */
    suspend fun getAllWallets(): List<EthWallet> {
        val walletEntities = walletDao.getAllWallets()
        return walletEntities.map { entity ->
            EthWallet(
                label = entity.label,
                address = EthAddress(entity.address),
            )
        }
    }

    /**
     * Retrieves the active wallet from the database.
     *
     * @return The active wallet, or null if no active wallet is found
     */
    suspend fun getActiveWallet(): EthWallet? {
        val walletEntity: List<WalletEntity> = walletDao.getAllWallets()
        val activeWallet: WalletEntity = walletEntity.firstOrNull { it.isActive } ?: run {
            Logger.warn(TAG) { "No active wallet found" }
            return null
        }

        return EthWallet(
            label = activeWallet.label,
            address = EthAddress(activeWallet.address),
        )
    }

    /**
     * Sets a wallet as the active wallet.
     *
     * @param address The address of the wallet to set as active
     */
    suspend fun setActiveWallet(address: EthAddress) {
        Logger.debug(TAG) { "Setting wallet $address as active" }
        walletDao.clearActiveWallets()
        walletDao.setWalletActive(address.toString())
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

    /**
     * Retrieves the encrypted private key for a specific wallet address.
     *
     * @param address The address of the wallet
     * @return The encrypted private key as Base64 string
     * @throws IllegalStateException if the wallet is not found
     */
    suspend fun getEncryptedPrivateKey(address: EthAddress): String {
        val walletEntity: List<WalletEntity> = walletDao.getAllWallets()
        val wallet = walletEntity.firstOrNull { it.address == address.toString() }
            ?: throw IllegalStateException("Wallet not found for address: $address")

        return wallet.encryptedPrivateKeyBase64
    }

    private data class PendingWallet(
        val address: String,
        val privateKey: BigInteger,
        val label: String,
    )
}
