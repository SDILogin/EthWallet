package me.sdi.ethwallet.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import me.sdi.ethwallet.ui.designsystem.EthText
import me.sdi.ethwallet.util.ToastHost
import me.sdi.ethwallet.util.ToastManager
import me.sdi.ethwallet.util.rememberToastManager
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val MESSAGE = "Could not update history. Pull down to try again."
private const val CONTENT_TEXT = "Screen content"

class DesktopToastTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun toastShowsOverContentAndHidesByItself() {
        lateinit var toastManager: ToastManager
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ToastHost {
                toastManager = rememberToastManager()
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    EthText(text = CONTENT_TEXT)
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val contentBoundsBefore = compose.onNodeWithText(CONTENT_TEXT).getBoundsInRoot()

        compose.runOnIdle { toastManager.showToast(MESSAGE) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText(MESSAGE).assertIsDisplayed()
        assertEquals(
            "The toast must not move other UI elements",
            contentBoundsBefore,
            compose.onNodeWithText(CONTENT_TEXT).getBoundsInRoot(),
        )

        compose.mainClock.advanceTimeBy(10_000)
        compose.onNodeWithText(MESSAGE).assertDoesNotExist()
    }
}
