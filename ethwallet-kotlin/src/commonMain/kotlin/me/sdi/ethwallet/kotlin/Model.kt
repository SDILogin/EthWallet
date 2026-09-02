package me.sdi.ethwallet.kotlin

import kotlin.jvm.JvmInline

internal val HEX_NUMBER_NO_LEADING_ZEROS_64_SYMBOLS_MAX = Regex("^0x(0|[1-9a-f][0-9a-f]{0,63})$")
internal val HEX_STRING_40_SYMBOLS_EXACTLY = Regex("^0x[0-9a-fA-F]{40}$")
internal val HEX_STRING_64_SYMBOLS_EXACTLY = Regex("^0x[0-9a-f]{64}$")
private const val MAX_BIP32_CHILD_INDEX = 0x7fff_ffffu

internal fun requireValidEthAddress(value: String, fieldName: String = "Ethereum address") {
    require(value.matches(HEX_STRING_40_SYMBOLS_EXACTLY)) {
        "$fieldName must be 0x followed by 40 hex symbols, got '$value'"
    }
}

fun interface JsonRpcTransport {
    suspend fun execute(requestJson: String): String
}

class EthereumNetwork(
    val chainId: Long,
    val transport: JsonRpcTransport,
)

data class EthereumDerivationPath(
    val accountIndex: UInt = 0u,
    val addressIndex: UInt = 0u,
) {
    init {
        require(accountIndex <= MAX_BIP32_CHILD_INDEX) {
            "BIP-44 account index must not exceed $MAX_BIP32_CHILD_INDEX"
        }
        require(addressIndex <= MAX_BIP32_CHILD_INDEX) {
            "BIP-44 address index must not exceed $MAX_BIP32_CHILD_INDEX"
        }
    }

    override fun toString(): String = "m/44'/60'/$accountIndex'/0/$addressIndex"
}

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
value class TransactionHash(
    val value: String,
)

sealed interface Asset {
    data object Eth : Asset

    data class Erc20(
        val contractAddress: String,
    ) : Asset {
        init {
            requireValidEthAddress(contractAddress, "ERC-20 contract address")
        }
    }
}

data class Eip1559FeeSuggestion(
    val baseFeePerGas: UInt256,
    val priorityFeePerGas: UInt256,
)

data class TransferRequest(
    val destination: String,
    val asset: Asset,
    val amount: UInt256,
    val gasUnits: UInt256,
    val maxPriorityFeePerGas: UInt256,
    val maxFeePerGas: UInt256,
) {
    init {
        requireValidEthAddress(destination, "Transfer destination")
    }
}
