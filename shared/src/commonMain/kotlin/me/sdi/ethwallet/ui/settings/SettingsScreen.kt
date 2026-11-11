package me.sdi.ethwallet.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.sdi.ethwallet.core.security.AppLockMethod
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.arrow_back_24px
import me.sdi.ethwallet.resources.back
import me.sdi.ethwallet.resources.settings_biometric_invalidated
import me.sdi.ethwallet.resources.settings_biometric_not_enrolled
import me.sdi.ethwallet.resources.settings_biometric_not_supported
import me.sdi.ethwallet.resources.settings_biometric_title
import me.sdi.ethwallet.resources.settings_change_pin
import me.sdi.ethwallet.resources.settings_change_pin_description
import me.sdi.ethwallet.resources.settings_lock_app
import me.sdi.ethwallet.resources.settings_lock_app_description
import me.sdi.ethwallet.resources.settings_lock_app_description_password
import me.sdi.ethwallet.resources.settings_title
import me.sdi.ethwallet.ui.designsystem.EthIconButton
import me.sdi.ethwallet.ui.designsystem.EthText
import me.sdi.ethwallet.ui.designsystem.EthTextStyle
import me.sdi.ethwallet.ui.theme.EthWalletTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onChangePinClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    SettingsScreen(
        viewState = viewState,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onChangePinClick = onChangePinClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    viewState: SettingsViewState,
    onEvent: (SettingsEvent) -> Unit,
    onBack: () -> Unit,
    onChangePinClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { EthText(stringResource(Res.string.settings_title)) },
                navigationIcon = {
                    EthIconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(Res.drawable.arrow_back_24px),
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (viewState.appLockMethod == AppLockMethod.Pin) {
                BiometricSettingRow(viewState = viewState, onEvent = onEvent)
                ChangePinRow(onClick = onChangePinClick)
            }
            LockAppRow(appLockMethod = viewState.appLockMethod, onEvent = onEvent)
        }
    }
}

@Composable
private fun BiometricSettingRow(
    viewState: SettingsViewState,
    onEvent: (SettingsEvent) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            EthText(
                text = stringResource(Res.string.settings_biometric_title),
                style = EthTextStyle.Body,
            )
            val hint = viewState.biometricHint.toStringRes()
            if (hint != null) {
                EthText(
                    text = stringResource(hint),
                    style = EthTextStyle.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Switch(
            checked = viewState.isBiometricEnabled,
            enabled = viewState.isBiometricSwitchEnabled,
            onCheckedChange = { onEvent(SettingsEvent.OnBiometricToggle(it)) },
        )
    }
}

@Composable
private fun ChangePinRow(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        EthText(
            text = stringResource(Res.string.settings_change_pin),
            style = EthTextStyle.Body,
        )
        EthText(
            text = stringResource(Res.string.settings_change_pin_description),
            style = EthTextStyle.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LockAppRow(
    appLockMethod: AppLockMethod,
    onEvent: (SettingsEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEvent(SettingsEvent.OnLockClick) }
            .padding(16.dp),
    ) {
        EthText(
            text = stringResource(Res.string.settings_lock_app),
            style = EthTextStyle.Body,
        )
        EthText(
            text = stringResource(
                when (appLockMethod) {
                    AppLockMethod.Pin -> Res.string.settings_lock_app_description
                    AppLockMethod.Password -> Res.string.settings_lock_app_description_password
                },
            ),
            style = EthTextStyle.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun BiometricSettingsHint.toStringRes(): StringResource? = when (this) {
    BiometricSettingsHint.None -> null
    BiometricSettingsHint.NotSupported -> Res.string.settings_biometric_not_supported
    BiometricSettingsHint.NotEnrolled -> Res.string.settings_biometric_not_enrolled
    BiometricSettingsHint.Invalidated -> Res.string.settings_biometric_invalidated
}

private class SettingsViewStateProvider : PreviewParameterProvider<SettingsViewState> {
    override val values = sequenceOf(
        SettingsViewState(isBiometricEnabled = true, isBiometricSwitchEnabled = true),
        SettingsViewState(appLockMethod = AppLockMethod.Password),
    )
}

@Preview
@Composable
private fun SettingsScreen_Preview(
    @PreviewParameter(SettingsViewStateProvider::class) viewState: SettingsViewState,
) {
    EthWalletTheme {
        SettingsScreen(
            viewState = viewState,
            onEvent = {},
            onBack = {},
            onChangePinClick = {},
        )
    }
}
