package me.sdi.ethwallet.core

import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.core.transfer.ERC20_TRANSFER_GAS_UNITS
import me.sdi.ethwallet.core.transfer.ETH_TRANSFER_GAS_UNITS
import me.sdi.ethwallet.core.transfer.Wei
import me.sdi.ethwallet.core.transfer.eth
import me.sdi.ethwallet.core.transfer.toWei
import me.sdi.ethwallet.ui.usdtOnMainnet
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Keys
import org.web3j.crypto.TransactionDecoder
import org.web3j.crypto.transaction.type.Transaction1559
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.Response
import org.web3j.protocol.core.methods.response.EthBlock
import org.web3j.protocol.core.methods.response.EthGasPrice
import org.web3j.protocol.core.methods.response.EthGetTransactionCount
import org.web3j.protocol.core.methods.response.EthSendTransaction
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class Web3jEthManagerTest {
    private lateinit var web3j: Web3j
    private lateinit var ethManager: Web3jEthManager

    private val testDestinationAddress = EthAddress("0x9876543210987654321098765432109876543210")
    private val testPrivateKey = BigInteger("123456789")
    private val testAmount = 1.eth.toWei().value
    private val testMaxFeePerGas = Wei(BigInteger("30000000000"))
    private val testMaxPriorityFeePerGas = Wei(BigInteger("2000000000"))

    private val vectorMnemonic =
        "abandon abandon abandon abandon abandon abandon " +
            "abandon abandon abandon abandon abandon about"
    private val vectorAddress = "0x9858effd232b4033e47d90003d41ec34ecaeda94"

    @BeforeTest
    fun setUp() {
        web3j = mockk()
        ethManager = Web3jEthManager(
            web3j = web3j,
            chainConfig = ChainConfig(
                rpcUrl = "https://eth.llamarpc.com",
                chainId = 1L,
                explorerBaseUrl = "https://etherscan.io",
            ),
        )
    }

    @Test
    fun importWallet_derives_the_same_account_as_trust_wallet_for_the_canonical_vector() {
        val result = ethManager.importWallet(vectorMnemonic)

        assertEquals(
            vectorAddress,
            result.address.toString().lowercase(Locale.getDefault()),
            "Mnemonic must derive the Trust Wallet/MetaMask account (BIP-44 m/44'/60'/0'/0/0)",
        )
    }

    @Test
    fun importWallet_normalizes_casing_and_whitespace() {
        val messy =
            "  ABANDON abandon\tabandon abandon abandon abandon " +
                "abandon abandon abandon  abandon abandon about  "

        val result = ethManager.importWallet(messy)

        assertEquals(
            vectorAddress,
            result.address.toString().lowercase(Locale.getDefault()),
            "Normalization should let a messy vector mnemonic derive the same account",
        )
    }

    @Test
    fun createWallet_then_importWallet_round_trips_to_the_same_account() {
        val created = ethManager.createWallet()

        val imported = ethManager.importWallet(created.seedPhrase.joinToString(" "))

        assertEquals(created.address, imported.address, "Imported address should match created address")
        assertEquals(created.privateKey, imported.privateKey, "Imported private key should match created key")
    }

    @Test
    fun importWallet_rejects_a_mnemonic_with_an_invalid_checksum() {
        val badChecksum =
            "abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon abandon"

        assertFailsWith<IllegalArgumentException>("Bad checksum mnemonic must be rejected") {
            ethManager.importWallet(badChecksum)
        }
    }

    @Test
    fun importWallet_rejects_a_mnemonic_with_an_unknown_word() {
        val badWord =
            "abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon zzzz"

        assertFailsWith<IllegalArgumentException>("Mnemonic with an unknown word must be rejected") {
            ethManager.importWallet(badWord)
        }
    }

    @Test
    fun createWallet_produces_a_valid_twelve_word_wallet() {
        val result = ethManager.createWallet()

        assertTrue(
            result.address.toString().matches(Regex("^0x[0-9a-fA-F]{40}$")),
            "Address should be a valid 0x-prefixed 40-hex Ethereum address",
        )
        assertEquals(12, result.seedPhrase.size, "Seed phrase should contain 12 words")
        result.seedPhrase.forEach { word ->
            assertTrue(word.isNotEmpty(), "Each seed word should be non-empty")
        }
    }

    @Test
    fun createWallet_produces_distinct_wallets_on_each_call() {
        val first = ethManager.createWallet()
        val second = ethManager.createWallet()

        assertFalse(first.address == second.address, "Two created wallets should have different addresses")
        assertFalse(first.privateKey == second.privateKey, "Two created wallets should have different keys")
        assertFalse(first.seedPhrase == second.seedPhrase, "Two created wallets should have different phrases")
    }

    @Test
    fun createWallet_derives_the_address_from_the_private_key() {
        val result = ethManager.createWallet()

        val ecKeyPair = ECKeyPair.create(result.privateKey)
        val calculatedAddress = "0x" + Keys.getAddress(ecKeyPair)

        assertEquals(
            result.address.toString().lowercase(Locale.getDefault()),
            calculatedAddress.lowercase(Locale.getDefault()),
            "Address should be derived from the private key",
        )
    }

    @Test
    fun sendTransfer_signs_eip1559_fees_and_gas_units_for_ETH_transfer() = runTest(timeout = 10.seconds) {
        val txSlot = setUpSendMocks("0xabc123def456")

        val hash = ethManager.sendTransfer(
            privateKey = testPrivateKey,
            contractAddress = EthContractAddress,
            destinationAddress = testDestinationAddress,
            amount = testAmount,
            gasUnits = ETH_TRANSFER_GAS_UNITS,
            maxPriorityFeePerGas = testMaxPriorityFeePerGas.value,
            maxFeePerGas = testMaxFeePerGas.value,
        )
        assertEquals("0xabc123def456", hash, "ETH transfer should return the mocked tx hash")

        val decoded = TransactionDecoder.decode(txSlot.captured)
        val tx = decoded.transaction as Transaction1559
        assertEquals(testMaxFeePerGas.value, tx.maxFeePerGas, "ETH transfer should sign the max fee per gas verbatim")
        assertEquals(
            testMaxPriorityFeePerGas.value,
            tx.maxPriorityFeePerGas,
            "ETH transfer should sign the max priority fee per gas verbatim",
        )
        assertEquals(ETH_TRANSFER_GAS_UNITS, decoded.gasLimit, "ETH transfer should use the transfer data gas units")
    }

    @Test
    fun sendTransfer_signs_eip1559_fees_and_gas_units_for_ERC20_transfer() = runTest(timeout = 10.seconds) {
        val txSlot = setUpSendMocks("0xdef456abc789")

        val hash = ethManager.sendTransfer(
            privateKey = testPrivateKey,
            contractAddress = usdtOnMainnet.ethAddress,
            destinationAddress = testDestinationAddress,
            amount = BigInteger("100000000000000000000"),
            gasUnits = ERC20_TRANSFER_GAS_UNITS,
            maxPriorityFeePerGas = testMaxPriorityFeePerGas.value,
            maxFeePerGas = testMaxFeePerGas.value,
        )
        assertEquals("0xdef456abc789", hash, "ERC-20 transfer should return the mocked tx hash")

        val decoded = TransactionDecoder.decode(txSlot.captured)
        val tx = decoded.transaction as Transaction1559
        assertEquals(testMaxFeePerGas.value, tx.maxFeePerGas, "ERC-20 transfer should sign the max fee per gas verbatim")
        assertEquals(
            testMaxPriorityFeePerGas.value,
            tx.maxPriorityFeePerGas,
            "ERC-20 transfer should sign the max priority fee per gas verbatim",
        )
        assertEquals(ERC20_TRANSFER_GAS_UNITS, decoded.gasLimit, "ERC-20 transfer should use the transfer data gas units")
        assertEquals(
            usdtOnMainnet.ethAddress.toString().lowercase(Locale.getDefault()),
            decoded.to.lowercase(Locale.getDefault()),
            "ERC-20 transfer should target the token contract address",
        )
    }

    @Test
    fun sendTransfer_surfaces_the_send_transaction_rpc_error() = runTest(timeout = 10.seconds) {
        val nonceRequest = mockk<Request<*, EthGetTransactionCount>>()
        val nonceResponse = mockk<EthGetTransactionCount>()
        every { nonceResponse.transactionCount } returns BigInteger.ONE
        every { nonceRequest.send() } returns nonceResponse
        every { web3j.ethGetTransactionCount(any(), any()) } returns nonceRequest

        val sendRequest = mockk<Request<*, EthSendTransaction>>()
        val sendResponse = mockk<EthSendTransaction>()
        val error = mockk<Response.Error>()
        every { error.message } returns "insufficient funds for transfer"
        every { sendResponse.hasError() } returns true
        every { sendResponse.error } returns error
        every { sendRequest.send() } returns sendResponse
        every { web3j.ethSendRawTransaction(any()) } returns sendRequest

        val exception = assertFailsWith<RuntimeException> {
            ethManager.sendTransfer(
                privateKey = testPrivateKey,
                contractAddress = EthContractAddress,
                destinationAddress = testDestinationAddress,
                amount = testAmount,
                gasUnits = ETH_TRANSFER_GAS_UNITS,
                maxPriorityFeePerGas = testMaxPriorityFeePerGas.value,
                maxFeePerGas = testMaxFeePerGas.value,
            )
        }
        assertEquals(
            "insufficient funds for transfer",
            exception.message,
            "sendTransfer should surface the RPC error message",
        )
    }

    @Test
    fun fetchFeeSuggestion_decodes_base_fee_and_derives_priority_fee() = runTest(timeout = 10.seconds) {
        mockFeeSuggestion(baseFee = BigInteger("20000000000"), gasPrice = BigInteger("25000000000"))

        val suggestion = ethManager.fetchFeeSuggestion()

        assertEquals(
            BigInteger("20000000000"),
            suggestion.baseFee.value,
            "Base fee should be decoded from the latest block",
        )
        assertEquals(
            BigInteger("5000000000"),
            suggestion.priorityFee.value,
            "Priority fee should be the network gas price minus the base fee",
        )
    }

    @Test
    fun fetchFeeSuggestion_floors_priority_fee_at_zero_when_gas_price_below_base_fee() = runTest(timeout = 10.seconds) {
        mockFeeSuggestion(baseFee = BigInteger("30000000000"), gasPrice = BigInteger("25000000000"))

        val suggestion = ethManager.fetchFeeSuggestion()

        assertEquals(
            BigInteger.ZERO,
            suggestion.priorityFee.value,
            "Priority fee should be floored at zero when the gas price is below the base fee",
        )
    }

    @Test
    fun fetchFeeSuggestion_throws_when_base_fee_missing() = runTest(timeout = 10.seconds) {
        val blockRequest = mockk<Request<*, EthBlock>>()
        val blockResponse = mockk<EthBlock>()
        val block = mockk<EthBlock.Block>()
        every { block.baseFeePerGas } returns null
        every { blockResponse.block } returns block
        every { blockRequest.send() } returns blockResponse
        every { web3j.ethGetBlockByNumber(any(), any()) } returns blockRequest

        assertFailsWith<RuntimeException> {
            ethManager.fetchFeeSuggestion()
        }
    }

    @Test
    fun fetchFeeSuggestion_surfaces_the_gas_price_rpc_error() = runTest(timeout = 10.seconds) {
        val blockRequest = mockk<Request<*, EthBlock>>()
        val blockResponse = mockk<EthBlock>()
        val block = mockk<EthBlock.Block>()
        every { block.baseFeePerGas } returns Numeric.encodeQuantity(BigInteger("20000000000"))
        every { blockResponse.block } returns block
        every { blockRequest.send() } returns blockResponse
        every { web3j.ethGetBlockByNumber(any(), any()) } returns blockRequest

        val gasPriceRequest = mockk<Request<*, EthGasPrice>>()
        val gasPriceResponse = mockk<EthGasPrice>()
        val error = mockk<Response.Error>()
        every { error.message } returns "gas price request failed"
        every { gasPriceResponse.hasError() } returns true
        every { gasPriceResponse.error } returns error
        every { gasPriceRequest.send() } returns gasPriceResponse
        every { web3j.ethGasPrice() } returns gasPriceRequest

        val exception = assertFailsWith<RuntimeException> {
            ethManager.fetchFeeSuggestion()
        }
        assertEquals(
            "gas price request failed",
            exception.message,
            "fetchFeeSuggestion should surface the RPC error message",
        )
    }

    private fun mockFeeSuggestion(baseFee: BigInteger, gasPrice: BigInteger) {
        val blockRequest = mockk<Request<*, EthBlock>>()
        val blockResponse = mockk<EthBlock>()
        val block = mockk<EthBlock.Block>()
        every { block.baseFeePerGas } returns Numeric.encodeQuantity(baseFee)
        every { blockResponse.block } returns block
        every { blockRequest.send() } returns blockResponse
        every { web3j.ethGetBlockByNumber(any(), any()) } returns blockRequest

        val gasPriceRequest = mockk<Request<*, EthGasPrice>>()
        val gasPriceResponse = mockk<EthGasPrice>()
        every { gasPriceResponse.hasError() } returns false
        every { gasPriceResponse.gasPrice } returns gasPrice
        every { gasPriceRequest.send() } returns gasPriceResponse
        every { web3j.ethGasPrice() } returns gasPriceRequest
    }

    private fun setUpSendMocks(txHash: String): CapturingSlot<String> {
        val nonceRequest = mockk<Request<*, EthGetTransactionCount>>()
        val nonceResponse = mockk<EthGetTransactionCount>()
        every { nonceResponse.transactionCount } returns BigInteger.ONE
        every { nonceRequest.send() } returns nonceResponse
        every { web3j.ethGetTransactionCount(any(), any()) } returns nonceRequest

        val txSlot = slot<String>()
        val sendRequest = mockk<Request<*, EthSendTransaction>>()
        val sendResponse = mockk<EthSendTransaction>()
        every { sendResponse.hasError() } returns false
        every { sendResponse.transactionHash } returns txHash
        every { sendRequest.send() } returns sendResponse
        every { web3j.ethSendRawTransaction(capture(txSlot)) } returns sendRequest

        return txSlot
    }
}
