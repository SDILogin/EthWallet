package me.sdi.ethwallet.ui.history

import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.history.TransactionStatus
import me.sdi.ethwallet.core.history.Transfer
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class TransferDirection {
    Sent,
    Received,
    Self,
}

data class HistoryRow(
    val item: Transfer,
    val action: TransferDirection,
    val status: TransactionStatus,
    val counterparty: EthAddress?,
    val amount: String?,
    val symbol: String,
    val timestampMillis: Long,
    val transactionFee: String? = null,
    val feeEstimated: Boolean = false,
) {
    val key: String
        get() = when (item) {
            is Transfer.Eth -> "${item.hash}:native"
            is Transfer.Erc20 -> "${item.hash}:${item.logIndex}"
        }

    // Native transactions have no transfer log; use the same sentinel as Room.
    val logIndex: Int get() = (item as? Transfer.Erc20)?.logIndex ?: -1
}

internal fun EthAddress.shortHistoryAddress(): String = toString().let { "${it.take(6)}…${it.takeLast(4)}" }

internal fun historyDateKey(timestampMillis: Long, timeZone: TimeZone): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { this.timeZone = timeZone }.format(Date(timestampMillis))

internal fun formatHistoryTime(timestampMillis: Long, timeZone: TimeZone): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).apply { this.timeZone = timeZone }.format(Date(timestampMillis))

internal fun formatHistoryDate(timestampMillis: Long, timeZone: TimeZone): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).apply { this.timeZone = timeZone }.format(Date(timestampMillis))
