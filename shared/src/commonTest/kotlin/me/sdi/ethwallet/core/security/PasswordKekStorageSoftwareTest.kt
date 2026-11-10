package me.sdi.ethwallet.core.security

import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.fakes.FakeMasterKekProtection
import me.sdi.ethwallet.fakes.FakePasswordKeyDerivation
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasswordKekStorageSoftwareTest {
    private val artifactsDirectory =
        File(System.getProperty("java.io.tmpdir"), "eth-wallet-password-kek-${System.nanoTime()}")
    private val artifactsFile = File(artifactsDirectory, ARTIFACTS_FILE_NAME)
    private val dataStore = pinArtifactsDataStore(artifactsDirectory)
    private val protection = FakeMasterKekProtection()
    private val keyDerivation = FakePasswordKeyDerivation()

    @AfterTest
    fun deleteArtifacts() {
        artifactsDirectory.listFiles()?.forEach { it.delete() }
        artifactsDirectory.delete()
    }

    @Test
    fun a_created_master_kek_is_read_back_with_the_same_password() = runTest {
        val masterKek = newStorage().create(PASSWORD)

        assertEquals(32, masterKek.size, "The master KEK should be a 256-bit key")
        assertContentEquals(masterKek, newStorage().read(PASSWORD), "The right password should return the master KEK")
    }

    @Test
    fun a_wrong_password_returns_no_master_kek() = runTest {
        newStorage().create(PASSWORD)

        assertNull(newStorage().read("wrong password"), "A wrong password should not unlock the master KEK")
    }

    @Test
    fun composed_and_decomposed_forms_of_a_password_unlock_the_same_kek() = runTest {
        val masterKek = newStorage().create("café password")

        assertContentEquals(
            masterKek,
            newStorage().read("café password"),
            "The same password typed with a decomposed accent should unlock",
        )
    }

    @Test
    fun the_password_bytes_given_to_the_key_derivation_are_zeroed() = runTest {
        newStorage().create(PASSWORD)

        assertTrue(keyDerivation.passwordArgumentsZeroed(), "Password bytes should be zeroed after the key derivation")
    }

    @Test
    fun exists_reports_whether_a_master_kek_is_stored() = runTest {
        assertFalse(newStorage().exists(), "A new store should have no master KEK")

        newStorage().create(PASSWORD)

        assertTrue(newStorage().exists(), "A created master KEK should be reported")
    }

    @Test
    fun every_create_generates_a_new_master_kek_and_salt() = runTest {
        val first = newStorage().create(PASSWORD)
        val firstSalt = dataStore.data.first()[KEK_PASSWORD_SALT]
        val second = newStorage().create(PASSWORD)

        assertFalse(first.contentEquals(second), "Each password setup should get its own random master KEK")
        assertNotEquals(firstSalt, dataStore.data.first()[KEK_PASSWORD_SALT], "Each setup should use a new salt")
        assertContentEquals(second, newStorage().read(PASSWORD), "The latest master KEK should replace the previous one")
    }

    @Test
    fun the_stored_artifacts_contain_neither_the_master_kek_nor_the_password() = runTest {
        val masterKek = newStorage().create(PASSWORD)
        val storedText = String(artifactsFile.readBytes(), Charsets.ISO_8859_1)

        assertFalse(storedText.contains(String(masterKek, Charsets.ISO_8859_1)), "The raw master KEK must not be stored")
        assertFalse(storedText.contains(Base64.encode(masterKek)), "The Base64 master KEK must not be stored")
        assertFalse(storedText.contains(PASSWORD), "The password must not be stored")
    }

    @Test
    fun reading_without_a_stored_master_kek_fails() = runTest {
        assertFailsWith<IllegalStateException>("Unlock is only offered after setup, when a KEK exists") {
            newStorage().read(PASSWORD)
        }
    }

    @Test
    fun the_master_kek_directory_is_enterable_only_by_its_owner() = runTest {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return@runTest
        }

        newStorage().create(PASSWORD)

        assertEquals(
            PosixFilePermissions.fromString("rwx------"),
            Files.getPosixFilePermissions(artifactsDirectory.toPath()),
            "No other user may enter the directory holding the master KEK",
        )
    }

    private fun newStorage() =
        PasswordKekStorageSoftware(dataStore = dataStore, protection = protection, keyDerivation = keyDerivation)

    private fun FakePasswordKeyDerivation.passwordArgumentsZeroed(): Boolean =
        passedPasswords.isNotEmpty() && passedPasswords.all { password -> password.all { it == 0.toByte() } }

    private companion object {
        private const val PASSWORD = "correct horse battery staple"
        private const val ARTIFACTS_FILE_NAME = "secure_pin_preferences.preferences_pb"

        private val KEK_PASSWORD_SALT = stringPreferencesKey("kek_password_salt")
    }
}
