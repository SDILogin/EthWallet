package me.sdi.ethwallet.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.history.HistoryInitializationStore
import me.sdi.ethwallet.core.history.HistoryPaginationCursor
import me.sdi.ethwallet.core.history.HistoryUpdateInteractor
import me.sdi.ethwallet.core.history.TransactionFee
import me.sdi.ethwallet.core.history.TransactionStatus
import me.sdi.ethwallet.core.history.Transfer
import me.sdi.ethwallet.core.history.TransferHistoryRepository
import me.sdi.ethwallet.core.transfer.Wei
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.ui.history.HistoryViewState.Content
import java.math.BigDecimal
import java.math.RoundingMode

class HistoryViewModel(
    private val walletAddress: EthAddress,
    private val historyRepository: TransferHistoryRepository,
    private val historyUpdateInteractor: HistoryUpdateInteractor,
    private val historyInitializationStore: HistoryInitializationStore,
    private val chainId: Long,
    private val walletAccounts: Flow<List<EthWallet>>,
) : ViewModel() {
    private var historyRequestRunning = false

    private val _viewState = MutableStateFlow<HistoryViewState>(HistoryViewState.Loading)
    val viewState: StateFlow<HistoryViewState> = _viewState
        .onStart {
            viewModelScope.launch(CoroutineName("history-observe")) { observeHistory() }
            loadInitialHistory()
        }
        .onEach { Logger.debug("HistoryViewModel") { "$it" } }
        .stateIn(viewModelScope, SharingStarted.Lazily, HistoryViewState.Loading)

    private val newsChannel = Channel<HistoryNews>(Channel.BUFFERED)
    val news: Flow<HistoryNews> = newsChannel.receiveAsFlow()

    fun onEvent(event: HistoryUserAction) {
        Logger.debug("HistoryViewModel") { "onEvent: $event" }

        when (event) {
            HistoryUserAction.Refresh -> onRefresh()
            HistoryUserAction.RetryInitialLoad -> onRetryInitialLoad()
            HistoryUserAction.LoadMore -> onLoadMore()
            HistoryUserAction.RetryLoadMore -> onRetryLoadMore()
        }
    }

    private suspend fun observeHistory() {
        historyInitializationStore.observe(chainId, walletAddress)
            .distinctUntilChanged()
            .collectLatest { initialized ->
                historyRepository.observe(walletAddress)
                    .combine(walletAccounts) { items, accounts ->
                        val addresses = accounts.mapTo(mutableSetOf(walletAddress.toString().lowercase())) {
                            it.address.toString().lowercase()
                        }
                        items.mapNotNull { it.toHistoryRow(walletAddress, addresses) }
                    }.collect { rows ->
                        updateViewState(HistoryEvent.HistoryItemsListChange(rows, initialized))
                    }
            }
    }

    private fun loadInitialHistory() {
        launchRequest {
            updateViewState(HistoryEvent.InitialLoadStarted)
            refreshHistory()
        }
    }

    private fun onRetryInitialLoad() {
        if (_viewState.value !is HistoryViewState.Error) return
        loadInitialHistory()
    }

    private fun onRefresh() {
        val content = _viewState.value as? Content ?: return
        if (content.refreshing) return

        launchRequest {
            updateViewState(HistoryEvent.RefreshStarted)
            try {
                refreshHistory()
            } finally {
                updateViewState(HistoryEvent.RefreshFinished)
            }
        }
    }

    private suspend fun refreshHistory() {
        try {
            val nextCursor = historyUpdateInteractor.refresh(chainId, walletAddress).nextCursor
            // Keep the result local until the DB observer has supplied content.
            _viewState.first { it is Content }
            updateViewState(HistoryEvent.PaginationChange(HistoryPagination.Idle(nextCursor)))
        } catch (e: Exception) {
            if (e is CancellationException) throw e

            Logger.warn("HistoryViewModel", e) { "Failed to refresh history" }
            updateViewState(HistoryEvent.RefreshFailed)
            if (_viewState.value is Content) {
                newsChannel.send(HistoryNews.ShowRefreshError)
            }
        }
    }

    private fun onLoadMore() {
        val content = _viewState.value as? Content ?: return
        if (content.refreshing) return
        val pagination = content.pagination as? HistoryPagination.Idle ?: return
        loadOlderHistory(pagination)
    }

    private fun onRetryLoadMore() {
        val content = _viewState.value as? Content ?: return
        if (content.refreshing) return
        val pagination = content.pagination as? HistoryPagination.Failed ?: return
        loadOlderHistory(pagination)
    }

    private fun loadOlderHistory(previous: HistoryPagination) {
        val cursor = previous.cursor ?: return
        launchRequest {
            updateViewState(HistoryEvent.PaginationChange(HistoryPagination.Loading(cursor)))
            try {
                val nextCursor = historyUpdateInteractor.loadMore(chainId, walletAddress, cursor).nextCursor
                updateViewState(HistoryEvent.PaginationChange(HistoryPagination.Idle(nextCursor)))
            } catch (e: Exception) {
                if (e is CancellationException) {
                    updateViewState(HistoryEvent.PaginationChange(previous))
                    throw e
                }
                Logger.warn("HistoryViewModel", e) { "Failed to load older history" }
                updateViewState(HistoryEvent.PaginationChange(HistoryPagination.Failed(cursor)))
            }
        }
    }

    private fun launchRequest(block: suspend () -> Unit) {
        if (historyRequestRunning) return
        historyRequestRunning = true

        viewModelScope.launch(CoroutineName("history-request")) { block() }
            .invokeOnCompletion { historyRequestRunning = false }
    }

    private fun updateViewState(event: HistoryEvent) = _viewState.update { historyReducer(it, event) }
}

sealed interface HistoryPagination {
    val cursor: HistoryPaginationCursor?

    val hasMore: Boolean
        get() = cursor != null

    data object Uninitialized : HistoryPagination {
        override val cursor: HistoryPaginationCursor? = null
    }

    data class Idle(override val cursor: HistoryPaginationCursor? = null) : HistoryPagination

    data class Loading(override val cursor: HistoryPaginationCursor) : HistoryPagination

    data class Failed(override val cursor: HistoryPaginationCursor) : HistoryPagination
}

sealed interface HistoryViewState {
    data object Loading : HistoryViewState

    data class Content(
        val items: List<HistoryRow>,
        val refreshing: Boolean,
        val pagination: HistoryPagination = HistoryPagination.Uninitialized,
    ) : HistoryViewState

    data object Error : HistoryViewState
}

sealed interface HistoryEvent {
    data object InitialLoadStarted : HistoryEvent

    data object RefreshFailed : HistoryEvent

    data object RefreshStarted : HistoryEvent

    data object RefreshFinished : HistoryEvent

    data class PaginationChange(val pagination: HistoryPagination) : HistoryEvent

    data class HistoryItemsListChange(
        val items: List<HistoryRow>,
        val initialized: Boolean,
    ) : HistoryEvent
}

internal fun historyReducer(state: HistoryViewState, event: HistoryEvent): HistoryViewState =
    when (event) {
        HistoryEvent.InitialLoadStarted -> if (state is Content) state else HistoryViewState.Loading
        HistoryEvent.RefreshFailed -> if (state is Content) state else HistoryViewState.Error
        HistoryEvent.RefreshStarted -> if (state is Content) state.copy(refreshing = true) else state
        HistoryEvent.RefreshFinished -> if (state is Content) state.copy(refreshing = false) else state
        is HistoryEvent.PaginationChange -> if (state is Content) state.copy(pagination = event.pagination) else state
        is HistoryEvent.HistoryItemsListChange -> when {
            state is Content -> state.copy(items = event.items)
            event.items.isNotEmpty() || event.initialized -> Content(event.items, refreshing = false)
            else -> state
        }
    }

sealed interface HistoryUserAction {
    data object Refresh : HistoryUserAction

    data object LoadMore : HistoryUserAction

    data object RetryLoadMore : HistoryUserAction

    data object RetryInitialLoad : HistoryUserAction
}

sealed interface HistoryNews {
    data object ShowRefreshError : HistoryNews
}

internal fun Transfer.toHistoryRow(
    walletAddress: EthAddress,
    accountAddresses: Set<String> = setOf(
        walletAddress.toString().lowercase(),
    ),
): HistoryRow? {
    val outgoing = from.toString().equals(walletAddress.toString(), ignoreCase = true)
    val incoming = to.toString().equals(walletAddress.toString(), ignoreCase = true)
    if (!outgoing && !incoming) return null
    if (status == TransactionStatus.Failed) return null
    if (this is Transfer.Eth && valueWei.value.signum() <= 0) return null
    if (this is Transfer.Erc20 && value.toBigInteger().signum() <= 0) return null
    val action = when {
        from.toString().lowercase() in accountAddresses && to.toString()
            .lowercase() in accountAddresses -> TransferDirection.Self
        outgoing -> TransferDirection.Sent
        incoming -> TransferDirection.Received
        else -> return null
    }
    val value = when (this) {
        is Transfer.Eth -> formatHistoryAmount(valueWei.value.toString(), 18)
        is Transfer.Erc20 -> formatHistoryAmount(value, tokenDecimals)
    }
    val sign = when {
        status != TransactionStatus.Confirmed -> ""
        action == TransferDirection.Sent -> "-"
        action == TransferDirection.Received -> "+"
        else -> ""
    }
    val fee: TransactionFee? = this.fee
    return HistoryRow(
        item = this,
        action = action,
        status = status,
        counterparty = if (outgoing) to else from,
        amount = value?.let { sign + it },
        symbol = when (this) {
            is Transfer.Eth -> "ETH"
            is Transfer.Erc20 -> tokenSymbol?.takeIf(String::isNotBlank)
                ?: tokenAddress.shortHistoryAddress()
        },
        timestampMillis = timestamp,
        transactionFee = if (outgoing && fee != null) {
            val wei: Wei = fee.value
            formatHistoryAmount(wei.value.toString(), decimals = 18)
        } else {
            null
        },
        feeEstimated = outgoing && fee?.estimated == true,
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
