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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import me.sdi.ethwallet.resources.backspace_24px
import me.sdi.ethwallet.resources.delete
import me.sdi.ethwallet.resources.fingerprint_24px
import me.sdi.ethwallet.resources.logout_dialog_confirm
import me.sdi.ethwallet.resources.logout_dialog_dismiss
import me.sdi.ethwallet.resources.logout_dialog_message
import me.sdi.ethwallet.resources.logout_dialog_title
import me.sdi.ethwallet.resources.pin_attempts_remaining
import me.sdi.ethwallet.resources.pin_biometric_button
import me.sdi.ethwallet.resources.pin_enter_subtitle
import me.sdi.ethwallet.resources.pin_enter_title
import me.sdi.ethwallet.resources.pin_forgot
import me.sdi.ethwallet.resources.pin_locked_subtitle
import me.sdi.ethwallet.util.ToastManager
import me.sdi.ethwallet.util.rememberToastManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

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
            when(news) {
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
            Text(stringResource(Res.string.logout_dialog_title))
        },
        text = {
            Text(stringResource(Res.string.logout_dialog_message))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.logout_dialog_confirm))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(Res.string.logout_dialog_dismiss))
            }
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
                    text = stringResource(Res.string.pin_enter_title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (viewState.isLocked) {
                        stringResource(Res.string.pin_locked_subtitle, viewState.lockCountdown)
                    } else {
                        stringResource(Res.string.pin_enter_subtitle)
                    },
                    style = MaterialTheme.typography.bodyLarge,
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
                            if (viewState.showRemainingAttempts && viewState.remainingAttempts > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(
                                        Res.string.pin_attempts_remaining,
                                        viewState.remainingAttempts,
                                    ),
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
                    onClick = onForgotPinClick,
                    modifier = Modifier.fillMaxWidth(0.8f),
                    enabled = !viewState.isLoggingOut,
                ) {
                    Text(
                        text = stringResource(Res.string.pin_forgot),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                VerifyNumberPad(
                    onNumberClick = { number ->
                        onEvent(VerifyPinCodeEvent.OnDigitClick(number))
                    },
                    onDeleteClick = {
                        onEvent(VerifyPinCodeEvent.OnDeleteClick)
                    },
                    showBiometricButton = viewState.isBiometricAvailable,
                    onBiometricClick = {
                        onEvent(VerifyPinCodeEvent.OnBiometricClick)
                    },
                    modifier = Modifier.padding(bottom = 16.dp),
                    enabled = !viewState.isLocked && !viewState.isVerifying,
                    biometricEnabled = !viewState.isVerifying,
                )
            }
        }
    }
}

@Composable
private fun VerifyNumberPad(
    onNumberClick: (Int) -> Unit,
    onDeleteClick: () -> Unit,
    showBiometricButton: Boolean,
    onBiometricClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    biometricEnabled: Boolean = enabled,
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
            if (showBiometricButton) {
                TextButton(
                    onClick = onBiometricClick,
                    modifier = Modifier.size(72.dp),
                    enabled = biometricEnabled,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.fingerprint_24px),
                        contentDescription = stringResource(Res.string.pin_biometric_button),
                        modifier = Modifier.size(28.dp),
                    )
                }
            } else {
                Box(modifier = Modifier.size(72.dp))
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
                    painter = painterResource(Res.drawable.backspace_24px),
                    contentDescription = stringResource(Res.string.delete),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}
