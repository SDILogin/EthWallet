package me.sdi.ethwallet.ui.receive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import me.sdi.ethwallet.ui.theme.EthWalletTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Receive") },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Receive Screen",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

@Composable
@PreviewLightDark
fun ReceiveScreenPreview() {
    EthWalletTheme {
        ReceiveScreen()
    }
}