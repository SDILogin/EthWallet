package me.sdi.ethwallet.core.security

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.util.preferencesDataStore
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Persistent [PinAttemptsGuard] that survives process death and detects filesystem rollback.
 *
 * Each state write rotates to a fresh AndroidKeyStore AES key (alias `pin_attempts_key_v{n}`).
 * Because the keystore lives outside the app's data files, restoring a snapshot of those files
 * leaves the referenced key version deleted, which surfaces as [PinLockState.Tampered]. Timing
 * relies solely on [SystemClock.elapsedRealtime] and `BOOT_COUNT`, never the wall clock, so
 * changing the system time cannot shorten a lock.
 */
class KeyStorePinAttemptsGuard(
    context: Context,
    private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() },
    private val bootCount: () -> Int = {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    },
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PinAttemptsGuard {
    private companion object {
        private const val TAG = "KeyStorePinAttemptsGuard"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ALIAS_PREFIX = "pin_attempts_key_"
        private const val ALIAS_VERSION_PREFIX = "pin_attempts_key_v"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128

        private val POINTER_KEY = intPreferencesKey("pin_attempts_pointer")
        private val BLOB_KEY = stringPreferencesKey("pin_attempts_blob")
    }

    private val dataStore = context.preferencesDataStore

    @Serializable
    private data class PinAttemptsPersistentState(
        val totalFailures: Int,
        val lockDeadlineElapsedMillis: Long?,
        val bootCount: Int,
    )

    private sealed interface LoadResult {
        data object Fresh : LoadResult

        data object Tampered : LoadResult

        data class Valid(val pointer: Int, val state: PinAttemptsPersistentState) : LoadResult
    }

    override suspend fun bootstrap() {
        writeState(freshState())
    }

    override suspend fun status(): PinLockState =
        when (val loaded = load()) {
            LoadResult.Fresh -> activeOf(0, Duration.ZERO)
            LoadResult.Tampered -> PinLockState.Tampered
            is LoadResult.Valid -> normalize(loaded.state)
        }

    override suspend fun onFailedAttempt(): PinLockState {
        val loaded = load()
        if (loaded is LoadResult.Tampered) {
            return PinLockState.Tampered
        }

        val currentTotal = (loaded as? LoadResult.Valid)?.state?.totalFailures ?: 0
        val newTotal = currentTotal + 1
        val locked = PinLockPolicy.isLockTriggered(newTotal)
        val lockDuration = if (locked) PinLockPolicy.lockDuration(newTotal) else Duration.ZERO
        val deadline = if (locked) elapsedRealtime() + lockDuration.inWholeMilliseconds else null

        writeState(PinAttemptsPersistentState(newTotal, deadline, bootCount()))
        return activeOf(newTotal, lockDuration)
    }

    override suspend fun onSuccess() {
        writeState(freshState())
    }

    override suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(POINTER_KEY)
            preferences.remove(BLOB_KEY)
        }
        withContext(dispatcher) {
            val keyStore = loadKeyStore()
            ensureActive()
            guardAliases(keyStore).forEach { keyStore.deleteEntry(it) }
        }
    }

    private fun freshState() = PinAttemptsPersistentState(totalFailures = 0, lockDeadlineElapsedMillis = null, bootCount = bootCount())

    private suspend fun normalize(state: PinAttemptsPersistentState): PinLockState {
        val currentBoot = bootCount()
        val now = elapsedRealtime()
        val deadline = state.lockDeadlineElapsedMillis

        if (currentBoot == state.bootCount) {
            return when {
                deadline == null -> {
                    activeOf(state.totalFailures, Duration.ZERO)
                }
                now >= deadline -> {
                    writeState(state.copy(lockDeadlineElapsedMillis = null, bootCount = currentBoot))
                    activeOf(state.totalFailures, Duration.ZERO)
                }
                else -> {
                    activeOf(state.totalFailures, (deadline - now).milliseconds)
                }
            }
        }

        if (deadline == null) {
            writeState(state.copy(bootCount = currentBoot))
            return activeOf(state.totalFailures, Duration.ZERO)
        }

        val fullDuration = PinLockPolicy.lockDuration(state.totalFailures)
        writeState(
            state.copy(lockDeadlineElapsedMillis = now + fullDuration.inWholeMilliseconds, bootCount = currentBoot),
        )
        return activeOf(state.totalFailures, fullDuration)
    }

    private fun activeOf(totalFailures: Int, lockRemaining: Duration): PinLockState =
        PinLockState.Active(totalFailures, PinLockPolicy.remainingInWindow(totalFailures), lockRemaining)

    private suspend fun load(): LoadResult =
        withContext(dispatcher) {
            val preferences = dataStore.data.first()
            val pointer = preferences[POINTER_KEY]
            val blob = preferences[BLOB_KEY]
            val keyStore = loadKeyStore()
            ensureActive()

            if (pointer == null || blob == null) {
                val bothAbsent = pointer == null && blob == null
                val hasGuardAlias = guardAliases(keyStore).isNotEmpty()
                return@withContext if (bothAbsent && !hasGuardAlias) LoadResult.Fresh else LoadResult.Tampered
            }

            val alias = aliasFor(pointer)
            if (!keyStore.containsAlias(alias)) {
                return@withContext LoadResult.Tampered
            }
            ensureActive()
            val state = decrypt(keyStore, alias, blob)
            if (state == null) LoadResult.Tampered else LoadResult.Valid(pointer, state)
        }

    private suspend fun writeState(state: PinAttemptsPersistentState): Unit =
        withContext(dispatcher) {
            val currentPointer = dataStore.data.first()[POINTER_KEY]
            val newPointer = (currentPointer ?: 0) + 1
            val newAlias = aliasFor(newPointer)

            val secretKey = generateKey(newAlias)
            ensureActive()
            val blob = encrypt(secretKey, state)
            ensureActive()

            dataStore.edit { preferences ->
                preferences[POINTER_KEY] = newPointer
                preferences[BLOB_KEY] = blob
            }

            val keyStore = loadKeyStore()
            guardAliases(keyStore)
                .filter { it != newAlias }
                .forEach { keyStore.deleteEntry(it) }
        }

    private fun decrypt(keyStore: KeyStore, alias: String, blob: String): PinAttemptsPersistentState? =
        try {
            val secretKey = keyStore.getKey(alias, null) as SecretKey
            val combined = Base64.decode(blob, Base64.DEFAULT)
            require(combined.size >= GCM_IV_LENGTH) { "PIN attempts blob is shorter than the IV" }
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val decrypted = cipher.doFinal(encrypted)
            Json.decodeFromString<PinAttemptsPersistentState>(decrypted.toString(StandardCharsets.UTF_8))
        } catch (e: GeneralSecurityException) {
            Logger.warn(TAG) { "PIN attempts blob failed authentication, treating as tamper: ${e.message}" }
            null
        } catch (e: IllegalArgumentException) {
            Logger.warn(TAG) { "PIN attempts blob is malformed, treating as tamper: ${e.message}" }
            null
        }

    private fun encrypt(secretKey: SecretKey, state: PinAttemptsPersistentState): String {
        val payload = Json.encodeToString(state).toByteArray(StandardCharsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(payload)
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return Base64.encodeToString(combined, Base64.DEFAULT)
    }

    private fun generateKey(alias: String): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec
            .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun loadKeyStore(): KeyStore {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        return keyStore
    }

    private fun guardAliases(keyStore: KeyStore): List<String> =
        keyStore.aliases().toList().filter { it.startsWith(ALIAS_PREFIX) }

    private fun aliasFor(pointer: Int): String = "$ALIAS_VERSION_PREFIX$pointer"
}
