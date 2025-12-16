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
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.BalanceProvider
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.core.FiatRateProvider
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.ui.KnownTokens
import me.sdi.ethwallet.util.formatUsdCurrency
import org.web3j.utils.Convert
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

private const val TAG = "WalletViewModel"

class WalletViewModel(
    private val walletRepository: WalletRepository,
    private val balanceProvider: BalanceProvider,
    private val fiatRateProvider: FiatRateProvider,
) : ViewModel() {
    private val _viewState = MutableStateFlow(WalletViewState())
    val viewState: StateFlow<WalletViewState> = _viewState.asStateFlow()

    private val _news = MutableSharedFlow<WalletNews>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val news: SharedFlow<WalletNews> = _news.asSharedFlow()

    private var forceUpdateJob: Job? = null

    fun onEvent(event: WalletEvent) {
        when (event) {
            is WalletEvent.Initialize -> startObservingActiveWalletBalanceChanges()
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
                    val activeWallet: EthWallet? = getActiveWalletOrShowError()
                    if (activeWallet == null) {
                        Logger.warn(TAG) {
                            "No active wallet found, cannot refresh balance"
                        }

                        return@launch
                    }

                    updateWalletBalanceOrShowError(activeWallet)
                    updateFiatRatesOrShowError()
                }
            }
        }
    }

    private fun startObservingActiveWalletBalanceChanges() {
        _viewState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            val activeWallet: EthWallet? = getActiveWalletOrShowError()
            if (activeWallet == null) {
                Logger.warn(TAG) {
                    "No active wallet found, stop observing balance updates"
                }

                return@launch
            }

            balanceProvider
                .observe(activeWallet.address)
                .combine(fiatRateProvider.observe()) { balance, fiatRates ->
                    Logger.debug(TAG) {
                        "Combining balance and fiat rates for ${activeWallet.address}"
                    }
                    CombinedBalance(balance, fiatRates)
                }
                .onStart {
                    forceUpdateJob = launch(CoroutineName("BalanceUpdate")) {
                        updateWalletBalanceOrShowError(activeWallet)
                        updateFiatRatesOrShowError()
                    }
                }
                .onEach {
                    Logger.debug(TAG) {
                        "Balance update for ${activeWallet.address}: $it"
                    }
                }
                .collectLatest { combinedBalance: CombinedBalance ->
                    displayUpdatedBalance(combinedBalance)
                }
        }
    }

    private fun displayUpdatedBalance(combinedBalance: CombinedBalance) {
        _viewState.update { it.copy(isLoading = false) }

        val balance = combinedBalance.balance
        val fiatRates = combinedBalance.fiatRates
        val ethBalanceWei = balance[EthContractAddress] ?: BigInteger.ZERO
        val ethBalanceEth: BigDecimal = Convert.fromWei(ethBalanceWei.toString(), Convert.Unit.ETHER)
        _viewState.update {
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

        _viewState.update { currentViewState: WalletViewState ->
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
    ): List<FavoriteToken> = KnownTokens.entries.map { tokenInfo ->
        val tokenBalanceInMinimalUnits = erc20Balances[tokenInfo.ethAddress] ?: BigInteger.ZERO
        // Convert from smallest units to token units using correct decimals
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
                "No fiat rate found for token ${tokenInfo.name}"
            }
        }
        FavoriteToken(
            address = tokenInfo.ethAddress,
            name = tokenInfo.tokenName,
            ticker = tokenInfo.name,
            amount = formatTokenAmount(tokenAmount),
            usdValue = tokenUsdValue,
            usdValueFormatted = tokenUsdValue?.let { formatUsdCurrency(tokenUsdValue) } ?: "N/A",
        )
    }

    private suspend fun getActiveWalletOrShowError(): EthWallet? {
        return try {
            walletRepository.getActiveWallet()
        } catch (e: Exception) {
            if (e is CancellationException) throw e

            Logger.warn(TAG) {
                "Error loading active wallet\n ${e.stackTraceToString()}"
            }

            _news.emit(WalletNews.ShowErrorMessage(e))

            null
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

            _news.emit(WalletNews.ShowErrorMessage(e))
        }
    }

    private suspend fun updateWalletBalanceOrShowError(wallet: EthWallet) {
        try {
            balanceProvider.forceUpdate(wallet)
        } catch (e: Exception) {
            if (e is CancellationException) throw e

            Logger.warn(TAG) {
                "Failed to force update balance for ${wallet.address}: ${e.message}"
            }

            _news.emit(WalletNews.ShowErrorMessage(e))
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
