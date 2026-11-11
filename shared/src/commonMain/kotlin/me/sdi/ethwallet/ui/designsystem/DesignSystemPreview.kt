package me.sdi.ethwallet.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.sdi.ethwallet.ui.theme.EthWalletTheme

@Preview(name = "Design system", showBackground = true, heightDp = 900)
@Composable
fun DesignSystemPreview() {
    EthWalletTheme(dynamicColor = false) {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EthCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        EthText(text = "Headline 1,234.56", style = EthTextStyle.Headline)
                        EthText(text = "Title Ethereum", style = EthTextStyle.Title)
                        EthText(text = "Body The quick brown fox", style = EthTextStyle.Body)
                        EthText(text = "Caption Mainnet", style = EthTextStyle.Caption)
                    }
                }
                EthCard(modifier = Modifier.fillMaxWidth(), tone = EthCardTone.Error) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        EthText(text = "Error card", style = EthTextStyle.Title)
                        EthText(text = "Something went wrong")
                    }
                }
                EthCard(modifier = Modifier.fillMaxWidth(), onClick = {}) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        EthText(text = "Clickable card", style = EthTextStyle.Title)
                        EthText(text = "Tap to open", style = EthTextStyle.Caption)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EthButton(text = "Primary", onClick = {})
                    EthButton(text = "Secondary", onClick = {}, style = EthButtonStyle.Secondary)
                    EthButton(text = "Text", onClick = {}, style = EthButtonStyle.Text)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EthButton(text = "Disabled", onClick = {}, enabled = false)
                    EthButton(text = "Disabled", onClick = {}, style = EthButtonStyle.Secondary, enabled = false)
                    EthButton(text = "Disabled", onClick = {}, style = EthButtonStyle.Text, enabled = false)
                }
            }
        }
    }
}
