package me.sdi.ethwallet.utils

import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.ui.daiOnMainnet
import me.sdi.ethwallet.ui.usdcOnMainnet
import me.sdi.ethwallet.ui.usdtOnMainnet
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReceiveUtilsTest {
    @Test
    fun generateReceiveQrPayload_should_generate_basic_ETH_URI_without_amount() {
        val receiverAddress = createTestAddress("0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2")

        val result = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = receiverAddress,
            contractAddress = EthContractAddress,
        )

        assertEquals(
            "ethereum:0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2",
            result,
            "ETH URI should contain only the receiver address when no amount is specified",
        )
    }

    @Test
    fun generateReceiveQrPayload_should_generate_ETH_URI_with_amount_in_wei_format() {
        val receiverAddress = createTestAddress("0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2")
        val amount = BigDecimal("1.5")

        val result = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = receiverAddress,
            contractAddress = EthContractAddress,
            amount = amount,
        )

        assertEquals(
            "ethereum:0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2?value=1500000000000000000",
            result,
            "ETH URI should include value parameter with wei format",
        )
    }

    @Test
    fun generateReceiveQrPayload_should_handle_very_small_ETH_amounts_without_scientific_notation() {
        val receiverAddress = createTestAddress("0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2")
        val amount = BigDecimal("0.000000001")

        val result = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = receiverAddress,
            contractAddress = EthContractAddress,
            amount = amount,
        )

        assertTrue(
            result.contains("value=1000000000"),
            "ETH URI should convert to wei format (1000000000 wei for 0.000000001 ETH)",
        )
        assertTrue(
            !result.substringAfter("value=").contains("E") && !result.substringAfter("value=").contains("+"),
            "Value part should not contain scientific notation",
        )
    }

    @Test
    fun generateReceiveQrPayload_should_generate_USDC_URI_without_amount() {
        val receiverAddress = createTestAddress("0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2")

        val result = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = receiverAddress,
            contractAddress = usdcOnMainnet.ethAddress,
        )

        assertEquals(
            "ethereum:0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48/transfer?address=0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2",
            result,
            "USDC URI should follow ERC-20 transfer format without amount parameters",
        )
    }

    @Test
    fun generateReceiveQrPayload_should_generate_USDC_URI_with_correct_minimal_units() {
        val receiverAddress = createTestAddress("0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2")
        val amount = BigDecimal("100.5")

        val result = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = receiverAddress,
            contractAddress = usdcOnMainnet.ethAddress,
            amount = amount,
        )

        assertEquals(
            "ethereum:0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48/transfer?address=0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2&uint256=100500000",
            result,
            "USDC URI should convert 100.5 USDC to 100500000 minimal units (6 decimals)",
        )
    }

    @Test
    fun generateReceiveQrPayload_should_generate_USDT_URI_with_correct_minimal_units() {
        val receiverAddress = createTestAddress("0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2")
        val amount = BigDecimal("50.123456")

        val result = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = receiverAddress,
            contractAddress = usdtOnMainnet.ethAddress,
            amount = amount,
        )

        assertEquals(
            "ethereum:0xdAC17F958D2ee523a2206206994597C13D831ec7/transfer?address=0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2&uint256=50123456",
            result,
            "USDT URI should convert 50.123456 USDT to 50123456 minimal units (6 decimals)",
        )
    }

    @Test
    fun generateReceiveQrPayload_should_generate_DAI_URI_with_correct_minimal_units() {
        val receiverAddress = createTestAddress("0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2")
        val amount = BigDecimal("2.5")

        val result = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = receiverAddress,
            contractAddress = daiOnMainnet.ethAddress,
            amount = amount,
        )

        assertEquals(
            "ethereum:0x6B175474E89094C44Da98b954EedeAC495271d0F/transfer?address=0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2&uint256=2500000000000000000",
            result,
            "DAI URI should convert 2.5 DAI to 2500000000000000000 minimal units (18 decimals)",
        )
    }

    @Test
    fun generateReceiveQrPayload_should_handle_fractional_amounts_with_proper_rounding_for_USDC() {
        val receiverAddress = createTestAddress("0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2")
        val amount = BigDecimal("1.1234567")

        val result = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = receiverAddress,
            contractAddress = usdcOnMainnet.ethAddress,
            amount = amount,
        )

        assertTrue(
            result.contains("uint256=1123457"),
            "USDC URI should round 1.1234567 to 1123457 minimal units (6 decimals, rounded up)",
        )
    }

    @Test
    fun generateReceiveQrPayload_should_handle_fractional_amounts_with_proper_rounding_for_USDT() {
        val receiverAddress = createTestAddress("0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2")
        val amount = BigDecimal("1.1234564")

        val result = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = receiverAddress,
            contractAddress = usdtOnMainnet.ethAddress,
            amount = amount,
        )

        assertTrue(
            result.contains("uint256=1123456"),
            "USDT URI should round 1.1234564 to 1123456 minimal units (6 decimals, rounded down)",
        )
    }

    @Test
    fun generateReceiveQrPayload_should_handle_unknown_token_with_default_18_decimals() {
        val receiverAddress = createTestAddress("0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2")
        val unknownTokenAddress = createTestAddress("0x1234567890123456789012345678901234567890")
        val amount = BigDecimal("1.0")

        val result = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = receiverAddress,
            contractAddress = unknownTokenAddress,
            amount = amount,
        )

        assertTrue(
            result.contains("uint256=1000000000000000000"),
            "Unknown token should default to 18 decimals, converting 1.0 to 1000000000000000000 minimal units",
        )
    }

    @Test
    fun generateReceiveQrPayload_should_handle_zero_amounts_correctly() {
        val receiverAddress = createTestAddress("0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2")
        val amount = BigDecimal.ZERO

        val ethResult = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = receiverAddress,
            contractAddress = EthContractAddress,
            amount = amount,
        )

        val usdcResult = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = receiverAddress,
            contractAddress = usdcOnMainnet.ethAddress,
            amount = amount,
        )

        assertEquals(
            "ethereum:0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2?value=0",
            ethResult,
            "ETH URI should handle zero amount correctly",
        )

        assertTrue(
            usdcResult.contains("uint256=0"),
            "USDC URI should handle zero amount correctly",
        )
    }

    @Test
    fun generateReceiveQrPayload_should_handle_very_large_amounts_without_overflow() {
        val receiverAddress = createTestAddress("0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2")
        val largeAmount = BigDecimal("999999999.999999")

        val result = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = receiverAddress,
            contractAddress = usdcOnMainnet.ethAddress,
            amount = largeAmount,
        )

        assertTrue(
            result.contains("uint256=999999999999999"),
            "Large USDC amount should be converted correctly to minimal units",
        )
    }

    @Test
    fun generateReceiveQrPayload_should_preserve_address_case_correctly() {
        val mixedCaseAddress = createTestAddress("0xaBcDeFaBcDeFaBcDeFaBcDeFaBcDeFaBcDeFaBcD")

        val ethResult = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = mixedCaseAddress,
            contractAddress = EthContractAddress,
        )

        val usdcResult = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = mixedCaseAddress,
            contractAddress = usdcOnMainnet.ethAddress,
        )

        assertTrue(
            ethResult.contains("0xaBcDeFaBcDeFaBcDeFaBcDeFaBcDeFaBcDeFaBcD"),
            "ETH URI should preserve original address case",
        )

        assertTrue(
            usdcResult.contains("address=0xaBcDeFaBcDeFaBcDeFaBcDeFaBcDeFaBcDeFaBcD"),
            "USDC URI should preserve original receiver address case",
        )
    }

    private fun createTestAddress(addressString: String): EthAddress {
        return EthAddress(addressString)
    }
}
