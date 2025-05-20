package me.sdi.ethwallet.ui.wallet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.sdi.ethwallet.ui.theme.EthWalletTheme

/**
 * Events that can be handled by the VerifyPinCodeViewModel.
 */
sealed interface VerifyPinCodeEvent {
    /**
     * User clicked a digit button
     */
    data class OnDigitClick(val digit: Int) : VerifyPinCodeEvent

    /**
     * User clicked the delete button
     */
    data object OnDeleteClick : VerifyPinCodeEvent

    /**
     * User clicked the logout button
     */
    data object OnLogoutClick : VerifyPinCodeEvent
}

@Composable
fun VerifyPinCodeScreen(
    onPinVerified: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel<VerifyPinCodeViewModel>(
        factory = VerifyPinCodeViewModel.Factory,
    )
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.news.collect { news ->
            when (news) {
                is VerifyPinCodeNews.PinVerified -> onPinVerified()
                is VerifyPinCodeNews.LoggedOut -> onLogout()
                is VerifyPinCodeNews.Error -> {
                    // Error already shown in the UI
                }
            }
        }
    }

    VerifyPinCodeScreen(
        viewState = viewState,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}

@Composable
private fun VerifyPinCodeScreen(
    viewState: VerifyPinCodeViewState,
    onEvent: (VerifyPinCodeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(64.dp))

                Text(
                    text = "Enter PIN Code",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (viewState.isLocked) {
                        "Too many attempts. Try again in ${viewState.lockCountdown} seconds"
                    } else {
                        "Enter your 6-digit PIN to access your wallet"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(32.dp))

                PinCodeInput(
                    pinCode = viewState.enteredPin,
                    maxLength = 6,
                )

                Spacer(modifier = Modifier.height(16.dp))

                AnimatedVisibility(
                    visible = viewState.errorMessage != null,
                    enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300)),
                ) {
                    viewState.errorMessage?.let { errorMessage ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (viewState.remainingAttempts > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Attempts remaining: ${viewState.remainingAttempts}",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TextButton(
                    onClick = { onEvent(VerifyPinCodeEvent.OnLogoutClick) },
                    modifier = Modifier.fillMaxWidth(0.8f),
                ) {
                    Text(text = "Forgot PIN Code?", style = MaterialTheme.typography.bodyLarge)
                }

                NumberPad(
                    onNumberClick = { number ->
                        onEvent(VerifyPinCodeEvent.OnDigitClick(number))
                    },
                    onDeleteClick = {
                        onEvent(VerifyPinCodeEvent.OnDeleteClick)
                    },
                    modifier = Modifier.padding(bottom = 16.dp),
                    enabled = !viewState.isLocked && !viewState.isVerifying,
                )
            }
        }
    }
}

@Composable
private fun NumberPad(
    onNumberClick: (Int) -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            (1..3).forEach { number ->
                NumberButton(number = number, onClick = { onNumberClick(number) }, enabled = enabled)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            (4..6).forEach { number ->
                NumberButton(number = number, onClick = { onNumberClick(number) }, enabled = enabled)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            (7..9).forEach { number ->
                NumberButton(number = number, onClick = { onNumberClick(number) }, enabled = enabled)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Box(modifier = Modifier.size(72.dp)) {
                // Empty space for layout balance
            }

            NumberButton(number = 0, onClick = { onNumberClick(0) }, enabled = enabled)

            TextButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(72.dp),
                enabled = enabled,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Delete",
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

private class VerifyPinCodeScreenPreviewParameter : PreviewParameterProvider<VerifyPinCodeViewState> {
    override val values = sequenceOf(
        VerifyPinCodeViewState(
            enteredPin = "123",
            errorMessage = "Incorrect PIN code",
            remainingAttempts = 2,
        ),
        VerifyPinCodeViewState(
            enteredPin = "",
            errorMessage = "Too many failed attempts",
            remainingAttempts = 0,
            isLocked = true,
            lockCountdown = 30,
        ),
    )
}

@Composable
@PreviewLightDark
@PreviewFontScale
@PreviewScreenSizes
private fun PreviewVerifyPinCodeScreen(
    @PreviewParameter(VerifyPinCodeScreenPreviewParameter::class)
    viewState: VerifyPinCodeViewState,
) {
    EthWalletTheme {
        VerifyPinCodeScreen(
            viewState = viewState,
            onEvent = { /* Handle event */ },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
