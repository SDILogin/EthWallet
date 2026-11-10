package me.sdi.ethwallet.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.ic_eth_logo
import me.sdi.ethwallet.resources.logout_dialog_confirm
import me.sdi.ethwallet.resources.logout_dialog_dismiss
import me.sdi.ethwallet.resources.logout_dialog_title
import me.sdi.ethwallet.resources.password_enter_title
import me.sdi.ethwallet.resources.password_field
import me.sdi.ethwallet.resources.password_forgot
import me.sdi.ethwallet.resources.password_logout_dialog_message
import me.sdi.ethwallet.resources.password_reset_failed
import me.sdi.ethwallet.resources.password_unlock
import me.sdi.ethwallet.resources.password_unlock_failed
import me.sdi.ethwallet.resources.password_wrong
import me.sdi.ethwallet.ui.components.FormFactorPreviews
import me.sdi.ethwallet.ui.theme.EthWalletTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PasswordUnlockScreen(
    onUnlocked: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PasswordUnlockViewModel = koinViewModel()
    var showLogoutDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.news.collect { news ->
            when (news) {
                PasswordUnlockNews.Unlocked -> onUnlocked()
                PasswordUnlockNews.LoggedOut -> onLogout()
            }
        }
    }

    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    PasswordUnlockScreenContent(
        viewState = viewState,
        onEvent = viewModel::onEvent,
        onForgotPasswordClick = { showLogoutDialog = true },
        modifier = modifier,
    )

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(Res.string.logout_dialog_title)) },
            text = { Text(stringResource(Res.string.password_logout_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.onEvent(PasswordUnlockEvent.OnResetConfirm)
                    },
                ) {
                    Text(stringResource(Res.string.logout_dialog_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(Res.string.logout_dialog_dismiss))
                }
            },
        )
    }
}

@Composable
private fun PasswordUnlockScreenContent(
    viewState: PasswordUnlockViewState,
    onEvent: (PasswordUnlockEvent) -> Unit,
    onForgotPasswordClick: () -> Unit,
    modifier: Modifier,
) {
    val canUnlock = viewState.password.isNotEmpty() && !viewState.isBusy
    Scaffold(modifier = modifier) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_eth_logo),
                tint = Color.Unspecified,
                contentDescription = null,
                modifier = Modifier.width(72.dp),
            )
            Text(
                text = stringResource(Res.string.password_enter_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            PasswordField(
                value = viewState.password,
                onValueChange = { onEvent(PasswordUnlockEvent.OnPasswordChange(it)) },
                label = stringResource(Res.string.password_field),
                enabled = !viewState.isBusy,
                isError = viewState.error == PasswordUnlockError.WrongPassword,
                imeAction = ImeAction.Done,
                onImeAction = {
                    if (canUnlock) {
                        onEvent(PasswordUnlockEvent.OnUnlockClick)
                    }
                },
                modifier = Modifier,
            )
            Text(
                text = stringResource(
                    when (viewState.error) {
                        PasswordUnlockError.WrongPassword, null -> Res.string.password_wrong
                        PasswordUnlockError.UnlockFailed -> Res.string.password_unlock_failed
                        PasswordUnlockError.ResetFailed -> Res.string.password_reset_failed
                    },
                ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .alpha(if (viewState.error == null) 0f else 1f),
            )
            Button(
                onClick = { onEvent(PasswordUnlockEvent.OnUnlockClick) },
                enabled = canUnlock,
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                if (viewState.isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text(stringResource(Res.string.password_unlock))
                }
            }
            TextButton(
                onClick = onForgotPasswordClick,
                enabled = !viewState.isBusy,
            ) {
                Text(stringResource(Res.string.password_forgot))
            }
        }
    }
}

private class PasswordUnlockViewStateProvider : PreviewParameterProvider<PasswordUnlockViewState> {
    override val values = sequenceOf(
        PasswordUnlockViewState(),
        PasswordUnlockViewState(password = "password", isBusy = true),
        PasswordUnlockViewState(password = "password", error = PasswordUnlockError.WrongPassword),
        PasswordUnlockViewState(password = "password", error = PasswordUnlockError.UnlockFailed),
    )
}

@FormFactorPreviews
@Composable
private fun PasswordUnlockScreenContentPreview(
    @PreviewParameter(PasswordUnlockViewStateProvider::class) viewState: PasswordUnlockViewState,
) {
    EthWalletTheme {
        PasswordUnlockScreenContent(
            viewState = viewState,
            onEvent = {},
            onForgotPasswordClick = {},
            modifier = Modifier,
        )
    }
}
