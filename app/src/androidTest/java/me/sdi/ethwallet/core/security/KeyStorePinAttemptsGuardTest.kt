package me.sdi.ethwallet.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.util.preferencesDataStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import javax.crypto.KeyGenerator
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class KeyStorePinAttemptsGuardTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private var elapsed = 0L
    private var boot = 1

    private fun newGuard() =
        KeyStorePinAttemptsGuard(
            context = context,
            elapsedRealtime = { elapsed },
            bootCount = { boot },
            dispatcher = UnconfinedTestDispatcher(),
        )

    @Before
    fun setUp() = runTest {
        elapsed = 0L
        boot = 1
        wipe()
    }

    @After
    fun tearDown() = runTest {
        wipe()
    }

    @Test
    fun lockSurvivesGuardRecreation() = runTest {
        val guard = newGuard()
        guard.bootstrap()
        repeat(3) { guard.onFailedAttempt() }

        val revived = newGuard().status() as PinLockState.Active

        assertTrue("Lock must persist across guard recreation", revived.lockRemaining > Duration.ZERO)
        assertEquals("Remaining lock time must match", 1.seconds, revived.lockRemaining)
        assertEquals(3, revived.totalFailures)
    }

    @Test
    fun rollbackToDeletedKeyVersionIsTampered() = runTest {
        val guard = newGuard()
        guard.bootstrap()
        guard.onFailedAttempt()

        val capturedPointer = readPointer()!!
        val capturedBlob = readBlob()!!

        guard.onFailedAttempt()

        writeState(capturedPointer, capturedBlob)

        assertEquals(PinLockState.Tampered, guard.status())
    }

    @Test
    fun restoringOldBlobUnderCurrentPointerIsTampered() = runTest {
        val guard = newGuard()
        guard.bootstrap()
        guard.onFailedAttempt()

        val capturedBlob = readBlob()!!

        guard.onFailedAttempt()

        writeBlob(capturedBlob)

        assertEquals(PinLockState.Tampered, guard.status())
    }

    @Test
    fun nonBase64BlobIsTampered() = runTest {
        val guard = newGuard()
        guard.bootstrap()
        guard.onFailedAttempt()

        writeBlob("@@not-base-64@@")

        assertEquals(PinLockState.Tampered, guard.status())
    }

    @Test
    fun blobShorterThanIvIsTampered() = runTest {
        val guard = newGuard()
        guard.bootstrap()
        guard.onFailedAttempt()

        writeBlob(Base64.encodeToString(ByteArray(4), Base64.DEFAULT))

        assertEquals(PinLockState.Tampered, guard.status())
    }

    @Test
    fun deletingDataStoreEntriesWhileAliasesRemainIsTampered() = runTest {
        val guard = newGuard()
        guard.bootstrap()

        context.preferencesDataStore.edit { preferences ->
            preferences.remove(POINTER_KEY)
            preferences.remove(BLOB_KEY)
        }

        assertEquals(PinLockState.Tampered, guard.status())
    }

    @Test
    fun bootCountChangeDuringLockRestartsFullTier() = runTest {
        val guard = newGuard()
        guard.bootstrap()
        repeat(6) { guard.onFailedAttempt() }

        elapsed = 10_000L
        val duringLock = guard.status() as PinLockState.Active
        assertTrue("Lock should have partially elapsed", duringLock.lockRemaining < 30.seconds)

        boot = 2
        val afterReboot = guard.status() as PinLockState.Active
        assertEquals("Reboot must restart the full tier duration", 30.seconds, afterReboot.lockRemaining)
        assertTrue("Reboot must not shorten the lock", afterReboot.lockRemaining > duringLock.lockRemaining)
    }

    @Test
    fun advancingPastDeadlineUnlocksAndPreservesFailures() = runTest {
        val guard = newGuard()
        guard.bootstrap()
        repeat(3) { guard.onFailedAttempt() }

        elapsed = 2_000L
        val unlocked = guard.status() as PinLockState.Active
        assertEquals(Duration.ZERO, unlocked.lockRemaining)
        assertEquals(3, unlocked.totalFailures)

        val next = guard.onFailedAttempt() as PinLockState.Active
        assertEquals("Escalation must continue from the preserved count", 4, next.totalFailures)
        assertEquals(Duration.ZERO, next.lockRemaining)
    }

    @Test
    fun orphanKeyFromCrashIsValidAndPrunedByNextWrite() = runTest {
        val guard = newGuard()
        guard.bootstrap()

        createOrphanAlias("pin_attempts_key_v2")
        createOrphanAlias("pin_attempts_key_v9")

        assertTrue("Orphan aliases must not be treated as tamper", guard.status() is PinLockState.Active)

        guard.onSuccess()

        val pointer = readPointer()!!
        assertEquals(
            "Only the committed key version must remain after pruning",
            listOf("pin_attempts_key_v$pointer"),
            guardAliases(),
        )
    }

    @Test
    fun successThenFailureRestartsCounter() = runTest {
        val guard = newGuard()
        guard.bootstrap()
        repeat(2) { guard.onFailedAttempt() }
        guard.onSuccess()

        val afterFailure = guard.onFailedAttempt()

        assertEquals(PinLockState.Active(1, 2, Duration.ZERO), afterFailure)
    }

    @Test
    fun fullWipeReportsFreshState() = runTest {
        val guard = newGuard()
        guard.bootstrap()
        guard.onFailedAttempt()

        wipe()

        assertEquals(PinLockState.Active(0, 3, Duration.ZERO), guard.status())
    }

    @Test
    fun bootstrapOverExistingStateResetsWithoutTamper() = runTest {
        val guard = newGuard()
        guard.bootstrap()
        repeat(4) { guard.onFailedAttempt() }
        createOrphanAlias("pin_attempts_key_v99")

        guard.bootstrap()

        assertEquals(PinLockState.Active(0, 3, Duration.ZERO), guard.status())
    }

    private suspend fun readPointer(): Int? = readPreferences()[POINTER_KEY]

    private suspend fun readBlob(): String? = readPreferences()[BLOB_KEY]

    private suspend fun readPreferences(): Preferences = context.preferencesDataStore.data.first()

    private suspend fun writeState(pointer: Int, blob: String) {
        context.preferencesDataStore.edit { preferences ->
            preferences[POINTER_KEY] = pointer
            preferences[BLOB_KEY] = blob
        }
    }

    private suspend fun writeBlob(blob: String) {
        context.preferencesDataStore.edit { preferences ->
            preferences[BLOB_KEY] = blob
        }
    }

    private suspend fun wipe() {
        context.preferencesDataStore.edit { preferences ->
            preferences.remove(POINTER_KEY)
            preferences.remove(BLOB_KEY)
        }
        val keyStore = loadKeyStore()
        guardAliases(keyStore).forEach { keyStore.deleteEntry(it) }
    }

    private fun guardAliases(keyStore: KeyStore = loadKeyStore()): List<String> =
        keyStore.aliases().toList().filter { it.startsWith(ALIAS_PREFIX) }.sorted()

    private fun createOrphanAlias(alias: String) {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec
            .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)
            .build()
        keyGenerator.init(spec)
        keyGenerator.generateKey()
    }

    private fun loadKeyStore(): KeyStore {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        return keyStore
    }

    private companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS_PREFIX = "pin_attempts_key_"

        private val POINTER_KEY = intPreferencesKey("pin_attempts_pointer")
        private val BLOB_KEY = stringPreferencesKey("pin_attempts_blob")
    }
}
