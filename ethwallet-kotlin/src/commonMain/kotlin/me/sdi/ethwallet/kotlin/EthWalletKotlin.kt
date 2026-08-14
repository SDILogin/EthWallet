package me.sdi.ethwallet.kotlin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * Stateless entry point for Ethereum wallet operations.
 *
 * Wallet creation, key derivation, JSON-RPC calls, and transaction signing are placeholders in
 * this skeleton and currently throw [NotImplementedError].
 */
object EthWalletKotlin {
    suspend fun createWallet(): Mnemonic = withContext(Dispatchers.Default) {
        notImplemented()
    }

    suspend fun getBalance(
        network: EthereumNetwork,
        owner: EthAddress,
        asset: Asset,
    ): UInt256 = withContext(Dispatchers.IO) {
        notImplemented()
    }

    suspend fun getFeeSuggestion(network: EthereumNetwork): FeeSuggestion = withContext(Dispatchers.IO) {
        notImplemented()
    }

    suspend fun sendTransfer(
        network: EthereumNetwork,
        privateKey: PrivateKey,
        request: TransferRequest,
    ): TransactionHash = withContext(Dispatchers.IO) {
        notImplemented()
    }
}

suspend fun Mnemonic.derivePrivateKey(): PrivateKey = withContext(Dispatchers.Default) {
    notImplemented()
}

suspend fun PrivateKey.deriveAddress(): EthAddress = withContext(Dispatchers.Default) {
    notImplemented()
}

internal fun notImplemented(): Nothing = throw NotImplementedError("EthWalletKotlin skeleton")
