package me.sdi.ethwallet.core.security

import com.sun.jna.Library
import com.sun.jna.Native
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

private const val STATUS_OK = 0
private const val WRAPPING_OVERHEAD_BYTES = 93
private const val WRAPPING_KEY_CAPACITY = 1024
private const val LIBRARY_FILE_NAME = "libethwallet_secure_enclave.dylib"
private const val LIBRARY_RESOURCE_PATH = "/native/macos/aarch64/$LIBRARY_FILE_NAME"

/**
 * [MasterKekProtection] backed by a Secure Enclave P-256 key (CryptoKit, through the bundled Swift library).
 *
 * The wrapping key handle is the Secure Enclave `dataRepresentation`: only this Mac's Secure Enclave can use it, so
 * a wrapped payload copied to another Mac cannot be unwrapped. The key needs no user presence: any process of this
 * user on this Mac can unwrap. Wrapping is ECDH with an ephemeral key, HKDF-SHA256 and AES-256-GCM; the wrapped form
 * is 93 bytes longer than the payload.
 */
class MasterKekProtectionSecureEnclave : MasterKekProtection {
    private val library by lazy(::loadSecureEnclaveLibrary)

    override suspend fun createWrappingKey(): ByteArray = withContext(Dispatchers.IO) {
        val output = ByteArray(WRAPPING_KEY_CAPACITY)
        val outputLength = IntArray(1)
        checkStatus(
            library.ethwalletSecureEnclaveCreateKey(
                output = output,
                outputCapacity = output.size,
                outputLength = outputLength,
            ),
        )
        output.copyOf(outputLength[0])
    }

    override suspend fun wrap(wrappingKey: ByteArray, payload: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        val output = ByteArray(payload.size + WRAPPING_OVERHEAD_BYTES)
        checkStatus(
            library.ethwalletSecureEnclaveWrap(
                key = wrappingKey,
                keyLength = wrappingKey.size,
                payload = payload,
                payloadLength = payload.size,
                output = output,
            ),
        )
        output
    }

    override suspend fun unwrap(wrappingKey: ByteArray, wrapped: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        require(wrapped.size > WRAPPING_OVERHEAD_BYTES) { "Wrapped payload is too short" }
        val output = ByteArray(wrapped.size - WRAPPING_OVERHEAD_BYTES)
        checkStatus(
            library.ethwalletSecureEnclaveUnwrap(
                key = wrappingKey,
                keyLength = wrappingKey.size,
                wrapped = wrapped,
                wrappedLength = wrapped.size,
                output = output,
            ),
        )
        output
    }

    private fun checkStatus(status: Int) {
        check(status == STATUS_OK) { "Secure Enclave operation failed with status $status" }
    }
}

private interface SecureEnclaveLibrary : Library {
    fun ethwalletSecureEnclaveCreateKey(output: ByteArray, outputCapacity: Int, outputLength: IntArray): Int

    fun ethwalletSecureEnclaveWrap(
        key: ByteArray,
        keyLength: Int,
        payload: ByteArray,
        payloadLength: Int,
        output: ByteArray,
    ): Int

    fun ethwalletSecureEnclaveUnwrap(
        key: ByteArray,
        keyLength: Int,
        wrapped: ByteArray,
        wrappedLength: Int,
        output: ByteArray,
    ): Int
}

private fun loadSecureEnclaveLibrary(): SecureEnclaveLibrary {
    val supportedHost = System.getProperty("os.name").equals("Mac OS X", ignoreCase = true) &&
        System.getProperty("os.arch").lowercase() in setOf("aarch64", "arm64")
    if (supportedHost) {
        return Native.load(extractLibrary().absolutePath, SecureEnclaveLibrary::class.java)
    } else {
        throw UnsupportedOperationException("Secure Enclave KEK protection requires Apple Silicon macOS")
    }
}

private fun extractLibrary(): File {
    val resource = checkNotNull(MasterKekProtectionSecureEnclave::class.java.getResourceAsStream(LIBRARY_RESOURCE_PATH)) {
        "Missing packaged native library: $LIBRARY_RESOURCE_PATH"
    }
    val directory = Files.createTempDirectory("ethwallet-secure-enclave-").toFile().apply { deleteOnExit() }
    return File(directory, LIBRARY_FILE_NAME).apply {
        deleteOnExit()
        resource.use { input -> outputStream().use(input::copyTo) }
    }
}
