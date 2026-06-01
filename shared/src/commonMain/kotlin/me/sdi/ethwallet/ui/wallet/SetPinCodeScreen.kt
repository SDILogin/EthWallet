package me.sdi.ethwallet.ui.wallet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.backspace_24px
import me.sdi.ethwallet.resources.delete
import me.sdi.ethwallet.resources.pin_confirm_subtitle
import me.sdi.ethwallet.resources.pin_confirm_title
import me.sdi.ethwallet.resources.pin_create_subtitle
import me.sdi.ethwallet.resources.pin_create_title
import me.sdi.ethwallet.resources.pin_reset
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

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
            Box(modifier = Modifier.size(72.dp)) {
                // Empty space for layout balance
            }

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

@Composable
fun NumberButton(
    number: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(72.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onBackground,
        ),
    ) {
        Text(
            text = number.toString(),
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun SetPinCodeScreen(
    onPinCodeSet: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SetPinCodeViewModel = koinViewModel(),
) {
    val viewState by viewModel.viewState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onEvent(SetPinCodeEvent.Reset)
        viewModel.news.collect { news ->
            when (news) {
                is SetPinCodeNews.PinCodeSet -> {
                    onPinCodeSet(news.pinCode)
                }
                is SetPinCodeNews.Error -> {
                    // We're handling errors through the state already
                }
            }
        }
    }

    SetPinCodeScreen(viewState, viewModel::onEvent, modifier)
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
                        if (viewState.isConfirmationMode) {
                            stringResource(Res.string.pin_confirm_title)
                        } else {
                            stringResource(Res.string.pin_create_title)
                        },
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text =
                        if (viewState.isConfirmationMode) {
                            stringResource(Res.string.pin_confirm_subtitle)
                        } else {
                            stringResource(Res.string.pin_create_subtitle)
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

                AnimatedVisibility(
                    visible = viewState.errorMessage != null,
                    enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300)),
                ) {
                    viewState.errorMessage?.let { errorMessage ->
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                if (viewState.isConfirmationMode) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = { onEvent(SetPinCodeEvent.OnResetClick) },
                    ) {
                        Text(
                            text = stringResource(Res.string.pin_reset),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            NumberPad(
                onNumberClick = { number -> onEvent(SetPinCodeEvent.OnDigitClick(number)) },
                onDeleteClick = { onEvent(SetPinCodeEvent.OnDeleteClick) },
                modifier = Modifier.padding(bottom = 32.dp),
            )
        }
    }
}
