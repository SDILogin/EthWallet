package me.sdi.ethwallet.core.security

import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.data.UserActivityManager
import me.sdi.ethwallet.fakes.FakePinAttemptsGuard
import me.sdi.ethwallet.fakes.FakePinSession
import me.sdi.ethwallet.fakes.FakeUserActivityManager
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.Properties
import kotlin.io.encoding.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PinCodeStorageFileTest {
    private val artifactsDirectory =
        File(System.getProperty("java.io.tmpdir"), "eth-wallet-pin-${System.nanoTime()}")
    private val artifactsFile = File(artifactsDirectory, "pin.properties")

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

        storage.clearPinCode()

        assertFalse(storage.hasPinCode(), "A cleared PIN must not be reported as present")
        assertFalse(artifactsFile.exists(), "Clearing the PIN should delete the artifacts file")
        assertNull(pinSession.lastKek, "Clearing the PIN should lock the session")
        assertFalse(
            newStorage(FakePinSession()).verifyPinCode(PIN),
            "A cleared PIN must no longer verify",
        )
    }

    @Test
    fun the_artifacts_file_is_readable_only_by_its_owner() = runTest {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return@runTest
        }

        newStorage().storePinCode(PIN)

        assertEquals(
            PosixFilePermissions.fromString("rw-------"),
            Files.getPosixFilePermissions(artifactsFile.toPath()),
            "The PIN artifacts file must never be readable by other users",
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

        val artifacts = artifactsFile.readText()

        assertFalse(artifacts.contains(PIN), "The PIN must never be persisted")
    }

    private fun newStorage(
        pinSession: PinSession = FakePinSession(),
        userActivityManager: UserActivityManager = FakeUserActivityManager(),
    ) = PinCodeStorageFile(
        artifactsFile = artifactsFile,
        userActivityManager = userActivityManager,
        pinAttemptsGuard = FakePinAttemptsGuard(),
        pinSession = pinSession,
    )

    private fun rewriteWrappedKek(wrappedKek: ByteArray) {
        val properties = Properties()
        artifactsFile.inputStream().use(properties::load)
        properties.setProperty("pin_wrapped_kek", Base64.encode(wrappedKek))
        artifactsFile.outputStream().use { properties.store(it, null) }
    }

    private companion object {
        private const val PIN = "123456"
        private const val NEW_PIN = "999999"
    }
}
