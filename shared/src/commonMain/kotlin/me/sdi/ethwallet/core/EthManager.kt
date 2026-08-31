package me.sdi.ethwallet.core

import me.sdi.ethwallet.core.transfer.FeeSuggestion
import me.sdi.ethwallet.kotlin.PrivateKey
import java.math.BigInteger

/**
 * Single abstraction over the underlying Ethereum client: balance reads, fee suggestions,
 * transaction signing/broadcasting and wallet derivation all live behind this interface so
 * a future library swap only touches its implementation.
 */
interface EthManager {
    suspend fun getBalance(address: EthAddress): Map<EthAddress, Result<BigInteger>>

    suspend fun fetchFeeSuggestion(): FeeSuggestion

    /**
     * Signs and broadcasts a transfer, returning the transaction hash.
     *
     * @param privateKey Already-decrypted sender private key; decryption stays the caller's responsibility.
     * @param contractAddress [EthContractAddress] for a plain ETH transfer, otherwise the ERC-20 token contract.
     */
    suspend fun sendTransfer(
        privateKey: PrivateKey,
        contractAddress: EthAddress,
        destinationAddress: EthAddress,
        amount: BigInteger,
        gasUnits: BigInteger,
        maxPriorityFeePerGas: BigInteger,
        maxFeePerGas: BigInteger,
    ): String

    suspend fun createWallet(): EthCreationResult

    /**
     * @throws IllegalArgumentException if the mnemonic is not a valid BIP-39 phrase.
     */
    suspend fun importWallet(mnemonic: String): EthCreationResult
}
