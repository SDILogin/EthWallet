package me.sdi.ethwallet.kotlin

import kotlin.jvm.JvmInline

internal val HEX_NUMBER_NO_LEADING_ZEROS_64_SYMBOLS_MAX = Regex("^0x(0|[1-9a-f][0-9a-f]{0,63})$")
internal val HEX_STRING_40_SYMBOLS_EXACTLY = Regex("^0x[0-9a-fA-F]{40}$")
internal val HEX_STRING_64_SYMBOLS_EXACTLY = Regex("^0x[0-9a-f]{64}$")

fun interface JsonRpcTransport {
    suspend fun execute(requestJson: String): String
}

class EthereumNetwork(
    val chainId: Long,
    val transport: JsonRpcTransport,
)

@JvmInline
value class UInt256(
    val hex: String,
) {
    init {
        require(hex.matches(HEX_NUMBER_NO_LEADING_ZEROS_64_SYMBOLS_MAX)) {
            "UInt256 must be 0x-prefixed lowercase hex without leading zeros, got '$hex'"
        }
    }
}

@JvmInline
value class EthAddress private constructor(
    val value: String,
) {
    companion object {
        operator fun invoke(value: String): EthAddress {
            require(value.matches(HEX_STRING_40_SYMBOLS_EXACTLY)) {
                "Ethereum address must be 0x followed by 40 hex symbols, got '$value'"
            }
            return EthAddress(value.lowercase())
        }
    }
}

@JvmInline
value class TransactionHash(
    val value: String,
)

sealed interface Asset {
    data object Eth : Asset

    data class Erc20(
        val contractAddress: EthAddress,
    ) : Asset
}

data class FeeSuggestion(
    val baseFeePerGas: UInt256,
    val priorityFeePerGas: UInt256,
)

data class TransferRequest(
    val destination: EthAddress,
    val asset: Asset,
    val amount: UInt256,
    val gasUnits: UInt256,
    val maxPriorityFeePerGas: UInt256,
    val maxFeePerGas: UInt256,
)
