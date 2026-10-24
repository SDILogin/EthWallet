package me.sdi.ethwallet.flow.pageobjects

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick

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

    fun clickUp() {
        composeTestRule.onNodeWithContentDescription("Back").performClick()
    }

    private fun tokenRow(symbol: String) = composeTestRule.onNode(hasText(symbol) and isToggleable())
}
