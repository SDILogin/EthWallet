package me.sdi.ethwallet.ui.wallet

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.style.ExperimentalFoundationStyleApi
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.mnemonic_content_description
import me.sdi.ethwallet.resources.mnemonic_long_press_to_copy
import me.sdi.ethwallet.ui.theme.EthWalletTheme
import org.jetbrains.compose.resources.stringResource

/**
 * A card that displays a mnemonic seed phrase with a copy button.
 */
@OptIn(ExperimentalFoundationStyleApi::class)
@Composable
fun MnemonicCard(
    seedPhrase: List<String>,
    modifier: Modifier = Modifier,
    onCopied: () -> Unit = {},
    style: Style = Style,
) {
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
    )
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) {
        it.isEnabled = true
    }
    val mnemonicDescription = stringResource(
        Res.string.mnemonic_content_description,
        seedPhrase.size,
        seedPhrase.joinToString(separator = " ") { it },
    )

    Box(
        modifier =
            modifier
                .semantics {
                    contentDescription = mnemonicDescription
                }
                .styleable(styleState, EthWalletTheme.styles.mnemonicCardStyle, style)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = { /* No action on click */ },
                    onLongClick = {
                        scope.launch {
                            clipboardManager.setText(AnnotatedString(seedPhrase.joinToString(" ")))
                            onCopied()
                        }
                    },
                ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.size(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    // first half of the mnemonic
                    val words = seedPhrase.take(seedPhrase.size / 2)
                    val wordModifier = Modifier.padding(end = 8.dp)
                    WordsColumn(0, words, textStyle, textMeasurer, wordModifier)
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    // second half of the mnemonic
                    val words = seedPhrase.drop(seedPhrase.size / 2)
                    val wordModifier = Modifier.padding(start = 8.dp)
                    WordsColumn(seedPhrase.size / 2, words, textStyle, textMeasurer, wordModifier)
                }
            }

            Spacer(modifier = Modifier.size(8.dp))
            Text(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = stringResource(Res.string.mnemonic_long_press_to_copy),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.size(8.dp))
        }
    }
}

@Composable
private fun WordsColumn(
    indexOffset: Int,
    words: List<String>,
    textStyle: TextStyle,
    textMeasurer: TextMeasurer,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        words.forEachIndexed { index, word ->
            IndexedText(
                index = index + indexOffset,
                word = word,
                textStyle = textStyle,
                textMeasurer = textMeasurer,
            )
        }
    }
}

@Composable
private fun IndexedText(
    index: Int,
    word: String,
    textStyle: TextStyle,
    textMeasurer: TextMeasurer,
) {
    Text(
        text = word,
        style = textStyle,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .clearAndSetSemantics { }
                .drawWithContent {
                    drawContent()

                    val prefix = "${index + 1}. "
                    val textLayoutResult =
                        textMeasurer.measure(
                            text = prefix,
                            style = textStyle,
                        )
                    val textWidth = textLayoutResult.size.width
                    drawText(
                        textMeasurer = textMeasurer,
                        style = textStyle,
                        text = prefix,
                        topLeft =
                            Offset(
                                x = -textWidth.toFloat(),
                                y = 0.toFloat(),
                            ),
                    )
                },
    )
}

@OptIn(ExperimentalFoundationStyleApi::class)
@Preview
@Composable
private fun MnemonicCardPreview() {
    EthWalletTheme {
        MnemonicCard(
            seedPhrase = listOf(
                "abandon",
                "ability",
                "able",
                "about",
                "above",
                "absent",
                "absorb",
                "abstract",
                "absurd",
                "abuse",
                "access",
                "accident",
            ),
        )
    }
}
