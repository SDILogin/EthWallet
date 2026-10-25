package me.sdi.ethwallet.kotlin

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val CANONICAL_MNEMONIC =
    "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
private const val CANONICAL_ETHEREUM_ADDRESS = "0x9858effd232b4033e47d90003d41ec34ecaeda94"
private const val PRIVATE_KEY_ONE_ETHEREUM_ADDRESS = "0x7e5f4552091a69125d5dfcb7b8c2659029395bdf"

// Hardcoded ethers.js compatibility vectors; the library has no ethers dependency.
private const val ACCOUNT_ZERO_ADDRESS_ONE = "0x6fac4d18c912343bf86fa7049364dd4e424ab9c0"
private const val ACCOUNT_ONE_ADDRESS_ZERO = "0x78839f6054d7ed13918bae0473ba31b1ca9d7265"
private const val MAXIMUM_INDEX_ADDRESS = "0x6e40eb459b5f08be907f9c1ccacaf55780d5cea8"

class EthWalletKotlinTest {
    @Test
    fun jsonRpcTransportSupportsSamConstruction() = runTest {
        val transport = JsonRpcTransport { requestJson -> requestJson }

        assertEquals("request", transport.execute("request"))
    }

    @Test
    fun modelsRepresentEthAndErc20Assets() {
        val address = CANONICAL_ETHEREUM_ADDRESS

        assertEquals(Asset.Eth, Asset.Eth)
        assertEquals(Asset.Erc20(address), Asset.Erc20(address))
        assertEquals(
            Eip1559FeeSuggestion(
                baseFeePerGas = UInt256("0x1"),
                priorityFeePerGas = UInt256("0x2"),
            ),
            Eip1559FeeSuggestion(
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

        assertEquals(CANONICAL_ETHEREUM_ADDRESS, privateKey.deriveAddress())
    }

    @Test
    fun passphraseDerivationMatchesOfficialSeedAtDefaultAndCustomPaths() = runTest {
        val mnemonic = Mnemonic.parse(CANONICAL_MNEMONIC)
        // Ethereum BIP-44 keys derived from the first official BIP-39 TREZOR seed vector.
        assertEquals(
            "62f1d86b246c81bdd8f6c166d56896a4a5e1eddbcaebe06480e5c0bc74c28224",
            mnemonic.derivePrivateKey(passphrase = "TREZOR").copyBytes().toHexString(),
        )
        assertEquals(
            "ef8dab9146cca357f32c1a0e1e46b132b63b36c426a15cd5453e98d3d439216e",
            mnemonic.derivePrivateKey(EthereumDerivationPath(1u, 2u), "TREZOR").copyBytes().toHexString(),
        )
        assertEquals(CANONICAL_ETHEREUM_ADDRESS, mnemonic.derivePrivateKey(passphrase = "").deriveAddress())
    }

    @Test
    fun passphrasesPreserveUtf8AndApplyNfkdAcrossNativeBindings() = runTest {
        val mnemonic = Mnemonic.parse(CANONICAL_MNEMONIC)

        suspend fun address(passphrase: String) = mnemonic.derivePrivateKey(passphrase).deriveAddress()

        assertEquals(address("\u00e9\uD83D\uDD11"), address("e\u0301\uD83D\uDD11"))
        assertEquals(address("TREZOR"), address("\uFF34\uFF32\uFF25\uFF3A\uFF2F\uFF32"))
        assertNotEquals(address("TREZOR"), address("trezor"))
        assertNotEquals(address("TREZOR"), address(" TREZOR "))
        assertNotEquals(address("abc"), address("abc\u0000def"))
        assertFailsWith<CharacterCodingException> { address("\uD800") }
    }

    @Test
    fun configurableBip44PathDerivesIndependentReferenceAddresses() = runTest {
        val mnemonic = Mnemonic.parse(CANONICAL_MNEMONIC)

        assertEquals(
            ACCOUNT_ZERO_ADDRESS_ONE,
            mnemonic.derivePrivateKey(EthereumDerivationPath(addressIndex = 1u)).deriveAddress(),
        )
        assertEquals(
            ACCOUNT_ONE_ADDRESS_ZERO,
            mnemonic.derivePrivateKey(EthereumDerivationPath(accountIndex = 1u)).deriveAddress(),
        )
        assertEquals(
            MAXIMUM_INDEX_ADDRESS,
            mnemonic.derivePrivateKey(
                EthereumDerivationPath(
                    accountIndex = Int.MAX_VALUE.toUInt(),
                    addressIndex = Int.MAX_VALUE.toUInt(),
                ),
            ).deriveAddress(),
        )
    }

    @Test
    fun ethereumDerivationPathFormatsCanonicalBip44Path() {
        assertEquals("m/44'/60'/7'/0/11", EthereumDerivationPath(7u, 11u).toString())
    }

    @Test
    fun ethereumDerivationPathRejectsIndicesWithTheHardenedBitSet() {
        val invalidIndex = 0x8000_0000u

        assertFailsWith<IllegalArgumentException> {
            EthereumDerivationPath(accountIndex = invalidIndex)
        }
        assertFailsWith<IllegalArgumentException> {
            EthereumDerivationPath(addressIndex = invalidIndex)
        }
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
        assertEquals(PRIVATE_KEY_ONE_ETHEREUM_ADDRESS, privateKey.deriveAddress())
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
        PrivateKey.fromBytes(SECP256K1_ORDER_BYTES.decrementUnsigned())
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
