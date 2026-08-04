package me.sdi.ethwallet.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
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

    val news: SharedFlow<WalletNews>
        field = MutableSharedFlow<WalletNews>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    private var refreshJob: Job? = null

    init {
        viewState
            .onEach { Logger.debug(TAG) { "ViewState: $it" } }
            .launchIn(viewModelScope)

        startObservingWalletBalanceChanges()
        startObservingWalletLabelChanges()

        news
            .onEach { Logger.debug(TAG) { "News: $it" } }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: WalletEvent) {
        Logger.debug(TAG) { "onEvent: $event" }
        when (event) {
            is WalletEvent.Refresh -> onRefreshRequested()
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
                isBalanceLoading = false,
            )
        }
    }

    private fun refreshData(isUserInitiated: Boolean) {
        refreshJob = viewModelScope.launch(CoroutineName("WalletRefresh")) {
            if (isUserInitiated) {
                viewState.update { it.copy(isRefreshing = true) }
            }

            try {
                supervisorScope {
                    launch { updateWalletBalanceOrShowError() }
                    launch { updateFiatRatesOrShowError() }
                }
            } finally {
                if (isUserInitiated) {
                    viewState.update { it.copy(isRefreshing = false) }
                }
            }
        }.also { job ->
            job.invokeOnCompletion { throwable ->
                when (throwable) {
                    null -> Logger.debug(TAG) { "refreshJob successfully completed" }
                    is CancellationException -> Logger.debug(TAG) { "refreshJob cancelled" }
                    else -> Logger.warn(TAG, throwable) { "refreshJob completed with exception" }
                }
            }
        }
    }

    private suspend fun updateFiatRatesOrShowError() {
        try {
            fiatRateProvider.forceUpdate()
        } catch (e: Exception) {
            if (e is CancellationException) throw e

            Logger.warn(TAG) {
                "Failed to force update fiat rates: ${e.message}"
            }

            news.emit(WalletNews.ShowErrorMessage(e))
        }
    }

    private suspend fun updateWalletBalanceOrShowError() {
        try {
            balanceProvider.forceUpdate(walletAddress)
        } catch (e: Exception) {
            if (e is CancellationException) throw e

            Logger.warn(TAG) {
                "Failed to force update balance for $walletAddress: ${e.message}"
            }

            news.emit(WalletNews.ShowErrorMessage(e))
        }
    }
}

data class CombinedBalance(
    val balance: Map<EthAddress, BigInteger>,
    val fiatRates: Map<EthAddress, BigDecimal>,
)
