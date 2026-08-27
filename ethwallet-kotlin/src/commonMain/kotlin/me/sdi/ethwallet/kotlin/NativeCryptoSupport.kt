package me.sdi.ethwallet.kotlin

internal const val NATIVE_ABI_VERSION = 1
internal const val STATUS_OK = 0
internal const val STATUS_INVALID_MNEMONIC = 1
internal const val STATUS_INVALID_PRIVATE_KEY = 2
internal const val STATUS_INVALID_ARGUMENT = 4
internal const val STATUS_ENTROPY_FAILURE = 5
internal const val STATUS_INTERNAL_PANIC = 6
internal const val STATUS_INVALID_TRANSACTION = 7

internal fun checkNativeStatus(status: Int) {
    when (status) {
        STATUS_OK -> Unit
        STATUS_INVALID_MNEMONIC -> throw IllegalArgumentException("Invalid BIP-39 mnemonic")
        STATUS_INVALID_PRIVATE_KEY -> throw IllegalArgumentException("Invalid secp256k1 private key")
        STATUS_INVALID_TRANSACTION -> throw IllegalArgumentException("Invalid EIP-1559 transaction")
        STATUS_INVALID_ARGUMENT,
        STATUS_ENTROPY_FAILURE,
        STATUS_INTERNAL_PANIC,
        -> throw IllegalStateException("ethwallet_crypto failed with status $status")
        else -> throw IllegalStateException("ethwallet_crypto returned unknown status $status")
    }
}

internal fun nativeOutput(
    size: Int,
    operation: (ByteArray) -> Int,
): ByteArray {
    val output = ByteArray(size)
    var accepted = false
    try {
        checkNativeStatus(operation(output))
        accepted = true
        return output
    } finally {
        if (!accepted) {
            output.fill(0)
        }
    }
}

internal fun requireNativePrivateKeyLength(privateKey: ByteArray) {
    require(privateKey.size == NATIVE_PRIVATE_KEY_LENGTH) {
        "Ethereum private keys must contain $NATIVE_PRIVATE_KEY_LENGTH bytes"
    }
}

internal fun requireNativeTransactionFieldLengths(
    nonce: ByteArray,
    maxPriorityFeePerGas: ByteArray,
    maxFeePerGas: ByteArray,
    gasLimit: ByteArray,
    destination: ByteArray,
    value: ByteArray,
) {
    require(nonce.size == 32) { "Nonce must contain 32 bytes" }
    require(maxPriorityFeePerGas.size == 32) { "Priority fee must contain 32 bytes" }
    require(maxFeePerGas.size == 32) { "Maximum fee must contain 32 bytes" }
    require(gasLimit.size == 32) { "Gas limit must contain 32 bytes" }
    require(destination.size == NATIVE_ADDRESS_LENGTH) { "Destination must contain 20 bytes" }
    require(value.size == 32) { "Value must contain 32 bytes" }
}

internal fun nativeSignedTransactionCapacity(dataLength: Int): Int {
    val capacity = dataLength.toLong() + NATIVE_SIGNED_TRANSACTION_CAPACITY_OVERHEAD
    check(capacity <= Int.MAX_VALUE) { "Signed transaction capacity is too large" }
    return capacity.toInt()
}
