package me.sdi.ethwallet.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.stateIn
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.history.TransactionStatus
import me.sdi.ethwallet.core.history.TransactionSummary
import me.sdi.ethwallet.core.history.Transfer
import me.sdi.ethwallet.core.history.TransfersHistoryProvider
import me.sdi.ethwallet.data.repository.WalletRepository
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "TransactionResult"

private const val STATUS_POLL_INTERVAL_MS = 5_000L

class TransactionResultViewModel(
    transfer: Transfer,
    private val historyProvider: TransfersHistoryProvider,
    walletRepository: WalletRepository,
) : ViewModel() {
    private var latestTransfer = transfer

    private val transferUpdates = flow {
        emit(latestTransfer)
        while (latestTransfer.status == TransactionStatus.Pending) {
            historyProvider.getTransaction(latestTransfer.hash)?.let { summary ->
                latestTransfer = latestTransfer.updatedWith(summary)
                emit(latestTransfer)
            }
            if (latestTransfer.status == TransactionStatus.Pending) {
                delay(STATUS_POLL_INTERVAL_MS.milliseconds)
            }
        }
    }.retry { error ->
        Logger.warn(TAG, error) { "Failed to get transaction status, retrying" }
        delay(STATUS_POLL_INTERVAL_MS.milliseconds)
        true
    }

    val viewState: StateFlow<TransactionDetailsState> = transferUpdates
        .combine(walletRepository.observeAllWallets()) { transfer, wallets ->
            transfer.toDetailsContent(wallets) as TransactionDetailsState
        }
        .catch {
            Logger.warn(TAG, it) { "Failed to get wallets" }
            emit(TransactionDetailsState.Error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionDetailsState.Loading)
}

private fun Transfer.updatedWith(summary: TransactionSummary): Transfer {
    val fee = summary.fee ?: fee
    return when (this) {
        is Transfer.Eth -> copy(status = summary.status, fee = fee)
        is Transfer.Erc20 -> copy(status = summary.status, fee = fee)
    }
}
