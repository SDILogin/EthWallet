package me.sdi.ethwallet.ui.wallet

import android.icu.text.NumberFormat
import android.icu.util.Currency
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.BalanceProvider
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthBalanceProvider
import me.sdi.ethwallet.core.Web3Provider
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.ui.KnownTokens
import org.web3j.utils.Convert
import java.math.BigDecimal
import java.math.BigInteger

private const val TAG = "WalletViewModel"

class WalletViewModel(
    private val walletRepository: WalletRepository,
    private val balanceProvider: BalanceProvider = EthBalanceProvider(Web3Provider.web3j),
    private val usdFormatter: (BigDecimal) -> String = { usdAmount ->
        val currencyFormatter = NumberFormat.getCurrencyInstance()
        currencyFormatter.currency = Currency.getInstance("USD")
        currencyFormatter.maximumFractionDigits = 2
        currencyFormatter.format(usdAmount)
    },
) : ViewModel() {
    private val _viewState = MutableStateFlow(WalletViewState())
    val viewState: StateFlow<WalletViewState> = _viewState.asStateFlow()

    private val _news = MutableSharedFlow<WalletNews>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val news: SharedFlow<WalletNews> = _news.asSharedFlow()

    fun onEvent(event: WalletEvent) {
        when (event) {
            is WalletEvent.Initialize -> loadActiveWallet()
            is WalletEvent.Refresh -> loadActiveWallet()
        }
    }

    private fun loadActiveWallet() {
        _viewState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                val activeWallet: EthWallet? = walletRepository.getActiveWallet()
                if (activeWallet != null) {
                    updateEthAccountInfo(activeWallet.address)
                } else {
                    // For now, use a placeholder. In a real app, we might navigate away
                    // or show a message prompting the user to create or select a wallet.
                    _viewState.update { walletViewState: WalletViewState ->
                        walletViewState.copy(
                            totalBalanceUsd = usdFormatter(BigDecimal.ZERO),
                            totalBalanceEth = "0 ETH",
                            favoriteTokens = emptyList(),
                        )
                    }
                }
            } catch (e: Exception) {
                ensureActive()

                Logger.warn(TAG) {
                    "Error loading active wallet\n ${e.stackTraceToString()}"
                }

                _news.emit(WalletNews.ShowErrorMessage(e))
            }
        }.invokeOnCompletion {
            _viewState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun updateEthAccountInfo(address: EthAddress) {
        val ethBalanceWei: BigInteger = balanceProvider.getEthBalance(address)
        val ethBalanceFormatted: BigDecimal? = Convert.fromWei(ethBalanceWei.toString(), Convert.Unit.ETHER)
        val favoriteTokens = KnownTokens.entries.map { knownToken ->
            FavoriteToken(
                address = knownToken.ethAddress,
                name = knownToken.tokenName,
                ticker = knownToken.name,
                amount = "0",
                usdValue = "0",
            )
        }
        _viewState.update { walletViewState: WalletViewState ->
            walletViewState.copy(
                totalBalanceEth = "$ethBalanceFormatted ETH",
                totalBalanceUsd = usdFormatter(BigDecimal.ZERO),
                favoriteTokens = favoriteTokens,
            )
        }
    }
}
