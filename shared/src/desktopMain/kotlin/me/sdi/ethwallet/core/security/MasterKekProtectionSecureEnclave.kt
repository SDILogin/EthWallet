package me.sdi.ethwallet.core.security

import com.sun.jna.Library
import com.sun.jna.Native
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

private const val STATUS_OK = 0
private const val STATUS_USER_CANCELLED = 4
private const val MASTER_KEK_BYTES = 32
private const val WRAPPED_MASTER_KEK_BYTES = 125
private const val WRAPPING_KEY_CAPACITY = 1024
private const val LIBRARY_FILE_NAME = "libethwallet_secure_enclave.dylib"
private const val LIBRARY_RESOURCE_PATH = "/native/macos/aarch64/$LIBRARY_FILE_NAME"

/**
 * [MasterKekProtection] backed by a Secure Enclave P-256 key (CryptoKit, through the bundled Swift library).
 *
 * The wrapping key handle is the Secure Enclave `dataRepresentation`: it is usable only by this Mac's Secure Enclave,
 * and its access control requires user presence (Touch ID or the macOS account password). [wrap] uses only the public
 * key (ECDH with an ephemeral key, HKDF-SHA256, AES-256-GCM) and never prompts. [unwrap] shows the system sheet and
 * blocks until the user answers it; cancelling the calling coroutine does not dismiss the sheet.
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

    override suspend fun wrap(wrappingKey: ByteArray, masterKek: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        require(masterKek.size == MASTER_KEK_BYTES) { "Master KEK must be $MASTER_KEK_BYTES bytes" }
        val output = ByteArray(WRAPPED_MASTER_KEK_BYTES)
        checkStatus(
            library.ethwalletSecureEnclaveWrap(
                key = wrappingKey,
                keyLength = wrappingKey.size,
                masterKek = masterKek,
                output = output,
            ),
        )
        output
    }

    override suspend fun unwrap(wrappingKey: ByteArray, wrappedMasterKek: ByteArray): ByteArray =
        withContext(Dispatchers.IO) {
            require(wrappedMasterKek.size == WRAPPED_MASTER_KEK_BYTES) {
                "Wrapped master KEK must be $WRAPPED_MASTER_KEK_BYTES bytes"
            }
            val output = ByteArray(MASTER_KEK_BYTES)
            val status = library.ethwalletSecureEnclaveUnwrap(
                key = wrappingKey,
                keyLength = wrappingKey.size,
                wrappedMasterKek = wrappedMasterKek,
                output = output,
            )
            if (status == STATUS_USER_CANCELLED) {
                throw UserPresenceCancelledException()
            } else {
                checkStatus(status)
            }
            output
        }

    private fun checkStatus(status: Int) {
        check(status == STATUS_OK) { "Secure Enclave operation failed with status $status" }
    }
}

private interface SecureEnclaveLibrary : Library {
    fun ethwalletSecureEnclaveCreateKey(output: ByteArray, outputCapacity: Int, outputLength: IntArray): Int

    fun ethwalletSecureEnclaveWrap(key: ByteArray, keyLength: Int, masterKek: ByteArray, output: ByteArray): Int

    fun ethwalletSecureEnclaveUnwrap(key: ByteArray, keyLength: Int, wrappedMasterKek: ByteArray, output: ByteArray): Int
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
