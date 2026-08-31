package me.sdi.ethwallet.kotlin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.encodeToJsonElement

private const val LATEST_BLOCK = "latest"
private const val BALANCE_OF_SELECTOR = "0x70a08231"
private const val ABI_WORD_HEX_LENGTH = 64
private const val FEE_HISTORY_BLOCK_COUNT = 5
private const val FEE_HISTORY_BLOCK_COUNT_QUANTITY = "0x5"
private const val FEE_HISTORY_REWARD_PERCENTILE = 50.0
private const val FEE_HISTORY_REWARD_PERCENTILE_COUNT = 1

/**
 * Stateless entry point for Ethereum wallet operations.
 *
 * Wallet creation and key derivation are implemented by the platform-native Rust library.
 * Balance reads and fee suggestions go through the caller's [JsonRpcTransport]. Transfer
 * submission remains a placeholder.
 */
object EthWalletKotlin {
    suspend fun createWallet(): Mnemonic = withContext(Dispatchers.Default) {
        Mnemonic.fromNativeCharacters(NativeCrypto.createMnemonic())
    }

    suspend fun getBalance(
        network: EthereumNetwork,
        owner: EthAddress,
        asset: Asset,
    ): UInt256 = when (asset) {
        Asset.Eth -> {
            network.transport.call(
                method = "eth_getBalance",
                params = buildJsonArray {
                    add(owner.value)
                    add(LATEST_BLOCK)
                },
            ).asQuantity()
        }
        is Asset.Erc20 -> {
            network.transport.call(
                method = "eth_call",
                params = buildJsonArray {
                    val abiWordDigits = owner.value.substring(2).padStart(ABI_WORD_HEX_LENGTH, '0')
                    add(
                        jsonRpcJson.encodeToJsonElement(
                            CallRequest(
                                to = asset.contractAddress.value,
                                data = BALANCE_OF_SELECTOR + abiWordDigits,
                            ),
                        ),
                    )
                    add(LATEST_BLOCK)
                },
            ).asAbiWord()
        }
    }

    suspend fun getFeeSuggestion(network: EthereumNetwork): FeeSuggestion {
        val feeHistory = network.transport.call(
            method = "eth_feeHistory",
            params = buildJsonArray {
                add(FEE_HISTORY_BLOCK_COUNT_QUANTITY)
                add(LATEST_BLOCK)
                addJsonArray {
                    add(FEE_HISTORY_REWARD_PERCENTILE)
                }
            },
        ).asFeeHistoryResult(
            maximumBlockCount = FEE_HISTORY_BLOCK_COUNT,
            rewardPercentileCount = FEE_HISTORY_REWARD_PERCENTILE_COUNT,
        )

        val rewards = feeHistory.rewards
            .filter { it.hex != "0x0" }
            .sortedWith(compareBy({ it.hex.length }, { it.hex }))

        val priorityFeePerGas = if (rewards.isEmpty()) {
            network.transport.call(method = "eth_maxPriorityFeePerGas", params = buildJsonArray {}).asQuantity()
        } else {
            rewards[rewards.size / 2]
        }

        return FeeSuggestion(
            baseFeePerGas = feeHistory.nextBaseFeePerGas,
            priorityFeePerGas = priorityFeePerGas,
        )
    }

    suspend fun sendTransfer(
        network: EthereumNetwork,
        privateKey: PrivateKey,
        request: TransferRequest,
    ): TransactionHash = withContext(Dispatchers.IO) {
        throw NotImplementedError("EthWalletKotlin skeleton")
    }
}

suspend fun Mnemonic.derivePrivateKey(): PrivateKey = withContext(Dispatchers.Default) {
    requireUsable()
    val mnemonicBytes = copyAsciiBytes()
    try {
        PrivateKey.fromNativeBytes(NativeCrypto.derivePrivateKey(mnemonicBytes))
    } finally {
        mnemonicBytes.fill(0)
    }
}

suspend fun PrivateKey.deriveAddress(): EthAddress = withContext(Dispatchers.Default) {
    requireUsable()
    val privateKeyBytes = copyBytes()
    try {
        val address = NativeCrypto.deriveAddress(privateKeyBytes)
        try {
            EthAddress("0x${address.toHexString()}")
        } finally {
            address.fill(0)
        }
    } finally {
        privateKeyBytes.fill(0)
    }
}
