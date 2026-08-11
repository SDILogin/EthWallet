package me.sdi.ethwallet.kotlin

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EthWalletKotlinTest {
    @Test
    fun jsonRpcTransportSupportsSamConstruction() = runTest {
        val transport = JsonRpcTransport { requestJson -> requestJson }

        assertEquals("request", transport.execute("request"))
    }

    @Test
    fun modelsRepresentEthAndErc20Assets() {
        val address = EthAddress("0x1234")

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
    fun mnemonicCopiesInputAndClearingUpdatesItsViews() {
        val source = StringBuilder("alpha beta")
        val mnemonic = Mnemonic(source)

        source[0] = 'X'

        assertEquals("alpha", mnemonic.words[0].copyToString())
        assertEquals("Mnemonic(**redacted**)", mnemonic.toString())
        assertEquals("MnemonicWord(**redacted**)", mnemonic.words[0].toString())

        mnemonic.clear()

        assertEquals("\u0000".repeat(5), mnemonic.words[0].copyToString())
    }

    @Test
    fun privateKeyCopiesInputAndOutputAndCanBeCleared() {
        val source = ByteArray(32) { it.toByte() }
        val privateKey = PrivateKey.fromBytes(source)
        source.fill(7)

        assertContentEquals(ByteArray(32) { it.toByte() }, privateKey.copyBytes())

        val copy = privateKey.copyBytes()
        copy.fill(8)
        assertContentEquals(ByteArray(32) { it.toByte() }, privateKey.copyBytes())
        assertEquals("PrivateKey(**redacted**)", privateKey.toString())

        privateKey.clear()

        assertContentEquals(ByteArray(32), privateKey.copyBytes())
    }

    @Test
    fun privateKeyRequiresExactlyThirtyTwoBytes() {
        assertFailsWith<IllegalArgumentException> {
            PrivateKey.fromBytes(ByteArray(31))
        }
    }

    @Test
    fun walletAndCryptographicOperationsAreStubs() = runTest {
        val network = EthereumNetwork(
            chainId = 1L,
            transport = JsonRpcTransport { error("Transport must not be used by stubs") },
        )
        val address = EthAddress("0x1234")
        val privateKey = PrivateKey.fromBytes(ByteArray(32))
        val request = TransferRequest(
            destination = address,
            asset = Asset.Eth,
            amount = UInt256("0x1"),
            gasUnits = UInt256("0x5208"),
            maxPriorityFeePerGas = UInt256("0x1"),
            maxFeePerGas = UInt256("0x2"),
        )

        assertFailsWith<NotImplementedError> { EthWalletKotlin.createWallet() }
        assertFailsWith<NotImplementedError> { Mnemonic.parse(StringBuilder("alpha beta")) }
        assertFailsWith<NotImplementedError> { Mnemonic("alpha beta").derivePrivateKey() }
        assertFailsWith<NotImplementedError> { privateKey.deriveAddress() }
        assertFailsWith<NotImplementedError> {
            EthWalletKotlin.getBalance(network, address, Asset.Eth)
        }
        assertFailsWith<NotImplementedError> {
            EthWalletKotlin.getFeeSuggestion(network)
        }
        assertFailsWith<NotImplementedError> {
            EthWalletKotlin.sendTransfer(network, privateKey, request)
        }
    }
}

private fun CharSequence.copyToString(): String = buildString(length) {
    for (index in this@copyToString.indices) {
        append(this@copyToString[index])
    }
}
