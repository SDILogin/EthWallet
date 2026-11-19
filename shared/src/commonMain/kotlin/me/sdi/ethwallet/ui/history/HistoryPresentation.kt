package me.sdi.ethwallet.ui.history

import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.history.TransactionStatus
import me.sdi.ethwallet.core.history.Transfer
import java.text.DateFormat
import java.util.Date
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
}

internal fun EthAddress.shortHistoryAddress(): String = toString().let { "${it.take(6)}…${it.takeLast(4)}" }

private const val DAY_MS = 86_400_000L

internal fun historyLocalDay(timestampMillis: Long, timeZone: TimeZone): Long =
    Math.floorDiv(timestampMillis + timeZone.getOffset(timestampMillis), DAY_MS)

internal class HistoryDateFormats(timeZone: TimeZone) {
    private val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT).apply { this.timeZone = timeZone }
    private val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM).apply { this.timeZone = timeZone }

    fun time(timestampMillis: Long): String = timeFormat.format(Date(timestampMillis))

    fun date(timestampMillis: Long): String = dateFormat.format(Date(timestampMillis))
}
