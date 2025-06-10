package me.sdi.ethwallet.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.sdi.ethwallet.R

@Composable
fun OnboardingScreen(
    onCreateWallet: () -> Unit,
    onImportWallet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface {
        Column(
            modifier =
                modifier
                    .systemBarsPadding()
                    .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            Spacer(Modifier.height(16.dp))
            Icon(
                painter = painterResource(R.drawable.eth_logo_120w),
                tint = Color.Unspecified,
                contentDescription = null,
                modifier = Modifier.width(100.dp),
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "ETH Wallet",
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineLarge,
            )

            Spacer(Modifier.height(16.dp))

            Spacer(Modifier.weight(1f))

            FilledTonalButton(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .widthIn(max = 320.dp)
                        .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                onClick = onCreateWallet,
            ) {
                Text(
                    text = "Create new wallet",
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .widthIn(max = 320.dp)
                        .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                onClick = onImportWallet,
            ) {
                Text(
                    text = "Import existing wallet",
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
