package me.sdi.ethwallet.core.security

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse

class DataCleanerPinFileTest {
    private val artifactsFile =
        File(System.getProperty("java.io.tmpdir"), "eth-wallet-pin-${System.nanoTime()}.properties")

    @AfterTest
    fun deleteArtifacts() {
        artifactsFile.delete()
    }

    @Test
    fun clean_deletes_the_pin_artifacts_file() = runTest {
        artifactsFile.writeText("pin_kdf_salt=c2FsdA==")

        DataCleanerPinFile(artifactsFile).clean()

        assertFalse(artifactsFile.exists(), "Logout should delete the PIN artifacts file")
    }

    @Test
    fun clean_succeeds_when_no_artifacts_were_written() = runTest {
        DataCleanerPinFile(artifactsFile).clean()

        assertFalse(artifactsFile.exists(), "Cleaning without stored artifacts should be a no-op")
    }
}
