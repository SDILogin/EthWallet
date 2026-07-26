package me.sdi.ethwallet.core.security

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataCleanerPinArtifactsTest {
    private val artifactsDirectory =
        File(System.getProperty("java.io.tmpdir"), "eth-wallet-pin-clean-${System.nanoTime()}")
    private val artifactsFile = File(artifactsDirectory, ARTIFACTS_FILE_NAME)
    private val dataStore = pinArtifactsDataStore(artifactsDirectory)

    @AfterTest
    fun deleteArtifacts() {
        artifactsDirectory.listFiles()?.forEach { it.delete() }
        artifactsDirectory.delete()
    }

    @Test
    fun clean_removes_the_stored_pin_artifacts() = runTest {
        dataStore.edit { preferences -> preferences[PIN_KDF_SALT] = SALT }

        DataCleanerPinArtifacts(dataStore).clean()

        assertNull(
            dataStore.data.first()[PIN_KDF_SALT],
            "Logout should remove the stored PIN artifacts",
        )
        assertFalse(
            String(artifactsFile.readBytes(), Charsets.ISO_8859_1).contains(SALT),
            "Logout should leave no readable PIN artifacts on disk",
        )
    }

    @Test
    fun clean_succeeds_when_no_artifacts_were_written() = runTest {
        DataCleanerPinArtifacts(dataStore).clean()

        assertTrue(
            dataStore.data.first().asMap().isEmpty(),
            "Cleaning without stored artifacts should leave the store empty",
        )
    }

    private companion object {
        private const val SALT = "c2FsdA=="
        private const val ARTIFACTS_FILE_NAME = "secure_pin_preferences.preferences_pb"

        private val PIN_KDF_SALT = stringPreferencesKey("pin_kdf_salt")
    }
}
