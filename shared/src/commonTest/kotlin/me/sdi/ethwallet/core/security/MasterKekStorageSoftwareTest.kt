package me.sdi.ethwallet.core.security

import kotlinx.coroutines.test.runTest
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

class MasterKekStorageSoftwareTest {
    private val artifactsDirectory =
        File(System.getProperty("java.io.tmpdir"), "eth-wallet-kek-${System.nanoTime()}")
    private val artifactsFile = File(artifactsDirectory, ARTIFACTS_FILE_NAME)
    private val dataStore = pinArtifactsDataStore(artifactsDirectory)

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
    fun clearing_removes_the_master_kek_from_disk() = runTest {
        val storage = newStorage()
        val encodedKek = Base64.encode(storage.create())

        storage.clear()

        assertFalse(
            String(artifactsFile.readBytes(), Charsets.ISO_8859_1).contains(encodedKek),
            "Clearing should leave no master KEK on disk",
        )
        assertFailsWith<IllegalStateException>("Reading is only done after a PIN check, when a KEK exists") {
            storage.read()
        }
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

    private fun newStorage() = MasterKekStorageSoftware(dataStore = dataStore)

    private companion object {
        private const val ARTIFACTS_FILE_NAME = "secure_pin_preferences.preferences_pb"
    }
}
