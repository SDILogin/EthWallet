package me.sdi.ethwallet.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.stateIn
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.history.TransactionStatus
import me.sdi.ethwallet.core.history.Transfer
import me.sdi.ethwallet.core.history.TransferHistoryRepository
import me.sdi.ethwallet.core.history.TransfersHistoryProvider
import me.sdi.ethwallet.data.repository.WalletRepository
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "TransactionDetails"

private const val STATUS_POLL_INTERVAL_MS = 5_000L

class TransactionDetailsViewModel(
    private val transactionHash: String,
    private val repository: TransferHistoryRepository,
    private val historyProvider: TransfersHistoryProvider,
    walletRepository: WalletRepository,
) : ViewModel() {
    private val statusUpdates = flow {
        emit(Unit)
        while (repository.observe(transactionHash).filterNotNull().first().status == TransactionStatus.Pending) {
            val summary = historyProvider.getTransaction(transactionHash)
            if (summary != null && summary.status != TransactionStatus.Pending) {
                repository.update(transactionHash, summary.status, summary.fee)
            } else {
                delay(STATUS_POLL_INTERVAL_MS.milliseconds)
            }
        }
    }.retry { error ->
        Logger.warn(TAG, error) { "Failed to get transaction status, retrying" }
        delay(STATUS_POLL_INTERVAL_MS.milliseconds)
        true
    }

    val viewState = repository
        .observe(transactionHash)
        .combine(statusUpdates) { transfer, _ -> transfer }
        .map { transfer ->
            if (transfer == null) {
                TransactionDetailsState.Unavailable
            } else {
                val wallets = walletRepository.observeAllWallets().first()
                val fromWalletLabel = wallets.firstOrNull { it.address.lowercase() == transfer.from.lowercase() }?.label
                val toWalletLabel = wallets.firstOrNull { it.address.lowercase() == transfer.to.lowercase() }?.label
                TransactionDetailsState.Content(
                    transfer = transfer,
                    fromWalletLabel = fromWalletLabel,
                    toWalletLabel = toWalletLabel,
                )
            }
        }
        .catch {
            Logger.warn(TAG, it) { "Failed to get transaction details" }
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
