package me.sdi.ethwallet.core.transfer

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.core.BalanceProvider
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.core.EthManager
import me.sdi.ethwallet.core.security.PrivateKeyEncryptor
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.kotlin.PrivateKey
import me.sdi.ethwallet.kotlin.deriveAddress
import me.sdi.ethwallet.ui.daiOnMainnet
import me.sdi.ethwallet.ui.usdtOnMainnet
import java.math.BigInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class EthTransferInteractorTest {
    private lateinit var ethManager: EthManager
    private lateinit var walletRepository: WalletRepository
    private lateinit var privateKeyEncryptor: PrivateKeyEncryptor
    private lateinit var balanceProvider: BalanceProvider
    private lateinit var transferInteractor: TransferInteractor

    private val testWalletAddress = EthAddress("0x1234567890123456789012345678901234567890")
    private val testDestinationAddress = EthAddress("0x9876543210987654321098765432109876543210")
    private val testAmount = 1.eth.toWei().value
    private val testMaxFeePerGas = Wei(BigInteger("30000000000"))
    private val testMaxPriorityFeePerGas = Wei(BigInteger("2000000000"))

    @BeforeTest
    fun setUp() {
        ethManager = mockk()
        walletRepository = mockk()
        privateKeyEncryptor = mockk()
        balanceProvider = mockk()
        transferInteractor = EthTransferInteractor(ethManager, walletRepository, privateKeyEncryptor, balanceProvider)

        coEvery {
            ethManager.sendTransfer(any(), EthContractAddress, testDestinationAddress, any(), any(), any(), any())
        } returns "0xabc123def456"
    }

    private fun transferData(
        sender: EthAddress = testWalletAddress,
        destination: EthAddress = testDestinationAddress,
        amount: BigInteger = testAmount,
        contract: EthAddress = EthContractAddress,
        maxFeePerGas: Wei = testMaxFeePerGas,
        maxPriorityFeePerGas: Wei = testMaxPriorityFeePerGas,
        gasUnits: BigInteger = ETH_TRANSFER_GAS_UNITS,
    ) = TransferData(
        senderAddress = sender,
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
        setUpEthTransferMocks()
        coEvery { walletRepository.getEncryptedPrivateKey(testWalletAddress) } throws RuntimeException("Wallet not found")

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
        val privateKey = setUpEthTransferMocks()

        val result = transferInteractor.transfer(transferData())

        assertEquals("0xabc123def456", result)
        assertPrivateKeyCleared(privateKey)
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
        coEvery { balanceProvider.forceUpdate(testWalletAddress) } returns Unit

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
    fun should_wrap_eth_manager_failure_as_transaction_failed() = runTest(timeout = 10.seconds) {
        val privateKey = setUpEthTransferMocks()
        coEvery {
            ethManager.sendTransfer(any(), EthContractAddress, testDestinationAddress, any(), any(), any(), any())
        } throws RuntimeException("node rejected the transaction")

        assertFailsWith<TransferException.TransactionFailed>(
            "An EthManager failure must surface as TransferException.TransactionFailed",
        ) {
            transferInteractor.transfer(transferData())
        }
        assertPrivateKeyCleared(privateKey)
    }

    @Test
    fun should_propagate_cancellation_exception_unwrapped() = runTest(timeout = 10.seconds) {
        val privateKey = setUpEthTransferMocks()
        coEvery {
            ethManager.sendTransfer(any(), EthContractAddress, testDestinationAddress, any(), any(), any(), any())
        } throws CancellationException("cancelled")

        assertFailsWith<CancellationException>(
            "CancellationException must propagate un-wrapped rather than as a TransferException",
        ) {
            transferInteractor.transfer(transferData())
        }
        assertPrivateKeyCleared(privateKey)
    }

    @Test
    fun should_delegate_fetch_fee_suggestion_to_eth_manager() = runTest(timeout = 10.seconds) {
        val expected = FeeSuggestion(baseFee = Wei(BigInteger("20000000000")), priorityFee = Wei(BigInteger("5000000000")))
        coEvery { ethManager.fetchFeeSuggestion() } returns expected

        assertEquals(expected, transferInteractor.fetchFeeSuggestion(), "fetchFeeSuggestion should delegate to EthManager")
    }

    @Test
    fun should_use_transfer_sender_for_balance_and_private_key_lookup() = runTest(timeout = 10.seconds) {
        val sender = EthAddress("0x1111111111111111111111111111111111111111")
        every { balanceProvider.observe(sender) } returns flowOf(mapOf(EthContractAddress to 2.eth.toWei().value))
        coEvery { balanceProvider.forceUpdate(sender) } returns Unit
        coEvery { walletRepository.getEncryptedPrivateKey(sender) } returns "encrypted_key"
        coEvery { privateKeyEncryptor.decryptPrivateKey("encrypted_key", sender.toString()) } returns privateKey123()

        transferInteractor.transfer(transferData(sender = sender))

        coVerify { balanceProvider.forceUpdate(sender) }
        verify { balanceProvider.observe(sender) }
        coVerify { walletRepository.getEncryptedPrivateKey(sender) }
        coVerify { privateKeyEncryptor.decryptPrivateKey("encrypted_key", sender.toString()) }
    }

    private fun setUpEthTransferMocks(): PrivateKey {
        val privateKey = privateKey123()
        every { balanceProvider.observe(testWalletAddress) } returns flowOf(mapOf(EthContractAddress to 2.eth.toWei().value))
        coEvery { balanceProvider.forceUpdate(testWalletAddress) } returns Unit
        coEvery { walletRepository.getEncryptedPrivateKey(testWalletAddress) } returns "encrypted_key"
        coEvery { privateKeyEncryptor.decryptPrivateKey("encrypted_key", testWalletAddress.toString()) } returns privateKey
        return privateKey
    }

    private fun setUpErc20TransferMocks() {
        every { balanceProvider.observe(testWalletAddress) } returns flowOf(
            mapOf(
                EthContractAddress to 1.eth.toWei().value,
                usdtOnMainnet.ethAddress to BigInteger.TEN.pow(21),
            ),
        )
        coEvery { balanceProvider.forceUpdate(testWalletAddress) } returns Unit
        coEvery { walletRepository.getEncryptedPrivateKey(testWalletAddress) } returns "encrypted_key"
        coEvery { privateKeyEncryptor.decryptPrivateKey("encrypted_key", testWalletAddress.toString()) } returns privateKey123()
        coEvery {
            ethManager.sendTransfer(any(), usdtOnMainnet.ethAddress, testDestinationAddress, any(), any(), any(), any())
        } returns "0xdef456abc789"
    }

    private fun privateKey123(): PrivateKey = PrivateKey.fromBytes(
        ByteArray(32).also { it[31] = 123 },
    )

    private suspend fun assertPrivateKeyCleared(privateKey: PrivateKey) {
        assertFailsWith<IllegalStateException> {
            privateKey.deriveAddress()
        }
    }
}
