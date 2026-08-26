package me.sdi.ethwallet.kotlin

internal const val NATIVE_ABI_VERSION = 1
internal const val STATUS_OK = 0
internal const val STATUS_INVALID_MNEMONIC = 1
internal const val STATUS_INVALID_PRIVATE_KEY = 2
internal const val STATUS_INSUFFICIENT_BUFFER = 3
internal const val STATUS_INVALID_ARGUMENT = 4
internal const val STATUS_ENTROPY_FAILURE = 5
internal const val STATUS_INTERNAL_PANIC = 6

internal fun checkNativeStatus(status: Int) {
    when (status) {
        STATUS_OK -> Unit
        STATUS_INVALID_MNEMONIC -> throw IllegalArgumentException("Invalid BIP-39 mnemonic")
        STATUS_INVALID_PRIVATE_KEY -> throw IllegalArgumentException("Invalid secp256k1 private key")
        STATUS_INSUFFICIENT_BUFFER,
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
