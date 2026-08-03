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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.BalanceProvider
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.core.FiatRateProvider
import me.sdi.ethwallet.core.transfer.Wei
import me.sdi.ethwallet.core.transfer.toEth
import me.sdi.ethwallet.ui.supportedKnownTokens
import me.sdi.ethwallet.util.formatUsdCurrency
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

private const val TAG = "WalletViewModel"

class WalletViewModel(
    private val walletAddress: EthAddress,
    private val balanceProvider: BalanceProvider,
    private val fiatRateProvider: FiatRateProvider,
    private val chainConfig: ChainConfig,
) : ViewModel() {
    val viewState: StateFlow<WalletViewState>
        field = MutableStateFlow(WalletViewState(chainId = chainConfig.chainId))

    val news: SharedFlow<WalletNews>
        field = MutableSharedFlow<WalletNews>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    private var forceUpdateJob: Job? = null

    init {
        viewState
            .onEach { Logger.debug(TAG) { "ViewState: $it" } }
            .onStart { startObservingWalletBalanceChanges() }
            .launchIn(viewModelScope)

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
        if (forceUpdateJob?.isActive == true) {
            Logger.debug(TAG) {
                "Balance update already in progress, skipping new request"
            }
            return
        }

        if (forceUpdateJob == null) {
            viewModelScope.launch {
                forceUpdateJob = launch(CoroutineName("BalanceUpdate")) {
                    updateWalletBalanceOrShowError()
                    updateFiatRatesOrShowError()
                }.also { job ->
                    job.invokeOnCompletion {
                        if (it == null) {
                            Logger.debug(TAG) { "balanceUpdateJob successfully completed" }
                        } else {
                            Logger.warn(TAG, it) { "balanceUpdateJob completed with exception" }
                        }
                    }
                }
            }.also { job ->
                job.invokeOnCompletion {
                    if (it == null) {
                        Logger.debug(TAG) { "refreshJob successfully completed" }
                    } else {
                        Logger.warn(TAG, it) { "refreshJob completed with exception" }
                    }
                }
            }
        }
    }

    private fun startObservingWalletBalanceChanges() {
        viewState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            balanceProvider
                .observe(walletAddress)
                .combine(fiatRateProvider.observe()) { balance, fiatRates ->
                    Logger.debug(TAG) {
                        "Combining balance and fiat rates for $walletAddress"
                    }
                    CombinedBalance(balance, fiatRates)
                }
                .onStart {
                    forceUpdateJob = launch(CoroutineName("BalanceUpdate")) {
                        updateWalletBalanceOrShowError()
                        updateFiatRatesOrShowError()
                    }.also { job ->
                        job.invokeOnCompletion {
                            if (it == null) {
                                Logger.debug(TAG) { "balanceUpdateJob successfully completed" }
                            } else {
                                Logger.warn(TAG, it) { "balanceUpdateJob completed with exception" }
                            }
                        }
                    }
                }
                .onEach {
                    Logger.debug(TAG) {
                        "Balance update for $walletAddress: $it"
                    }
                }
                .collectLatest { combinedBalance: CombinedBalance ->
                    displayUpdatedBalance(combinedBalance)
                }
        }.also { job ->
            job.invokeOnCompletion {
                if (it == null) {
                    Logger.debug(TAG) { "observeBalanceJob successfully completed" }
                } else {
                    Logger.warn(TAG, it) { "observeBalanceJob completed with exception" }
                }
            }
        }
    }

    private fun displayUpdatedBalance(combinedBalance: CombinedBalance) {
        viewState.update { it.copy(isLoading = false) }

        val balance = combinedBalance.balance
        val fiatRates = combinedBalance.fiatRates
        val ethBalanceWei = balance[EthContractAddress] ?: BigInteger.ZERO
        val ethBalanceEth: BigDecimal = Wei(ethBalanceWei).toEth().value
        viewState.update {
            it.copy(
                totalBalanceEth = "${ethBalanceEth.toPlainString()} ETH",
            )
        }

        val erc20Balances = balance.filterKeys { it != EthContractAddress }
        val ethPriceUsd: BigDecimal = fiatRates[EthContractAddress] ?: BigDecimal.ZERO
        val ethUsdValue = ethBalanceEth.multiply(ethPriceUsd)

        val favoriteTokens = getFavoriteTokensUiState(erc20Balances, fiatRates)
        val totalErc20TokensUsdValue = favoriteTokens.mapNotNull { it.usdValue }.sumOf { it }
        val totalUsdValue = ethUsdValue.add(totalErc20TokensUsdValue)

        Logger.debug(TAG) {
            "Balance update: ETH=$ethBalanceEth ($$ethUsdValue), Tokens=$$totalErc20TokensUsdValue, Total=$$totalUsdValue"
        }

        viewState.update { currentViewState: WalletViewState ->
            currentViewState.copy(
                totalBalanceEth = "$ethBalanceEth ETH",
                totalBalanceUsd = formatUsdCurrency(totalUsdValue),
                favoriteTokens = favoriteTokens,
            )
        }
    }

    private fun getFavoriteTokensUiState(
        erc20Balances: Map<EthAddress, BigInteger>,
        fiatRates: Map<EthAddress, BigDecimal>,
    ): List<FavoriteToken> = supportedKnownTokens(chainConfig.chainId).map { tokenInfo ->
        val tokenBalanceInMinimalUnits = erc20Balances[tokenInfo.ethAddress] ?: BigInteger.ZERO
        val tokenAmount = BigDecimal(tokenBalanceInMinimalUnits).divide(
            BigDecimal.TEN.pow(tokenInfo.decimals),
            tokenInfo.decimals,
            RoundingMode.DOWN,
        )

        val tokenUsdValue = fiatRates[tokenInfo.ethAddress]?.let { rate ->
            tokenAmount.multiply(rate)
        }
        if (tokenUsdValue == null) {
            Logger.warn(TAG) {
                "No fiat rate found for token ${tokenInfo.symbol}"
            }
        }
        FavoriteToken(
            address = tokenInfo.ethAddress,
            name = tokenInfo.tokenName,
            ticker = tokenInfo.symbol,
            amount = formatTokenAmount(tokenAmount),
            usdValue = tokenUsdValue,
            usdValueFormatted = tokenUsdValue?.let { formatUsdCurrency(tokenUsdValue) } ?: "N/A",
        )
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

    private fun formatTokenAmount(amount: BigDecimal): String {
        return when {
            amount == BigDecimal.ZERO -> "0"
            amount < BigDecimal.ONE -> amount.setScale(6, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
            else -> amount.setScale(2, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
        }
    }
}

data class CombinedBalance(
    val balance: Map<EthAddress, BigInteger>,
    val fiatRates: Map<EthAddress, BigDecimal>,
)
