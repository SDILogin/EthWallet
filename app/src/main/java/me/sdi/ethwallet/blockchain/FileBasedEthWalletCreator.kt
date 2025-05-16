package me.sdi.ethwallet.blockchain

import org.web3j.crypto.WalletUtils
import java.security.SecureRandom

/**
 * File-based implementation of EthWalletCreator.
 * Creates Ethereum wallets using Web3j's file-based wallet generation.
 *
 * @property tempFileProvider Provider for secure temporary file operations
 */
class FileBasedEthWalletCreator(private val tempFileProvider: TempFileProvider) : EthWalletCreator {
    companion object {
        private const val PASSWORD_LENGTH = 16
        private const val WALLET_TEMP_DIR_PREFIX = "eth_wallet"
    }

    /**
     * Creates a new Ethereum wallet using Web3j's BIP39 wallet generator.
     *
     * @return EthCreationResult containing the wallet's address, private key, and seed phrase
     */
    override fun createWallet(): EthCreationResult {
        return tempFileProvider.withTempDirectory(WALLET_TEMP_DIR_PREFIX) { tempDir ->
            val password = generateRandomPassword()
            val bip39Wallet = WalletUtils.generateBip39Wallet(password, tempDir)
            val seedPhrase = bip39Wallet.mnemonic.split(" ").map { it.trim() }
            val credentials = WalletUtils.loadBip39Credentials(password, bip39Wallet.mnemonic)
            EthCreationResult(
                address = EthAddress(credentials.address),
                privateKey = credentials.ecKeyPair.privateKey,
                seedPhrase = seedPhrase,
            )
        }
    }

    /**
     * Generates a cryptographically secure random password.
     *
     * @return A strong random password
     */
    private fun generateRandomPassword(): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9') + "!@#$%^&*()_-+=<>?"
        val secureRandom = SecureRandom()
        return (1..PASSWORD_LENGTH)
            .map { allowedChars[secureRandom.nextInt(allowedChars.size)] }
            .joinToString("")
    }
}
