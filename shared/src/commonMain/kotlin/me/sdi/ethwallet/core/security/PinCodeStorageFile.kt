package me.sdi.ethwallet.core.security

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.data.UserActivityManager
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermissions
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Properties
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.io.encoding.Base64

/**
 * [PinCodeStorage] backed by a plain file, for targets without a hardware-backed key store.
 *
 * A `pinKey` derived from the PIN (PBKDF2WithHmacSHA256) wraps a random 256-bit master KEK with
 * software AES-256-GCM ([KekCipher]). The KDF salt and that wrapped blob are the only artifacts
 * persisted; no PIN hash is ever stored. Verification is decrypt-by-unwrap: a successful GCM unwrap
 * both proves the PIN and yields the KEK, which is published to the [PinSession] for the session.
 *
 * With no hardware layer to bind the envelope to the machine, an attacker who copies the artifacts
 * file can brute force the PIN offline at the cost of the KDF alone. That is weaker than the Android
 * storage, which wraps the same blob again with an AndroidKeyStore key.
 */
class PinCodeStorageFile(
    private val artifactsFile: File,
    private val userActivityManager: UserActivityManager,
    private val pinAttemptsGuard: PinAttemptsGuard,
    private val pinSession: PinSession,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PinCodeStorage {
    override suspend fun storePinCode(pinCode: String) {
        pinAttemptsGuard.bootstrap()

        val salt = randomBytes(SALT_LENGTH)
        val masterKek = randomBytes(MASTER_KEK_LENGTH)
        val pinKey = deriveKey(pinCode, salt)
        val wrappedKek = try {
            KekCipher.encrypt(pinKey, masterKek)
        } finally {
            pinKey.fill(0)
        }

        writeArtifacts(salt, wrappedKek)

        pinSession.set(masterKek)
        Logger.debug(TAG) { "PIN code stored successfully" }
    }

    override suspend fun verifyPinCode(pinCode: String): Boolean {
        val artifacts = readArtifacts()
        if (artifacts == null) {
            Logger.debug(TAG) { "No PIN code stored for verification" }
            return false
        }

        var pinKey: ByteArray? = null
        return try {
            pinKey = deriveKey(pinCode, Base64.decode(artifacts.salt))
            val masterKek = KekCipher.decrypt(pinKey, Base64.decode(artifacts.wrappedKek))
            pinSession.set(masterKek)
            userActivityManager.onAppUnlocked()
            Logger.debug(TAG) { "PIN verification succeeded" }
            true
        } catch (e: GeneralSecurityException) {
            Logger.debug(TAG) { "PIN verification failed: ${e.message}" }
            false
        } catch (e: IllegalArgumentException) {
            Logger.debug(TAG) { "PIN envelope is malformed: ${e.message}" }
            false
        } finally {
            pinKey?.fill(0)
        }
    }

    override suspend fun hasPinCode(): Boolean = readArtifacts() != null

    override suspend fun clearPinCode() {
        withContext(dispatcher) {
            if (artifactsFile.exists() && !artifactsFile.delete()) {
                Logger.warn(TAG) { "Failed to delete the PIN artifacts file" }
            }
        }

        pinAttemptsGuard.clear()
        pinSession.clear()
    }

    private suspend fun randomBytes(size: Int): ByteArray = withContext(Dispatchers.Default) {
        ByteArray(size).also(SecureRandom()::nextBytes)
    }

    private suspend fun deriveKey(pinCode: String, salt: ByteArray): ByteArray =
        withContext(Dispatchers.Default) {
            val spec = PBEKeySpec(pinCode.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
            try {
                val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).also { ensureActive() }
                factory.generateSecret(spec).encoded.also { ensureActive() }
            } finally {
                spec.clearPassword()
            }
        }

    private suspend fun writeArtifacts(salt: ByteArray, wrappedKek: ByteArray) =
        withContext(dispatcher) {
            val properties = Properties()
            properties.setProperty(PIN_KDF_SALT, Base64.encode(salt))
            properties.setProperty(PIN_WRAPPED_KEK, Base64.encode(wrappedKek))

            val target = artifactsFile.toPath().toAbsolutePath()
            val directory = Files.createDirectories(target.parent)
            val pending = Files.createTempFile(directory, "pin", ".tmp", *ownerOnlyPermissions())
            try {
                Files.newOutputStream(pending).use { properties.store(it, null) }
                replace(pending, target)
            } finally {
                Files.deleteIfExists(pending)
            }
        }

    /**
     * Renames [pending] over [target] so a crash mid-write cannot leave a half-written envelope,
     * and so the permissions [pending] was created with become the permissions of the stored file.
     */
    private fun replace(pending: Path, target: Path) {
        try {
            Files.move(
                pending,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (e: AtomicMoveNotSupportedException) {
            Logger.warn(TAG, e) { "Atomic replace is unsupported here, falling back to a plain move" }
            Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun ownerOnlyPermissions(): Array<FileAttribute<*>> =
        if (FileSystems.getDefault().supportedFileAttributeViews().contains(POSIX_VIEW)) {
            arrayOf(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        } else {
            emptyArray()
        }

    private suspend fun readArtifacts(): PinArtifacts? = withContext(dispatcher) {
        if (!artifactsFile.exists()) {
            return@withContext null
        }

        val properties = Properties()
        try {
            artifactsFile.inputStream().use(properties::load)
        } catch (e: IOException) {
            Logger.warn(TAG, e) { "Failed to read the PIN artifacts file" }
            return@withContext null
        }

        val salt = properties.getProperty(PIN_KDF_SALT)
        val wrappedKek = properties.getProperty(PIN_WRAPPED_KEK)
        if (salt == null || wrappedKek == null) null else PinArtifacts(salt, wrappedKek)
    }

    private class PinArtifacts(val salt: String, val wrappedKek: String)

    private companion object {
        private const val TAG = "PinCodeStorageFile"
        private const val POSIX_VIEW = "posix"

        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 200_000
        private const val PBKDF2_KEY_BITS = 256
        private const val SALT_LENGTH = 16
        private const val MASTER_KEK_LENGTH = 32

        private const val PIN_KDF_SALT = "pin_kdf_salt"
        private const val PIN_WRAPPED_KEK = "pin_wrapped_kek"
    }
}
