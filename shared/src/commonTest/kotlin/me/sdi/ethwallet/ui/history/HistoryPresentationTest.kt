package me.sdi.ethwallet.ui.history

import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.history.HistoryItem
import me.sdi.ethwallet.core.history.TransactionStatus
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class HistoryPresentationTest {
    private val wallet = EthAddress("0x${"a".repeat(40)}")
    private val other = EthAddress("0x${"b".repeat(40)}")
    private val native = HistoryItem.NativeTransfer(
        transactionHash = "0x${"c".repeat(64)}",
        timestamp = "2026-09-07T23:30:00Z",
        from = wallet,
        to = other,
        valueWei = "42000000000000000",
        status = TransactionStatus.Confirmed,
    )
    private val token = HistoryItem.TokenTransfer(
        transactionHash = native.transactionHash,
        timestamp = requireNotNull(native.timestamp),
        from = other,
        to = wallet,
        logIndex = 4,
        tokenAddress = EthAddress("0x${"d".repeat(40)}"),
        tokenSymbol = "USDC",
        tokenDecimals = 6,
        value = "250000000",
    )

    @Test
    fun outgoing_native_uses_recipient_and_negative_amount() {
        val row = native.toHistoryRow(wallet)
        assertEquals(HistoryAction.Sent, row.action)
        assertEquals(other, row.counterparty)
        assertEquals("-0.042", row.amount)
        assertEquals("ETH", row.symbol)
        assertEquals(-1, row.logIndex)
    }

    @Test
    fun incoming_token_uses_sender_not_token_contract() {
        val row = token.toHistoryRow(wallet)
        assertEquals(HistoryAction.Received, row.action)
        assertEquals(other, row.counterparty)
        assertEquals("+250", row.amount)
        assertEquals("USDC", row.symbol)
        assertEquals(4, row.logIndex)
    }

    @Test
    fun direction_ignores_address_case_and_self_has_no_sign() {
        val mixedCaseWallet = EthAddress("0x${"A".repeat(40)}")
        assertEquals(HistoryAction.Sent, native.toHistoryRow(mixedCaseWallet).action)
        val row = native.copy(to = mixedCaseWallet).toHistoryRow(wallet)
        assertEquals(HistoryAction.Self, row.action)
        assertEquals("0.042", row.amount)
    }

    @Test
    fun pending_and_failed_amounts_are_not_completed_balance_changes() {
        listOf(TransactionStatus.Pending, TransactionStatus.Failed).forEach { status ->
            val row = native.copy(status = status).toHistoryRow(wallet)
            assertEquals(status, row.status)
            assertEquals("0.042", row.amount)
        }
    }

    @Test
    fun zero_native_value_and_contract_creation_are_not_labelled_sent() {
        assertEquals(HistoryAction.Transaction, native.copy(valueWei = "0").toHistoryRow(wallet).action)
        val contract = native.copy(to = null).toHistoryRow(wallet)
        assertEquals(HistoryAction.ContractCreation, contract.action)
        assertNull(contract.counterparty)
    }

    @Test
    fun unknown_token_metadata_is_not_guessed() {
        val row = token.copy(tokenDecimals = null, tokenSymbol = null).toHistoryRow(wallet)
        assertNull(row.amount)
        assertEquals(token.tokenAddress.shortHistoryAddress(), row.symbol)
    }

    @Test
    fun formatting_preserves_large_values_and_does_not_round_small_transfers_to_zero() {
        assertEquals("123456789012345678901234567890", formatHistoryAmount("123456789012345678901234567890", 0))
        assertEquals("<0.00000001", formatHistoryAmount("1", 18))
        assertEquals("0.12345678", formatHistoryAmount("123456789123456789", 18))
        assertEquals("0", formatHistoryAmount("0", 18))
        assertNull(formatHistoryAmount("100", -1))
        assertNull(formatHistoryAmount("100", 256))
        assertNull(formatHistoryAmount("invalid", 6))
    }

    @Test
    fun missing_or_invalid_timestamp_stays_unknown() {
        assertNull(native.copy(timestamp = null).toHistoryRow(wallet).timestampMillis)
        assertNull(native.copy(timestamp = "not a timestamp").toHistoryRow(wallet).timestampMillis)
    }

    @Test
    fun date_grouping_uses_local_day() {
        val timestamp = requireNotNull(native.toHistoryRow(wallet).timestampMillis)
        assertEquals("2026-09-07", historyDateKey(timestamp, TimeZone.getTimeZone("UTC")))
        assertEquals("2026-09-08", historyDateKey(timestamp, TimeZone.getTimeZone("Asia/Bangkok")))
    }

    @Test
    fun transfer_keys_keep_multiple_logs_in_same_transaction_distinct() {
        assertNotEquals(native.toHistoryRow(wallet).key, token.toHistoryRow(wallet).key)
        assertNotEquals(token.toHistoryRow(wallet).key, token.copy(logIndex = 5).toHistoryRow(wallet).key)
    }
}
