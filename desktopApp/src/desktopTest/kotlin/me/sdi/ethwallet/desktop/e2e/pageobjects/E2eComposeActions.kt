package me.sdi.ethwallet.desktop.e2e.pageobjects

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.CancellationException

internal fun ComposeTestRule.awaitNode(
    matcher: SemanticsMatcher,
    timeoutMillis: Long = DEFAULT_UI_TIMEOUT_MILLIS,
): SemanticsNodeInteraction {
    waitUntil(timeoutMillis) {
        onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    }
    return onNode(matcher)
}

internal fun ComposeTestRule.clickNode(
    matcher: SemanticsMatcher,
    timeoutMillis: Long = DEFAULT_UI_TIMEOUT_MILLIS,
) {
    awaitNode(matcher, timeoutMillis).performClick()
}

internal inline fun secretSafeAction(
    failureMessage: String,
    action: () -> Unit,
) {
    try {
        action()
    } catch (failure: Throwable) {
        if (failure is CancellationException) throw failure
        // Compose assertion failures can include the recovery phrase from the semantics tree.
        throw AssertionError(failureMessage)
    }
}

internal const val DEFAULT_UI_TIMEOUT_MILLIS = 10_000L
