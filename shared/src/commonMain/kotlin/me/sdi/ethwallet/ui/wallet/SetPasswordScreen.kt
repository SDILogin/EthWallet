package me.sdi.ethwallet.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.sdi.ethwallet.core.security.PasswordStrength
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.password_confirm_field
import me.sdi.ethwallet.resources.password_continue
import me.sdi.ethwallet.resources.password_create_subtitle
import me.sdi.ethwallet.resources.password_create_title
import me.sdi.ethwallet.resources.password_field
import me.sdi.ethwallet.resources.password_mismatch
import me.sdi.ethwallet.resources.password_setup_failed
import me.sdi.ethwallet.resources.password_strength_fair
import me.sdi.ethwallet.resources.password_strength_strong
import me.sdi.ethwallet.resources.password_strength_weak
import me.sdi.ethwallet.resources.password_weak_warning
import me.sdi.ethwallet.ui.components.FormFactorPreviews
import me.sdi.ethwallet.ui.designsystem.EthButton
import me.sdi.ethwallet.ui.designsystem.EthText
import me.sdi.ethwallet.ui.designsystem.EthTextStyle
import me.sdi.ethwallet.ui.theme.EthWalletTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SetPasswordScreen(
    onPasswordSet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SetPasswordViewModel = koinViewModel()
    LaunchedEffect(Unit) {
        viewModel.news.collect { news ->
            when (news) {
                SetPasswordNews.PasswordSet -> onPasswordSet()
            }
        }
    }

    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    SetPasswordScreenContent(
        viewState = viewState,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}

@Composable
private fun SetPasswordScreenContent(
    viewState: SetPasswordViewState,
    onEvent: (SetPasswordEvent) -> Unit,
    modifier: Modifier,
) {
    val focusManager = LocalFocusManager.current
    Scaffold(modifier = modifier) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EthText(
                text = stringResource(Res.string.password_create_title),
                style = EthTextStyle.Headline,
                textAlign = TextAlign.Center,
            )
            EthText(
                text = stringResource(Res.string.password_create_subtitle),
                style = EthTextStyle.Body,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
            PasswordField(
                value = viewState.password,
                onValueChange = { onEvent(SetPasswordEvent.OnPasswordChange(it)) },
                label = stringResource(Res.string.password_field),
                enabled = !viewState.isSaving,
                isError = false,
                imeAction = ImeAction.Next,
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                modifier = Modifier,
            )
            PasswordStrengthMeter(strength = viewState.strength)
            PasswordField(
                value = viewState.confirmation,
                onValueChange = { onEvent(SetPasswordEvent.OnConfirmationChange(it)) },
                label = stringResource(Res.string.password_confirm_field),
                enabled = !viewState.isSaving,
                isError = viewState.isMismatch,
                imeAction = ImeAction.Done,
                onImeAction = {
                    if (viewState.canContinue) {
                        onEvent(SetPasswordEvent.OnContinueClick)
                    }
                },
                modifier = Modifier,
            )
            EthText(
                text = stringResource(Res.string.password_mismatch),
                color = MaterialTheme.colorScheme.error,
                style = EthTextStyle.Caption,
                modifier = Modifier.alpha(if (viewState.isMismatch) 1f else 0f),
            )
            EthButton(
                text = stringResource(Res.string.password_continue),
                onClick = { onEvent(SetPasswordEvent.OnContinueClick) },
                enabled = viewState.canContinue,
                loading = viewState.isSaving,
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            )
            EthText(
                text = stringResource(Res.string.password_setup_failed),
                color = MaterialTheme.colorScheme.error,
                style = EthTextStyle.Caption,
                modifier = Modifier.alpha(if (viewState.error == SetPasswordError.SetupFailed) 1f else 0f),
            )
        }
    }
}

@Composable
private fun PasswordStrengthMeter(strength: PasswordStrength?) {
    Column(
        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LinearProgressIndicator(
            progress = {
                when (strength) {
                    null -> 0f
                    PasswordStrength.Weak -> 1f / 3
                    PasswordStrength.Fair -> 2f / 3
                    PasswordStrength.Strong -> 1f
                }
            },
            color = when (strength) {
                PasswordStrength.Weak, null -> MaterialTheme.colorScheme.error
                PasswordStrength.Fair -> MaterialTheme.colorScheme.tertiary
                PasswordStrength.Strong -> MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.fillMaxWidth(),
        )
        EthText(
            text = stringResource(
                when (strength) {
                    PasswordStrength.Weak, null -> Res.string.password_strength_weak
                    PasswordStrength.Fair -> Res.string.password_strength_fair
                    PasswordStrength.Strong -> Res.string.password_strength_strong
                },
            ),
            style = EthTextStyle.Caption,
            modifier = Modifier.alpha(if (strength == null) 0f else 1f),
        )
        EthText(
            text = stringResource(Res.string.password_weak_warning),
            color = MaterialTheme.colorScheme.error,
            style = EthTextStyle.Caption,
            modifier = Modifier.alpha(if (strength == PasswordStrength.Weak) 1f else 0f),
        )
    }
}

private class SetPasswordViewStateProvider : PreviewParameterProvider<SetPasswordViewState> {
    override val values = sequenceOf(
        SetPasswordViewState(),
        SetPasswordViewState(password = "password", confirmation = "pass", strength = PasswordStrength.Weak),
        SetPasswordViewState(
            password = "correct horse battery staple",
            confirmation = "correct horse battery staple",
            strength = PasswordStrength.Strong,
        ),
        SetPasswordViewState(
            password = "correct horse battery staple",
            confirmation = "correct horse battery staple",
            strength = PasswordStrength.Strong,
            error = SetPasswordError.SetupFailed,
        ),
    )
}

@FormFactorPreviews
@Composable
private fun SetPasswordScreenContentPreview(
    @PreviewParameter(SetPasswordViewStateProvider::class) viewState: SetPasswordViewState,
) {
    EthWalletTheme {
        SetPasswordScreenContent(viewState = viewState, onEvent = {}, modifier = Modifier)
    }
}
