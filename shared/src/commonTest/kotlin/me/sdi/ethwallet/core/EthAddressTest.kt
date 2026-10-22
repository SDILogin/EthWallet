package me.sdi.ethwallet.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class EthAddressTest {
    @Test
    fun valid_address_with_0x_prefix_and_40_hex_chars_should_be_accepted() {
        val address = EthAddress("0x1234567890123456789012345678901234567890")
        assertEquals("0x1234567890123456789012345678901234567890", address.toString())
    }

    @Test
    fun valid_address_with_lowercase_hex_chars_should_be_accepted() {
        val address = EthAddress("0xabcdef1234567890abcdef1234567890abcdef12")
        assertEquals("0xabcdef1234567890abcdef1234567890abcdef12", address.toString())
    }

    @Test
    fun valid_address_with_uppercase_hex_chars_should_be_accepted() {
        val address = EthAddress("0xABCDEF1234567890ABCDEF1234567890ABCDEF12")
        assertEquals("0xABCDEF1234567890ABCDEF1234567890ABCDEF12", address.toString())
    }

    @Test
    fun valid_address_with_mixed_case_hex_chars_should_be_accepted() {
        val address = EthAddress("0xAbCdEf1234567890AbCdEf1234567890AbCdEf12")
        assertEquals("0xAbCdEf1234567890AbCdEf1234567890AbCdEf12", address.toString())
    }

    @Test
    fun address_without_0x_prefix_should_be_rejected() {
        assertFailsWith<IllegalArgumentException> {
            EthAddress("1234567890123456789012345678901234567890")
        }
    }

    @Test
    fun empty_address_should_be_rejected() {
        assertFailsWith<IllegalArgumentException> {
            EthAddress("")
        }
    }

    @Test
    fun address_with_only_0x_prefix_should_be_rejected() {
        assertFailsWith<IllegalArgumentException> {
            EthAddress("0x")
        }
    }

    @Test
    fun address_too_short_should_be_rejected() {
        assertFailsWith<IllegalArgumentException> {
            EthAddress("0x123456789012345678901234567890123456789")
        }
    }

    @Test
    fun address_too_long_should_be_rejected() {
        assertFailsWith<IllegalArgumentException> {
            EthAddress("0x12345678901234567890123456789012345678901")
        }
    }

    @Test
    fun address_with_non_hex_characters_should_be_rejected() {
        assertFailsWith<IllegalArgumentException> {
            EthAddress("0xGHIJKL7890123456789012345678901234567890")
        }
    }

    @Test
    fun address_with_special_characters_should_be_rejected() {
        assertFailsWith<IllegalArgumentException> {
            EthAddress("0x123456789012345678901234567890123456789!")
        }
    }

    @Test
    fun address_with_spaces_should_be_rejected() {
        assertFailsWith<IllegalArgumentException> {
            EthAddress("0x12345678901234567890 234567890123456789")
        }
    }

    @Test
    fun two_addresses_with_same_value_should_be_equal() {
        val address1 = EthAddress("0x1234567890123456789012345678901234567890")
        val address2 = EthAddress("0x1234567890123456789012345678901234567890")
        assertEquals(address1, address2)
    }

    @Test
    fun two_addresses_with_different_values_should_not_be_equal() {
        val address1 = EthAddress("0x1234567890123456789012345678901234567890")
        val address2 = EthAddress("0x9876543210987654321098765432109876543210")
        assertNotEquals(address1, address2)
    }

    @Test
    fun lowercase_returns_normalized_address_without_changing_original() {
        val original = "0xAbCdEf1234567890AbCdEf1234567890AbCdEf12"
        val address = EthAddress(original)
        val normalized = address.lowercase()

        assertEquals(EthAddress(original.lowercase()), normalized)
        assertEquals(normalized, normalized.lowercase())
        assertEquals(normalized.hashCode(), EthAddress(original.lowercase()).hashCode())
        assertEquals(original, address.toString())
    }

    @Test
    fun EthContractAddress_should_be_valid_zero_address() {
        assertEquals("0x0000000000000000000000000000000000000000", EthContractAddress.toString())
    }
}
