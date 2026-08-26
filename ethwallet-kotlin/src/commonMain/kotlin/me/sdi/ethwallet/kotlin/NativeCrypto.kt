package me.sdi.ethwallet.kotlin

internal expect object NativeCrypto {
    fun createMnemonic(): CharArray

    fun validateMnemonic(mnemonic: ByteArray)

    fun derivePrivateKey(mnemonic: ByteArray): ByteArray

    fun deriveAddress(privateKey: ByteArray): ByteArray
}

internal const val NATIVE_MNEMONIC_MAXIMUM_LENGTH = 107
internal const val NATIVE_PRIVATE_KEY_LENGTH = 32
internal const val NATIVE_ADDRESS_LENGTH = 20
