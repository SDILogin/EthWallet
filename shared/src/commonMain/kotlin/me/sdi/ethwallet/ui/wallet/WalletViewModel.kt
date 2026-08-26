package me.sdi.ethwallet.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.BalanceProvider
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.FiatRateProvider
import me.sdi.ethwallet.data.repository.WalletRepository
import java.math.BigDecimal
import java.math.BigInteger

private const val TAG = "WalletViewModel"

class WalletViewModel(
    private val walletAddress: EthAddress,
    private val balanceProvider: BalanceProvider,
    private val fiatRateProvider: FiatRateProvider,
    private val chainConfig: ChainConfig,
    private val walletRepository: WalletRepository,
) : ViewModel() {
    val viewState: StateFlow<WalletViewState>
        field = MutableStateFlow(WalletViewState(chainId = chainConfig.chainId))

    private val newsChannel = Channel<WalletNews>(Channel.BUFFERED)

    val news: Flow<WalletNews> = newsChannel.receiveAsFlow()

    private var refreshJob: Job? = null

    init {
        viewState
            .onEach { Logger.debug(TAG) { "ViewState: $it" } }
            .launchIn(viewModelScope)

        startObservingWalletBalanceChanges()
        startObservingWalletLabelChanges()
    }

    fun onEvent(event: WalletEvent) {
        Logger.debug(TAG) { "onEvent: $event" }
        when (event) {
            is WalletEvent.Refresh -> onRefreshRequested()
            is WalletEvent.Retry -> onRefreshRequested()
        }
    }

    private fun onRefreshRequested() {
        if (refreshJob?.isActive == true) {
            Logger.debug(TAG) {
                "Balance update already in progress, skipping new request"
            }
            return
        }

        refreshData(isUserInitiated = true)
    }

    private fun startObservingWalletBalanceChanges() {
        viewModelScope.launch {
            val walletBalance = balanceProvider.observe(walletAddress)
            val fiatRate = fiatRateProvider.observe().onStart { emit(emptyMap()) }

            combine(walletBalance, fiatRate) { balance, fiatRates -> CombinedBalance(balance, fiatRates) }
                .onStart { refreshData(isUserInitiated = false) }
                .onEach { Logger.debug(TAG) { "Balance update for $walletAddress: $it" } }
                .collectLatest { combinedBalance: CombinedBalance -> displayUpdatedBalance(combinedBalance) }
        }.also { job ->
            job.invokeOnCompletion { throwable ->
                when (throwable) {
                    null -> Logger.debug(TAG) { "observeBalanceJob successfully completed" }
                    is CancellationException -> Logger.debug(TAG) { "observeBalanceJob cancelled" }
                    else -> Logger.warn(TAG, throwable) { "observeBalanceJob completed with exception" }
                }
            }
        }
    }

    private fun startObservingWalletLabelChanges() {
        walletRepository
            .observeAllWallets()
            .map { wallets -> wallets.firstOrNull { it.address == walletAddress }?.label }
            .onEach { label ->
                if (label != null) {
                    viewState.update { it.copy(walletName = label) }
                }
            }.launchIn(viewModelScope)
    }

    private fun displayUpdatedBalance(combinedBalance: CombinedBalance) {
        if (combinedBalance.balance.isEmpty()) return

        val summary = mapWalletBalance(
            balances = combinedBalance.balance,
            fiatRates = combinedBalance.fiatRates,
            chainId = chainConfig.chainId,
        )

        viewState.update { currentViewState: WalletViewState ->
            currentViewState.copy(
                totalBalanceEth = summary.totalBalanceEth,
                totalBalanceUsd = summary.totalBalanceUsd,
                favoriteTokens = summary.favoriteTokens,
                balanceLoadState = WalletBalanceLoadState.Content,
                fiatLoadState = if (summary.totalBalanceUsd != null) {
                    WalletFiatLoadState.Available
                } else {
                    currentViewState.fiatLoadState
                },
            )
        }
    }

    private fun refreshData(isUserInitiated: Boolean) {
        viewState.update { state ->
            state.copy(
                balanceLoadState = when (state.balanceLoadState) {
                    WalletBalanceLoadState.Content -> state.balanceLoadState
                    else -> WalletBalanceLoadState.Loading
                },
                fiatLoadState = when (state.fiatLoadState) {
                    WalletFiatLoadState.Available -> state.fiatLoadState
                    else -> WalletFiatLoadState.Loading
                },
                showP2rIndicator = isUserInitiated,
            )
        }

        refreshJob = viewModelScope.launch(CoroutineName("WalletRefresh")) {
            try {
                val (balanceUpdated, fiatUpdated) = supervisorScope {
                    val balance = async { updateWalletBalance() }
                    val fiat = async { updateFiatRates() }
                    balance.await() to fiat.await()
                }

                viewState.update { state ->
                    state.copy(
                        balanceLoadState = when {
                            balanceUpdated -> state.balanceLoadState
                            state.balanceLoadState == WalletBalanceLoadState.Content -> state.balanceLoadState
                            else -> WalletBalanceLoadState.Error
                        },
                        fiatLoadState = when {
                            fiatUpdated -> state.fiatLoadState
                            state.fiatLoadState == WalletFiatLoadState.Available -> state.fiatLoadState
                            else -> WalletFiatLoadState.Unavailable
                        },
                    )
                }

                val shouldShowRefreshError = isRefreshErrorShouldBeShown(
                    balanceLoadingState = viewState.value.balanceLoadState,
                    balanceUpdateSuccess = balanceUpdated,
                    fiatUpdateSuccess = fiatUpdated,
                )
                if (shouldShowRefreshError) {
                    newsChannel.send(WalletNews.ShowErrorMessage)
                }
            } finally {
                if (isUserInitiated) {
                    viewState.update { it.copy(showP2rIndicator = false) }
                }
            }
        }
    }

    private fun isRefreshErrorShouldBeShown(
        balanceLoadingState: WalletBalanceLoadState,
        balanceUpdateSuccess: Boolean,
        fiatUpdateSuccess: Boolean,
    ): Boolean {
        // toast must be shown only when content is on screen. In other cases errors must be handled by
        // error states with recovery action buttons
        if (balanceLoadingState != WalletBalanceLoadState.Content) return false

        val success = balanceUpdateSuccess && fiatUpdateSuccess
        return !success
    }

    private suspend fun updateFiatRates(): Boolean {
        return try {
            fiatRateProvider.forceUpdate()
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e

            Logger.warn(TAG) {
                "Failed to force update fiat rates: ${e.message}"
            }
            false
        }
    }

    private suspend fun updateWalletBalance(): Boolean {
        return try {
            balanceProvider.forceUpdate(walletAddress)
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e

            Logger.warn(TAG) {
                "Failed to force update balance for $walletAddress: ${e.message}"
            }
            false
        }
    }
}

data class CombinedBalance(
    val balance: Map<EthAddress, BigInteger>,
    val fiatRates: Map<EthAddress, BigDecimal>,
)
