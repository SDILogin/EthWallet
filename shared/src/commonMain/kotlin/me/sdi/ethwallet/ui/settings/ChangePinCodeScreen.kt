package me.sdi.ethwallet.ui.settings

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.arrow_back_24px
import me.sdi.ethwallet.resources.back
import me.sdi.ethwallet.resources.change_pin_confirm_new
import me.sdi.ethwallet.resources.change_pin_enter_current
import me.sdi.ethwallet.resources.change_pin_enter_new
import me.sdi.ethwallet.resources.change_pin_success
import me.sdi.ethwallet.resources.change_pin_title
import me.sdi.ethwallet.resources.pin_attempts_remaining
import me.sdi.ethwallet.resources.pin_locked_subtitle
import me.sdi.ethwallet.ui.theme.EthWalletTheme
import me.sdi.ethwallet.ui.wallet.PinCodeInput
import me.sdi.ethwallet.ui.wallet.PinNumberPad
import me.sdi.ethwallet.util.rememberToastManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ChangePinCodeScreen(
    onBack: () -> Unit,
    onPinChanged: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChangePinCodeViewModel = koinViewModel(),
) {
    val toastManager = rememberToastManager()
    val successMessage = stringResource(Res.string.change_pin_success)
    LaunchedEffect(Unit) {
        viewModel.news.collect { news ->
            when (news) {
                ChangePinCodeNews.PinChanged -> {
                    toastManager.showToast(successMessage)
                    onPinChanged()
                }
                ChangePinCodeNews.LoggedOut -> onLogout()
            }
        }
    }

    val viewState by viewModel.viewState.collectAsState()
    ChangePinCodeScreen(
        viewState = viewState,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangePinCodeScreen(
    viewState: ChangePinCodeViewState,
    onEvent: (ChangePinCodeEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.change_pin_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(Res.drawable.arrow_back_24px),
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = stringResource(viewState.step.titleRes()),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )

                if (viewState.isLocked) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.pin_locked_subtitle, viewState.lockCountdown),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                PinCodeInput(
                    pinCode = viewState.enteredPin,
                    maxLength = 6,
                    isError = viewState.errorMessage != null,
                    shakeTrigger = viewState.errorId,
                )

                Spacer(modifier = Modifier.height(16.dp))

                ErrorMessage(viewState)
            }

            PinNumberPad(
                onNumberClick = { number -> onEvent(ChangePinCodeEvent.OnDigitClick(number)) },
                onDeleteClick = { onEvent(ChangePinCodeEvent.OnDeleteClick) },
                modifier = Modifier.padding(bottom = 32.dp),
                enabled = viewState.isKeypadEnabled,
            )
        }
    }
}

@Composable
private fun ErrorMessage(viewState: ChangePinCodeViewState) {
    AnimatedVisibility(
        visible = viewState.errorMessage != null,
        enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300)),
    ) {
        viewState.errorMessage?.let { errorMessage ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                if (viewState.showRemainingAttempts && viewState.remainingAttempts > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.pin_attempts_remaining, viewState.remainingAttempts),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private fun ChangePinCodeStep.titleRes() = when (this) {
    ChangePinCodeStep.EnterCurrent -> Res.string.change_pin_enter_current
    ChangePinCodeStep.EnterNew -> Res.string.change_pin_enter_new
    ChangePinCodeStep.ConfirmNew -> Res.string.change_pin_confirm_new
}

@Preview
@Composable
private fun ChangePinCodeScreen_Preview() {
    EthWalletTheme {
        ChangePinCodeScreen(
            viewState = ChangePinCodeViewState(
                step = ChangePinCodeStep.EnterNew,
                newPin = "12",
                isInitializing = false,
            ),
            onEvent = {},
            onBack = {},
        )
    }
}
