package me.sdi.ethwallet.fakes

import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthCreationResult
import me.sdi.ethwallet.core.EthWalletCreator
import java.math.BigInteger

class FakeEthWalletCreator : EthWalletCreator {
    var creationResult = EthCreationResult(
        address = EthAddress("0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2"),
        privateKey = BigInteger.ONE,
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
        privateKey = BigInteger.TWO,
        seedPhrase = List(12) { "abandon" },
    )
    var shouldThrowException = false
    var exceptionToThrow: Throwable? = null
    var lastImportedMnemonic: String? = null

    override fun createWallet(): EthCreationResult {
        exceptionToThrow?.let { throw it }
        if (shouldThrowException) throw RuntimeException("Wallet creation failed")
        return creationResult
    }

    override fun importWallet(mnemonic: String): EthCreationResult {
        lastImportedMnemonic = mnemonic
        exceptionToThrow?.let { throw it }
        if (shouldThrowException) throw RuntimeException("Wallet import failed")
        return importResult
    }
}
