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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.history.HistoryItem
import me.sdi.ethwallet.core.history.HistoryUpdateInteractor
import me.sdi.ethwallet.core.history.TransactionHistoryRepository

class HistoryViewModel(
    private val walletAddress: EthAddress,
    private val historyRepository: TransactionHistoryRepository,
    private val historyUpdateInteractor: HistoryUpdateInteractor,
) : ViewModel() {
    private var observationJob: Job? = null

    private val _viewState = MutableStateFlow(HistoryViewState())
    val viewState: StateFlow<HistoryViewState> = _viewState
        .onStart {
            // just in case view resubscribed while view model was already observing
            observationJob?.cancel()
            observationJob = historyRepository
                .observe(walletAddress)
                .onEach { items -> _viewState.update { it.copy(items = items) } }
                .launchIn(viewModelScope)

            // force update on start
            viewModelScope.launch {
                try {
                    historyUpdateInteractor.update(walletAddress)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e

                    Logger.warn("HistoryViewModel", e) { "Failed to load history on start" }
                    newsChannel.send(HistoryNews.ShowRefreshError)
                }
            }
        }
        .onEach { Logger.debug("HistoryViewModel") { "$it" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), HistoryViewState())

    private val newsChannel = Channel<HistoryNews>(Channel.BUFFERED)
    val news: Flow<HistoryNews> = newsChannel.receiveAsFlow()

    fun onEvent(event: HistoryEvent) {
        Logger.debug("HistoryViewModel") { "onEvent: $event" }

        when (event) {
            HistoryEvent.Refresh -> onRefresh()
        }
    }

    private fun onRefresh() {
        if (_viewState.value.refreshing) {
            Logger.warn("HistoryViewModel") {
                "Pull2Refresh call while already refreshing. Should never happen."
            }
            return
        }

        _viewState.update { it.copy(refreshing = true) }
        viewModelScope.launch(CoroutineName("pull2refresh")) {
            try {
                historyUpdateInteractor.update(walletAddress)
            } catch (e: Exception) {
                if (e is CancellationException) throw e

                Logger.warn("HistoryViewModel", e) { "Failed to refresh history" }
                newsChannel.send(HistoryNews.ShowRefreshError)
            } finally {
                _viewState.update { it.copy(refreshing = false) }
            }
        }
    }
}

data class HistoryViewState(
    val items: List<HistoryItem>? = null,
    val refreshing: Boolean = false,
)

sealed interface HistoryEvent {
    data object Refresh : HistoryEvent
}

sealed interface HistoryNews {
    data object ShowRefreshError : HistoryNews
}
