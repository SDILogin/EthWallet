package me.sdi.ethwallet.kotlin

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeCryptoDeviceTest {
    @Test
    fun nativeLibraryLoadsThroughWalletCreationApi() = runBlocking {
        val mnemonic = EthWalletKotlin.createWallet()
        try {
            assertEquals(12, mnemonic.words.size)
            val phrase = mnemonic.words.joinToString(" ") { it.copyCharsToString() }
            val reparsed = Mnemonic.parse(phrase)
            try {
                assertEquals(phrase, reparsed.words.joinToString(" ") { it.copyCharsToString() })
            } finally {
                reparsed.clear()
            }
        } finally {
            mnemonic.clear()
        }
    }

    @Test
    fun canonicalMnemonicDerivesTheFirstEthereumBip44Address() = runBlocking {
        val mnemonic = Mnemonic.parse("  ABANDON\tabandon abandon\nabandon abandon abandon abandon abandon abandon abandon abandon ABOUT ")
        try {
            val privateKey = mnemonic.derivePrivateKey()
            try {
                assertEquals(CANONICAL_ETHEREUM_ADDRESS, privateKey.deriveAddress().value)
            } finally {
                privateKey.clear()
            }
        } finally {
            mnemonic.clear()
        }
    }

    @Test
    fun signsTheWeb3jEthEip1559ReferenceVector() = runBlocking {
        val privateKey = privateKeyOne()
        try {
            assertEquals(
                EXPECTED_SIGNED_TRANSACTION,
                privateKey.signTransaction(referenceTransaction()).toHexString(),
            )
        } finally {
            privateKey.clear()
        }
    }

    @Test
    fun signsTheWeb3jErc20Eip1559ReferenceVector() = runBlocking {
        val privateKey = privateKeyOne()
        try {
            assertEquals(
                EXPECTED_SIGNED_ERC20_TRANSACTION,
                privateKey.signTransaction(erc20ReferenceTransaction()).toHexString(),
            )
        } finally {
            privateKey.clear()
        }
    }

    @Test
    fun clearedMnemonicAndPrivateKeyRejectNativeOperations() = runBlocking {
        val mnemonic = Mnemonic.parse(CANONICAL_MNEMONIC)
        mnemonic.clear()
        try {
            mnemonic.derivePrivateKey()
            fail("Cleared mnemonic must not derive a private key")
        } catch (_: IllegalStateException) {
        }

        val privateKey = privateKeyOne()
        privateKey.clear()
        assertArrayEquals(ByteArray(32), privateKey.copyBytes())
        try {
            privateKey.deriveAddress()
            fail("Cleared private key must not derive an address")
        } catch (_: IllegalStateException) {
        }
    }
}

private fun privateKeyOne(): PrivateKey = PrivateKey.fromBytes(ByteArray(32).also { it[31] = 1 })

private fun CharSequence.copyCharsToString(): String = buildString(length) {
    for (index in this@copyCharsToString.indices) {
        append(this@copyCharsToString[index])
    }
}

private fun referenceTransaction() = Eip1559TransactionPayload(
    chainId = 11_155_111,
    nonce = UInt256("0x7"),
    maxPriorityFeePerGas = UInt256("0x3b9aca00"),
    maxFeePerGas = UInt256("0x77359400"),
    gasLimit = UInt256("0x5208"),
    destination = EthAddress("0x3535353535353535353535353535353535353535"),
    value = UInt256("0xde0b6b3a7640000"),
)

private fun erc20ReferenceTransaction() = referenceTransaction().copy(
    gasLimit = UInt256("0xfde8"),
    destination = EthAddress("0xdac17f958d2ee523a2206206994597c13d831ec7"),
    value = UInt256("0x0"),
    data = "0x$ERC20_TRANSFER_CALLDATA",
)

private const val CANONICAL_MNEMONIC =
    "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
private const val CANONICAL_ETHEREUM_ADDRESS = "0x9858effd232b4033e47d90003d41ec34ecaeda94"
private const val EXPECTED_SIGNED_TRANSACTION =
    "02f87583aa36a707843b9aca008477359400825208943535353535353535353535353535353535353535880de0b6b3a764000080c080a0d203a655156d8a48583b27eb743fe8c6dfb5c0862763a80d3e07e55b718e4038a005c95b32d2ba7ad56455b64735834e041792bac244223533971db91a19c6caa3"
private const val ERC20_TRANSFER_CALLDATA =
    "a9059cbb0000000000000000000000009858effd232b4033e47d90003d41ec34ecaeda9400000000000000000000000000000000000000000000000000000000000f4240"
private const val EXPECTED_SIGNED_ERC20_TRANSACTION =
    "02f8b283aa36a707843b9aca00847735940082fde894dac17f958d2ee523a2206206994597c13d831ec780b844a9059cbb0000000000000000000000009858effd232b4033e47d90003d41ec34ecaeda9400000000000000000000000000000000000000000000000000000000000f4240c001a0d2cff7062b64372a1d503044996f508acddf169dcb1d2828c7bb248b9fa2c35fa072a2c6dd35dcb2e00220e335e7bbfcd85786dea11b36716708cbbfcf2eb535a4"
