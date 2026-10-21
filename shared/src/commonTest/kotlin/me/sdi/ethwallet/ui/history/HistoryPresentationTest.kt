package me.sdi.ethwallet.ui.history

import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.history.TransactionFee
import me.sdi.ethwallet.core.history.TransactionStatus
import me.sdi.ethwallet.core.history.Transfer
import me.sdi.ethwallet.core.transfer.toWei
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class HistoryPresentationTest {
    private val wallet = EthAddress("0x${"a".repeat(40)}")
    private val other = EthAddress("0x${"b".repeat(40)}")
    private val native = Transfer.Eth(
        hash = "0x${"c".repeat(64)}",
        timestamp = Instant.parse("2026-09-07T23:30:00Z").toEpochMilliseconds(),
        from = wallet,
        to = other,
        valueWei = "42000000000000000".toBigInteger().toWei(),
        status = TransactionStatus.Confirmed,
    )
    private val token = Transfer.Erc20(
        hash = native.hash,
        timestamp = native.timestamp,
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
        val row = native.requiredRow(wallet)
        assertEquals(TransferDirection.Sent, row.action)
        assertEquals(other, row.counterparty)
        assertEquals("-0.042", row.amount)
        assertEquals("ETH", row.symbol)
        assertEquals(-1, row.logIndex)
    }

    @Test
    fun incoming_token_uses_sender_not_token_contract() {
        val row = token.requiredRow(wallet)
        assertEquals(TransferDirection.Received, row.action)
        assertEquals(other, row.counterparty)
        assertEquals("+250", row.amount)
        assertEquals("USDC", row.symbol)
        assertEquals(4, row.logIndex)
    }

    @Test
    fun direction_ignores_address_case_and_self_has_no_sign() {
        val mixedCaseWallet = EthAddress("0x${"A".repeat(40)}")
        assertEquals(TransferDirection.Sent, native.requiredRow(mixedCaseWallet).action)
        val row = native.copy(to = mixedCaseWallet).requiredRow(wallet)
        assertEquals(TransferDirection.Self, row.action)
        assertEquals("0.042", row.amount)
    }

    @Test
    fun pending_amount_has_no_completed_balance_change_sign() {
        val row = native.copy(status = TransactionStatus.Pending).requiredRow(wallet)
        assertEquals(TransactionStatus.Pending, row.status)
        assertEquals("0.042", row.amount)
    }

    @Test
    fun zero_values_and_failed_attempts_are_hidden() {
        assertNull(native.copy(valueWei = "0".toBigInteger().toWei()).toHistoryRow(wallet))
        assertNull(native.copy(status = TransactionStatus.Failed).toHistoryRow(wallet))
        assertNull(token.copy(value = "0").toHistoryRow(wallet))
        assertNull(token.copy(status = TransactionStatus.Failed).toHistoryRow(wallet))
    }

    @Test
    fun missing_token_symbol_uses_contract_address() {
        val row = token.copy(tokenSymbol = null).requiredRow(wallet)
        assertEquals("+250", row.amount)
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
    fun date_grouping_uses_local_day() {
        val timestamp = native.requiredRow(wallet).timestampMillis
        assertEquals("2026-09-07", historyDateKey(timestamp, TimeZone.getTimeZone("UTC")))
        assertEquals("2026-09-08", historyDateKey(timestamp, TimeZone.getTimeZone("Asia/Bangkok")))
    }

    @Test
    fun transfer_keys_keep_multiple_logs_in_same_transaction_distinct() {
        assertNotEquals(native.requiredRow(wallet).key, token.requiredRow(wallet).key)
        assertNotEquals(token.requiredRow(wallet).key, token.copy(logIndex = 5).requiredRow(wallet).key)
    }

    @Test
    fun full_fee_is_shown_for_outgoing_and_self_transfers_only() {
        val fee = TransactionFee("21000000000000".toBigInteger().toWei(), estimated = true)
        val outgoing = native.copy(fee = fee).requiredRow(wallet)
        assertEquals("0.000021", outgoing.transactionFee)
        assertEquals(true, outgoing.feeEstimated)
        val self = native.copy(to = wallet, fee = fee).requiredRow(wallet)
        assertEquals("0.000021", self.transactionFee)
        assertNull(native.copy(fee = fee).requiredRow(other).transactionFee)
        assertEquals(false, native.copy(fee = fee).requiredRow(other).feeEstimated)
        val outgoingToken = token.copy(from = wallet, to = other, fee = fee)
        assertEquals(
            outgoingToken.requiredRow(wallet).transactionFee,
            outgoingToken.copy(logIndex = 5).requiredRow(wallet).transactionFee,
        )
    }

    private fun Transfer.requiredRow(address: EthAddress) = requireNotNull(toHistoryRow(address))
}
