package me.sdi.ethwallet.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.ensureActive
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.security.DataCleaner
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.ic_eth_logo
import me.sdi.ethwallet.resources.onboarding_title
import me.sdi.ethwallet.resources.pin_create_title
import me.sdi.ethwallet.ui.components.ScrollableColumn
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin

@Composable
fun OnboardingScreen(
    onCreatePinClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val koin = getKoin()
    LaunchedEffect(Unit) {
        val dataCleaner = koin.get<DataCleaner>()
        try {
            dataCleaner.clean()
        } catch (e: Exception) {
            ensureActive()

            Logger.warn("Onboarding", e) { "Failed to clean data" }
        }
    }

    Scaffold(modifier = modifier) { paddingValues ->
        ScrollableColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            Spacer(Modifier.height(16.dp))
            Icon(
                painter = painterResource(Res.drawable.ic_eth_logo),
                tint = Color.Unspecified,
                contentDescription = null,
                modifier = Modifier.width(100.dp),
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.onboarding_title),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineLarge,
            )

            Spacer(Modifier.height(16.dp))

            Spacer(Modifier.weight(1f))

            FilledTonalButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .widthIn(max = 320.dp)
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                onClick = onCreatePinClick,
            ) {
                Text(
                    text = stringResource(Res.string.pin_create_title),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
