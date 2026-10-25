package me.sdi.ethwallet.kotlin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.encodeToJsonElement

private const val LATEST_BLOCK = "latest"
private const val BALANCE_OF_SELECTOR = "0x70a08231"
private const val TRANSFER_SELECTOR = "0xa9059cbb"
private const val ABI_WORD_HEX_LENGTH = 64
private const val FEE_HISTORY_BLOCK_COUNT = 5
private const val FEE_HISTORY_BLOCK_COUNT_QUANTITY = "0x5"
private const val FEE_HISTORY_REWARD_PERCENTILE = 50.0
private const val FEE_HISTORY_REWARD_PERCENTILE_COUNT = 1
private const val PENDING_BLOCK = "pending"

/**
 * Stateless entry point for Ethereum wallet operations.
 *
 * Wallet creation and key derivation are implemented by the platform-native Rust library.
 * Network operations go through the caller's [JsonRpcTransport].
 */
object EthWalletKotlin {
    suspend fun createWallet(): Mnemonic = withContext(Dispatchers.Default) {
        Mnemonic.fromNativeCharacters(NativeCrypto.createMnemonic())
    }

    suspend fun getBalance(
        network: EthereumNetwork,
        owner: String,
        asset: Asset,
    ): UInt256 {
        requireValidEthAddress(owner, "Balance owner")
        val normalizedOwner = owner.lowercase()
        return when (asset) {
            Asset.Eth -> {
                network.transport.call(
                    method = "eth_getBalance",
                    params = buildJsonArray {
                        add(normalizedOwner)
                        add(LATEST_BLOCK)
                    },
                ).asQuantity()
            }
            is Asset.Erc20 -> {
                network.transport.call(
                    method = "eth_call",
                    params = buildJsonArray {
                        val abiWordDigits = normalizedOwner.substring(2).padStart(ABI_WORD_HEX_LENGTH, '0')
                        add(
                            jsonRpcJson.encodeToJsonElement(
                                CallRequest(
                                    to = asset.contractAddress.lowercase(),
                                    data = BALANCE_OF_SELECTOR + abiWordDigits,
                                ),
                            ),
                        )
                        add(LATEST_BLOCK)
                    },
                ).asAbiWord()
            }
        }
    }

    suspend fun getFeeSuggestion(network: EthereumNetwork): Eip1559FeeSuggestion {
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

        return Eip1559FeeSuggestion(
            baseFeePerGas = feeHistory.nextBaseFeePerGas,
            priorityFeePerGas = priorityFeePerGas,
        )
    }

    suspend fun sendTransfer(
        network: EthereumNetwork,
        privateKey: PrivateKey,
        request: TransferRequest,
    ): TransactionHash {
        val sender = privateKey.deriveAddress()
        val nonce = network.transport.call(
            method = "eth_getTransactionCount",
            params = buildJsonArray {
                add(sender)
                add(PENDING_BLOCK)
            },
        ).asQuantity()
        val transaction = request.toEip1559Transaction(network.chainId, nonce)
        val signedTransaction = privateKey.signTransaction(transaction)
        val rawTransaction = "0x${signedTransaction.toHexString()}"

        return network.transport.call(
            method = "eth_sendRawTransaction",
            params = buildJsonArray { add(rawTransaction) },
        ).asTransactionHash()
    }
}

private fun TransferRequest.toEip1559Transaction(
    chainId: Long,
    nonce: UInt256,
): Eip1559TransactionPayload {
    val (destination, value, data) = when (val asset = asset) {
        Asset.Eth -> Triple(destination, amount, "0x")
        is Asset.Erc20 -> Triple(
            asset.contractAddress,
            UInt256("0x0"),
            TRANSFER_SELECTOR + destination.substring(2).padStart(ABI_WORD_HEX_LENGTH, '0') +
                amount.hex.substring(2).padStart(ABI_WORD_HEX_LENGTH, '0'),
        )
    }
    return Eip1559TransactionPayload(
        chainId = chainId,
        nonce = nonce,
        maxPriorityFeePerGas = maxPriorityFeePerGas,
        maxFeePerGas = maxFeePerGas,
        gasLimit = gasUnits,
        destination = destination,
        value = value,
        data = data,
    )
}

/**
 * Derives the first Ethereum key. The passphrase is case-sensitive and is not trimmed.
 * Rust applies BIP-39 NFKD normalization. The caller owns the passphrase String;
 * temporary UTF-8 buffers are cleared, but Strings cannot be cleared.
 */
suspend fun Mnemonic.derivePrivateKey(passphrase: String = ""): PrivateKey =
    derivePrivateKey(EthereumDerivationPath(), passphrase)

suspend fun Mnemonic.derivePrivateKey(
    derivationPath: EthereumDerivationPath,
    passphrase: String = "",
): PrivateKey = withContext(Dispatchers.Default) {
    requireUsable()
    val passphraseBytes = passphrase.encodeToByteArray(throwOnInvalidSequence = true)
    try {
        val mnemonicBytes = copyAsciiBytes()
        try {
            PrivateKey.fromNativeBytes(
                NativeCrypto.derivePrivateKey(
                    mnemonic = mnemonicBytes,
                    passphrase = passphraseBytes,
                    accountIndex = derivationPath.accountIndex,
                    addressIndex = derivationPath.addressIndex,
                ),
            )
        } finally {
            mnemonicBytes.fill(0)
        }
    } finally {
        passphraseBytes.fill(0)
    }
}

suspend fun PrivateKey.deriveAddress(): String = withContext(Dispatchers.Default) {
    requireUsable()
    val privateKeyBytes = copyBytes()
    try {
        val address = NativeCrypto.deriveAddress(privateKeyBytes)
        try {
            "0x${address.toHexString()}"
        } finally {
            address.fill(0)
        }
    } finally {
        privateKeyBytes.fill(0)
    }
}
