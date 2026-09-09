package me.sdi.ethwallet.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.history.HistoryInitializationStore
import me.sdi.ethwallet.core.history.HistoryItem
import me.sdi.ethwallet.core.history.HistoryUpdateInteractor
import me.sdi.ethwallet.core.history.TransactionHistoryRepository
import me.sdi.ethwallet.core.history.TransactionStatus
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.ui.history.HistoryViewState.Content
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.time.Instant

class HistoryViewModel(
    private val walletAddress: EthAddress,
    private val historyRepository: TransactionHistoryRepository,
    private val historyUpdateInteractor: HistoryUpdateInteractor,
    private val historyInitializationStore: HistoryInitializationStore,
    private val chainId: Long,
    private val walletAccounts: Flow<List<EthWallet>>,
) : ViewModel() {
    private var observationJob: Job? = null

    private val _viewState = MutableStateFlow<HistoryViewState>(HistoryViewState.Loading)
    val viewState: StateFlow<HistoryViewState> = _viewState
        .onStart {
            // just in case view resubscribed while view model was already observing
            observationJob?.cancel()
            observationJob = viewModelScope.launch {
                historyInitializationStore
                    .observe(chainId, walletAddress)
                    .distinctUntilChanged()
                    .collectLatest { initialized ->
                        historyRepository.observe(walletAddress)
                            .combine(walletAccounts) { items, accounts ->
                                val addresses = accounts.mapTo(mutableSetOf(walletAddress.toString().lowercase())) {
                                    it.address.toString().lowercase()
                                }
                                items.mapNotNull { it.toHistoryRow(walletAddress, addresses) }
                                    .sortedWith(
                                        compareBy<HistoryRow> { it.status != TransactionStatus.Pending }
                                            .thenByDescending { it.timestampMillis },
                                    )
                            }.collect { rows ->
                                updateViewState(HistoryEvent.HistoryItemsListChange(rows, initialized))
                            }
                    }
            }

            // force update on start
            viewModelScope.launch(CoroutineName("history-update-on-start")) {
                try {
                    historyUpdateInteractor.update(chainId, walletAddress)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e

                    Logger.warn("HistoryViewModel", e) {
                        "Failed to load history on start"
                    }

                    updateViewState(HistoryEvent.HistoryUpdateOnStartFailed)

                    if (_viewState.value !is HistoryViewState.Error) {
                        // something is on screen (not an error state, maybe we have something is DB).
                        // Show toast instead of overriding content
                        Logger.warn("HistoryViewModel") {
                            "Initial history loading failed, but there are some records in the db. Display DB records"
                        }
                        newsChannel.send(HistoryNews.ShowRefreshError)
                    }
                }
            }
        }
        .onEach { Logger.debug("HistoryViewModel") { "$it" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), HistoryViewState.Loading)

    private val newsChannel = Channel<HistoryNews>(Channel.BUFFERED)
    val news: Flow<HistoryNews> = newsChannel.receiveAsFlow()

    fun onEvent(event: HistoryUserAction) {
        Logger.debug("HistoryViewModel") { "onEvent: $event" }

        when (event) {
            HistoryUserAction.Refresh -> onRefresh()
            HistoryUserAction.RetryInitialLoad -> onRetryInitialLoad()
        }
    }

    /**
     * No content on screen. We are in Error state when this method is called
     */
    private fun onRetryInitialLoad() {
        updateViewState(HistoryEvent.RetryInitialLoadStart)
        viewModelScope.launch(CoroutineName("retry-initial-load")) {
            try {
                historyUpdateInteractor.update(chainId, walletAddress)
            } catch (e: Exception) {
                if (e is CancellationException) throw e

                Logger.warn("HistoryViewModel", e) { "Failed to retry initial load" }

                updateViewState(HistoryEvent.RetryInitialLoadFail)
                if (_viewState.value !is HistoryViewState.Error) {
                    // something is on screen (not an error state, maybe we have something is DB).
                    // Show toast instead of overriding content
                    Logger.warn("HistoryViewModel") {
                        "Initial history loading failed, but there are some records in the db. Display DB records"
                    }
                    newsChannel.send(HistoryNews.ShowRefreshError)
                }
            }
        }
    }

    private fun onRefresh() {
        val viewState = _viewState.value
        if (viewState !is HistoryViewState.Content) {
            Logger.warn("HistoryViewModel") {
                "Pull2Refresh call while not in content state. Should never happen."
            }
            return
        }

        if (viewState.refreshing) {
            Logger.warn("HistoryViewModel") {
                "Pull2Refresh call while already refreshing. Should never happen."
            }
            return
        }

        updateViewState(HistoryEvent.P2RefreshStart)
        viewModelScope.launch(CoroutineName("pull2refresh")) {
            try {
                historyUpdateInteractor.update(chainId, walletAddress)
            } catch (e: Exception) {
                if (e is CancellationException) throw e

                Logger.warn("HistoryViewModel", e) { "Failed to refresh history" }
                newsChannel.send(HistoryNews.ShowRefreshError)
            } finally {
                updateViewState(HistoryEvent.P2RefreshFinish)
            }
        }
    }

    private fun updateViewState(event: HistoryEvent) = _viewState.update { historyReducer(it, event) }
}

sealed interface HistoryViewState {
    data object Loading : HistoryViewState

    data class Content(
        val items: List<HistoryRow>,
        val refreshing: Boolean,
    ) : HistoryViewState

    data object Error : HistoryViewState
}

/**
 * General interface for both public (user generated events, that goes from view) and for
 * private events (view model specific, technical). General interface is used to simplify
 * reducer implementation.
 */
sealed interface HistoryEvent {
    /**
     * Called only in Error state
     */
    data object RetryInitialLoadStart : HistoryEvent

    /**
     * Called only in Loading state
     */
    data object RetryInitialLoadFail : HistoryEvent

    data object P2RefreshStart : HistoryEvent

    data object P2RefreshFinish : HistoryEvent

    data object HistoryUpdateOnStartFailed : HistoryEvent

    /**
     * DB records set has changed. Something were loaded. DB is a single source of truth so we have to display
     * an update list on this event.
     */
    data class HistoryItemsListChange(
        val items: List<HistoryRow>,
        val initialized: Boolean,
    ) : HistoryEvent

    /**
     * This is account intialization flag. It will be reset after the first successful history loading.
     * On start we force update history, and this is also an "initial load", but "initial" in context
     * of screen.
     *
     * This flag is account specific initialization flag. It just shows if an empty db stands for
     * the empty history or we haven't loaded anything yet.
     */
    data class InitialLoadingFlagChange(val initialized: Boolean) : HistoryEvent
}

internal val historyReducer: (HistoryViewState, HistoryEvent) -> HistoryViewState =
    { state: HistoryViewState, event: HistoryEvent ->
        val reduceContent: HistoryViewState.Content.(HistoryEvent) -> HistoryViewState = {
            when (event) {
                is HistoryEvent.HistoryItemsListChange -> copy(items = event.items)
                HistoryEvent.HistoryUpdateOnStartFailed -> this // no ui changes, just show toast
                is HistoryEvent.InitialLoadingFlagChange -> this // no ui changes, some content is on screen
                HistoryEvent.P2RefreshFinish -> copy(refreshing = false)
                HistoryEvent.P2RefreshStart -> copy(refreshing = true)
                HistoryEvent.RetryInitialLoadFail -> this // initial load triggered only in error state
                HistoryEvent.RetryInitialLoadStart -> this // initial load triggered only in error state
            }
        }

        val reduceError: HistoryViewState.Error.(HistoryEvent) -> HistoryViewState = {
            when (event) {
                is HistoryEvent.HistoryItemsListChange -> {
                    if (event.items.isEmpty() && !event.initialized) {
                        // first db emission (before we made the first one successful network request for a new account)
                        // we must still show error. On this stage we don't know if account has any history
                        HistoryViewState.Error
                    } else {
                        Content(event.items, refreshing = false)
                    }
                }
                HistoryEvent.HistoryUpdateOnStartFailed -> {
                    this
                } // not happened in this state
                is HistoryEvent.InitialLoadingFlagChange -> {
                    this
                } // not happened in this state
                HistoryEvent.P2RefreshStart -> {
                    this
                } // there is no p2r on error screen
                HistoryEvent.P2RefreshFinish -> {
                    this
                } // there is no p2r on error screen
                HistoryEvent.RetryInitialLoadFail -> {
                    this
                } // will never happen in Error state. Fail happens on Loading screen
                HistoryEvent.RetryInitialLoadStart -> {
                    HistoryViewState.Loading
                }
            }
        }

        val reduceLoading: HistoryViewState.Loading.(HistoryEvent) -> HistoryViewState = {
            when (event) {
                is HistoryEvent.HistoryItemsListChange -> {
                    if (event.items.isEmpty() && !event.initialized) {
                        // first db emission (before we made the first network request for a new account)
                        // we must still show shimmers. On this stage we don't know if account has any history
                        HistoryViewState.Loading
                    } else {
                        Content(event.items, refreshing = false)
                    }
                }
                // very first load from network failed (first load for a newly created account)
                HistoryEvent.HistoryUpdateOnStartFailed -> {
                    HistoryViewState.Error
                }
                is HistoryEvent.InitialLoadingFlagChange -> {
                    this
                }
                HistoryEvent.P2RefreshFinish -> {
                    this
                } // p2r is unavailable for loading state
                HistoryEvent.P2RefreshStart -> {
                    this
                } // p2r is unavailable for loading state
                HistoryEvent.RetryInitialLoadFail -> {
                    HistoryViewState.Error
                }
                HistoryEvent.RetryInitialLoadStart -> {
                    HistoryViewState.Loading
                }
            }
        }

        when (state) {
            is Content -> reduceContent(state, event)
            is HistoryViewState.Error -> reduceError(state, event)
            is HistoryViewState.Loading -> reduceLoading(state, event)
        }
    }

sealed interface HistoryUserAction {
    data object Refresh : HistoryUserAction

    data object RetryInitialLoad : HistoryUserAction
}

sealed interface HistoryNews {
    data object ShowRefreshError : HistoryNews
}

internal fun HistoryItem.toHistoryRow(
    walletAddress: EthAddress,
    accountAddresses: Set<String> = setOf(
        walletAddress.toString().lowercase(),
    ),
): HistoryRow? {
    val outgoing = from.toString().equals(walletAddress.toString(), ignoreCase = true)
    val incoming = to?.toString()?.equals(walletAddress.toString(), ignoreCase = true) == true
    if (!outgoing && !incoming) return null
    if (to == null) return null
    if (this is HistoryItem.NativeTransfer && valueWei.toBigDecimalOrNull()?.signum() == 0) return null
    val action = when {
        from.toString().lowercase() in accountAddresses && to.toString().lowercase() in accountAddresses -> TransactionType.Self
        outgoing -> TransactionType.Sent
        incoming -> TransactionType.Received
        else -> return null
    }
    val status = (this as? HistoryItem.NativeTransfer)?.status ?: TransactionStatus.Confirmed
    val value = when (this) {
        is HistoryItem.NativeTransfer -> formatHistoryAmount(valueWei, 18)
        is HistoryItem.TokenTransfer -> formatHistoryAmount(value, tokenDecimals)
    }
    val sign = when {
        status != TransactionStatus.Confirmed -> ""
        action == TransactionType.Sent -> "-"
        action == TransactionType.Received -> "+"
        else -> ""
    }
    return HistoryRow(
        item = this,
        action = action,
        status = status,
        counterparty = if (outgoing) to else from,
        amount = value?.let { sign + it },
        symbol = when (this) {
            is HistoryItem.NativeTransfer -> "ETH"
            is HistoryItem.TokenTransfer -> tokenSymbol?.takeIf(String::isNotBlank)
                ?: tokenAddress.shortHistoryAddress()
        },
        timestampMillis = timestamp?.let {
            try {
                Instant.parse(it).toEpochMilliseconds()
            } catch (_: IllegalArgumentException) {
                null
            }
        },
    )
}

internal fun formatHistoryAmount(rawValue: String, decimals: Int?): String? {
    if (decimals == null || decimals !in 0..255) return null
    val amount = rawValue.toBigDecimalOrNull()?.movePointLeft(decimals) ?: return null
    if (amount.signum() < 0) return null
    val smallest = BigDecimal("0.00000001")
    if (amount.signum() > 0 && amount < smallest) return "<0.00000001"
    return amount.setScale(8, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
}
