package me.sdi.ethwallet.ui.wallet

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.backspace_24px
import me.sdi.ethwallet.resources.biometric_offer_enable
import me.sdi.ethwallet.resources.biometric_offer_message
import me.sdi.ethwallet.resources.biometric_offer_skip
import me.sdi.ethwallet.resources.biometric_offer_title
import me.sdi.ethwallet.resources.delete
import me.sdi.ethwallet.resources.pin_confirm_subtitle
import me.sdi.ethwallet.resources.pin_confirm_title
import me.sdi.ethwallet.resources.pin_create_subtitle
import me.sdi.ethwallet.resources.pin_create_title
import me.sdi.ethwallet.resources.pin_reset
import me.sdi.ethwallet.ui.components.ScrollableColumn
import me.sdi.ethwallet.ui.theme.EthWalletTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SetPinCodeScreen(
    onPinCodeSet: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SetPinCodeViewModel = koinViewModel(),
) {
    val viewState by viewModel.viewState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onEvent(SetPinCodeEvent.Reset)
        viewModel.news.collect { news ->
            when (news) {
                is SetPinCodeNews.PinCodeSet -> onPinCodeSet()
                is SetPinCodeNews.Error -> Unit
            }
        }
    }

    SetPinCodeScreen(viewState, viewModel::onEvent, modifier)

    if (viewState.showBiometricOffer) {
        BiometricOfferDialog(
            onAccept = { viewModel.onEvent(SetPinCodeEvent.OnBiometricOfferAccept) },
            onSkip = { viewModel.onEvent(SetPinCodeEvent.OnBiometricOfferSkip) },
        )
    }
}

@Composable
private fun BiometricOfferDialog(
    onAccept: () -> Unit,
    onSkip: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = {
            Text(stringResource(Res.string.biometric_offer_title))
        },
        text = {
            Text(stringResource(Res.string.biometric_offer_message))
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(stringResource(Res.string.biometric_offer_enable))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onSkip) {
                Text(stringResource(Res.string.biometric_offer_skip))
            }
        },
    )
}

@Composable
private fun SetPinCodeScreen(
    viewState: SetPinCodeViewState,
    onEvent: (SetPinCodeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentPin = if (viewState.isConfirmationMode) viewState.secondPin else viewState.firstPin

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        ScrollableColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(64.dp))
                Title(isConfirmationMode = viewState.isConfirmationMode)
                Spacer(modifier = Modifier.height(8.dp))
                Subtitle(isConfirmationMode = viewState.isConfirmationMode)
                Spacer(modifier = Modifier.height(32.dp))
                PinCodeInput(pinCode = currentPin, maxLength = 6)
                Spacer(modifier = Modifier.height(16.dp))
                ErrorMessage(viewState.errorMessage)
                Spacer(modifier = Modifier.height(16.dp))
                ResetButton(
                    visible = viewState.isConfirmationMode,
                    onClick = { onEvent(SetPinCodeEvent.OnResetClick) },
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            NumberPad(
                onNumberClick = { number -> onEvent(SetPinCodeEvent.OnDigitClick(number)) },
                onDeleteClick = { onEvent(SetPinCodeEvent.OnDeleteClick) },
                modifier = Modifier.padding(bottom = 32.dp),
            )
        }
    }
}

@Composable
private fun Title(isConfirmationMode: Boolean) {
    val textRes = if (isConfirmationMode) {
        Res.string.pin_confirm_title
    } else {
        Res.string.pin_create_title
    }

    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Subtitle(isConfirmationMode: Boolean) {
    val textRes = if (isConfirmationMode) {
        Res.string.pin_confirm_subtitle
    } else {
        Res.string.pin_create_subtitle
    }

    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ErrorMessage(errorMessage: String?) {
    var lastActiveMessage by remember { mutableStateOf("") }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            lastActiveMessage = errorMessage
        }
    }

    val alphaAnim by animateFloatAsState(if (errorMessage == null) 0f else 1f)
    val slideAnim by animateDpAsState(if (errorMessage == null) 16.dp else 0.dp)
    val slideAnimPx = with(LocalDensity.current) { slideAnim.toPx() }

    Text(
        modifier = Modifier.graphicsLayer {
            alpha = alphaAnim
            translationY = -slideAnimPx
        },
        text = lastActiveMessage,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun ResetButton(visible: Boolean, onClick: () -> Unit) {
    val alphaAnim by animateFloatAsState(if (visible) 1f else 0f)
    TextButton(onClick = onClick, enabled = visible, modifier = Modifier.graphicsLayer { alpha = alphaAnim }) {
        Text(
            text = stringResource(Res.string.pin_reset),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun NumberPad(
    onNumberClick: (Int) -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
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
                NumberButton(number = number, enabled = true, onClick = { onNumberClick(number) })
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            (4..6).forEach { number ->
                NumberButton(number = number, enabled = true, onClick = { onNumberClick(number) })
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            (7..9).forEach { number ->
                NumberButton(number = number, enabled = true, onClick = { onNumberClick(number) })
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Box(modifier = Modifier.size(72.dp)) {}

            NumberButton(number = 0, enabled = true, onClick = { onNumberClick(0) })

            IconButton(
                onClick = onDeleteClick,
                enabled = true,
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.iconButtonColors(
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

private class SetPinCodeViewStateProvider : PreviewParameterProvider<SetPinCodeViewState> {
    override val values = sequenceOf(
        SetPinCodeViewState(
            firstPin = "123",
        ),
        SetPinCodeViewState(
            firstPin = "123456",
            isConfirmationMode = true,
            secondPin = "12",
        ),
        SetPinCodeViewState(
            firstPin = "123456",
            secondPin = "123455",
            isConfirmationMode = true,
            errorMessage = "Pins do not match",
        ),
    )
}

@Preview
@Composable
private fun SetPinCodeScreen_Preview(
    @PreviewParameter(SetPinCodeViewStateProvider::class) viewState: SetPinCodeViewState,
) {
    EthWalletTheme {
        SetPinCodeScreen(
            viewState = viewState,
            onEvent = { },
        )
    }
}
