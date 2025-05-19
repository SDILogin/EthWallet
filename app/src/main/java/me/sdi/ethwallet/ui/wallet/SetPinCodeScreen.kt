package me.sdi.ethwallet.ui.wallet

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import me.sdi.ethwallet.ui.theme.EthWalletTheme

@Composable
fun PinCodeInput(
    pinCode: String,
    modifier: Modifier = Modifier,
    maxLength: Int = 6,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(maxLength) { index ->
            val isActive = index < pinCode.length
            val color by animateColorAsState(
                targetValue =
                    if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    },
                animationSpec = tween(300),
                label = "colorAnimation",
            )
            val scale by animateFloatAsState(
                targetValue = if (isActive) 1.2f else 1f,
                animationSpec = tween(300),
                label = "scaleAnimation",
            )

            Box(
                modifier =
                    Modifier
                        .size(16.dp)
                        .scale(scale)
                        .background(color, CircleShape),
            )
        }
    }
}

@Composable
fun NumberPad(
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
                NumberButton(number = number, onClick = { onNumberClick(number) })
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            (4..6).forEach { number ->
                NumberButton(number = number, onClick = { onNumberClick(number) })
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            (7..9).forEach { number ->
                NumberButton(number = number, onClick = { onNumberClick(number) })
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

            NumberButton(number = 0, onClick = { onNumberClick(0) })

            TextButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(72.dp),
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

@Composable
fun NumberButton(
    number: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.size(72.dp),
    ) {
        Text(
            text = number.toString(),
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

enum class SetPinCodeState {
    SET_PIN,
    CONFIRM_PIN,
}

@Composable
fun SetPinCodeScreen(
    onPinCodeSet: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pinState by remember { mutableStateOf(SetPinCodeState.SET_PIN) }
    var firstPin by remember { mutableStateOf("") }
    var secondPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Get current pin based on the state
    val currentPin = if (pinState == SetPinCodeState.SET_PIN) firstPin else secondPin

    // Handler for pin input completion
    LaunchedEffect(currentPin) {
        if (currentPin.length == 6) {
            delay(300) // Short delay for animation to complete

            when (pinState) {
                SetPinCodeState.SET_PIN -> {
                    pinState = SetPinCodeState.CONFIRM_PIN
                }

                SetPinCodeState.CONFIRM_PIN -> {
                    if (firstPin == secondPin) {
                        onPinCodeSet(firstPin)
                    } else {
                        errorMessage = "PINs don't match. Try again."
                        delay(1500) // Show error for a short time
                        errorMessage = null
                        pinState = SetPinCodeState.SET_PIN
                        firstPin = ""
                        secondPin = ""
                    }
                }
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
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
                    text =
                        if (pinState == SetPinCodeState.SET_PIN) {
                            "Create PIN Code"
                        } else {
                            "Confirm PIN Code"
                        },
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text =
                        if (pinState == SetPinCodeState.SET_PIN) {
                            "Set a 6-digit PIN to secure your wallet"
                        } else {
                            "Enter the same PIN again to confirm"
                        },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(32.dp))

                PinCodeInput(
                    pinCode = currentPin,
                    maxLength = 6,
                )

                Spacer(modifier = Modifier.height(16.dp))

                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // Number pad
            NumberPad(
                onNumberClick = { number ->
                    if (currentPin.length < 6) {
                        if (pinState == SetPinCodeState.SET_PIN) {
                            firstPin += number.toString()
                        } else {
                            secondPin += number.toString()
                        }
                    }
                },
                onDeleteClick = {
                    if (pinState == SetPinCodeState.SET_PIN && firstPin.isNotEmpty()) {
                        firstPin = firstPin.dropLast(1)
                    } else if (pinState == SetPinCodeState.CONFIRM_PIN && secondPin.isNotEmpty()) {
                        secondPin = secondPin.dropLast(1)
                    }
                },
                modifier = Modifier.padding(bottom = 32.dp),
            )
        }
    }
}

@Composable
@PreviewLightDark
@PreviewFontScale
@PreviewScreenSizes
private fun PreviewSetPinCodeScreen() {
    EthWalletTheme {
        SetPinCodeScreen(
            onPinCodeSet = { /* Handle pin code set */ },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
