package me.sdi.ethwallet.kotlin

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeCryptoDeviceTest {
    @Test
    fun canonicalMnemonicDerivesTheFirstEthereumBip44Address() = runBlocking {
        val privateKey = Mnemonic.parse(CANONICAL_MNEMONIC).derivePrivateKey()

        assertEquals(CANONICAL_ETHEREUM_ADDRESS, privateKey.deriveAddress().value)
    }
}

private const val CANONICAL_MNEMONIC =
    "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
private const val CANONICAL_ETHEREUM_ADDRESS = "0x9858effd232b4033e47d90003d41ec34ecaeda94"
