package me.sdi.ethwallet.ui.history

import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.history.HistoryItem
import me.sdi.ethwallet.core.history.TransactionStatus
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class TransactionType {
    Sent,
    Received,
    Self,
}

data class HistoryRow(
    val item: HistoryItem,
    val action: TransactionType,
    val status: TransactionStatus,
    val counterparty: EthAddress?,
    val amount: String?,
    val symbol: String,
    val timestampMillis: Long?,
) {
    val key: String
        get() = when (item) {
            is HistoryItem.NativeTransfer -> "${item.transactionHash}:native"
            is HistoryItem.TokenTransfer -> "${item.transactionHash}:${item.logIndex}"
        }

    // Native transactions have no transfer log; use the same sentinel as Room.
    val logIndex: Int get() = (item as? HistoryItem.TokenTransfer)?.logIndex ?: -1
}

internal fun EthAddress.shortHistoryAddress(): String = toString().let { "${it.take(6)}…${it.takeLast(4)}" }

internal fun historyDateKey(timestampMillis: Long, timeZone: TimeZone): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { this.timeZone = timeZone }.format(Date(timestampMillis))

internal fun formatHistoryTime(timestampMillis: Long, timeZone: TimeZone): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).apply { this.timeZone = timeZone }.format(Date(timestampMillis))

internal fun formatHistoryDate(timestampMillis: Long, timeZone: TimeZone): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).apply { this.timeZone = timeZone }.format(Date(timestampMillis))
