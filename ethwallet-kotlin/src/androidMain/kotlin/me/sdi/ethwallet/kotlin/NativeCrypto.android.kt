package me.sdi.ethwallet.kotlin

internal actual object NativeCrypto {
    private val nativeLibrary = lazy {
        try {
            System.loadLibrary("ethwallet_crypto")
        } catch (error: UnsatisfiedLinkError) {
            throw IllegalStateException("Unable to load ethwallet_crypto", error)
        }
        checkAbiVersion()
    }

    actual fun createMnemonic(): CharArray {
        ensureNativeLibrary()
        val output = ByteArray(NATIVE_MNEMONIC_MAXIMUM_LENGTH)
        val length = IntArray(1)
        try {
            checkNativeStatus(nativeCreateMnemonic(output, length))
            require(length[0] in 1..output.size) { "Native mnemonic length is invalid" }
            return CharArray(length[0]) { output[it].toInt().toChar() }
        } finally {
            output.fill(0)
        }
    }

    actual fun validateMnemonic(mnemonic: ByteArray) {
        ensureNativeLibrary()
        checkNativeStatus(nativeValidateMnemonic(mnemonic))
    }

    actual fun derivePrivateKey(mnemonic: ByteArray): ByteArray = nativeOutput(NATIVE_PRIVATE_KEY_LENGTH) { output ->
        ensureNativeLibrary()
        nativeDerivePrivateKey(mnemonic, output)
    }

    actual fun deriveAddress(privateKey: ByteArray): ByteArray = nativeOutput(NATIVE_ADDRESS_LENGTH) { output ->
        ensureNativeLibrary()
        requireNativePrivateKeyLength(privateKey)
        nativeDeriveAddress(privateKey, output)
    }

    private fun ensureNativeLibrary() {
        nativeLibrary.value
    }

    private fun checkAbiVersion() {
        check(nativeAbiVersion() == NATIVE_ABI_VERSION) { "Unsupported ethwallet_crypto ABI" }
    }

    @JvmStatic
    private external fun nativeAbiVersion(): Int

    @JvmStatic
    private external fun nativeCreateMnemonic(
        output: ByteArray,
        length: IntArray,
    ): Int

    @JvmStatic
    private external fun nativeValidateMnemonic(input: ByteArray): Int

    @JvmStatic
    private external fun nativeDerivePrivateKey(
        mnemonic: ByteArray,
        output: ByteArray,
    ): Int

    @JvmStatic
    private external fun nativeDeriveAddress(
        privateKey: ByteArray,
        output: ByteArray,
    ): Int
}
