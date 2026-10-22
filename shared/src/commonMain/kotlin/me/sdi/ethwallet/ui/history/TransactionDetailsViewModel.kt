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
            val wallets = walletRepository.observeAllWallets().first()
            val fromWalletLabel = wallets.firstOrNull { it.address.lowercase() == transfer.from.lowercase() }?.label
            val toWalletLabel = wallets.firstOrNull { it.address.lowercase() == transfer.to.lowercase() }?.label
            TransactionDetailsState.Content(
                transfer = transfer,
                fromWalletLabel = fromWalletLabel,
                toWalletLabel = toWalletLabel,
            ) as TransactionDetailsState
        }
        .catch {
            Logger.warn("TransactionDetails", it) { "Failed ot get transaction details" }
            emit(TransactionDetailsState.Error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionDetailsState.Loading)
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
