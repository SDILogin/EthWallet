package me.sdi.ethwallet.ui.wallet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.logout_dialog_confirm
import me.sdi.ethwallet.resources.logout_dialog_dismiss
import me.sdi.ethwallet.resources.logout_dialog_message
import me.sdi.ethwallet.resources.logout_dialog_title
import me.sdi.ethwallet.resources.pin_attempts_remaining
import me.sdi.ethwallet.resources.pin_enter_subtitle
import me.sdi.ethwallet.resources.pin_enter_title
import me.sdi.ethwallet.resources.pin_forgot
import me.sdi.ethwallet.resources.pin_locked_subtitle
import me.sdi.ethwallet.ui.designsystem.EthButton
import me.sdi.ethwallet.ui.designsystem.EthButtonStyle
import me.sdi.ethwallet.ui.designsystem.EthText
import me.sdi.ethwallet.ui.designsystem.EthTextStyle
import me.sdi.ethwallet.util.rememberToastManager
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val ERROR_AREA_HEIGHT_DP = 48

@Composable
fun VerifyPinCodeScreen(
    onLogout: () -> Unit,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: VerifyPinCodeViewModel = koinViewModel()
    val showLogoutDialog = remember { mutableStateOf(false) }

    val toastManager = rememberToastManager()
    LaunchedEffect(Unit) {
        viewModel.news.collect { news ->
            when (news) {
                VerifyPinCodeNews.Error -> toastManager.showToast("Something went wrong")
                VerifyPinCodeNews.LoggedOut -> onLogout()
                VerifyPinCodeNews.Unlocked -> onUnlocked()
            }
        }
    }

    val viewState by viewModel.viewState.collectAsState()
    VerifyPinCodeScreen(
        viewState = viewState,
        onEvent = viewModel::onEvent,
        onForgotPinClick = { showLogoutDialog.value = true },
        modifier = modifier,
    )

    if (showLogoutDialog.value) {
        LogoutConfirmationDialog(
            onConfirm = {
                showLogoutDialog.value = false
                viewModel.onEvent(VerifyPinCodeEvent.Reset)
            },
            onDismiss = {
                showLogoutDialog.value = false
            },
        )
    }
}

@Composable
private fun LogoutConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            EthText(stringResource(Res.string.logout_dialog_title))
        },
        text = {
            EthText(stringResource(Res.string.logout_dialog_message))
        },
        confirmButton = {
            EthButton(
                text = stringResource(Res.string.logout_dialog_confirm),
                onClick = onConfirm,
                style = EthButtonStyle.Text,
            )
        },
        dismissButton = {
            EthButton(
                text = stringResource(Res.string.logout_dialog_dismiss),
                onClick = onDismiss,
                style = EthButtonStyle.Secondary,
            )
        },
    )
}

@Composable
private fun VerifyPinCodeScreen(
    viewState: VerifyPinCodeViewState,
    onEvent: (VerifyPinCodeEvent) -> Unit,
    onForgotPinClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                EthText(
                    text = stringResource(Res.string.pin_enter_title),
                    style = EthTextStyle.Headline,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                EthText(
                    text = if (viewState.isLocked) {
                        stringResource(Res.string.pin_locked_subtitle, viewState.lockCountdown)
                    } else {
                        stringResource(Res.string.pin_enter_subtitle)
                    },
                    style = EthTextStyle.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(32.dp))

                PinCodeInput(
                    pinCode = viewState.enteredPin,
                    maxLength = 6,
                    isError = viewState.errorMessage != null,
                    shakeTrigger = viewState.failedAttemptId,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier.height(ERROR_AREA_HEIGHT_DP.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AnimatedVisibility(
                        visible = viewState.errorMessage != null,
                        enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
                        exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300)),
                    ) {
                        viewState.errorMessage?.let { errorMessage ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                EthText(
                                    text = errorMessage,
                                    color = MaterialTheme.colorScheme.error,
                                    style = EthTextStyle.Body,
                                )
                                if (viewState.showRemainingAttempts && viewState.remainingAttempts > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    EthText(
                                        text = stringResource(
                                            Res.string.pin_attempts_remaining,
                                            viewState.remainingAttempts,
                                        ),
                                        color = MaterialTheme.colorScheme.error,
                                        style = EthTextStyle.Caption,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            EthButton(
                text = stringResource(Res.string.pin_forgot),
                onClick = onForgotPinClick,
                enabled = !viewState.isLoggingOut,
                style = EthButtonStyle.Text,
            )

            PinNumberPad(
                onNumberClick = { number -> onEvent(VerifyPinCodeEvent.OnDigitClick(number)) },
                onDeleteClick = { onEvent(VerifyPinCodeEvent.OnDeleteClick) },
                modifier = Modifier.padding(bottom = 32.dp),
                enabled = !viewState.isLocked && !viewState.isVerifying,
                onBiometricClick = if (viewState.isBiometricAvailable) {
                    { onEvent(VerifyPinCodeEvent.OnBiometricClick) }
                } else {
                    null
                },
                biometricEnabled = !viewState.isVerifying,
            )
        }
    }
}
