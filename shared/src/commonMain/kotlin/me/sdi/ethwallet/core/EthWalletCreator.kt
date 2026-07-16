package me.sdi.ethwallet.core

/**
 * Interface for creating Ethereum wallets.
 * Abstracts the wallet creation process to allow for different implementations.
 */
interface EthWalletCreator {
    /**
     * Creates a new Ethereum wallet.
     *
     * @return EthCreationResult containing the wallet's address, private key, and seed phrase
     */
    fun createWallet(): EthCreationResult

    /**
     * Restores an Ethereum wallet from an existing BIP-39 mnemonic.
     *
     * @param mnemonic The recovery phrase to import
     * @return EthCreationResult containing the wallet's address, private key, and seed phrase
     * @throws IllegalArgumentException if the mnemonic is not a valid BIP-39 phrase
     */
    fun importWallet(mnemonic: String): EthCreationResult
}
