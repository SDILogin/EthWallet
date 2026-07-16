package me.sdi.ethwallet.core

import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.Credentials
import org.web3j.crypto.MnemonicUtils
import java.security.SecureRandom

/**
 * Creates and restores Ethereum wallets with the standard BIP-39/BIP-44 scheme
 * (path m/44'/60'/0'/0/0, empty passphrase), so seed phrases are interchangeable
 * with Trust Wallet, MetaMask and other mainstream wallets.
 */
class Bip44EthWalletCreator : EthWalletCreator {
    override fun createWallet(): EthCreationResult {
        val entropy = ByteArray(ENTROPY_BYTES).also { SecureRandom().nextBytes(it) }
        val mnemonic = MnemonicUtils.generateMnemonic(entropy)
        return deriveWallet(mnemonic)
    }

    override fun importWallet(mnemonic: String): EthCreationResult {
        val normalized = mnemonic.trim().lowercase().replace(WHITESPACE, " ")
        require(MnemonicUtils.validateMnemonic(normalized)) { "Invalid recovery phrase" }
        return deriveWallet(normalized)
    }

    private fun deriveWallet(mnemonic: String): EthCreationResult {
        val seed = MnemonicUtils.generateSeed(mnemonic, "")
        val masterKeyPair = Bip32ECKeyPair.generateKeyPair(seed)
        val derivedKeyPair = Bip32ECKeyPair.deriveKeyPair(masterKeyPair, DERIVATION_PATH)
        val credentials = Credentials.create(derivedKeyPair)
        return EthCreationResult(
            address = EthAddress(credentials.address),
            privateKey = derivedKeyPair.privateKey,
            seedPhrase = mnemonic.split(" "),
        )
    }

    private companion object {
        const val ENTROPY_BYTES = 16
        val WHITESPACE = Regex("\\s+")

        // BIP-44 account path m/44'/60'/0'/0/0 used by Trust Wallet and MetaMask.
        val DERIVATION_PATH = intArrayOf(
            44 or Bip32ECKeyPair.HARDENED_BIT,
            60 or Bip32ECKeyPair.HARDENED_BIT,
            0 or Bip32ECKeyPair.HARDENED_BIT,
            0,
            0,
        )
    }
}
