package me.sdi.ethwallet.utils

import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.ui.findKnownToken
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

object ReceiveUtils {
    /**
     * Generates QR code payload for receiving payments according to EIP-681 standard.
     *
     * @param receiverEthAddress The recipient's Ethereum address
     * @param contractAddress The token contract address (use EthContractAddress for ETH)
     * @param amount Optional amount to request (in human-readable units, e.g., 1.5 for 1.5 USDC)
     * @return URI string for QR code generation
     */
    fun generateReceiveQrPayload(
        receiverEthAddress: EthAddress,
        contractAddress: EthAddress,
        amount: BigDecimal? = null,
    ): String {
        return if (contractAddress == EthContractAddress) {
            generateEthPaymentUri(receiverEthAddress, amount)
        } else {
            generateErc20PaymentUri(receiverEthAddress, contractAddress, amount)
        }
    }

    private fun generateEthPaymentUri(
        receiverAddress: EthAddress,
        amount: BigDecimal?,
    ): String {
        val baseUri = "ethereum:$receiverAddress"
        return if (amount != null) {
            // Convert ETH amount to wei (18 decimals)
            val amountInWei = convertToMinimalUnits(amount, 18)
            "$baseUri?value=$amountInWei"
        } else {
            baseUri
        }
    }

    private fun generateErc20PaymentUri(
        receiverAddress: EthAddress,
        contractAddress: EthAddress,
        amount: BigDecimal?,
    ): String {
        val baseUri = "ethereum:$contractAddress/transfer?address=$receiverAddress"

        return if (amount != null) {
            val decimals = getTokenDecimals(contractAddress)
            val amountInMinimalUnits = convertToMinimalUnits(amount, decimals)
            "$baseUri&uint256=$amountInMinimalUnits"
        } else {
            baseUri
        }
    }

    private fun getTokenDecimals(contractAddress: EthAddress): Int {
        return findKnownToken(contractAddress)?.decimals ?: 18 // Default to 18 decimals for unknown tokens
    }

    private fun convertToMinimalUnits(amount: BigDecimal, decimals: Int): BigInteger {
        val multiplier = BigDecimal.TEN.pow(decimals)
        return amount.multiply(multiplier).setScale(0, RoundingMode.HALF_UP).toBigInteger()
    }
}
