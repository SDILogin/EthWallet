package me.sdi.ethwallet.flow

import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.fragment.app.FragmentActivity
import me.sdi.ethwallet.core.security.InMemoryPinSession
import me.sdi.ethwallet.core.security.biometry.BiometricResult
import me.sdi.ethwallet.core.security.biometry.BiometricStatus
import me.sdi.ethwallet.core.security.biometry.BiometricUnlock
import me.sdi.ethwallet.ui.settings.SettingsScreen
import me.sdi.ethwallet.ui.settings.SettingsViewModel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P], application = WalletFlowTestApplication::class)
class SettingsScreenTest {
    @get:Rule
    val composeTestRule: ComposeTestRule = createEmptyComposeRule()

    private lateinit var activityController: ActivityController<SettingsScreenTestActivity>
    private var changePinClickCount = 0

    @Before
    fun setUp() {
        stopKoin()
        startKoin {
            modules(
                module {
                    viewModel {
                        SettingsViewModel(
                            biometricUnlock = object : BiometricUnlock {
                                override suspend fun status(): BiometricStatus = BiometricStatus.NotSupported

                                override suspend fun enable(): BiometricResult = BiometricResult.Error("unsupported")

                                override suspend fun disable() = Unit

                                override suspend fun unlock(): BiometricResult = BiometricResult.Error("unsupported")
                            },
                            pinSession = InMemoryPinSession(),
                        )
                    }
                },
            )
        }
        activityController = Robolectric.buildActivity(SettingsScreenTestActivity::class.java).setup()
        activityController.get().setContent {
            @Suppress("DEPRECATION")
            KoinContext(koin = GlobalContext.get()) {
                MaterialTheme {
                    SettingsScreen(
                        onBack = {},
                        onChangePinClick = { changePinClickCount += 1 },
                    )
                }
            }
        }
    }

    @After
    fun tearDown() {
        if (::activityController.isInitialized) {
            activityController.pause().stop().destroy()
        }
        stopKoin()
    }

    @Test
    fun clicking_change_pin_row_opens_change_pin_flow() {
        composeTestRule.runOnIdle {
            assertEquals(0, changePinClickCount, "Change PIN should not open before the row is clicked")
        }

        composeTestRule.onNodeWithText("Change PIN").performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, changePinClickCount, "Clicking the Change PIN row should open the change PIN flow once")
        }
    }
}

private class SettingsScreenTestActivity : FragmentActivity()
