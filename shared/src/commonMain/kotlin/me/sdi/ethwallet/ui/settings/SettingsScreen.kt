package me.sdi.ethwallet.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.arrow_back_24px
import me.sdi.ethwallet.resources.back
import me.sdi.ethwallet.resources.settings_biometric_invalidated
import me.sdi.ethwallet.resources.settings_biometric_not_enrolled
import me.sdi.ethwallet.resources.settings_biometric_not_supported
import me.sdi.ethwallet.resources.settings_biometric_title
import me.sdi.ethwallet.resources.settings_lock_app
import me.sdi.ethwallet.resources.settings_lock_app_description
import me.sdi.ethwallet.resources.settings_title
import me.sdi.ethwallet.ui.theme.EthWalletTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val viewState by viewModel.viewState.collectAsState()
    SettingsScreen(
        viewState = viewState,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    viewState: SettingsViewState,
    onEvent: (SettingsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_title)) },
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
        Column(modifier = Modifier.padding(innerPadding)) {
            BiometricSettingRow(viewState = viewState, onEvent = onEvent)
            LockAppRow(onEvent = onEvent)
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
            Text(
                text = stringResource(Res.string.settings_biometric_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            val hint = viewState.biometricHint.toStringRes()
            if (hint != null) {
                Text(
                    text = stringResource(hint),
                    style = MaterialTheme.typography.bodySmall,
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
private fun LockAppRow(onEvent: (SettingsEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEvent(SettingsEvent.OnLockClick) }
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.settings_lock_app),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(Res.string.settings_lock_app_description),
            style = MaterialTheme.typography.bodySmall,
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

@Preview
@Composable
private fun SettingsScreen_Preview() {
    EthWalletTheme {
        SettingsScreen(
            viewState = SettingsViewState(isBiometricEnabled = true, isBiometricSwitchEnabled = true),
            onEvent = {},
            onBack = {},
        )
    }
}
