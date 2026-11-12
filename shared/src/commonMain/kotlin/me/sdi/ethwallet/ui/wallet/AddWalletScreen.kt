package me.sdi.ethwallet.ui.wallet

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.arrow_back_24px
import me.sdi.ethwallet.resources.back
import me.sdi.ethwallet.resources.ic_eth_logo
import me.sdi.ethwallet.resources.onboarding_create_wallet
import me.sdi.ethwallet.resources.onboarding_import_wallet
import me.sdi.ethwallet.resources.wallets_list_add_wallet
import me.sdi.ethwallet.ui.components.FormFactorPreviews
import me.sdi.ethwallet.ui.designsystem.EthButton
import me.sdi.ethwallet.ui.designsystem.EthButtonStyle
import me.sdi.ethwallet.ui.designsystem.EthButtonsLayout
import me.sdi.ethwallet.ui.designsystem.EthIconButton
import me.sdi.ethwallet.ui.designsystem.EthText
import me.sdi.ethwallet.ui.theme.EthWalletTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val LOGO_MAX_SIZE_DP = 120

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWalletScreen(
    onUpButtonClick: () -> Unit,
    onImportWallet: () -> Unit,
    onCreateWallet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { EthText(stringResource(Res.string.wallets_list_add_wallet)) },
                navigationIcon = {
                    EthIconButton(onClick = onUpButtonClick) {
                        Icon(
                            painter = painterResource(Res.drawable.arrow_back_24px),
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        EthButtonsLayout(
            buttons = {
                EthButton(
                    text = stringResource(Res.string.onboarding_import_wallet),
                    onClick = onImportWallet,
                    modifier = Modifier.fillMaxWidth(),
                    style = EthButtonStyle.Secondary,
                )
                EthButton(
                    text = stringResource(Res.string.onboarding_create_wallet),
                    onClick = onCreateWallet,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
        ) { contentPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_eth_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .sizeIn(maxWidth = LOGO_MAX_SIZE_DP.dp, maxHeight = LOGO_MAX_SIZE_DP.dp)
                        .fillMaxSize(),
                )
            }
        }
    }
}

@FormFactorPreviews
@Composable
private fun AddWalletScreenPreview() {
    EthWalletTheme {
        AddWalletScreen(
            onUpButtonClick = {},
            onImportWallet = {},
            onCreateWallet = {},
        )
    }
}
