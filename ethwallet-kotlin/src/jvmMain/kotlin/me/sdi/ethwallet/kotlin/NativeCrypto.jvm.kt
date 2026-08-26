package me.sdi.ethwallet.kotlin

import java.io.File
import java.nio.file.Files

internal actual object NativeCrypto {
    private val nativeLibrary = lazy {
        loadMacOsLibrary()
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

    private fun loadMacOsLibrary() {
        if (!System.getProperty("os.name").equals("Mac OS X", ignoreCase = true)) {
            throw UnsupportedOperationException("ethwallet_crypto JVM support is currently macOS-only")
        }

        val architecture = when (System.getProperty("os.arch").lowercase()) {
            "aarch64", "arm64" -> "aarch64"
            "x86_64", "amd64" -> "x86_64"
            else -> throw UnsupportedOperationException("Unsupported macOS architecture")
        }
        val resourcePath = "/native/macos/$architecture/libethwallet_crypto.dylib"
        val resource = checkNotNull(NativeCrypto::class.java.getResourceAsStream(resourcePath)) {
            "Missing packaged native library: $resourcePath"
        }
        val directory = Files.createTempDirectory("ethwallet-crypto-").toFile().apply {
            deleteOnExit()
            setReadable(true, true)
            setWritable(true, true)
            setExecutable(true, true)
        }
        val library = File(directory, "libethwallet_crypto.dylib").apply {
            deleteOnExit()
            resource.use { input -> outputStream().use(input::copyTo) }
            setReadable(true, true)
            setExecutable(true, true)
        }
        try {
            System.load(library.absolutePath)
        } catch (error: UnsatisfiedLinkError) {
            throw IllegalStateException("Unable to load ethwallet_crypto", error)
        }
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
