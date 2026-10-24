package me.sdi.ethwallet.flow.pageobjects

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import kotlin.test.assertTrue

class TokensListPage(private val composeTestRule: ComposeTestRule) {
    fun toggleToken(symbol: String) {
        tokenRow(symbol).performClick()
    }

    fun assertTokenChecked(symbol: String, checked: Boolean) {
        if (checked) {
            tokenRow(symbol).assertIsOn()
        } else {
            tokenRow(symbol).assertIsOff()
        }
    }

    fun dragTokenTo(symbol: String, targetSymbol: String) {
        val start = tokenRow(symbol).fetchSemanticsNode().boundsInRoot.center
        val target = tokenRow(targetSymbol).fetchSemanticsNode().boundsInRoot
        val end = start.copy(y = if (start.y < target.center.y) target.bottom - 1f else target.top + 1f)
        composeTestRule.onRoot().performTouchInput {
            swipe(start = start, end = end, durationMillis = 600)
        }
    }

    fun assertTokenOrder(vararg symbols: String) {
        val positions = symbols.map { tokenRow(it).fetchSemanticsNode().boundsInRoot.top }
        assertTrue(positions.zipWithNext().all { (first, second) -> first < second }, "Expected token order: ${symbols.toList()}")
    }

    fun clickUp() {
        composeTestRule.onNodeWithContentDescription("Back").performClick()
    }

    private fun tokenRow(symbol: String) = composeTestRule.onNode(hasText(symbol) and isToggleable())
}
