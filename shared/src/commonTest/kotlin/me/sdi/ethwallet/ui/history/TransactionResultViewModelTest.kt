package me.sdi.ethwallet.ui.history

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.history.CombinedTransfersPage
import me.sdi.ethwallet.core.history.TransactionFee
import me.sdi.ethwallet.core.history.TransactionStatus
import me.sdi.ethwallet.core.history.TransactionSummary
import me.sdi.ethwallet.core.history.Transfer
import me.sdi.ethwallet.core.history.TransfersHistoryProvider
import me.sdi.ethwallet.core.history.TransfersPageRequest
import me.sdi.ethwallet.core.history.UNKNOWN_LOG_INDEX
import me.sdi.ethwallet.core.transfer.toWei
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.database.entity.WalletEntity
import me.sdi.ethwallet.fakes.FakeEthManager
import me.sdi.ethwallet.fakes.FakePrivateKeyEncryptor
import me.sdi.ethwallet.fakes.FakeWalletDao
import me.sdi.ethwallet.fakes.HistoryInitializationStoreInMemory
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionResultViewModelTest {
    private val sender = EthAddress("0x${"1".repeat(40)}")
    private val receiver = EthAddress("0x${"2".repeat(40)}")
    private val hash = "0x${"a".repeat(64)}"
    private val estimatedFee = TransactionFee("500".toBigInteger().toWei(), estimated = true)
    private val actualFee = TransactionFee("300".toBigInteger().toWei(), estimated = false)
    private val sent = Transfer.Erc20(
        hash = hash,
        timestamp = 1L,
        from = sender,
        to = receiver,
        logIndex = UNKNOWN_LOG_INDEX,
        tokenAddress = EthAddress("0x${"3".repeat(40)}"),
        tokenSymbol = "USDT",
        tokenDecimals = 6,
        value = "10500000",
        status = TransactionStatus.Pending,
        fee = estimatedFee,
    )
    private val provider = FakeTransfersHistoryProvider()
    private val walletRepository = WalletRepository(
        walletDao = FakeWalletDao().apply {
            wallets = listOf(
                WalletEntity(address = sender.toString(), encryptedPrivateKeyBase64 = "unused", label = "Main"),
                WalletEntity(address = receiver.toString(), encryptedPrivateKeyBase64 = "unused", label = "Savings"),
            )
        },
        ethManager = FakeEthManager(),
        privateKeyEncryptor = FakePrivateKeyEncryptor(),
        historyInitializationStore = HistoryInitializationStoreInMemory(),
    )

    @Test
    fun pollsUntilConfirmedAndReplacesEstimatedFee() = resultTest { viewModel ->
        provider.responses += listOf(
            Result.success(null),
            Result.success(TransactionSummary(TransactionStatus.Pending, estimatedFee)),
            Result.success(TransactionSummary(TransactionStatus.Confirmed, actualFee)),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.viewState.collect {} }
        runCurrent()

        assertEquals(content(sent), viewModel.viewState.value, "Not indexed transaction should stay pending")
        assertEquals(1, provider.calls, "Status should be requested at once")

        advanceTimeBy(POLL_STEP_MS.milliseconds)
        assertEquals(content(sent), viewModel.viewState.value, "Pending transaction should stay pending")
        assertEquals(2, provider.calls, "Status should be polled again")

        advanceTimeBy(POLL_STEP_MS.milliseconds)
        assertEquals(
            content(sent.copy(status = TransactionStatus.Confirmed, fee = actualFee)),
            viewModel.viewState.value,
            "Confirmed status should show the actual fee",
        )

        advanceTimeBy((POLL_STEP_MS * 4).milliseconds)
        assertEquals(3, provider.calls, "Polling should stop after a final status")
    }

    @Test
    fun failedStatusWithoutFeeKeepsEstimatedFee() = resultTest { viewModel ->
        provider.responses += Result.success(TransactionSummary(TransactionStatus.Failed, null))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.viewState.collect {} }
        runCurrent()

        assertEquals(content(sent.copy(status = TransactionStatus.Failed)), viewModel.viewState.value, "Failed status should be shown")
        advanceTimeBy((POLL_STEP_MS * 4).milliseconds)
        assertEquals(1, provider.calls, "Polling should stop after a final status")
    }

    @Test
    fun ethTransferIsConfirmed() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val ethTransfer = Transfer.Eth(
                hash = hash,
                timestamp = 1L,
                from = sender,
                to = receiver,
                valueWei = "1000".toBigInteger().toWei(),
                status = TransactionStatus.Pending,
                fee = estimatedFee,
            )
            provider.responses += Result.success(TransactionSummary(TransactionStatus.Confirmed, actualFee))
            val viewModel = TransactionResultViewModel(
                transfer = ethTransfer,
                historyProvider = provider,
                walletRepository = walletRepository,
            )
            store.put("result", viewModel)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.viewState.collect {} }
            runCurrent()

            assertEquals(
                content(ethTransfer.copy(status = TransactionStatus.Confirmed, fee = actualFee)),
                viewModel.viewState.value,
                "ETH transfer should show the confirmed status",
            )
        } finally {
            store.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun statusErrorKeepsLastStatusAndPollsAgain() = resultTest { viewModel ->
        provider.responses += listOf(
            Result.failure(IOException("Network is down")),
            Result.success(TransactionSummary(TransactionStatus.Confirmed, actualFee)),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.viewState.collect {} }
        runCurrent()

        assertEquals(content(sent), viewModel.viewState.value, "Error should keep the pending transfer")

        advanceTimeBy(POLL_STEP_MS.milliseconds)
        assertEquals(
            content(sent.copy(status = TransactionStatus.Confirmed, fee = actualFee)),
            viewModel.viewState.value,
            "Status should update after a retry",
        )
    }

    @Test
    fun resubscriptionAfterFinalStatusDoesNotPollOrShowPending() = resultTest { viewModel ->
        provider.responses += Result.success(TransactionSummary(TransactionStatus.Confirmed, actualFee))
        val firstCollector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.viewState.collect {} }
        runCurrent()
        firstCollector.cancel()
        advanceTimeBy(10.seconds)

        val states = mutableListOf<TransactionDetailsState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.viewState.collect { states += it } }
        runCurrent()

        val confirmed = content(sent.copy(status = TransactionStatus.Confirmed, fee = actualFee))
        assertEquals(confirmed, viewModel.viewState.value, "Resubscription should keep the final status")
        assertEquals(
            listOf(confirmed),
            states.filterIsInstance<TransactionDetailsState.Content>().distinct(),
            "Pending should not be shown again",
        )
        assertEquals(1, provider.calls, "Resubscription should not poll after a final status")
    }

    private fun content(transfer: Transfer) = TransactionDetailsState.Content(
        transfer = transfer,
        fromWalletLabel = "Main",
        toWalletLabel = "Savings",
    )

    private fun resultTest(body: suspend TestScope.(TransactionResultViewModel) -> Unit) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val viewModel = TransactionResultViewModel(
                transfer = sent,
                historyProvider = provider,
                walletRepository = walletRepository,
            )
            store.put("result", viewModel)
            body(viewModel)
        } finally {
            store.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    private class FakeTransfersHistoryProvider : TransfersHistoryProvider {
        val responses = ArrayDeque<Result<TransactionSummary?>>()
        var calls = 0

        override suspend fun getTransfers(
            address: EthAddress,
            eth: TransfersPageRequest,
            erc20: TransfersPageRequest,
        ): CombinedTransfersPage = error("Not used by the result screen")

        override suspend fun getTransaction(hash: String): TransactionSummary? {
            calls++
            return responses.removeFirst().getOrThrow()
        }
    }
}

private const val POLL_STEP_MS = 5_001L
