package me.sdi.ethwallet.fakes

import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthCreationResult
import me.sdi.ethwallet.core.EthManager
import me.sdi.ethwallet.core.transfer.FeeSuggestion
import me.sdi.ethwallet.kotlin.PrivateKey
import java.math.BigInteger

class FakeEthManager : EthManager {
    var creationResult = EthCreationResult(
        address = EthAddress("0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2"),
        privateKey = privateKey(1),
        seedPhrase = listOf(
            "abandon",
            "ability",
            "able",
            "about",
            "above",
            "absent",
            "absorb",
            "abstract",
            "absurd",
            "abuse",
            "access",
            "accident",
        ),
    )
    var importResult = EthCreationResult(
        address = EthAddress("0x9858effd232b4033e47d90003d41ec34ecaeda94"),
        privateKey = privateKey(2),
        seedPhrase = List(12) { "abandon" },
    )
    var shouldThrowException = false
    var exceptionToThrow: Throwable? = null
    var lastImportedMnemonic: String? = null
    var balanceResult: Map<EthAddress, Result<BigInteger>> = emptyMap()
    var beforeCreateWallet: (suspend () -> Unit)? = null

    override suspend fun getBalance(address: EthAddress): Map<EthAddress, Result<BigInteger>> = balanceResult

    override suspend fun fetchFeeSuggestion(): FeeSuggestion = throw NotImplementedError()

    override suspend fun sendTransfer(
        privateKey: PrivateKey,
        contractAddress: EthAddress,
        destinationAddress: EthAddress,
        amount: BigInteger,
        gasUnits: BigInteger,
        maxPriorityFeePerGas: BigInteger,
        maxFeePerGas: BigInteger,
    ): String = throw NotImplementedError()

    override suspend fun createWallet(): EthCreationResult {
        beforeCreateWallet?.invoke()
        exceptionToThrow?.let { throw it }
        if (shouldThrowException) throw RuntimeException("Wallet creation failed")
        return creationResult
    }

    override suspend fun importWallet(mnemonic: String): EthCreationResult {
        lastImportedMnemonic = mnemonic
        exceptionToThrow?.let { throw it }
        if (shouldThrowException) throw RuntimeException("Wallet import failed")
        return importResult
    }

    private fun privateKey(value: Int): PrivateKey = PrivateKey.fromBytes(
        ByteArray(32).also { it[31] = value.toByte() },
    )
}
