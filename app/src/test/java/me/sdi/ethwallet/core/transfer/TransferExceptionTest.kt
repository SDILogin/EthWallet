package me.sdi.ethwallet.core.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TransferExceptionTest {
    @Test
    fun `InsufficientBalance stores message correctly`() {
        val exception = TransferException.InsufficientBalance("Required: 1 ETH, Available: 0.5 ETH")
        assertEquals("Required: 1 ETH, Available: 0.5 ETH", exception.message)
    }

    @Test
    fun `InsufficientBalance extends TransferException`() {
        val exception = TransferException.InsufficientBalance("Not enough funds")
        assertTrue(exception is TransferException)
    }

    @Test
    fun `InsufficientBalance has no cause by default`() {
        val exception = TransferException.InsufficientBalance("Not enough funds")
        assertNull(exception.cause)
    }

    @Test
    fun `InvalidAddress stores message correctly`() {
        val exception = TransferException.InvalidAddress("Invalid destination address format")
        assertEquals("Invalid destination address format", exception.message)
    }

    @Test
    fun `InvalidAddress extends TransferException`() {
        val exception = TransferException.InvalidAddress("Bad address")
        assertTrue(exception is TransferException)
    }

    @Test
    fun `InvalidAmount stores message correctly`() {
        val exception = TransferException.InvalidAmount("Amount must be positive")
        assertEquals("Amount must be positive", exception.message)
    }

    @Test
    fun `InvalidAmount extends TransferException`() {
        val exception = TransferException.InvalidAmount("Negative amount")
        assertTrue(exception is TransferException)
    }

    @Test
    fun `NetworkError stores message and cause correctly`() {
        val cause = IOException("Connection timeout")
        val exception = TransferException.NetworkError("Failed to connect to network", cause)
        assertEquals("Failed to connect to network", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `NetworkError extends TransferException`() {
        val cause = RuntimeException("Network issue")
        val exception = TransferException.NetworkError("Network failed", cause)
        assertTrue(exception is TransferException)
    }

    @Test
    fun `NetworkError cause is accessible`() {
        val cause = IOException("No internet")
        val exception = TransferException.NetworkError("Network error", cause)
        assertTrue(exception.cause is IOException)
        assertEquals("No internet", exception.cause?.message)
    }

    @Test
    fun `TransactionFailed stores message correctly`() {
        val exception = TransferException.TransactionFailed("Transaction reverted: out of gas")
        assertEquals("Transaction reverted: out of gas", exception.message)
    }

    @Test
    fun `TransactionFailed extends TransferException`() {
        val exception = TransferException.TransactionFailed("TX failed")
        assertTrue(exception is TransferException)
    }

    @Test
    fun `GasPriceError stores message correctly`() {
        val exception = TransferException.GasPriceError("Gas price estimation failed")
        assertEquals("Gas price estimation failed", exception.message)
    }

    @Test
    fun `GasPriceError extends TransferException`() {
        val exception = TransferException.GasPriceError("Gas error")
        assertTrue(exception is TransferException)
    }

    @Test
    fun `all exception types extend Exception`() {
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
    fun `exceptions can be caught as TransferException`() {
        var caught = false
        try {
            throw TransferException.InsufficientBalance("Test")
        } catch (e: TransferException) {
            caught = true
        }
        assertTrue(caught)
    }
}
