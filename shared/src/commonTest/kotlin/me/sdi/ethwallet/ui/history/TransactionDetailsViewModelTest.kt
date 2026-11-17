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
import me.sdi.ethwallet.fakes.TransferHistoryRepositoryInMemory
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionDetailsViewModelTest {
    private val wallet = EthAddress("0x${"1".repeat(40)}")
    private val receiver = EthAddress("0x${"b".repeat(40)}")
    private val hash = "0x${"a".repeat(64)}"
    private val estimatedFee = TransactionFee("500".toBigInteger().toWei(), estimated = true)
    private val actualFee = TransactionFee("300".toBigInteger().toWei(), estimated = false)
    private val confirmed = Transfer.Erc20(
        hash = hash,
        timestamp = 1L,
        from = wallet,
        to = receiver,
        logIndex = 2,
        tokenAddress = wallet,
        tokenSymbol = "USDC",
        tokenDecimals = 6,
        value = "250000000",
    )
    private val sent = confirmed.copy(logIndex = UNKNOWN_LOG_INDEX, status = TransactionStatus.Pending, fee = estimatedFee)
    private val walletDao = FakeWalletDao().apply {
        wallets = listOf(
            WalletEntity(address = wallet.toString(), encryptedPrivateKeyBase64 = "unused", label = "Main"),
            WalletEntity(address = "0x" + "B".repeat(40), encryptedPrivateKeyBase64 = "unused", label = "Savings"),
        )
    }
    private val walletRepository = WalletRepository(
        walletDao = walletDao,
        ethManager = FakeEthManager(),
        privateKeyEncryptor = FakePrivateKeyEncryptor(),
        historyInitializationStore = HistoryInitializationStoreInMemory(),
    )
    private val repository = TransferHistoryRepositoryInMemory()
    private val provider = FakeTransfersHistoryProvider()

    @Test
    fun observesLowestLogOfHashAndDatabaseChanges() = detailsTest { viewModel ->
        repository.upsert(wallet, listOf(confirmed.copy(logIndex = 3), confirmed))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.viewState.collect {} }
        runCurrent()
        assertEquals(content(confirmed), viewModel.viewState.value, "The lowest log index of the hash should be shown")

        val updated = confirmed.copy(value = "300000000")
        repository.upsert(wallet, listOf(updated))
        runCurrent()
        assertEquals(content(updated), viewModel.viewState.value, "Database changes should be shown")

        walletDao.wallets = listOf(WalletEntity(address = wallet.toString(), encryptedPrivateKeyBase64 = "unused", label = "Renamed"))
        val refreshed = updated.copy(value = "400000000")
        repository.upsert(wallet, listOf(refreshed))
        runCurrent()
        assertEquals(content(refreshed, from = "Renamed", to = null), viewModel.viewState.value, "Wallet labels should be read again")
        assertEquals(0, provider.calls, "A confirmed transfer should not be polled")
    }

    @Test
    fun pendingTransferIsPolledUntilConfirmed() = detailsTest { viewModel ->
        repository.upsert(wallet, listOf(sent))
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
        val expected = sent.copy(status = TransactionStatus.Confirmed, fee = actualFee)
        assertEquals(content(expected), viewModel.viewState.value, "Confirmed status should show the actual fee")
        assertEquals(listOf(expected), repository.read(wallet), "Confirmed status should be saved")

        advanceTimeBy((POLL_STEP_MS * 4).milliseconds)
        assertEquals(3, provider.calls, "Polling should stop after a final status")
    }

    @Test
    fun failedStatusWithoutFeeKeepsEstimatedFee() = detailsTest { viewModel ->
        repository.upsert(wallet, listOf(sent))
        provider.responses += Result.success(TransactionSummary(TransactionStatus.Failed, null))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.viewState.collect {} }
        runCurrent()

        assertEquals(content(sent.copy(status = TransactionStatus.Failed)), viewModel.viewState.value, "Failed status should be shown")
        advanceTimeBy((POLL_STEP_MS * 4).milliseconds)
        assertEquals(1, provider.calls, "Polling should stop after a final status")
    }

    @Test
    fun statusErrorKeepsLastStatusAndPollsAgain() = detailsTest { viewModel ->
        repository.upsert(wallet, listOf(sent))
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
    fun resubscriptionAfterFinalStatusDoesNotPoll() = detailsTest { viewModel ->
        repository.upsert(wallet, listOf(sent))
        provider.responses += Result.success(TransactionSummary(TransactionStatus.Confirmed, actualFee))
        val firstCollector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.viewState.collect {} }
        runCurrent()
        firstCollector.cancel()
        advanceTimeBy(10.seconds)

        val states = mutableListOf<TransactionDetailsState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.viewState.collect { states += it } }
        runCurrent()

        val expected = content(sent.copy(status = TransactionStatus.Confirmed, fee = actualFee))
        assertEquals(expected, viewModel.viewState.value, "Resubscription should keep the final status")
        assertEquals(
            listOf(expected),
            states.filterIsInstance<TransactionDetailsState.Content>().distinct(),
            "Pending should not be shown again",
        )
        assertEquals(1, provider.calls, "Resubscription should not poll after a final status")
    }

    private fun content(transfer: Transfer, from: String? = "Main", to: String? = "Savings") =
        TransactionDetailsState.Content(transfer = transfer, fromWalletLabel = from, toWalletLabel = to)

    private fun detailsTest(body: suspend TestScope.(TransactionDetailsViewModel) -> Unit) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val viewModel = TransactionDetailsViewModel(
                transactionHash = hash,
                repository = repository,
                historyProvider = provider,
                walletRepository = walletRepository,
            )
            store.put("details", viewModel)
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
        ): CombinedTransfersPage = error("Not used by transaction details")

        override suspend fun getTransaction(hash: String): TransactionSummary? {
            calls++
            return responses.removeFirst().getOrThrow()
        }
    }
}

private const val POLL_STEP_MS = 5_001L
