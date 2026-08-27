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

    actual fun signEip1559Transaction(
        privateKey: ByteArray,
        chainId: Long,
        nonce: ByteArray,
        maxPriorityFeePerGas: ByteArray,
        maxFeePerGas: ByteArray,
        gasLimit: ByteArray,
        destination: ByteArray,
        value: ByteArray,
        data: ByteArray,
    ): ByteArray {
        ensureNativeLibrary()
        require(chainId > 0) { "EIP-1559 chain ID must be positive" }
        requireNativePrivateKeyLength(privateKey)
        requireNativeTransactionFieldLengths(
            nonce,
            maxPriorityFeePerGas,
            maxFeePerGas,
            gasLimit,
            destination,
            value,
        )
        val output = ByteArray(nativeSignedTransactionCapacity(data.size))
        val outputLength = IntArray(1)
        try {
            checkNativeStatus(
                nativeSignEip1559Transaction(
                    privateKey,
                    chainId,
                    nonce,
                    maxPriorityFeePerGas,
                    maxFeePerGas,
                    gasLimit,
                    destination,
                    value,
                    data,
                    output,
                    outputLength,
                ),
            )
            check(outputLength[0] in 1..output.size) { "Native signed transaction length is invalid" }
            return output.copyOf(outputLength[0])
        } finally {
            output.fill(0)
        }
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

    @JvmStatic
    private external fun nativeSignEip1559Transaction(
        privateKey: ByteArray,
        chainId: Long,
        nonce: ByteArray,
        maxPriorityFeePerGas: ByteArray,
        maxFeePerGas: ByteArray,
        gasLimit: ByteArray,
        destination: ByteArray,
        value: ByteArray,
        data: ByteArray,
        output: ByteArray,
        outputLength: IntArray,
    ): Int
}
