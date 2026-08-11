package me.sdi.ethwallet.kotlin

/**
 * Stateless entry point for Ethereum wallet operations.
 *
 * Wallet creation, key derivation, JSON-RPC calls, and transaction signing are placeholders in
 * this skeleton and currently throw [NotImplementedError].
 */
object EthWalletKotlin {
    fun createWallet(): Mnemonic = notImplemented()

    suspend fun getBalance(
        network: EthereumNetwork,
        owner: EthAddress,
        asset: Asset,
    ): UInt256 = notImplemented()

    suspend fun getFeeSuggestion(
        network: EthereumNetwork,
    ): FeeSuggestion = notImplemented()

    suspend fun sendTransfer(
        network: EthereumNetwork,
        privateKey: PrivateKey,
        request: TransferRequest,
    ): TransactionHash = notImplemented()
}

fun Mnemonic.derivePrivateKey(): PrivateKey = notImplemented()

fun PrivateKey.deriveAddress(): EthAddress = notImplemented()

internal fun notImplemented(): Nothing = throw NotImplementedError("EthWalletKotlin skeleton")
