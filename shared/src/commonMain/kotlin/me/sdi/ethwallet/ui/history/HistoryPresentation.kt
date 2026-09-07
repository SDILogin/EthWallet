package me.sdi.ethwallet.ui.history

import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.history.HistoryItem
import me.sdi.ethwallet.core.history.TransactionStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Instant

internal enum class HistoryAction {
    Sent,
    Received,
    Self,
    Transaction,
    ContractCreation,
}

internal data class HistoryRow(
    val item: HistoryItem,
    val action: HistoryAction,
    val status: TransactionStatus,
    val counterparty: EthAddress?,
    val amount: String?,
    val symbol: String,
    val timestampMillis: Long?,
) {
    val key: String get() = when (item) {
        is HistoryItem.NativeTransfer -> "${item.transactionHash}:native"
        is HistoryItem.TokenTransfer -> "${item.transactionHash}:${item.logIndex}"
    }

    // Native transactions have no transfer log; use the same sentinel as Room.
    val logIndex: Int get() = (item as? HistoryItem.TokenTransfer)?.logIndex ?: -1
}

internal fun HistoryItem.toHistoryRow(walletAddress: EthAddress): HistoryRow {
    val outgoing = from.toString().equals(walletAddress.toString(), ignoreCase = true)
    val incoming = to?.toString()?.equals(walletAddress.toString(), ignoreCase = true) == true
    val action = when {
        outgoing && incoming -> HistoryAction.Self
        to == null -> HistoryAction.ContractCreation
        this is HistoryItem.NativeTransfer && valueWei.toBigDecimalOrNull()?.signum() == 0 -> HistoryAction.Transaction
        outgoing -> HistoryAction.Sent
        incoming -> HistoryAction.Received
        else -> HistoryAction.Transaction
    }
    val status = (this as? HistoryItem.NativeTransfer)?.status ?: TransactionStatus.Confirmed
    val value = when (this) {
        is HistoryItem.NativeTransfer -> formatHistoryAmount(valueWei, 18)
        is HistoryItem.TokenTransfer -> formatHistoryAmount(value, tokenDecimals)
    }
    val sign = when {
        status != TransactionStatus.Confirmed -> ""
        action == HistoryAction.Sent -> "-"
        action == HistoryAction.Received -> "+"
        else -> ""
    }
    return HistoryRow(
        item = this,
        action = action,
        status = status,
        counterparty = if (outgoing) to else from,
        amount = value?.let { sign + it },
        symbol = when (this) {
            is HistoryItem.NativeTransfer -> "ETH"
            is HistoryItem.TokenTransfer -> tokenSymbol?.takeIf(String::isNotBlank) ?: tokenAddress.shortHistoryAddress()
        },
        timestampMillis = timestamp?.let {
            try {
                Instant.parse(it).toEpochMilliseconds()
            } catch (_: IllegalArgumentException) {
                null
            }
        },
    )
}

internal fun formatHistoryAmount(rawValue: String, decimals: Int?): String? {
    if (decimals == null || decimals !in 0..255) return null
    val amount = rawValue.toBigDecimalOrNull()?.movePointLeft(decimals) ?: return null
    if (amount.signum() < 0) return null
    val smallest = BigDecimal("0.00000001")
    if (amount.signum() > 0 && amount < smallest) return "<0.00000001"
    return amount.setScale(8, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
}

internal fun EthAddress.shortHistoryAddress(): String = toString().let { "${it.take(6)}…${it.takeLast(4)}" }

internal fun historyDateKey(timestampMillis: Long, timeZone: TimeZone): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { this.timeZone = timeZone }.format(Date(timestampMillis))

internal fun formatHistoryTime(timestampMillis: Long, timeZone: TimeZone): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).apply { this.timeZone = timeZone }.format(Date(timestampMillis))

internal fun formatHistoryDate(timestampMillis: Long, timeZone: TimeZone): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).apply { this.timeZone = timeZone }.format(Date(timestampMillis))
