package me.sdi.ethwallet.core.security

import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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
    fun a_stored_pin_verifies() = runTest {
        val storage = newStorage()

        storage.storePinCode(PIN)

        assertTrue(storage.hasPinCode(), "A stored PIN should be reported as present")
        assertTrue(newStorage().verifyPinCode(PIN), "The stored PIN should verify")
    }

    @Test
    fun a_wrong_pin_is_rejected() = runTest {
        newStorage().storePinCode(PIN)

        assertFalse(newStorage().verifyPinCode(WRONG_PIN), "A wrong PIN must not verify")
    }

    @Test
    fun verification_without_a_stored_pin_is_a_programming_error() = runTest {
        val storage = newStorage()

        assertFalse(storage.hasPinCode(), "No PIN should be reported when nothing was stored")
        assertFailsWith<IllegalStateException>("The lock screen is only shown once a PIN is stored") {
            storage.verifyPinCode(PIN)
        }
    }

    @Test
    fun clearing_the_pin_removes_the_stored_hash() = runTest {
        val storage = newStorage()
        storage.storePinCode(PIN)

        storage.clearPinCode()

        val preferences = dataStore.data.first()
        assertFalse(storage.hasPinCode(), "A cleared PIN must not be reported as present")
        assertFalse(PIN_HASH_SALT in preferences, "Clearing the PIN should leave no salt on disk")
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

        newStorage().changePinCode(NEW_PIN)

        assertTrue(newStorage().verifyPinCode(NEW_PIN), "The new PIN should verify after the change")
        assertFalse(newStorage().verifyPinCode(PIN), "The old PIN must no longer verify after the change")
    }

    @Test
    fun a_pin_change_keeps_the_other_stored_values() = runTest {
        newStorage().storePinCode(PIN)
        val masterKek = MasterKekStorageSoftware(dataStore).create()

        newStorage().changePinCode(NEW_PIN)

        assertTrue(
            masterKek.contentEquals(MasterKekStorageSoftware(dataStore).read()),
            "A PIN change must not rewrite the master KEK stored next to the PIN hash",
        )
    }

    @Test
    fun every_change_stores_a_fresh_salt_and_pin_hash() = runTest {
        newStorage().storePinCode(PIN)
        val before = dataStore.data.first()

        newStorage().changePinCode(PIN)

        val after = dataStore.data.first()
        assertNotEquals(before[PIN_HASH_SALT], after[PIN_HASH_SALT], "Each PIN change should use a new salt")
        assertNotEquals(before[PIN_HASH], after[PIN_HASH], "Each PIN change should store a new PIN hash")
        assertTrue(newStorage().verifyPinCode(PIN), "Re-setting the same PIN should keep it valid")
    }

    private fun newStorage() = PinCodeStorageSoftware(dataStore = dataStore)

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

        private val PIN_HASH_SALT = stringPreferencesKey("pin_hash_salt")
        private val PIN_HASH = stringPreferencesKey("pin_hash")
    }
}
