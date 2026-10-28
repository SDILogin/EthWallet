package me.sdi.ethwallet.ui.settings

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.arrow_back_24px
import me.sdi.ethwallet.resources.back
import me.sdi.ethwallet.resources.change_pin_confirm_new
import me.sdi.ethwallet.resources.change_pin_enter_new
import me.sdi.ethwallet.resources.change_pin_error_mismatch
import me.sdi.ethwallet.resources.change_pin_success
import me.sdi.ethwallet.resources.change_pin_title
import me.sdi.ethwallet.resources.error_something_went_wrong
import me.sdi.ethwallet.ui.theme.EthWalletTheme
import me.sdi.ethwallet.ui.wallet.PinCodeInput
import me.sdi.ethwallet.ui.wallet.PinNumberPad
import me.sdi.ethwallet.util.rememberToastManager
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ChangePinCodeScreen(
    onBack: () -> Unit,
    onPinChanged: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChangePinCodeViewModel = koinViewModel(),
) {
    val toastManager = rememberToastManager()
    val successMessage = stringResource(Res.string.change_pin_success)
    var shakeTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        viewModel.news.collect { news ->
            when (news) {
                ChangePinCodeNews.PinChanged -> {
                    toastManager.showToast(successMessage)
                    onPinChanged()
                }
                ChangePinCodeNews.ShakePinInput -> shakeTrigger++
            }
        }
    }

    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    ChangePinCodeScreen(
        viewState = viewState,
        shakeTrigger = shakeTrigger,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangePinCodeScreen(
    viewState: ChangePinCodeViewState,
    shakeTrigger: Int,
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

                Spacer(modifier = Modifier.height(32.dp))

                PinCodeInput(
                    pinCode = viewState.enteredPin,
                    maxLength = 6,
                    isError = viewState.error != null,
                    shakeTrigger = shakeTrigger,
                )

                Spacer(modifier = Modifier.height(16.dp))

                ErrorMessage(viewState.error)
            }

            PinNumberPad(
                onNumberClick = { number -> onEvent(ChangePinCodeEvent.OnDigitClick(number)) },
                onDeleteClick = { onEvent(ChangePinCodeEvent.OnDeleteClick) },
                modifier = Modifier.padding(bottom = 32.dp),
                enabled = !viewState.isSaving,
            )
        }
    }
}

@Composable
private fun ErrorMessage(error: ChangePinCodeError?) {
    var lastActiveError by remember { mutableStateOf(error) }
    LaunchedEffect(error) {
        if (error != null) {
            lastActiveError = error
        }
    }

    val alphaAnim by animateFloatAsState(if (error == null) 0f else 1f)

    Text(
        modifier = Modifier.graphicsLayer { alpha = alphaAnim },
        text = lastActiveError?.let { stringResource(it.messageRes()) }.orEmpty(),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
}

private fun ChangePinCodeStep.titleRes(): StringResource = when (this) {
    ChangePinCodeStep.EnterNew -> Res.string.change_pin_enter_new
    ChangePinCodeStep.ConfirmNew -> Res.string.change_pin_confirm_new
}

private fun ChangePinCodeError.messageRes(): StringResource = when (this) {
    ChangePinCodeError.PinsDoNotMatch -> Res.string.change_pin_error_mismatch
    ChangePinCodeError.Unknown -> Res.string.error_something_went_wrong
}

@Preview
@Composable
private fun ChangePinCodeScreen_Preview() {
    EthWalletTheme {
        ChangePinCodeScreen(
            viewState = ChangePinCodeViewState(
                newPin = "12",
                error = ChangePinCodeError.PinsDoNotMatch,
            ),
            shakeTrigger = 0,
            onEvent = {},
            onBack = {},
        )
    }
}
