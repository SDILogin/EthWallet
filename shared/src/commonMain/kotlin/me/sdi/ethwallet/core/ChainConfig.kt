package me.sdi.ethwallet.core

/**
 * Runtime blockchain connection configuration passed from platform startup into shared DI.
 *
 * @property rpcUrl Ethereum JSON-RPC endpoint URL used by Web3j.
 * @property chainId EVM chain ID used for transaction signing and chain-specific behavior.
 * @property explorerBaseUrl Optional base URL of a block explorer for this chain.
 */
data class ChainConfig(
    val rpcUrl: String,
    val chainId: Long,
    val explorerBaseUrl: String?,
)
