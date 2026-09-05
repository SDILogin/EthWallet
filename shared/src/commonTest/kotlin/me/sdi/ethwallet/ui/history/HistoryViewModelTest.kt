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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.history.HistoryItem
import me.sdi.ethwallet.core.history.HistoryUpdateInteractor
import me.sdi.ethwallet.core.history.TransactionHistoryRepository
import me.sdi.ethwallet.core.history.TransactionStatus
import me.sdi.ethwallet.di.commonModule
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

        assertNull(vm.viewState.value.items)
        assertFalse(vm.viewState.value.refreshing)
        runCurrent()

        assertEquals(listOf(wallet), repository.observedAddresses)
        assertEquals(listOf(wallet), updater.addresses)
        assertEquals(listOf(item), vm.viewState.value.items)
        assertFalse(vm.viewState.value.refreshing)

        updateGate.complete(Unit)
        runCurrent()
        assertFalse(vm.viewState.value.refreshing)
    }

    @Test
    fun successful_update_does_not_finish_database_loading_before_first_emission() = runTest(dispatcher) {
        val readGate = CompletableDeferred<Unit>()
        repository.beforeObserve = { readGate.await() }
        val vm = createViewModel()
        runCurrent()

        assertFalse(vm.viewState.value.refreshing)
        assertNull(vm.viewState.value.items)

        readGate.complete(Unit)
        runCurrent()
        assertEquals(emptyList(), vm.viewState.value.items)
    }

    @Test
    fun list_follows_database_emissions_including_deletion_during_refresh() = runTest(dispatcher) {
        repository.items.value = listOf(item)
        val vm = createViewModel()
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        updater.action = { gate.await() }

        vm.onEvent(HistoryEvent.Refresh)
        runCurrent()
        assertEquals(listOf(item), vm.viewState.value.items)
        assertTrue(vm.viewState.value.refreshing)
        repository.items.value = emptyList()
        runCurrent()
        assertEquals(emptyList(), vm.viewState.value.items)

        val updatedItem = item.copy(valueWei = "200")
        repository.items.value = listOf(updatedItem)
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(updatedItem), vm.viewState.value.items)
        assertFalse(vm.viewState.value.refreshing)
    }

    @Test
    fun refresh_ignores_duplicate_events_before_and_during_update() = runTest(dispatcher) {
        val vm = createViewModel()
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        updater.action = { gate.await() }

        vm.onEvent(HistoryEvent.Refresh)
        vm.onEvent(HistoryEvent.Refresh)
        runCurrent()
        vm.onEvent(HistoryEvent.Refresh)
        runCurrent()

        assertEquals(listOf(wallet, wallet), updater.addresses)
        assertTrue(vm.viewState.value.refreshing)
        gate.complete(Unit)
        runCurrent()
        assertFalse(vm.viewState.value.refreshing)
        vm.onEvent(HistoryEvent.Refresh)
        runCurrent()
        assertEquals(3, updater.addresses.size)
    }

    @Test
    fun refresh_failure_preserves_cache_and_buffers_news_once_for_late_collector() = runTest(dispatcher) {
        repository.items.value = listOf(item)
        val vm = createViewModel()
        runCurrent()
        updater.action = { error("Refresh failed") }
        vm.onEvent(HistoryEvent.Refresh)
        runCurrent()

        assertEquals(listOf(item), vm.viewState.value.items)
        assertFalse(vm.viewState.value.refreshing)
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
    fun initial_update_failure_emits_news_and_allows_pull_to_refresh() = runTest(dispatcher) {
        updater.action = { error("Offline") }
        val vm = createViewModel()
        val news = mutableListOf<HistoryNews>()
        backgroundScope.launch { vm.news.collect(news::add) }
        runCurrent()
        assertEquals(listOf<HistoryNews>(HistoryNews.ShowRefreshError), news)
        assertFalse(vm.viewState.value.refreshing)
        assertEquals(emptyList(), vm.viewState.value.items)

        updater.action = {}
        vm.onEvent(HistoryEvent.Refresh)
        runCurrent()

        assertEquals(listOf(wallet, wallet), updater.addresses)
        assertFalse(vm.viewState.value.refreshing)
        assertEquals(listOf<HistoryNews>(HistoryNews.ShowRefreshError), news)
    }

    @Test
    fun refresh_cancellation_does_not_become_error_news_and_allows_later_refresh() = runTest(dispatcher) {
        val vm = createViewModel()
        val news = mutableListOf<HistoryNews>()
        backgroundScope.launch { vm.news.collect(news::add) }
        runCurrent()
        updater.action = { throw CancellationException("Cancelled") }
        vm.onEvent(HistoryEvent.Refresh)
        runCurrent()

        assertFalse(vm.viewState.value.refreshing)
        assertEquals(emptyList(), news)
        updater.action = {}
        vm.onEvent(HistoryEvent.Refresh)
        runCurrent()
        assertEquals(3, updater.addresses.size)
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
        val app = koinApplication {
            modules(
                commonModule,
                module {
                    single<TransactionHistoryRepository> { repository }
                    factory<HistoryUpdateInteractor> { updater }
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
        val vm = createViewModel(subscribe = false)
        backgroundScope.launch { vm.news.collect {} }
        runCurrent()
        assertEquals(emptyList(), repository.observedAddresses)
        assertEquals(emptyList(), updater.addresses)
        assertNull(vm.viewState.value.items)
        assertFalse(vm.viewState.value.refreshing)

        backgroundScope.launch { vm.viewState.collect {} }
        runCurrent()
        assertEquals(listOf(wallet), repository.observedAddresses)
        assertEquals(listOf(wallet), updater.addresses)
    }

    @Test
    fun empty_database_emission_marks_list_loaded_while_initial_update_is_running() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        updater.action = { gate.await() }
        val vm = createViewModel()
        runCurrent()
        assertEquals(emptyList(), vm.viewState.value.items)
        assertEquals(1, updater.addresses.size)
        assertFalse(vm.viewState.value.refreshing)

        gate.complete(Unit)
        runCurrent()
        assertEquals(emptyList(), vm.viewState.value.items)
        assertFalse(vm.viewState.value.refreshing)
    }

    @Test
    fun refreshing_empty_history_keeps_list_loaded_and_shows_refresh_indicator() = runTest(dispatcher) {
        val vm = createViewModel()
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        updater.action = { gate.await() }
        vm.onEvent(HistoryEvent.Refresh)
        runCurrent()
        assertEquals(emptyList(), vm.viewState.value.items)
        assertTrue(vm.viewState.value.refreshing)

        gate.complete(Unit)
        runCurrent()
        assertEquals(emptyList(), vm.viewState.value.items)
        assertFalse(vm.viewState.value.refreshing)
    }

    @Test
    fun multiple_subscribers_and_resubscription_share_one_initial_update_and_observation() = runTest(dispatcher) {
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
        assertEquals(listOf(item), vm.viewState.value.items)
        assertFalse(vm.viewState.value.refreshing)
    }

    @Test
    fun clearing_viewModel_cancels_pending_database_read_without_news() = runTest(dispatcher) {
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
        assertNull(vm.viewState.value.items)
        assertEquals(emptyList(), news)
    }

    private fun TestScope.createViewModel(subscribe: Boolean = true): HistoryViewModel =
        HistoryViewModel(wallet, repository, updater).also {
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

    private class FakeHistoryUpdateInteractor : HistoryUpdateInteractor {
        val addresses = mutableListOf<EthAddress>()
        var action: suspend () -> Unit = {}

        override suspend fun update(walletAddress: EthAddress) {
            addresses += walletAddress
            action()
        }
    }
}
