package me.sdi.ethwallet.core.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.data.UserActivityManager
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class DataStorePinCodeStorageTest {
    private lateinit var dataStorePinCodeStorage: DataStorePinCodeStorage
    private lateinit var userActivityManager: FakeUserActivityManager

    @Before
    fun setUp() {
        userActivityManager = FakeUserActivityManager()
        dataStorePinCodeStorage = DataStorePinCodeStorage(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            userActivityManager = userActivityManager,
            dispatcher = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() = runTest {
        dataStorePinCodeStorage.clearPinCode()
    }

    @Test
    fun testStorePinCode() = runTest {
        dataStorePinCodeStorage.storePinCode("123456")

        assertTrue("Pin code not stored", dataStorePinCodeStorage.hasPinCode())
    }

    @Test
    fun testVerifyCorrectPinCode() = runTest {
        val pinCode = "123456"
        dataStorePinCodeStorage.storePinCode(pinCode)

        val result = dataStorePinCodeStorage.verifyPinCode(pinCode)

        assertTrue("Verification with correct PIN code failed", result)
    }

    @Test
    fun testVerifyIncorrectPinCode() = runTest {
        val correctPin = "123456"
        val incorrectPin = "654321"

        dataStorePinCodeStorage.storePinCode(correctPin)

        val result = dataStorePinCodeStorage.verifyPinCode(incorrectPin)

        assertFalse("Verification with incorrect PIN code should fail", result)
    }

    @Test
    fun testVerifyWithNoPinCodeSet() = runTest {
        val result = dataStorePinCodeStorage.verifyPinCode("123456")

        assertFalse("Verification with no PIN code set should fail", result)
    }

    @Test
    fun testClearPinCode() = runTest {
        val pinCode = "123456"

        dataStorePinCodeStorage.storePinCode(pinCode)
        assertTrue("Pin code should be stored", dataStorePinCodeStorage.hasPinCode())

        dataStorePinCodeStorage.clearPinCode()

        assertFalse("Pin code should be removed after clearing", dataStorePinCodeStorage.hasPinCode())
        assertFalse("Verification should fail after clearing PIN", dataStorePinCodeStorage.verifyPinCode(pinCode))
    }
}

private class FakeUserActivityManager : UserActivityManager {
    private val _showLockScreen = MutableStateFlow(false)
    override val showLockScreen: StateFlow<Boolean> = _showLockScreen.asStateFlow()

    override fun onAnyUserAction() {}

    override fun onAppUnlocked() {}
}
