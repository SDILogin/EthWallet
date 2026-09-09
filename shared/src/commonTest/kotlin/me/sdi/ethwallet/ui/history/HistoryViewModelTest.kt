package me.sdi.ethwallet.ui.history

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.history.HistoryInitializationStore
import me.sdi.ethwallet.core.history.HistoryItem
import me.sdi.ethwallet.core.history.HistoryUpdateInteractor
import me.sdi.ethwallet.core.history.TransactionHistoryRepository
import me.sdi.ethwallet.core.history.TransactionStatus
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.di.commonModule
import me.sdi.ethwallet.fakes.FakeEthManager
import me.sdi.ethwallet.fakes.FakePrivateKeyEncryptor
import me.sdi.ethwallet.fakes.FakeWalletDao
import me.sdi.ethwallet.fakes.HistoryInitializationStoreInMemory
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val repository = FakeHistoryRepository()
    private val updater = FakeHistoryUpdateInteractor()
    private val wallet = EthAddress("0x1111111111111111111111111111111111111111")
    private val item = HistoryItem.NativeTransfer(
        transactionHash = "0x${"a".repeat(64)}",
        timestamp = "2026-09-05T08:00:00Z",
        from = wallet,
        to = EthAddress("0x2222222222222222222222222222222222222222"),
        valueWei = "100",
        status = TransactionStatus.Confirmed,
    )

    private val historyInitializationStoreInMemory = HistoryInitializationStoreInMemory()
    private val chainId = 1L
    private val accounts = MutableStateFlow<List<EthWallet>>(emptyList())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun initialLoad_reads_cache_while_update_is_running() = runTest(dispatcher) {
        repository.items.value = listOf(item)
        val updateGate = CompletableDeferred<Unit>()
        updater.action = { updateGate.await() }
        val vm = createViewModel()

        assertEquals(HistoryViewState.Loading, vm.viewState.value)
        runCurrent()

        assertEquals(listOf(wallet), repository.observedAddresses)
        assertEquals(listOf(wallet), updater.addresses)
        assertEquals(listOf(requireNotNull(item.toHistoryRow(wallet))), assertIs<HistoryViewState.Content>(vm.viewState.value).items)
        assertFalse((vm.viewState.value as? HistoryViewState.Content)?.refreshing == true)

        updateGate.complete(Unit)
        runCurrent()
        assertFalse((vm.viewState.value as? HistoryViewState.Content)?.refreshing == true)
    }

    @Test
    fun successful_update_does_not_finish_database_loading_before_first_emission() = runTest(dispatcher) {
        val readGate = CompletableDeferred<Unit>()
        repository.beforeObserve = { readGate.await() }
        val vm = createViewModel()
        runCurrent()

        assertEquals(HistoryViewState.Loading, vm.viewState.value)

        readGate.complete(Unit)
        runCurrent()
        assertEquals(emptyList(), assertIs<HistoryViewState.Content>(vm.viewState.value).items)
    }

    @Test
    fun list_follows_database_emissions_including_deletion_during_refresh() = runTest(dispatcher) {
        repository.items.value = listOf(item)
        val vm = createViewModel()
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        updater.action = { gate.await() }

        vm.onEvent(HistoryUserAction.Refresh)
        runCurrent()
        assertEquals(listOf(requireNotNull(item.toHistoryRow(wallet))), assertIs<HistoryViewState.Content>(vm.viewState.value).items)
        assertTrue(assertIs<HistoryViewState.Content>(vm.viewState.value).refreshing)
        repository.items.value = emptyList()
        runCurrent()
        assertEquals(emptyList(), assertIs<HistoryViewState.Content>(vm.viewState.value).items)

        val updatedItem = item.copy(valueWei = "200")
        repository.items.value = listOf(updatedItem)
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(requireNotNull(updatedItem.toHistoryRow(wallet))), assertIs<HistoryViewState.Content>(vm.viewState.value).items)
        assertFalse((vm.viewState.value as? HistoryViewState.Content)?.refreshing == true)
    }

    @Test
    fun refresh_ignores_duplicate_events_before_and_during_update() = runTest(dispatcher) {
        val vm = createViewModel()
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        updater.action = { gate.await() }

        vm.onEvent(HistoryUserAction.Refresh)
        vm.onEvent(HistoryUserAction.Refresh)
        runCurrent()
        vm.onEvent(HistoryUserAction.Refresh)
        runCurrent()

        assertEquals(listOf(wallet, wallet), updater.addresses)
        assertTrue(assertIs<HistoryViewState.Content>(vm.viewState.value).refreshing)
        gate.complete(Unit)
        runCurrent()
        assertFalse((vm.viewState.value as? HistoryViewState.Content)?.refreshing == true)
        vm.onEvent(HistoryUserAction.Refresh)
        runCurrent()
        assertEquals(3, updater.addresses.size)
    }

    @Test
    fun refresh_failure_preserves_cache_and_buffers_news_once_for_late_collector() = runTest(dispatcher) {
        repository.items.value = listOf(item)
        val vm = createViewModel()
        runCurrent()
        updater.action = { error("Refresh failed") }
        vm.onEvent(HistoryUserAction.Refresh)
        runCurrent()

        assertEquals(listOf(requireNotNull(item.toHistoryRow(wallet))), assertIs<HistoryViewState.Content>(vm.viewState.value).items)
        assertFalse((vm.viewState.value as? HistoryViewState.Content)?.refreshing == true)
        val news = mutableListOf<HistoryNews>()
        val firstCollector = backgroundScope.launch { vm.news.collect(news::add) }
        runCurrent()
        assertEquals(listOf<HistoryNews>(HistoryNews.ShowRefreshError), news)
        firstCollector.cancel()
        runCurrent()
        backgroundScope.launch { vm.news.collect(news::add) }
        runCurrent()
        assertEquals(listOf<HistoryNews>(HistoryNews.ShowRefreshError), news)
    }

    @Test
    fun initial_update_failure_shows_error_and_retry_loads_empty_content() = runTest(dispatcher) {
        updater.action = { error("Offline") }
        val vm = createViewModel()
        val news = mutableListOf<HistoryNews>()
        backgroundScope.launch { vm.news.collect(news::add) }
        runCurrent()
        assertEquals(HistoryViewState.Error, vm.viewState.value)
        assertEquals(emptyList(), news)

        val gate = CompletableDeferred<Unit>()
        updater.action = { gate.await() }
        vm.onEvent(HistoryUserAction.RetryInitialLoad)
        runCurrent()
        assertEquals(HistoryViewState.Loading, vm.viewState.value)

        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(wallet, wallet), updater.addresses)
        assertEquals(HistoryViewState.Content(emptyList(), refreshing = false), vm.viewState.value)
        assertEquals(emptyList(), news)
    }

    @Test
    fun refresh_cancellation_does_not_become_error_news_and_allows_later_refresh() = runTest(dispatcher) {
        val vm = createViewModel()
        val news = mutableListOf<HistoryNews>()
        backgroundScope.launch { vm.news.collect(news::add) }
        runCurrent()
        updater.action = { throw CancellationException("Cancelled") }
        vm.onEvent(HistoryUserAction.Refresh)
        runCurrent()

        assertFalse((vm.viewState.value as? HistoryViewState.Content)?.refreshing == true)
        assertEquals(emptyList(), news)
        updater.action = {}
        vm.onEvent(HistoryUserAction.Refresh)
        runCurrent()
        assertEquals(3, updater.addresses.size)
    }

    @Test
    fun failed_retry_returns_to_error_without_toast() = runTest(dispatcher) {
        updater.action = { error("Offline") }
        val vm = createViewModel()
        val news = mutableListOf<HistoryNews>()
        backgroundScope.launch { vm.news.collect(news::add) }
        runCurrent()
        assertEquals(HistoryViewState.Error, vm.viewState.value)

        vm.onEvent(HistoryUserAction.RetryInitialLoad)
        runCurrent()
        assertEquals(2, updater.addresses.size)
        assertEquals(HistoryViewState.Error, vm.viewState.value)
        assertEquals(emptyList(), news)
    }

    @Test
    fun retry_failure_preserves_items_arriving_during_retry_and_emits_news() = runTest(dispatcher) {
        updater.action = { error("Offline") }
        val vm = createViewModel()
        val news = mutableListOf<HistoryNews>()
        backgroundScope.launch { vm.news.collect(news::add) }
        runCurrent()
        assertEquals(HistoryViewState.Error, vm.viewState.value)

        val gate = CompletableDeferred<Unit>()
        updater.action = {
            gate.await()
            error("Still offline")
        }
        vm.onEvent(HistoryUserAction.RetryInitialLoad)
        runCurrent()
        assertEquals(HistoryViewState.Loading, vm.viewState.value)
        repository.items.value = listOf(item)
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertEquals(HistoryViewState.Content(listOf(requireNotNull(item.toHistoryRow(wallet))), false), vm.viewState.value)
        assertEquals(listOf<HistoryNews>(HistoryNews.ShowRefreshError), news)
    }

    @Test
    fun clearing_viewModel_cancels_database_observation_and_running_update() = runTest(dispatcher) {
        var updateCancelled = false
        updater.action = {
            try {
                awaitCancellation()
            } finally {
                updateCancelled = true
            }
        }
        val vm = createViewModel()
        val news = mutableListOf<HistoryNews>()
        backgroundScope.launch { vm.news.collect(news::add) }
        runCurrent()
        assertEquals(1, repository.activeObservers)

        store.clear()
        runCurrent()

        assertTrue(updateCancelled)
        assertEquals(0, repository.activeObservers)
        assertEquals(emptyList(), news)
    }

    @Test
    fun koin_passes_wallet_parameter_to_history_dependencies() = runTest(dispatcher) {
        historyInitializationStoreInMemory.markInitialized(chainId, wallet)
        val app = koinApplication {
            modules(
                commonModule,
                module {
                    single {
                        WalletRepository(
                            FakeWalletDao(),
                            FakeEthManager(),
                            FakePrivateKeyEncryptor(),
                            historyInitializationStoreInMemory,
                        )
                    }
                    single<TransactionHistoryRepository> { repository }
                    factory<HistoryUpdateInteractor> { updater }
                    single<HistoryInitializationStore> { historyInitializationStoreInMemory }
                    single { ChainConfig("https://unused.invalid", chainId, null, "https://unused.invalid") }
                },
            )
        }
        try {
            val vm = app.koin.get<HistoryViewModel> { parametersOf(wallet) }
            store.put("history", vm)
            backgroundScope.launch { vm.viewState.collect {} }
            runCurrent()
            assertEquals(listOf(wallet), repository.observedAddresses)
            assertEquals(listOf(wallet), updater.addresses)
        } finally {
            store.clear()
            app.close()
        }
    }

    @Test
    fun construction_and_news_collection_do_not_start_work() = runTest(dispatcher) {
        historyInitializationStoreInMemory.markInitialized(chainId, wallet)
        val vm = createViewModel(subscribe = false)
        backgroundScope.launch { vm.news.collect {} }
        runCurrent()
        assertEquals(emptyList(), repository.observedAddresses)
        assertEquals(emptyList(), updater.addresses)
        assertEquals(HistoryViewState.Loading, vm.viewState.value)

        backgroundScope.launch { vm.viewState.collect {} }
        runCurrent()
        assertEquals(listOf(wallet), repository.observedAddresses)
        assertEquals(listOf(wallet), updater.addresses)
    }

    @Test
    fun empty_cache_keeps_shimmers_until_initial_update_succeeds_with_empty_history() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        updater.action = { gate.await() }
        val vm = createViewModel()
        runCurrent()
        assertEquals(HistoryViewState.Loading, vm.viewState.value, "An empty cache must not finish initial loading")
        assertEquals(1, updater.addresses.size)
        assertFalse((vm.viewState.value as? HistoryViewState.Content)?.refreshing == true)

        gate.complete(Unit)
        runCurrent()
        assertEquals(emptyList(), assertIs<HistoryViewState.Content>(vm.viewState.value).items)
        assertFalse((vm.viewState.value as? HistoryViewState.Content)?.refreshing == true)
    }

    @Test
    fun first_network_items_replace_shimmers_without_an_intermediate_empty_state() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        updater.action = {
            gate.await()
            repository.items.value = listOf(item)
        }
        val vm = createViewModel()
        val states = mutableListOf<HistoryViewState>()
        backgroundScope.launch { vm.viewState.collect(states::add) }
        runCurrent()

        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf(requireNotNull(item.toHistoryRow(wallet))), assertIs<HistoryViewState.Content>(vm.viewState.value).items)
        assertTrue(states.isNotEmpty())
        assertTrue(
            states.none { it is HistoryViewState.Content && it.items.isEmpty() },
            "First load must transition from shimmers to Room items without flashing empty content",
        )
        assertTrue(states.none { it is HistoryViewState.Content && it.refreshing }, "Startup must not show the P2R indicator")
    }

    @Test
    fun reopening_after_cancelled_initial_update_keeps_shimmers_for_uninitialized_empty_cache() = runTest(dispatcher) {
        updater.action = { awaitCancellation() }
        createViewModel()
        runCurrent()
        store.clear()
        runCurrent()

        val reopened = createViewModel()
        runCurrent()

        assertEquals(listOf(wallet, wallet), updater.addresses)
        assertEquals(HistoryViewState.Loading, reopened.viewState.value, "A cancelled update must not initialize empty history")
        assertFalse((reopened.viewState.value as? HistoryViewState.Content)?.refreshing == true)
    }

    @Test
    fun refreshing_empty_history_keeps_list_loaded_and_shows_refresh_indicator() = runTest(dispatcher) {
        val vm = createViewModel()
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        updater.action = { gate.await() }
        vm.onEvent(HistoryUserAction.Refresh)
        runCurrent()
        assertEquals(emptyList(), assertIs<HistoryViewState.Content>(vm.viewState.value).items)
        assertTrue(assertIs<HistoryViewState.Content>(vm.viewState.value).refreshing)

        gate.complete(Unit)
        runCurrent()
        assertEquals(emptyList(), assertIs<HistoryViewState.Content>(vm.viewState.value).items)
        assertFalse((vm.viewState.value as? HistoryViewState.Content)?.refreshing == true)
    }

    @Test
    fun multiple_subscribers_and_resubscription_share_one_initial_update_and_observation() = runTest(dispatcher) {
        historyInitializationStoreInMemory.markInitialized(chainId, wallet)
        val vm = createViewModel(subscribe = false)
        val first = backgroundScope.launch { vm.viewState.collect {} }
        val second = backgroundScope.launch { vm.viewState.collect {} }
        runCurrent()
        first.cancel()
        second.cancel()
        runCurrent()
        advanceTimeBy(4_999L)
        runCurrent()
        backgroundScope.launch { vm.viewState.collect {} }
        runCurrent()

        assertEquals(1, repository.activeObservers)
        assertEquals(listOf(wallet), repository.observedAddresses)
        assertEquals(listOf(wallet), updater.addresses)
    }

    @Test
    fun resubscription_after_timeout_replaces_observation_and_updates_once() = runTest(dispatcher) {
        historyInitializationStoreInMemory.markInitialized(chainId, wallet)
        val vm = createViewModel(subscribe = false)
        val first = backgroundScope.launch { vm.viewState.collect {} }
        runCurrent()
        assertEquals(1, repository.activeObservers)

        first.cancel()
        runCurrent()
        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(1, repository.activeObservers)

        repository.items.value = listOf(item)
        backgroundScope.launch { vm.viewState.collect {} }
        backgroundScope.launch { vm.viewState.collect {} }
        runCurrent()

        assertEquals(1, repository.activeObservers)
        assertEquals(listOf(wallet, wallet), repository.observedAddresses)
        assertEquals(listOf(wallet, wallet), updater.addresses)
        assertEquals(listOf(requireNotNull(item.toHistoryRow(wallet))), assertIs<HistoryViewState.Content>(vm.viewState.value).items)
        assertFalse((vm.viewState.value as? HistoryViewState.Content)?.refreshing == true)
    }

    @Test
    fun clearing_viewModel_cancels_pending_database_read_without_news() = runTest(dispatcher) {
        historyInitializationStoreInMemory.markInitialized(chainId, wallet)
        repository.beforeObserve = { awaitCancellation() }
        val vm = createViewModel()
        val news = mutableListOf<HistoryNews>()
        backgroundScope.launch { vm.news.collect(news::add) }
        runCurrent()
        assertEquals(1, repository.activeObservers)

        store.clear()
        runCurrent()
        advanceTimeBy(2_000L)
        runCurrent()

        assertEquals(0, repository.activeObservers)
        assertEquals(listOf(wallet), repository.observedAddresses)
        assertEquals(HistoryViewState.Loading, vm.viewState.value)
        assertEquals(emptyList(), news)
    }

    @Test
    fun account_changes_reclassify_cached_transfers_without_network_update() = runTest(dispatcher) {
        repository.items.value = listOf(item)
        val vm = createViewModel()
        runCurrent()
        assertEquals(TransactionType.Sent, assertIs<HistoryViewState.Content>(vm.viewState.value).items.single().action)

        accounts.value = listOf(EthWallet("Other account", requireNotNull(item.to)))
        runCurrent()
        val row = assertIs<HistoryViewState.Content>(vm.viewState.value).items.single()
        assertEquals(TransactionType.Self, row.action)
        assertEquals(item.to, row.counterparty)
        assertEquals(listOf(wallet), updater.addresses)

        accounts.value = emptyList()
        runCurrent()
        assertEquals(TransactionType.Sent, assertIs<HistoryViewState.Content>(vm.viewState.value).items.single().action)
    }

    @Test
    fun mapping_excludes_contract_creation_zero_native_value_and_unrelated_transfers() = runTest(dispatcher) {
        repository.items.value = listOf(
            item,
            item.copy(to = null),
            item.copy(valueWei = "0"),
            item.copy(from = requireNotNull(item.to)),
        )
        val vm = createViewModel()
        runCurrent()
        assertEquals(listOf(requireNotNull(item.toHistoryRow(wallet))), assertIs<HistoryViewState.Content>(vm.viewState.value).items)
    }

    private fun TestScope.createViewModel(subscribe: Boolean = true): HistoryViewModel =
        HistoryViewModel(
            walletAddress = wallet,
            historyRepository = repository,
            historyUpdateInteractor = updater,
            historyInitializationStore = historyInitializationStoreInMemory,
            chainId = chainId,
            walletAccounts = accounts,
        ).also {
            store.put("history", it)
            if (subscribe) backgroundScope.launch { it.viewState.collect {} }
        }

    private class FakeHistoryRepository : TransactionHistoryRepository {
        val items = MutableStateFlow<List<HistoryItem>>(emptyList())
        val observedAddresses = mutableListOf<EthAddress>()
        var beforeObserve: suspend () -> Unit = {}
        var activeObservers = 0

        override fun observe(walletAddress: EthAddress): Flow<List<HistoryItem>> = flow {
            observedAddresses += walletAddress
            activeObservers++
            try {
                beforeObserve()
                emitAll(items)
            } finally {
                activeObservers--
            }
        }

        override suspend fun upsert(walletAddress: EthAddress, items: List<HistoryItem>) {
            error("ViewModel must only observe the repository")
        }
    }

    private inner class FakeHistoryUpdateInteractor : HistoryUpdateInteractor {
        val addresses = mutableListOf<EthAddress>()
        var action: suspend () -> Unit = {}

        override suspend fun update(chainId: Long, walletAddress: EthAddress) {
            addresses += walletAddress
            action()
            historyInitializationStoreInMemory.markInitialized(chainId, walletAddress)
        }
    }
}
