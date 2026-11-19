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
}

internal fun EthAddress.shortHistoryAddress(): String = toString().let { "${it.take(6)}…${it.takeLast(4)}" }

internal class HistoryDateFormats(timeZone: TimeZone) {
    private val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { this.timeZone = timeZone }
    private val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT).apply { this.timeZone = timeZone }
    private val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM).apply { this.timeZone = timeZone }

    fun dateKey(timestampMillis: Long): String = keyFormat.format(Date(timestampMillis))

    fun time(timestampMillis: Long): String = timeFormat.format(Date(timestampMillis))

    fun date(timestampMillis: Long): String = dateFormat.format(Date(timestampMillis))
}
