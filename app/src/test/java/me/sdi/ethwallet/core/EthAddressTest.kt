package me.sdi.ethwallet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EthAddressTest {
    @Test
    fun `valid address with 0x prefix and 40 hex chars should be accepted`() {
        val address = EthAddress("0x1234567890123456789012345678901234567890")
        assertEquals("0x1234567890123456789012345678901234567890", address.toString())
    }

    @Test
    fun `valid address with lowercase hex chars should be accepted`() {
        val address = EthAddress("0xabcdef1234567890abcdef1234567890abcdef12")
        assertEquals("0xabcdef1234567890abcdef1234567890abcdef12", address.toString())
    }

    @Test
    fun `valid address with uppercase hex chars should be accepted`() {
        val address = EthAddress("0xABCDEF1234567890ABCDEF1234567890ABCDEF12")
        assertEquals("0xABCDEF1234567890ABCDEF1234567890ABCDEF12", address.toString())
    }

    @Test
    fun `valid address with mixed case hex chars should be accepted`() {
        val address = EthAddress("0xAbCdEf1234567890AbCdEf1234567890AbCdEf12")
        assertEquals("0xAbCdEf1234567890AbCdEf1234567890AbCdEf12", address.toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `address without 0x prefix should be rejected`() {
        EthAddress("1234567890123456789012345678901234567890")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty address should be rejected`() {
        EthAddress("")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `address with only 0x prefix should be rejected`() {
        EthAddress("0x")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `address too short should be rejected`() {
        EthAddress("0x123456789012345678901234567890123456789")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `address too long should be rejected`() {
        EthAddress("0x12345678901234567890123456789012345678901")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `address with non-hex characters should be rejected`() {
        EthAddress("0xGHIJKL7890123456789012345678901234567890")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `address with special characters should be rejected`() {
        EthAddress("0x123456789012345678901234567890123456789!")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `address with spaces should be rejected`() {
        EthAddress("0x12345678901234567890 234567890123456789")
    }

    @Test
    fun `two addresses with same value should be equal`() {
        val address1 = EthAddress("0x1234567890123456789012345678901234567890")
        val address2 = EthAddress("0x1234567890123456789012345678901234567890")
        assertEquals(address1, address2)
    }

    @Test
    fun `two addresses with different values should not be equal`() {
        val address1 = EthAddress("0x1234567890123456789012345678901234567890")
        val address2 = EthAddress("0x9876543210987654321098765432109876543210")
        assertNotEquals(address1, address2)
    }

    @Test
    fun `EthContractAddress should be valid zero address`() {
        assertEquals("0x0000000000000000000000000000000000000000", EthContractAddress.toString())
    }
}
