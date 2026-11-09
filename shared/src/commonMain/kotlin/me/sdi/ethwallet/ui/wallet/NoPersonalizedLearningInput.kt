package me.sdi.ethwallet.ui.wallet

import androidx.compose.runtime.Composable

/**
 * Asks the system keyboard not to learn from text fields inside [content], where the platform supports it.
 */
@Composable
expect fun NoPersonalizedLearningInput(content: @Composable () -> Unit)
