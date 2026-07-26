package me.sdi.ethwallet.flow

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString

fun ComposeTestRule.clickText(
    text: String,
    substring: Boolean = false,
    ignoreCase: Boolean = false,
) {
    runSafe {
        val alternatives = mutableListOf(text)
        if (text.contains("'")) {
            alternatives.add(text.replace("'", "\\'"))
        }

        var node: SemanticsNodeInteraction? = null
        for (alt in alternatives) {
            try {
                val candidate = onNodeWithText(alt, substring = substring, ignoreCase = ignoreCase)
                candidate.assertExists()
                node = candidate
                break
            } catch (e: Throwable) {
                // Try next alternative
            }
        }

        if (node == null) {
            node = onNodeWithText(text, substring = substring, ignoreCase = ignoreCase)
            node.assertExists()
        }

        try {
            node.performScrollTo()
        } catch (e: Throwable) {
            // Ignore if parent is not scrollable
        }
        node.performClick()
    }
}

fun ComposeTestRule.clickContentDescription(
    contentDescription: String,
    substring: Boolean = false,
    ignoreCase: Boolean = false,
) {
    runSafe {
        var node: SemanticsNodeInteraction? = null
        val unmergedOptions = listOf(false, true)
        for (useUnmerged in unmergedOptions) {
            try {
                val candidate = onNodeWithContentDescription(
                    contentDescription,
                    substring = substring,
                    ignoreCase = ignoreCase,
                    useUnmergedTree = useUnmerged,
                )
                candidate.assertExists()
                node = candidate
                break
            } catch (e: Throwable) {
                // Try next
            }
        }

        if (node == null) {
            node = onNodeWithContentDescription(
                contentDescription,
                substring = substring,
                ignoreCase = ignoreCase,
                useUnmergedTree = true,
            )
            node.assertExists()
        }

        try {
            node.performScrollTo()
        } catch (e: Throwable) {
            // Ignore if parent is not scrollable
        }
        node.performClick()
    }
}

fun ComposeTestRule.waitUntilTextExists(text: String) {
    waitUntil(10000) {
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}

fun ComposeTestRule.assertTextExists(
    text: String,
    substring: Boolean = false,
    ignoreCase: Boolean = false,
) {
    runSafe {
        val alternatives = mutableListOf(text)
        if (text.contains("'")) {
            alternatives.add(text.replace("'", "\\'"))
        }

        var success = false
        for (alt in alternatives) {
            try {
                onNodeWithText(alt, substring = substring, ignoreCase = ignoreCase).assertExists()
                success = true
                break
            } catch (e: Throwable) {
                // Try next alternative
            }
        }

        if (!success) {
            onNodeWithText(text, substring = substring, ignoreCase = ignoreCase).assertExists()
        }
    }
}

fun ComposeTestRule.assertContentDescriptionExists(
    contentDescription: String,
    substring: Boolean = false,
    ignoreCase: Boolean = false,
) {
    runSafe {
        var success = false
        val unmergedOptions = listOf(false, true)
        for (useUnmerged in unmergedOptions) {
            try {
                onNodeWithContentDescription(
                    contentDescription,
                    substring = substring,
                    ignoreCase = ignoreCase,
                    useUnmergedTree = useUnmerged,
                )
                    .assertExists()

                success = true
                break
            } catch (e: Throwable) {
                // Try next
            }
        }
        if (!success) {
            onNodeWithContentDescription(
                contentDescription,
                substring = substring,
                ignoreCase = ignoreCase,
                useUnmergedTree = true,
            )
                .assertExists()
        }
    }
}

private fun ComposeTestRule.runSafe(action: () -> Unit) {
    try {
        action()
    } catch (e: Throwable) {
        System.err.println("--- COMPOSE UI TEST FAILURE DETECTED ---")
        try {
            val rootStr = onRoot().printToString()
            System.err.println("UI Semantics Tree:")
            System.err.println(rootStr)
        } catch (printEx: Throwable) {
            System.err.println("Failed to print UI tree: ${printEx.message}")
        }
        System.err.println("----------------------------------------")
        throw e
    }
}
