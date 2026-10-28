package me.sdi.ethwallet.core.security

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
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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
        val verified = newStorage(restartedSession, userActivityManager).verifyPinCode(WRONG_PIN)

        assertFalse(verified, "A wrong PIN must not verify")
        assertNull(restartedSession.lastKek, "A wrong PIN must not publish a master KEK")
        assertEquals(
            0,
            userActivityManager.onAppUnlockedCallCount,
            "A wrong PIN must not unlock the app",
        )
    }

    @Test
    fun verification_without_a_stored_pin_is_a_programming_error() = runTest {
        val pinSession = FakePinSession()
        val storage = newStorage(pinSession)

        assertFalse(storage.hasPinCode(), "No PIN should be reported when nothing was stored")
        assertFailsWith<IllegalStateException>("The lock screen is only shown once a PIN is stored") {
            storage.verifyPinCode(PIN)
        }
        assertNull(pinSession.lastKek, "A failed verification must not publish a master KEK")
    }

    @Test
    fun clearing_the_pin_removes_the_stored_artifacts() = runTest {
        val pinSession = FakePinSession()
        val storage = newStorage(pinSession)
        storage.storePinCode(PIN)
        val storedKek = assertNotNull(dataStore.data.first()[MASTER_KEK])

        storage.clearPinCode()

        assertFalse(storage.hasPinCode(), "A cleared PIN must not be reported as present")
        assertFalse(
            persistedArtifacts().contains(storedKek),
            "Clearing the PIN should leave no master KEK on disk",
        )
        assertNull(pinSession.lastKek, "Clearing the PIN should lock the session")
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
    fun storing_a_new_pin_replaces_the_previous_one() = runTest {
        newStorage().storePinCode(PIN)
        newStorage().storePinCode(NEW_PIN)

        assertFalse(newStorage().verifyPinCode(PIN), "The replaced PIN must no longer verify")
        assertTrue(newStorage().verifyPinCode(NEW_PIN), "The new PIN should verify")
        assertEquals(
            listOf(artifactsFile.name),
            artifactsDirectory.list()?.sorted(),
            "Replacing the PIN should leave no temporary files behind",
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

        newStorage(pinSession, userActivityManager).changePinCode(NEW_PIN)

        assertNull(pinSession.lastKek, "Changing the PIN must not publish a KEK to the session")
        assertEquals(0, userActivityManager.onAppUnlockedCallCount, "Changing the PIN must not unlock the app")
        assertTrue(newStorage().verifyPinCode(NEW_PIN), "The new PIN should verify after the change")
        assertFalse(newStorage().verifyPinCode(PIN), "The old PIN must no longer verify after the change")
    }

    @Test
    fun a_pin_change_keeps_the_master_kek() = runTest {
        val storeSession = FakePinSession()
        newStorage(storeSession).storePinCode(PIN)
        val kekBeforeChange = assertNotNull(storeSession.lastKek).copyOf()
        val storedKekBeforeChange = dataStore.data.first()[MASTER_KEK]

        newStorage().changePinCode(NEW_PIN)
        val unlockedSession = FakePinSession()
        newStorage(unlockedSession).verifyPinCode(NEW_PIN)

        assertEquals(storedKekBeforeChange, dataStore.data.first()[MASTER_KEK], "A PIN change must not rewrite the KEK")
        assertContentEquals(
            kekBeforeChange,
            unlockedSession.lastKek,
            "Unlocking with the new PIN should publish the master KEK from before the change",
        )
    }

    @Test
    fun every_change_stores_a_fresh_salt_and_verifier() = runTest {
        newStorage().storePinCode(PIN)
        val before = dataStore.data.first()

        newStorage().changePinCode(PIN)

        val after = dataStore.data.first()
        assertNotEquals(before[PIN_VERIFIER_SALT], after[PIN_VERIFIER_SALT], "Each PIN change should use a new salt")
        assertNotEquals(before[PIN_VERIFIER], after[PIN_VERIFIER], "Each PIN change should store a new verifier")
        assertTrue(newStorage().verifyPinCode(PIN), "Re-setting the same PIN should keep it valid")
    }

    @Test
    fun changing_the_pin_without_a_stored_pin_is_a_programming_error() = runTest {
        assertFailsWith<IllegalStateException>("Settings are only reachable once a PIN is stored") {
            newStorage().changePinCode(NEW_PIN)
        }
        assertFalse(newStorage().hasPinCode(), "A failed change must not create a PIN")
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

        private val PIN_VERIFIER_SALT = stringPreferencesKey("pin_verifier_salt")
        private val PIN_VERIFIER = stringPreferencesKey("pin_verifier")
        private val MASTER_KEK = stringPreferencesKey("master_kek")
    }
}
