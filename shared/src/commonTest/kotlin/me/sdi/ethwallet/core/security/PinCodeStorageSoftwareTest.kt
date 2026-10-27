package me.sdi.ethwallet.core.security

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.data.UserActivityManager
import me.sdi.ethwallet.fakes.FakePinAttemptsGuard
import me.sdi.ethwallet.fakes.FakePinSession
import me.sdi.ethwallet.fakes.FakeUserActivityManager
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.encoding.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PinCodeStorageSoftwareTest {
    private val artifactsDirectory =
        File(System.getProperty("java.io.tmpdir"), "eth-wallet-pin-${System.nanoTime()}")
    private val artifactsFile = File(artifactsDirectory, ARTIFACTS_FILE_NAME)
    private val dataStore = pinArtifactsDataStore(artifactsDirectory)

    @AfterTest
    fun deleteArtifacts() {
        artifactsDirectory.listFiles()?.forEach { it.delete() }
        artifactsDirectory.delete()
    }

    @Test
    fun a_stored_pin_verifies_and_unlocks_the_session() = runTest {
        val pinSession = FakePinSession()
        val userActivityManager = FakeUserActivityManager()
        val storage = newStorage(pinSession, userActivityManager)

        storage.storePinCode(PIN)
        val kekFromStore = pinSession.lastKek
        val verified = storage.verifyPinCode(PIN)

        assertTrue(verified, "The stored PIN should verify")
        assertTrue(storage.hasPinCode(), "A stored PIN should be reported as present")
        assertContentEquals(
            kekFromStore,
            pinSession.lastKek,
            "Verification should republish the master KEK created when the PIN was stored",
        )
        assertEquals(
            1,
            userActivityManager.onAppUnlockedCallCount,
            "A successful verification should unlock the app",
        )
    }

    @Test
    fun a_wrong_pin_is_rejected_and_leaves_the_session_locked() = runTest {
        newStorage().storePinCode(PIN)

        val restartedSession = FakePinSession()
        val userActivityManager = FakeUserActivityManager()
        val verified = newStorage(restartedSession, userActivityManager).verifyPinCode("654321")

        assertFalse(verified, "A wrong PIN must not verify")
        assertNull(restartedSession.lastKek, "A wrong PIN must not publish a master KEK")
        assertEquals(
            0,
            userActivityManager.onAppUnlockedCallCount,
            "A wrong PIN must not unlock the app",
        )
    }

    @Test
    fun verification_fails_when_no_pin_is_stored() = runTest {
        val pinSession = FakePinSession()
        val storage = newStorage(pinSession)

        val verified = storage.verifyPinCode(PIN)

        assertFalse(verified, "Verification must fail when no PIN has ever been stored")
        assertFalse(storage.hasPinCode(), "No PIN should be reported when nothing was stored")
        assertNull(pinSession.lastKek, "A failed verification must not publish a master KEK")
    }

    @Test
    fun a_tampered_envelope_is_rejected() = runTest {
        newStorage().storePinCode(PIN)
        rewriteWrappedKek(ByteArray(44) { it.toByte() })

        val restartedSession = FakePinSession()
        val verified = newStorage(restartedSession).verifyPinCode(PIN)

        assertFalse(verified, "An envelope that fails its GCM tag must not verify")
        assertNull(restartedSession.lastKek, "A tampered envelope must not publish a master KEK")
    }

    @Test
    fun a_truncated_envelope_is_rejected() = runTest {
        newStorage().storePinCode(PIN)
        rewriteWrappedKek(ByteArray(4))

        val restartedSession = FakePinSession()
        val verified = newStorage(restartedSession).verifyPinCode(PIN)

        assertFalse(verified, "An envelope shorter than the IV must not verify")
        assertNull(restartedSession.lastKek, "A truncated envelope must not publish a master KEK")
    }

    @Test
    fun clearing_the_pin_removes_the_stored_envelope() = runTest {
        val pinSession = FakePinSession()
        val storage = newStorage(pinSession)
        storage.storePinCode(PIN)
        val storedKek = assertNotNull(dataStore.data.first()[PIN_WRAPPED_KEK])

        storage.clearPinCode()

        assertFalse(storage.hasPinCode(), "A cleared PIN must not be reported as present")
        assertFalse(
            persistedArtifacts().contains(storedKek),
            "Clearing the PIN should leave no wrapped KEK on disk",
        )
        assertNull(pinSession.lastKek, "Clearing the PIN should lock the session")
        assertFalse(
            newStorage(FakePinSession()).verifyPinCode(PIN),
            "A cleared PIN must no longer verify",
        )
    }

    @Test
    fun the_artifacts_directory_is_enterable_only_by_its_owner() = runTest {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return@runTest
        }

        newStorage().storePinCode(PIN)

        assertEquals(
            PosixFilePermissions.fromString("rwx------"),
            Files.getPosixFilePermissions(artifactsDirectory.toPath()),
            "No other user may enter the directory holding the PIN artifacts",
        )
    }

    @Test
    fun storing_a_new_pin_replaces_the_previous_envelope() = runTest {
        newStorage().storePinCode(PIN)
        newStorage().storePinCode(NEW_PIN)

        assertFalse(newStorage().verifyPinCode(PIN), "The replaced PIN must no longer verify")
        assertTrue(newStorage().verifyPinCode(NEW_PIN), "The new PIN should verify")
        assertEquals(
            listOf(artifactsFile.name),
            artifactsDirectory.list()?.sorted(),
            "Replacing the envelope should leave no temporary files behind",
        )
    }

    @Test
    fun the_stored_artifacts_never_contain_the_pin() = runTest {
        newStorage().storePinCode(PIN)

        assertFalse(persistedArtifacts().contains(PIN), "The PIN must never be persisted")
    }

    @Test
    fun a_changed_pin_verifies_and_the_old_one_does_not() = runTest {
        newStorage().storePinCode(PIN)
        val pinSession = FakePinSession()
        val userActivityManager = FakeUserActivityManager()

        val changed = newStorage(pinSession, userActivityManager).changePinCode(PIN, NEW_PIN)

        assertTrue(changed, "Changing the PIN with the correct current PIN should succeed")
        assertNull(pinSession.lastKek, "Changing the PIN must not publish a KEK to the session")
        assertEquals(0, userActivityManager.onAppUnlockedCallCount, "Changing the PIN must not unlock the app")
        assertTrue(newStorage().verifyPinCode(NEW_PIN), "The new PIN should verify after the change")
        assertFalse(newStorage().verifyPinCode(PIN), "The old PIN must no longer verify after the change")
    }

    @Test
    fun a_pin_change_keeps_the_master_kek_and_existing_ciphertexts() = runTest {
        val storeSession = FakePinSession()
        newStorage(storeSession).storePinCode(PIN)
        val kekBeforeChange = assertNotNull(storeSession.lastKek).copyOf()
        val encryptedPayload = KekCipher.encrypt(kekBeforeChange, SAMPLE_PAYLOAD)

        newStorage().changePinCode(PIN, NEW_PIN)
        val unlockedSession = FakePinSession()
        newStorage(unlockedSession).verifyPinCode(NEW_PIN)
        val kekAfterChange = assertNotNull(unlockedSession.lastKek, "The new PIN should unlock a KEK")

        assertContentEquals(kekBeforeChange, kekAfterChange, "The new PIN should unwrap the same master KEK")
        assertContentEquals(
            SAMPLE_PAYLOAD,
            KekCipher.decrypt(kekAfterChange, encryptedPayload),
            "Data encrypted before the change should still decrypt after unlocking with the new PIN",
        )
    }

    @Test
    fun a_wrong_current_pin_leaves_the_stored_envelope_unchanged() = runTest {
        newStorage().storePinCode(PIN)
        val before = dataStore.data.first()

        val changed = newStorage().changePinCode(WRONG_PIN, NEW_PIN)

        val after = dataStore.data.first()
        assertFalse(changed, "A wrong current PIN must not change the PIN")
        assertEquals(before[PIN_KDF_SALT], after[PIN_KDF_SALT], "A failed change must keep the stored salt")
        assertEquals(before[PIN_WRAPPED_KEK], after[PIN_WRAPPED_KEK], "A failed change must keep the wrapped KEK")
        assertTrue(newStorage().verifyPinCode(PIN), "The current PIN should still verify after a failed change")
    }

    @Test
    fun changing_the_pin_fails_when_no_pin_is_stored() = runTest {
        assertFalse(newStorage().changePinCode(PIN, NEW_PIN), "There is no PIN to change before one is stored")
        assertFalse(newStorage().hasPinCode(), "A failed change must not create a PIN")
    }

    @Test
    fun every_successful_change_uses_a_fresh_salt() = runTest {
        newStorage().storePinCode(PIN)
        val initialSalt = assertNotNull(dataStore.data.first()[PIN_KDF_SALT])

        newStorage().changePinCode(PIN, NEW_PIN)
        val firstChangeSalt = assertNotNull(dataStore.data.first()[PIN_KDF_SALT])
        newStorage().changePinCode(NEW_PIN, PIN)
        val secondChangeSalt = assertNotNull(dataStore.data.first()[PIN_KDF_SALT])

        assertEquals(
            3,
            setOf(initialSalt, firstChangeSalt, secondChangeSalt).size,
            "Each PIN change should store a new random salt",
        )
    }

    private fun newStorage(
        pinSession: PinSession = FakePinSession(),
        userActivityManager: UserActivityManager = FakeUserActivityManager(),
    ) = PinCodeStorageSoftware(
        dataStore = dataStore,
        userActivityManager = userActivityManager,
        pinAttemptsGuard = FakePinAttemptsGuard(),
        pinSession = pinSession,
    )

    private suspend fun rewriteWrappedKek(wrappedKek: ByteArray) {
        dataStore.edit { preferences ->
            preferences[PIN_WRAPPED_KEK] = Base64.encode(wrappedKek)
        }
    }

    /**
     * The stored file is protobuf, so it is read back as Latin-1: every byte maps to one character
     * and a search over the result is a search over the raw bytes.
     */
    private fun persistedArtifacts(): String =
        String(artifactsFile.readBytes(), Charsets.ISO_8859_1)

    private companion object {
        private const val PIN = "123456"
        private const val NEW_PIN = "999999"
        private const val WRONG_PIN = "654321"
        private const val ARTIFACTS_FILE_NAME = "secure_pin_preferences.preferences_pb"

        private val PIN_KDF_SALT = stringPreferencesKey("pin_kdf_salt")
        private val PIN_WRAPPED_KEK = stringPreferencesKey("pin_wrapped_kek")
        private val SAMPLE_PAYLOAD = "wallet private key".encodeToByteArray()
    }
}
