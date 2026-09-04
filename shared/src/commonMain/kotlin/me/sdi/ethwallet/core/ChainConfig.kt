package me.sdi.ethwallet.core

/**
 * Runtime blockchain connection configuration passed from platform startup into shared DI.
 *
 * @property rpcUrl Ethereum JSON-RPC endpoint URL used by the Ethereum client.
 * @property chainId EVM chain ID used for transaction signing and chain-specific behavior.
 * @property explorerBaseUrl Optional base URL of a block explorer for this chain.
 * @property transactionHistoryApiBaseUrl Blockscout instance base URL used for transaction history.
 */
data class ChainConfig(
    val rpcUrl: String,
    val chainId: Long,
    val explorerBaseUrl: String?,
    val transactionHistoryApiBaseUrl: String,
)
