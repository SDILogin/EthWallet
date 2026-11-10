package me.sdi.ethwallet.core.security

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.fakes.FakeMasterKekProtection
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.encoding.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MasterKekStorageSoftwareTest {
    private val artifactsDirectory =
        File(System.getProperty("java.io.tmpdir"), "eth-wallet-kek-${System.nanoTime()}")
    private val artifactsFile = File(artifactsDirectory, ARTIFACTS_FILE_NAME)
    private val dataStore = pinArtifactsDataStore(artifactsDirectory)
    private val protection = FakeMasterKekProtection()

    @AfterTest
    fun deleteArtifacts() {
        artifactsDirectory.listFiles()?.forEach { it.delete() }
        artifactsDirectory.delete()
    }

    @Test
    fun a_created_master_kek_is_read_back() = runTest {
        val masterKek = newStorage().create()

        assertEquals(32, masterKek.size, "The master KEK should be a 256-bit key")
        assertContentEquals(masterKek, newStorage().read(), "The stored master KEK should be read back unchanged")
    }

    @Test
    fun every_create_generates_a_new_random_master_kek() = runTest {
        val first = newStorage().create()
        val second = newStorage().create()

        assertFalse(first.contentEquals(second), "Each onboarding should get its own random master KEK")
        assertContentEquals(second, newStorage().read(), "The latest master KEK should replace the previous one")
    }

    @Test
    fun the_stored_artifacts_do_not_contain_the_master_kek() = runTest {
        val masterKek = newStorage().create()

        assertFalse(
            storedText().contains(String(masterKek, Charsets.ISO_8859_1)),
            "The raw master KEK must not be written to disk",
        )
        assertFalse(
            storedText().contains(Base64.encode(masterKek)),
            "The Base64 master KEK must not be written to disk",
        )
    }

    @Test
    fun clearing_removes_the_wrapping_key_and_the_wrapped_master_kek() = runTest {
        val storage = newStorage()
        storage.create()

        storage.clear()

        assertTrue(dataStore.data.first().asMap().isEmpty(), "Clearing should leave no master KEK artifacts")
        assertFailsWith<IllegalStateException>("Reading is only done after a PIN check, when a KEK exists") {
            storage.read()
        }
    }

    @Test
    fun a_plain_master_kek_from_an_earlier_version_is_wrapped_on_read() = runTest {
        val legacyMasterKek = ByteArray(32) { it.toByte() }
        dataStore.edit { preferences -> preferences[LEGACY_MASTER_KEK] = Base64.encode(legacyMasterKek) }

        val migrated = newStorage().read()

        assertContentEquals(legacyMasterKek, migrated, "The migrated master KEK should keep its value")
        assertNull(dataStore.data.first()[LEGACY_MASTER_KEK], "The plain master KEK should be removed")
        assertFalse(
            storedText().contains(Base64.encode(legacyMasterKek)),
            "The plain master KEK should no longer be on disk",
        )
        assertContentEquals(legacyMasterKek, newStorage().read(), "The wrapped master KEK should be read back")
    }

    @Test
    fun the_master_kek_directory_is_enterable_only_by_its_owner() = runTest {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return@runTest
        }

        newStorage().create()

        assertEquals(
            PosixFilePermissions.fromString("rwx------"),
            Files.getPosixFilePermissions(artifactsDirectory.toPath()),
            "No other user may enter the directory holding the master KEK",
        )
    }

    private fun newStorage() = MasterKekStorageSoftware(dataStore = dataStore, protection = protection)

    private fun storedText() = String(artifactsFile.readBytes(), Charsets.ISO_8859_1)

    private companion object {
        private const val ARTIFACTS_FILE_NAME = "secure_pin_preferences.preferences_pb"

        private val LEGACY_MASTER_KEK = stringPreferencesKey("master_kek")
    }
}
