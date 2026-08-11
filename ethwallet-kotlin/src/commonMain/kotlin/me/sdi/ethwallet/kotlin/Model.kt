package me.sdi.ethwallet.kotlin

import kotlin.jvm.JvmInline

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
)

@JvmInline
value class EthAddress(
    val value: String,
)

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
