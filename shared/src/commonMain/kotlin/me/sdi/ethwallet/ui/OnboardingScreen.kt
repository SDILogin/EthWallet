package me.sdi.ethwallet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.ensureActive
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.security.AppLockMethod
import me.sdi.ethwallet.core.security.DataCleaner
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.ic_eth_logo
import me.sdi.ethwallet.resources.onboarding_title
import me.sdi.ethwallet.resources.password_create_button
import me.sdi.ethwallet.resources.pin_create_title
import me.sdi.ethwallet.ui.components.FormFactorPreviews
import me.sdi.ethwallet.ui.designsystem.EthButton
import me.sdi.ethwallet.ui.designsystem.EthButtonsLayout
import me.sdi.ethwallet.ui.designsystem.EthText
import me.sdi.ethwallet.ui.designsystem.EthTextStyle
import me.sdi.ethwallet.ui.theme.EthWalletTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin

@Composable
fun OnboardingScreen(
    onContinueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val koin = getKoin()
    val appLockMethod = remember(koin) { koin.get<AppLockMethod>() }
    LaunchedEffect(Unit) {
        val dataCleaner = koin.get<DataCleaner>()
        try {
            dataCleaner.clean()
        } catch (e: Exception) {
            ensureActive()

            Logger.warn("Onboarding", e) { "Failed to clean data" }
        }
    }

    OnboardingScreenContent(
        appLockMethod = appLockMethod,
        onContinueClick = onContinueClick,
        modifier = modifier,
    )
}

@Composable
private fun OnboardingScreenContent(
    appLockMethod: AppLockMethod,
    onContinueClick: () -> Unit,
    modifier: Modifier,
) {
    Scaffold(modifier = modifier) { paddingValues ->
        EthButtonsLayout(
            buttons = {
                EthButton(
                    text = stringResource(
                        when (appLockMethod) {
                            AppLockMethod.Pin -> Res.string.pin_create_title
                            AppLockMethod.Password -> Res.string.password_create_button
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onContinueClick,
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_eth_logo),
                    tint = Color.Unspecified,
                    contentDescription = null,
                    modifier = Modifier.width(100.dp),
                )

                Spacer(Modifier.height(16.dp))

                EthText(
                    text = stringResource(Res.string.onboarding_title),
                    textAlign = TextAlign.Center,
                    style = EthTextStyle.Headline,
                )
            }
        }
    }
}

private class AppLockMethodProvider : PreviewParameterProvider<AppLockMethod> {
    override val values = AppLockMethod.entries.asSequence()
}

@FormFactorPreviews
@Composable
private fun OnboardingScreenContentPreview(
    @PreviewParameter(AppLockMethodProvider::class) appLockMethod: AppLockMethod,
) {
    EthWalletTheme {
        OnboardingScreenContent(
            appLockMethod = appLockMethod,
            onContinueClick = {},
            modifier = Modifier,
        )
    }
}
