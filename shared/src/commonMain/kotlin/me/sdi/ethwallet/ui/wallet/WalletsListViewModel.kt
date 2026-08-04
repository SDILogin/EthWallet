package me.sdi.ethwallet.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.yield
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.BalanceProvider
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.FiatRateProvider
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.data.repository.WalletRepository
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private const val TAG = "WalletsListViewModel"

@OptIn(ExperimentalCoroutinesApi::class)
class WalletsListViewModel(
    private val walletRepository: WalletRepository,
    private val balanceProvider: BalanceProvider,
    private val fiatRateProvider: FiatRateProvider,
    private val chainConfig: ChainConfig,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    val uiState: StateFlow<WalletsListUiState>
        field = MutableStateFlow(WalletsListUiState(isLoading = true))

    private val newsChannel = Channel<WalletsListNews>(Channel.BUFFERED)
    val news = newsChannel.receiveAsFlow().onEach { Logger.debug(TAG) { "News: $it" } }

    init {
        uiState.onEach { Logger.debug(TAG) { "ViewState: $it" } }.launchIn(viewModelScope)

        walletRepository.observeAllWallets().distinctUntilChanged().flatMapLatest { wallets ->
            channelFlow {
                launch(CoroutineName("WalletsListRefresh") + backgroundDispatcher) {
                    refreshWallets(wallets)
                }
                observeWalletItems(wallets).collect { walletItems ->
                    send(walletItems)
                }
            }
        }.onEach { walletItems ->
            uiState.update {
                it.copy(
                    wallets = walletItems,
                    isLoading = false,
                )
            }
        }.catch { error ->
            Logger.warn(TAG, error) { "Failed to observe wallet balances" }
            uiState.update { it.copy(isLoading = false) }
            newsChannel.send(WalletsListNews.ShowErrorMessage)
        }.launchIn(viewModelScope + backgroundDispatcher)
    }

    private fun observeWalletItems(wallets: List<EthWallet>): Flow<List<WalletListItemUiState>> {
        if (wallets.isEmpty()) {
            return flowOf(emptyList())
        }

        val balanceFlows = wallets.map { wallet ->
            balanceProvider.observe(wallet.address).map { balances -> wallet to balances }
        }

        return combine(balanceFlows) { it.toList() }.combine(fiatRateProvider.observe()) { walletBalances, fiatRates ->
            val previousItems = uiState.value.wallets.associateBy { it.wallet.address }

            walletBalances.map { (wallet, balances) ->
                val totalBalanceUsd = if (balances.isEmpty()) {
                    previousItems[wallet.address]?.totalBalanceUsd
                } else {
                    mapWalletBalance(
                        balances = balances,
                        fiatRates = fiatRates,
                        chainId = chainConfig.chainId,
                    ).totalBalanceUsd
                }

                WalletListItemUiState(wallet = wallet, totalBalanceUsd = totalBalanceUsd)
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private suspend fun refreshWallets(wallets: List<EthWallet>) = coroutineScope {
        if (wallets.isEmpty()) return@coroutineScope

        val isAnyJobFailed = AtomicBoolean(false)
        val jobs = wallets.map { wallet ->
            launch(CoroutineName("WalletsListRefresh-${wallet.address}")) {
                val walletUpdateSuccess = refreshWalletBalance(wallet.address)
                if (!walletUpdateSuccess) {
                    isAnyJobFailed.store(true)
                }
            }
        }
        val fiatRateRefreshSuccess = refreshFiatRates()
        if (!fiatRateRefreshSuccess) {
            isAnyJobFailed.store(true)
        }

        jobs.joinAll()
        if (isAnyJobFailed.load()) {
            newsChannel.send(WalletsListNews.ShowErrorMessage)
        }
    }

    private suspend fun refreshWalletBalance(address: EthAddress): Boolean {
        return try {
            balanceProvider.forceUpdate(address)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Logger.warn(TAG, error) { "Error refreshing balance for $address" }
            false
        }
    }

    private suspend fun refreshFiatRates(): Boolean {
        return try {
            fiatRateProvider.forceUpdate()
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Logger.warn(TAG, error) { "Error refreshing fiat rates" }
            false
        }
    }

    fun onEvent(event: WalletsListEvent) {
        Logger.debug(TAG) { "onEvent: $event" }
        when (event) {
            is WalletsListEvent.OnDeleteWalletClick -> deleteWallet(event.address)
        }
    }

    private fun deleteWallet(address: EthAddress) {
        viewModelScope.launch {
            suspendableTryCatch(
                block = {
                    walletRepository.deleteWallet(address)
                    newsChannel.send(WalletsListNews.WalletDeleted(address))
                },
                onFailure = { e ->
                    Logger.warn(TAG, e) { "Error deleting wallet" }

                    newsChannel.send(WalletsListNews.ShowErrorMessage)
                },
            )
        }
    }
}

private suspend fun suspendableTryCatch(
    block: suspend () -> Unit,
    onFailure: suspend (Exception) -> Unit,
) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        yield()

        onFailure(e)
    }
}

data class WalletsListUiState(
    val wallets: List<WalletListItemUiState> = emptyList(),
    val isLoading: Boolean = false,
)

data class WalletListItemUiState(
    val wallet: EthWallet,
    val totalBalanceUsd: String? = null,
)

sealed interface WalletsListNews {
    data class WalletDeleted(val address: EthAddress) : WalletsListNews

    data object ShowErrorMessage : WalletsListNews
}

sealed interface WalletsListEvent {
    data class OnDeleteWalletClick(val address: EthAddress) : WalletsListEvent
}
