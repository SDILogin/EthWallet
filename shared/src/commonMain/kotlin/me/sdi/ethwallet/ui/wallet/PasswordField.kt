package me.sdi.ethwallet.ui.wallet

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.isSensitiveData
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.password_hide
import me.sdi.ethwallet.resources.password_show
import me.sdi.ethwallet.resources.visibility_24px
import me.sdi.ethwallet.resources.visibility_off_24px
import me.sdi.ethwallet.ui.designsystem.EthIconButton
import me.sdi.ethwallet.ui.designsystem.EthText
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    isError: Boolean,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    modifier: Modifier,
) {
    var isVisible by rememberSaveable { mutableStateOf(false) }
    NoPersonalizedLearningInput {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { EthText(text = label) },
            modifier = modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .semantics { isSensitiveData = true },
            enabled = enabled,
            isError = isError,
            singleLine = true,
            visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(onAny = { onImeAction() }),
            trailingIcon = {
                EthIconButton(onClick = { isVisible = !isVisible }) {
                    Icon(
                        painter = painterResource(if (isVisible) Res.drawable.visibility_off_24px else Res.drawable.visibility_24px),
                        contentDescription = stringResource(if (isVisible) Res.string.password_hide else Res.string.password_show),
                    )
                }
            },
        )
    }
}
