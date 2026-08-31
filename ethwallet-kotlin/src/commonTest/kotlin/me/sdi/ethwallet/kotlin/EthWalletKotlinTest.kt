package me.sdi.ethwallet.kotlin

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val CANONICAL_MNEMONIC =
    "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
private const val CANONICAL_ETHEREUM_ADDRESS = "0x9858effd232b4033e47d90003d41ec34ecaeda94"
private const val PRIVATE_KEY_ONE_ETHEREUM_ADDRESS = "0x7e5f4552091a69125d5dfcb7b8c2659029395bdf"

class EthWalletKotlinTest {
    @Test
    fun jsonRpcTransportSupportsSamConstruction() = runTest {
        val transport = JsonRpcTransport { requestJson -> requestJson }

        assertEquals("request", transport.execute("request"))
    }

    @Test
    fun modelsRepresentEthAndErc20Assets() {
        val address = EthAddress(CANONICAL_ETHEREUM_ADDRESS)

        assertEquals(Asset.Eth, Asset.Eth)
        assertEquals(Asset.Erc20(address), Asset.Erc20(address))
        assertEquals(
            FeeSuggestion(
                baseFeePerGas = UInt256("0x1"),
                priorityFeePerGas = UInt256("0x2"),
            ),
            FeeSuggestion(
                baseFeePerGas = UInt256("0x1"),
                priorityFeePerGas = UInt256("0x2"),
            ),
        )
    }

    @Test
    fun parseNormalizesAsciiCaseAndWhitespace() = runTest {
        val source = StringBuilder(
            "  ABANDON\tABANDON abandon\nABANDON abandon abandon  abandon abandon abandon abandon abandon  ABOUT  ",
        )

        val mnemonic = Mnemonic.parse(source)
        source[2] = 'x'

        assertEquals(12, mnemonic.words.size)
        assertEquals("abandon", mnemonic.words.first().copyToString())
        assertEquals("about", mnemonic.words.last().copyToString())
        assertEquals("Mnemonic(**redacted**)", mnemonic.toString())
        assertEquals("MnemonicWord(**redacted**)", mnemonic.words.first().toString())
    }

    @Test
    fun parseRejectsInvalidBip39Phrases() = runTest {
        assertFailsWith<IllegalArgumentException> {
            Mnemonic.parse("abandon ".repeat(12).trim())
        }
        assertFailsWith<IllegalArgumentException> {
            Mnemonic.parse("abandon ".repeat(11) + "zzzz")
        }
        assertFailsWith<IllegalArgumentException> {
            Mnemonic.parse("abandon ".repeat(11) + "about!")
        }
        assertFailsWith<IllegalArgumentException> {
            Mnemonic.parse("abandon ".repeat(11).trim())
        }
    }

    @Test
    fun canonicalMnemonicDerivesTheFirstEthereumBip44Address() = runTest {
        val privateKey = Mnemonic.parse(CANONICAL_MNEMONIC).derivePrivateKey()

        assertEquals(CANONICAL_ETHEREUM_ADDRESS, privateKey.deriveAddress().value)
    }

    @Test
    fun privateKeyOneDerivesTheKnownEthereumAddress() = runTest {
        val privateKeyBytes = ByteArray(32).also { it[31] = 1 }
        val privateKey = PrivateKey.fromBytes(privateKeyBytes)
        privateKeyBytes.fill(7)

        assertContentEquals(ByteArray(32).also { it[31] = 1 }, privateKey.copyBytes())
        privateKey.copyBytes().also { returned ->
            returned.fill(7)
        }
        assertContentEquals(ByteArray(32).also { it[31] = 1 }, privateKey.copyBytes())
        assertEquals(PRIVATE_KEY_ONE_ETHEREUM_ADDRESS, privateKey.deriveAddress().value)
        assertEquals("PrivateKey(**redacted**)", privateKey.toString())
    }

    @Test
    fun walletCreationProducesAValidTwelveWordMnemonic() = runTest {
        val mnemonic = EthWalletKotlin.createWallet()
        val phrase = mnemonic.words.joinToString(separator = " ") { it.copyToString() }

        assertEquals(12, mnemonic.words.size)
        assertEquals(phrase, Mnemonic.parse(phrase).words.joinToString(" ") { it.copyToString() })
        assertTrue(mnemonic.words.all { word -> word.all { it in 'a'..'z' } })
    }

    @Test
    fun clearedSecretsCannotBeUsedAndTheirMutableViewsAreCleared() = runTest {
        val mnemonic = Mnemonic.parse(CANONICAL_MNEMONIC)
        val firstWord = mnemonic.words.first()
        mnemonic.clear()

        assertEquals("\u0000".repeat(firstWord.length), firstWord.copyToString())
        assertSuspendFailsWith<IllegalStateException> { mnemonic.derivePrivateKey() }

        val privateKey = PrivateKey.fromBytes(ByteArray(32).also { it[31] = 1 })
        privateKey.clear()

        assertContentEquals(ByteArray(32), privateKey.copyBytes())
        assertSuspendFailsWith<IllegalStateException> { privateKey.deriveAddress() }
    }

    @Test
    fun privateKeyRequiresExactlyThirtyTwoBytes() {
        assertFailsWith<IllegalArgumentException> {
            PrivateKey.fromBytes(ByteArray(31))
        }
    }

    @Test
    fun invalidPrivateKeysAreRejectedAtConstruction() {
        assertFailsWith<IllegalArgumentException> {
            PrivateKey.fromBytes(ByteArray(32))
        }
        assertFailsWith<IllegalArgumentException> {
            PrivateKey.fromBytes(SECP256K1_ORDER_BYTES.copyOf())
        }
        assertFailsWith<IllegalArgumentException> {
            PrivateKey.fromBytes(ByteArray(32) { 0xff.toByte() })
        }
        PrivateKey.fromBytes(SECP256K1_ORDER_BYTES.decrementUnsigned()).clear()
    }
}

private val SECP256K1_ORDER_BYTES = byteArrayOf(
    0xff.toByte(),
    0xff.toByte(),
    0xff.toByte(),
    0xff.toByte(),
    0xff.toByte(),
    0xff.toByte(),
    0xff.toByte(),
    0xff.toByte(),
    0xff.toByte(),
    0xff.toByte(),
    0xff.toByte(),
    0xff.toByte(),
    0xff.toByte(),
    0xff.toByte(),
    0xff.toByte(),
    0xfe.toByte(),
    0xba.toByte(),
    0xae.toByte(),
    0xdc.toByte(),
    0xe6.toByte(),
    0xaf.toByte(),
    0x48,
    0xa0.toByte(),
    0x3b,
    0xbf.toByte(),
    0xd2.toByte(),
    0x5e,
    0x8c.toByte(),
    0xd0.toByte(),
    0x36,
    0x41,
    0x41,
)

private fun ByteArray.decrementUnsigned(): ByteArray = copyOf().also { bytes ->
    for (index in bytes.lastIndex downTo 0) {
        if (bytes[index].toInt() and 0xff > 0) {
            bytes[index] = (bytes[index] - 1).toByte()
            break
        }
        bytes[index] = 0xff.toByte()
    }
}

private fun CharSequence.copyToString(): String = buildString(length) {
    for (index in this@copyToString.indices) {
        append(this@copyToString[index])
    }
}
