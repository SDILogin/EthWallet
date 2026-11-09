package me.sdi.ethwallet.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.backspace_24px
import me.sdi.ethwallet.resources.delete
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun PinNumberPad(
    onNumberClick: (Int) -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            (1..3).forEach { number ->
                NumberButton(number = number, enabled = enabled, onClick = { onNumberClick(number) })
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            (4..6).forEach { number ->
                NumberButton(number = number, enabled = enabled, onClick = { onNumberClick(number) })
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            (7..9).forEach { number ->
                NumberButton(number = number, enabled = enabled, onClick = { onNumberClick(number) })
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Box(modifier = Modifier.size(72.dp)) {}

            NumberButton(number = 0, enabled = enabled, onClick = { onNumberClick(0) })

            IconButton(
                onClick = onDeleteClick,
                enabled = enabled,
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.backspace_24px),
                    contentDescription = stringResource(Res.string.delete),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}
