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
}
