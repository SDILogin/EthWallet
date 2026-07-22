package me.sdi.ethwallet.core.transfer

import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.core.BalanceProvider
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.core.security.PrivateKeyEncryptor
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.ui.daiOnMainnet
import me.sdi.ethwallet.ui.usdtOnMainnet
import org.web3j.crypto.RawTransaction
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
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class TransferInteractorTest {
    private lateinit var web3j: Web3j
    private lateinit var walletRepository: WalletRepository
    private lateinit var keyStoreEncryptor: PrivateKeyEncryptor
    private lateinit var balanceProvider: BalanceProvider
    private lateinit var chainConfig: ChainConfig
    private lateinit var transferInteractor: TransferInteractor

    private val testWalletAddress = EthAddress("0x1234567890123456789012345678901234567890")
    private val testDestinationAddress = EthAddress("0x9876543210987654321098765432109876543210")
    private val testAmount = 1.eth.toWei().value
    private val testMaxFeePerGas = Wei(BigInteger("30000000000"))
    private val testMaxPriorityFeePerGas = Wei(BigInteger("2000000000"))
    private val testWallet = EthWallet(label = "Test Wallet", address = testWalletAddress)

    @BeforeTest
    fun setUp() {
        web3j = mockk()
        walletRepository = mockk()
        keyStoreEncryptor = mockk()
        balanceProvider = mockk()
        chainConfig = ChainConfig(
            rpcUrl = "https://eth.llamarpc.com",
            chainId = 1L,
            explorerBaseUrl = "https://etherscan.io",
        )
        transferInteractor = Web3jTransferInteractor(web3j, walletRepository, keyStoreEncryptor, balanceProvider, chainConfig)

        coEvery { walletRepository.getActiveWallet() } returns testWallet
    }

    private fun transferData(
        destination: EthAddress = testDestinationAddress,
        amount: BigInteger = testAmount,
        contract: EthAddress = EthContractAddress,
        maxFeePerGas: Wei = testMaxFeePerGas,
        maxPriorityFeePerGas: Wei = testMaxPriorityFeePerGas,
        gasUnits: BigInteger = ETH_TRANSFER_GAS_UNITS,
    ) = TransferData(
        destinationAddress = destination,
        amount = amount,
        contractAddress = contract,
        maxFeePerGas = maxFeePerGas,
        maxPriorityFeePerGas = maxPriorityFeePerGas,
        gasUnits = gasUnits,
    )

    @Test
    fun should_validate_positive_amount() = runTest(timeout = 10.seconds) {
        assertFailsWith<TransferException.InvalidAmount> {
            transferInteractor.transfer(transferData(amount = BigInteger.ZERO))
        }
    }

    @Test
    fun should_validate_different_source_and_destination_addresses() = runTest(timeout = 10.seconds) {
        assertFailsWith<TransferException.InvalidAddress> {
            transferInteractor.transfer(transferData(destination = testWalletAddress))
        }
    }

    @Test
    fun should_validate_max_fee_is_positive() = runTest(timeout = 10.seconds) {
        assertFailsWith<TransferException.GasPriceError> {
            transferInteractor.transfer(transferData(maxFeePerGas = Wei(BigInteger.ZERO)))
        }
    }

    @Test
    fun should_reject_negative_priority_fee() = runTest(timeout = 10.seconds) {
        assertFailsWith<TransferException.GasPriceError> {
            transferInteractor.transfer(transferData(maxPriorityFeePerGas = Wei(BigInteger("-1"))))
        }
    }

    @Test
    fun should_reject_priority_fee_greater_than_max_fee() = runTest(timeout = 10.seconds) {
        assertFailsWith<TransferException.GasPriceError> {
            transferInteractor.transfer(
                transferData(
                    maxFeePerGas = Wei(BigInteger("1000000000")),
                    maxPriorityFeePerGas = Wei(BigInteger("2000000000")),
                ),
            )
        }
    }

    @Test
    fun should_reject_gas_units_below_minimum() = runTest(timeout = 10.seconds) {
        assertFailsWith<TransferException.GasPriceError> {
            transferInteractor.transfer(transferData(gasUnits = BigInteger.valueOf(20_000)))
        }
    }

    @Test
    fun should_handle_wallet_repository_errors() = runTest(timeout = 10.seconds) {
        coEvery { walletRepository.getActiveWallet() } throws RuntimeException("Wallet not found")

        assertFailsWith<RuntimeException> {
            transferInteractor.transfer(transferData())
        }
    }

    @Test
    fun should_validate_negative_amount_throws_exception() = runTest(timeout = 10.seconds) {
        assertFailsWith<TransferException.InvalidAmount> {
            transferInteractor.transfer(transferData(amount = (-1).eth.toWei().value))
        }
    }

    @Test
    fun should_validate_gas_params_with_different_token_contracts() = runTest(timeout = 10.seconds) {
        assertFailsWith<Exception> {
            transferInteractor.transfer(
                transferData(
                    amount = BigInteger("200000000000000000000"),
                    contract = daiOnMainnet.ethAddress,
                    gasUnits = ERC20_TRANSFER_GAS_UNITS,
                ),
            )
        }
    }

    @Test
    fun should_successfully_transfer_ETH_with_sufficient_balance() = runTest(timeout = 10.seconds) {
        setUpEthTransferMocks()

        val result = transferInteractor.transfer(transferData())

        assertEquals("0xabc123def456", result)
    }

    @Test
    fun should_successfully_transfer_ERC20_token_with_sufficient_balance() = runTest(timeout = 10.seconds) {
        setUpErc20TransferMocks()

        val result = transferInteractor.transfer(
            transferData(
                amount = BigInteger("100000000000000000000"),
                contract = usdtOnMainnet.ethAddress,
                gasUnits = ERC20_TRANSFER_GAS_UNITS,
            ),
        )

        assertEquals("0xdef456abc789", result)
    }

    @Test
    fun should_gate_eth_transfer_on_amount_plus_gas_cost_boundary() = runTest(timeout = 10.seconds) {
        setUpEthTransferMocks()
        val gasCost = testMaxFeePerGas.value.multiply(ETH_TRANSFER_GAS_UNITS)
        val required = testAmount.add(gasCost)

        every { balanceProvider.observe(testWalletAddress) } returns
            flowOf(mapOf(EthContractAddress to required.subtract(BigInteger.ONE)))
        assertFailsWith<TransferException.InsufficientBalance>(
            "ETH balance one wei below amount + maxFeePerGas * gasUnits must be rejected",
        ) {
            transferInteractor.transfer(transferData())
        }

        every { balanceProvider.observe(testWalletAddress) } returns
            flowOf(mapOf(EthContractAddress to required))
        assertEquals(
            "0xabc123def456",
            transferInteractor.transfer(transferData()),
            "ETH balance exactly amount + maxFeePerGas * gasUnits must pass",
        )
    }

    @Test
    fun should_reject_erc20_transfer_when_eth_balance_below_gas_cost() = runTest(timeout = 10.seconds) {
        val gasCost = testMaxFeePerGas.value.multiply(ERC20_TRANSFER_GAS_UNITS)
        every { balanceProvider.observe(testWalletAddress) } returns flowOf(
            mapOf(
                EthContractAddress to gasCost.subtract(BigInteger.ONE),
                usdtOnMainnet.ethAddress to BigInteger.TEN.pow(21),
            ),
        )
        coEvery { balanceProvider.forceUpdate(any()) } returns Unit

        assertFailsWith<TransferException.InsufficientBalance>(
            "ERC-20 transfer with ETH balance below maxFeePerGas * gasUnits must be rejected",
        ) {
            transferInteractor.transfer(
                transferData(
                    amount = BigInteger("100000000000000000000"),
                    contract = usdtOnMainnet.ethAddress,
                    gasUnits = ERC20_TRANSFER_GAS_UNITS,
                ),
            )
        }
    }

    @Test
    fun should_sign_eip1559_fees_and_gas_units_for_ETH_transfer() = runTest(timeout = 10.seconds) {
        val txSlot = setUpEthTransferMocks()

        val hash = transferInteractor.transfer(transferData())
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
    fun should_sign_eip1559_fees_and_gas_units_for_ERC20_transfer() = runTest(timeout = 10.seconds) {
        val txSlot = setUpErc20TransferMocks()

        val hash = transferInteractor.transfer(
            transferData(
                amount = BigInteger("100000000000000000000"),
                contract = usdtOnMainnet.ethAddress,
                gasUnits = ERC20_TRANSFER_GAS_UNITS,
            ),
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
    }

    @Test
    fun should_return_fee_suggestion_from_fetch_fee_suggestion() = runTest(timeout = 10.seconds) {
        mockFeeSuggestion(baseFee = BigInteger("20000000000"), gasPrice = BigInteger("25000000000"))

        val suggestion = transferInteractor.fetchFeeSuggestion()

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
    fun should_floor_priority_fee_at_zero_when_gas_price_below_base_fee() = runTest(timeout = 10.seconds) {
        mockFeeSuggestion(baseFee = BigInteger("30000000000"), gasPrice = BigInteger("25000000000"))

        val suggestion = transferInteractor.fetchFeeSuggestion()

        assertEquals(
            BigInteger.ZERO,
            suggestion.priorityFee.value,
            "Priority fee should be floored at zero when the gas price is below the base fee",
        )
    }

    @Test
    fun should_throw_from_fetch_fee_suggestion_when_base_fee_missing() = runTest(timeout = 10.seconds) {
        val blockRequest = mockk<Request<*, EthBlock>>()
        val blockResponse = mockk<EthBlock>()
        val block = mockk<EthBlock.Block>()
        every { block.baseFeePerGas } returns null
        every { blockResponse.block } returns block
        every { blockRequest.send() } returns blockResponse
        every { web3j.ethGetBlockByNumber(any(), any()) } returns blockRequest

        assertFailsWith<RuntimeException> {
            transferInteractor.fetchFeeSuggestion()
        }
    }

    @Test
    fun should_throw_from_fetch_fee_suggestion_when_gas_price_has_error() = runTest(timeout = 10.seconds) {
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
            transferInteractor.fetchFeeSuggestion()
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

    private fun setUpEthTransferMocks(): CapturingSlot<String> {
        every { balanceProvider.observe(testWalletAddress) } returns flowOf(mapOf(EthContractAddress to 2.eth.toWei().value))

        val nonceRequest = mockk<Request<*, EthGetTransactionCount>>()
        val nonceResponse = mockk<EthGetTransactionCount>()
        every { nonceResponse.transactionCount } returns BigInteger.ONE
        every { nonceRequest.send() } returns nonceResponse
        every { web3j.ethGetTransactionCount(any(), any()) } returns nonceRequest

        val txSlot = slot<String>()
        val sendRequest = mockk<Request<*, EthSendTransaction>>()
        val sendResponse = mockk<EthSendTransaction>()
        every { sendResponse.hasError() } returns false
        every { sendResponse.transactionHash } returns "0xabc123def456"
        every { sendRequest.send() } returns sendResponse
        every { web3j.ethSendRawTransaction(capture(txSlot)) } returns sendRequest

        coEvery { balanceProvider.forceUpdate(any()) } returns Unit
        coEvery { walletRepository.getEncryptedPrivateKey(testWalletAddress) } returns "encrypted_key"
        every { keyStoreEncryptor.decryptPrivateKey("encrypted_key", testWalletAddress.toString()) } returns BigInteger("123456789")

        return txSlot
    }

    private fun setUpErc20TransferMocks(): CapturingSlot<String> {
        every { balanceProvider.observe(testWalletAddress) } returns flowOf(
            mapOf(
                EthContractAddress to 1.eth.toWei().value,
                usdtOnMainnet.ethAddress to BigInteger.TEN.pow(21),
            ),
        )

        val nonceRequest = mockk<Request<*, EthGetTransactionCount>>()
        val nonceResponse = mockk<EthGetTransactionCount>()
        every { nonceResponse.transactionCount } returns BigInteger.ONE
        every { nonceRequest.send() } returns nonceResponse
        every { web3j.ethGetTransactionCount(any(), any()) } returns nonceRequest

        val txSlot = slot<String>()
        val sendRequest = mockk<Request<*, EthSendTransaction>>()
        val sendResponse = mockk<EthSendTransaction>()
        every { sendResponse.hasError() } returns false
        every { sendResponse.transactionHash } returns "0xdef456abc789"
        every { sendRequest.send() } returns sendResponse
        every { web3j.ethSendRawTransaction(capture(txSlot)) } returns sendRequest

        coEvery { balanceProvider.forceUpdate(any()) } returns Unit
        coEvery { walletRepository.getEncryptedPrivateKey(testWalletAddress) } returns "encrypted_key"
        every { keyStoreEncryptor.decryptPrivateKey("encrypted_key", testWalletAddress.toString()) } returns BigInteger("123456789")

        return txSlot
    }
}
