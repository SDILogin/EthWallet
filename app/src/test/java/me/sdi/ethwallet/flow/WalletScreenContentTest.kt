package me.sdi.ethwallet.flow

import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.fragment.app.FragmentActivity
import me.sdi.ethwallet.ui.wallet.WalletBalanceLoadState
import me.sdi.ethwallet.ui.wallet.WalletFiatLoadState
import me.sdi.ethwallet.ui.wallet.WalletScreenContent
import me.sdi.ethwallet.ui.wallet.WalletViewState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P], application = WalletFlowTestApplication::class)
class WalletScreenContentTest {
    @get:Rule
    val composeTestRule: ComposeTestRule = createEmptyComposeRule()

    private val activityController = Robolectric.buildActivity(WalletScreenContentTestActivity::class.java).setup()
    private val activity: WalletScreenContentTestActivity
        get() = activityController.get()

    @Test
    fun balance_error_keeps_receive_enabled_and_send_disabled() {
        var retryClicked = false
        activity.setWalletContent(
            WalletViewState(
                balanceLoadState = WalletBalanceLoadState.Error,
                fiatLoadState = WalletFiatLoadState.Unavailable,
            ),
            onRetry = { retryClicked = true },
        )

        composeTestRule.onNodeWithText("Couldn\'t load wallet balances").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Receive").assertIsEnabled()
        composeTestRule.onNodeWithContentDescription("Send").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Retry").performClick()

        assertTrue(retryClicked)
    }

    @Test
    fun unavailable_fiat_value_does_not_render_a_shimmer_placeholder() {
        activity.setWalletContent(
            WalletViewState(
                totalBalanceEth = "1 ETH",
                balanceLoadState = WalletBalanceLoadState.Content,
                fiatLoadState = WalletFiatLoadState.Unavailable,
            ),
        )

        composeTestRule.onNodeWithText("USD value unavailable").assertIsDisplayed()
    }

    @Test
    fun pulling_from_balance_refreshes_dashboard() {
        var refreshCount = 0
        activity.setWalletContent(
            WalletViewState(
                chainId = 11155111L,
                totalBalanceEth = "1 ETH",
                balanceLoadState = WalletBalanceLoadState.Content,
                fiatLoadState = WalletFiatLoadState.Available,
            ),
            onRefresh = { refreshCount += 1 },
        )

        composeTestRule.onNodeWithContentDescription("ETH balance: 1 ETH").performTouchInput {
            swipe(
                start = center,
                end = Offset(center.x, center.y + 240f),
                durationMillis = 500L,
            )
        }
        composeTestRule.waitForIdle()

        assertEquals(1, refreshCount)
    }
}

private class WalletScreenContentTestActivity : FragmentActivity() {
    fun setWalletContent(
        viewState: WalletViewState,
        onRetry: () -> Unit = {},
        onRefresh: () -> Unit = {},
    ) {
        setContent {
            MaterialTheme {
                WalletScreenContent(
                    viewState = viewState,
                    onRefresh = onRefresh,
                    onRetry = onRetry,
                    showUpButton = false,
                    onUpButtonClick = {},
                    onEditButtonClick = {},
                    onTokenClick = {},
                    onReceiveButtonClick = {},
                    onSendButtonClick = {},
                    onHistoryButtonClick = {},
                )
            }
        }
    }
}
