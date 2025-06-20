package me.sdi.ethwallet.utils

import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.ui.KnownTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ReceiveUtilsTest {
    @Test
    fun `generateReceiveQrPayload should generate basic ETH URI without amount`() {
        val receiverAddress = createTestAddress("0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2")

        val result = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = receiverAddress,
            contractAddress = EthContractAddress,
        )

        assertEquals(
            "ETH URI should contain only the receiver address when no amount is specified",
            "ethereum:0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2",
            result,
        )
    }

    @Test
    fun `generateReceiveQrPayload should generate ETH URI with amount in plain decimal format`() {
        val receiverAddress = createTestAddress("0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2")
        val amount = BigDecimal("1.5")

        val result = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = receiverAddress,
            contractAddress = EthContractAddress,
            amount = amount,
        )

        assertEquals(
            "ETH URI should include value parameter with plain decimal format",
            "ethereum:0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2?value=1.5",
            result,
        )
    }

    @Test
    fun `generateReceiveQrPayload should handle very small ETH amounts without scientific notation`() {
        val receiverAddress = createTestAddress("0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2")
        val amount = BigDecimal("0.000000001")

        val result = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = receiverAddress,
            contractAddress = EthContractAddress,
            amount = amount,
        )

        assertTrue(
            "ETH URI should use plain decimal format instead of scientific notation",
            result.contains("value=0.000000001"),
        )
        assertTrue(
            "Value part should not contain scientific notation",
            !result.substringAfter("value=").contains("E") && !result.substringAfter("value=").contains("+"),
        )
    }

    @Test
    fun `generateReceiveQrPayload should generate USDC URI without amount`() {
        val receiverAddress = createTestAddress("0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2")

        val result = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = receiverAddress,
            contractAddress = KnownTokens.USDC.ethAddress,
        )

        assertEquals(
            "USDC URI should follow ERC-20 transfer format without amount parameters",
            "ethereum:0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48/transfer?address=0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2",
            result,
        )
    }

    @Test
    fun `generateReceiveQrPayload should generate USDC URI with correct minimal units`() {
        val receiverAddress = createTestAddress("0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2")
        val amount = BigDecimal("100.5")

        val result = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = receiverAddress,
            contractAddress = KnownTokens.USDC.ethAddress,
            amount = amount,
        )

        assertEquals(
            "USDC URI should convert 100.5 USDC to 100500000 minimal units (6 decimals)",
            "ethereum:0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48/transfer?address=0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2&uint256=100500000",
            result,
        )
    }

    @Test
    fun `generateReceiveQrPayload should generate USDT URI with correct minimal units`() {
        val receiverAddress = createTestAddress("0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2")
        val amount = BigDecimal("50.123456")

        val result = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = receiverAddress,
            contractAddress = KnownTokens.USDT.ethAddress,
            amount = amount,
        )

        assertEquals(
            "USDT URI should convert 50.123456 USDT to 50123456 minimal units (6 decimals)",
            "ethereum:0xdAC17F958D2ee523a2206206994597C13D831ec7/transfer?address=0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2&uint256=50123456",
            result,
        )
    }

    @Test
    fun `generateReceiveQrPayload should generate DAI URI with correct minimal units`() {
        val receiverAddress = createTestAddress("0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2")
        val amount = BigDecimal("2.5")

        val result = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = receiverAddress,
            contractAddress = KnownTokens.DAI.ethAddress,
            amount = amount,
        )

        assertEquals(
            "DAI URI should convert 2.5 DAI to 2500000000000000000 minimal units (18 decimals)",
            "ethereum:0x6B175474E89094C44Da98b954EedeAC495271d0F/transfer?address=0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2&uint256=2500000000000000000",
            result,
        )
    }

    @Test
    fun `generateReceiveQrPayload should handle fractional amounts with proper rounding for USDC`() {
        val receiverAddress = createTestAddress("0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2")
        val amount = BigDecimal("1.1234567")

        val result = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = receiverAddress,
            contractAddress = KnownTokens.USDC.ethAddress,
            amount = amount,
        )

        assertTrue(
            "USDC URI should round 1.1234567 to 1123457 minimal units (6 decimals, rounded up)",
            result.contains("uint256=1123457"),
        )
    }

    @Test
    fun `generateReceiveQrPayload should handle fractional amounts with proper rounding for USDT`() {
        val receiverAddress = createTestAddress("0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2")
        val amount = BigDecimal("1.1234564")

        val result = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = receiverAddress,
            contractAddress = KnownTokens.USDT.ethAddress,
            amount = amount,
        )

        assertTrue(
            "USDT URI should round 1.1234564 to 1123456 minimal units (6 decimals, rounded down)",
            result.contains("uint256=1123456"),
        )
    }

    @Test
    fun `generateReceiveQrPayload should handle unknown token with default 18 decimals`() {
        val receiverAddress = createTestAddress("0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2")
        val unknownTokenAddress = createTestAddress("0x1234567890123456789012345678901234567890")
        val amount = BigDecimal("1.0")

        val result = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = receiverAddress,
            contractAddress = unknownTokenAddress,
            amount = amount,
        )

        assertTrue(
            "Unknown token should default to 18 decimals, converting 1.0 to 1000000000000000000 minimal units",
            result.contains("uint256=1000000000000000000"),
        )
    }

    @Test
    fun `generateReceiveQrPayload should handle zero amounts correctly`() {
        val receiverAddress = createTestAddress("0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2")
        val amount = BigDecimal.ZERO

        val ethResult = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = receiverAddress,
            contractAddress = EthContractAddress,
            amount = amount,
        )

        val usdcResult = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = receiverAddress,
            contractAddress = KnownTokens.USDC.ethAddress,
            amount = amount,
        )

        assertEquals(
            "ETH URI should handle zero amount correctly",
            "ethereum:0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2?value=0",
            ethResult,
        )

        assertTrue(
            "USDC URI should handle zero amount correctly",
            usdcResult.contains("uint256=0"),
        )
    }

    @Test
    fun `generateReceiveQrPayload should handle very large amounts without overflow`() {
        val receiverAddress = createTestAddress("0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2")
        val largeAmount = BigDecimal("999999999.999999")

        val result = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = receiverAddress,
            contractAddress = KnownTokens.USDC.ethAddress,
            amount = largeAmount,
        )

        assertTrue(
            "Large USDC amount should be converted correctly to minimal units",
            result.contains("uint256=999999999999999"),
        )
    }

    @Test
    fun `generateReceiveQrPayload should preserve address case correctly`() {
        val mixedCaseAddress = createTestAddress("0xaBcDeFaBcDeFaBcDeFaBcDeFaBcDeFaBcDeFaBcD")

        val ethResult = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = mixedCaseAddress,
            contractAddress = EthContractAddress,
        )

        val usdcResult = ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = mixedCaseAddress,
            contractAddress = KnownTokens.USDC.ethAddress,
        )

        assertTrue(
            "ETH URI should preserve original address case",
            ethResult.contains("0xaBcDeFaBcDeFaBcDeFaBcDeFaBcDeFaBcDeFaBcD"),
        )

        assertTrue(
            "USDC URI should preserve original receiver address case",
            usdcResult.contains("address=0xaBcDeFaBcDeFaBcDeFaBcDeFaBcDeFaBcDeFaBcD"),
        )
    }

    private fun createTestAddress(addressString: String): EthAddress {
        return EthAddress(addressString)
    }
}
