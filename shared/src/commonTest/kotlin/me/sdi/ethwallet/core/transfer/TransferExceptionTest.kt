package me.sdi.ethwallet.core.transfer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransferExceptionTest {
    @Test
    fun InsufficientBalance_stores_message_correctly() {
        val exception = TransferException.InsufficientBalance("Required: 1 ETH, Available: 0.5 ETH")
        assertEquals("Required: 1 ETH, Available: 0.5 ETH", exception.message)
    }

    @Test
    fun InsufficientBalance_extends_TransferException() {
        val exception = TransferException.InsufficientBalance("Not enough funds")
        assertTrue(exception is TransferException)
    }

    @Test
    fun InsufficientBalance_has_no_cause_by_default() {
        val exception = TransferException.InsufficientBalance("Not enough funds")
        assertNull(exception.cause)
    }

    @Test
    fun InvalidAddress_stores_message_correctly() {
        val exception = TransferException.InvalidAddress("Invalid destination address format")
        assertEquals("Invalid destination address format", exception.message)
    }

    @Test
    fun InvalidAddress_extends_TransferException() {
        val exception = TransferException.InvalidAddress("Bad address")
        assertTrue(exception is TransferException)
    }

    @Test
    fun InvalidAmount_stores_message_correctly() {
        val exception = TransferException.InvalidAmount("Amount must be positive")
        assertEquals("Amount must be positive", exception.message)
    }

    @Test
    fun InvalidAmount_extends_TransferException() {
        val exception = TransferException.InvalidAmount("Negative amount")
        assertTrue(exception is TransferException)
    }

    @Test
    fun NetworkError_stores_message_and_cause_correctly() {
        val cause = RuntimeException("Connection timeout")
        val exception = TransferException.NetworkError("Failed to connect to network", cause)
        assertEquals("Failed to connect to network", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun NetworkError_extends_TransferException() {
        val cause = RuntimeException("Network issue")
        val exception = TransferException.NetworkError("Network failed", cause)
        assertTrue(exception is TransferException)
    }

    @Test
    fun NetworkError_cause_is_accessible() {
        val cause = RuntimeException("No internet")
        val exception = TransferException.NetworkError("Network error", cause)
        assertTrue(exception.cause is RuntimeException)
        assertEquals("No internet", exception.cause?.message)
    }

    @Test
    fun TransactionFailed_stores_message_correctly() {
        val exception = TransferException.TransactionFailed("Transaction reverted: out of gas")
        assertEquals("Transaction reverted: out of gas", exception.message)
    }

    @Test
    fun TransactionFailed_extends_TransferException() {
        val exception = TransferException.TransactionFailed("TX failed")
        assertTrue(exception is TransferException)
    }

    @Test
    fun GasPriceError_stores_message_correctly() {
        val exception = TransferException.GasPriceError("Gas price estimation failed")
        assertEquals("Gas price estimation failed", exception.message)
    }

    @Test
    fun GasPriceError_extends_TransferException() {
        val exception = TransferException.GasPriceError("Gas error")
        assertTrue(exception is TransferException)
    }

    @Test
    fun all_exception_types_extend_Exception() {
        val exceptions = listOf(
            TransferException.InsufficientBalance("msg"),
            TransferException.InvalidAddress("msg"),
            TransferException.InvalidAmount("msg"),
            TransferException.NetworkError("msg", RuntimeException()),
            TransferException.TransactionFailed("msg"),
            TransferException.GasPriceError("msg"),
        )
        exceptions.forEach { exception ->
            assertTrue(exception is Exception)
        }
    }

    @Test
    fun exceptions_can_be_caught_as_TransferException() {
        var caught = false
        try {
            throw TransferException.InsufficientBalance("Test")
        } catch (e: TransferException) {
            caught = true
        }
        assertTrue(caught)
    }
}
