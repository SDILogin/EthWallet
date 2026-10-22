package me.sdi.ethwallet.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.history.Transfer
import me.sdi.ethwallet.core.history.TransferHistoryRepository
import me.sdi.ethwallet.core.history.logIndex

class TransactionDetailsViewModel(
    walletAddress: EthAddress,
    transactionHash: String,
    logIndex: Int,
    repository: TransferHistoryRepository,
) : ViewModel() {
    val viewState = repository.observe(walletAddress)
        .map<List<Transfer>, TransactionDetailsState> { transfers ->
            transfers.firstOrNull { it.hash.equals(transactionHash, ignoreCase = true) && it.logIndex == logIndex }
                ?.let(TransactionDetailsState::Content) ?: TransactionDetailsState.Unavailable
        }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(TransactionDetailsState.Error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionDetailsState.Loading)
}

sealed interface TransactionDetailsState {
    data object Loading : TransactionDetailsState

    data object Unavailable : TransactionDetailsState

    data object Error : TransactionDetailsState

    data class Content(val transfer: Transfer) : TransactionDetailsState
}
