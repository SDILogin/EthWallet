package me.sdi.ethwallet.core.security

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
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
        dataStore.edit { preferences ->
            preferences[KEK_WRAPPING_KEY] = STORED_WRAPPING_KEY
            preferences[PASSWORD_WRAPPED_MASTER_KEK] = STORED_WRAPPED_KEK
        }

        DataCleanerPinArtifacts(dataStore).clean()

        assertTrue(
            dataStore.data.first().asMap().isEmpty(),
            "Logout should remove the stored PIN artifacts",
        )
        val storedText = String(artifactsFile.readBytes(), Charsets.ISO_8859_1)
        assertFalse(
            storedText.contains(STORED_WRAPPING_KEY),
            "Logout should delete the KEK wrapping key handle from disk",
        )
        assertFalse(
            storedText.contains(STORED_WRAPPED_KEK),
            "Logout should leave no wrapped master KEK on disk",
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
        private const val STORED_WRAPPING_KEY = "d3JhcHBpbmcta2V5"
        private const val STORED_WRAPPED_KEK = "a2VrLWJ5dGVz"
        private const val ARTIFACTS_FILE_NAME = "secure_pin_preferences.preferences_pb"

        private val KEK_WRAPPING_KEY = stringPreferencesKey("kek_wrapping_key")
        private val PASSWORD_WRAPPED_MASTER_KEK = stringPreferencesKey("password_wrapped_master_kek")
    }
}
