package me.sdi.ethwallet.core.transfer

/**
 * EIP-1559 fee components fetched from the node, expressed per gas unit.
 */
data class FeeSuggestion(
    val baseFee: Wei,
    val priorityFee: Wei,
)
