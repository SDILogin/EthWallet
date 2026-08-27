package me.sdi.ethwallet.kotlin

internal expect object NativeCrypto {
    fun createMnemonic(): CharArray

    fun validateMnemonic(mnemonic: ByteArray)

    fun derivePrivateKey(mnemonic: ByteArray): ByteArray

    fun deriveAddress(privateKey: ByteArray): ByteArray

    fun signEip1559Transaction(
        privateKey: ByteArray,
        chainId: Long,
        nonce: ByteArray,
        maxPriorityFeePerGas: ByteArray,
        maxFeePerGas: ByteArray,
        gasLimit: ByteArray,
        destination: ByteArray,
        value: ByteArray,
        data: ByteArray,
    ): ByteArray
}

internal const val NATIVE_MNEMONIC_MAXIMUM_LENGTH = 107
internal const val NATIVE_PRIVATE_KEY_LENGTH = 32
internal const val NATIVE_ADDRESS_LENGTH = 20
internal const val NATIVE_SIGNED_TRANSACTION_CAPACITY_OVERHEAD = 320
