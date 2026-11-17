package me.sdi.ethwallet.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.history.Transfer
import me.sdi.ethwallet.core.history.TransferHistoryRepository
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.data.repository.WalletRepository

class TransactionDetailsViewModel(
    walletAddress: EthAddress,
    transactionHash: String,
    logIndex: Int,
    repository: TransferHistoryRepository,
    walletRepository: WalletRepository,
) : ViewModel() {
    val viewState = repository
        .observe(walletAddress, transactionHash, logIndex)
        .map { transfer ->
            transfer.toDetailsContent(walletRepository.observeAllWallets().first()) as TransactionDetailsState
        }
        .catch {
            Logger.warn("TransactionDetails", it) { "Failed ot get transaction details" }
            emit(TransactionDetailsState.Error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionDetailsState.Loading)
}

internal fun Transfer.toDetailsContent(wallets: List<EthWallet>): TransactionDetailsState.Content {
    val fromWalletLabel = wallets.firstOrNull { it.address.lowercase() == from.lowercase() }?.label
    val toWalletLabel = wallets.firstOrNull { it.address.lowercase() == to.lowercase() }?.label
    return TransactionDetailsState.Content(
        transfer = this,
        fromWalletLabel = fromWalletLabel,
        toWalletLabel = toWalletLabel,
    )
}

sealed interface TransactionDetailsState {
    data object Loading : TransactionDetailsState

    data object Unavailable : TransactionDetailsState

    data object Error : TransactionDetailsState

    data class Content(
        val transfer: Transfer,
        val fromWalletLabel: String? = null,
        val toWalletLabel: String? = null,
    ) : TransactionDetailsState
}
