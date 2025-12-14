package me.sdi.ethwallet.ui.wallet

import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.sensitiveContent
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.sdi.ethwallet.R
import me.sdi.ethwallet.ui.theme.EthWalletTheme

/**
 * A card that displays a mnemonic seed phrase with a copy button.
 */
@Composable
fun MnemonicCard(
    seedPhrase: List<String>,
    modifier: Modifier = Modifier,
    onCopied: () -> Unit = {},
) {
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
    )
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val context = LocalContext.current
    val mnemonicDescription = context.getString(
        R.string.mnemonic_content_description,
        seedPhrase.size,
        seedPhrase.joinToString(separator = " ") { it },
    )
    val clipboardLabel = stringResource(R.string.mnemonic_clipboard_label)

    Card(
        modifier =
            modifier
                .sensitiveContent(isContentSensitive = true)
                .semantics {
                    contentDescription = mnemonicDescription
                }
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(16.dp),
                )
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = { /* No action on click */ },
                    onLongClick = {
                        scope.launch {
                            copyToClipboard(seedPhrase, clipboardManager, clipboardLabel)
                            onCopied()
                        }
                    },
                )
                .clip(CardDefaults.shape),
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
                    val modifier = Modifier.padding(end = 8.dp)
                    WordsColumn(0, words, textStyle, textMeasurer, modifier)
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    // second half of the mnemonic
                    val words = seedPhrase.drop(seedPhrase.size / 2)
                    val modifier = Modifier.padding(start = 8.dp)
                    WordsColumn(seedPhrase.size / 2, words, textStyle, textMeasurer, modifier)
                }
            }

            Spacer(modifier = Modifier.size(8.dp))
            Text(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = stringResource(R.string.mnemonic_long_press_to_copy),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.size(8.dp))
        }
    }
}

private suspend fun copyToClipboard(
    seedPhrase: List<String>,
    clipboard: Clipboard,
    clipboardLabel: String,
) {
    val clipData =
        ClipData(
            clipboardLabel,
            arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN),
            ClipData.Item(seedPhrase.joinToString(" ")),
        )
    clipboard.setClipEntry(ClipEntry(clipData))
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

@Composable
@PreviewFontScale
@PreviewLightDark
@Preview
private fun MnemonicCardPreview() {
    EthWalletTheme {
        Surface {
            MnemonicCard(
                seedPhrase =
                    listOf(
                        "draft",
                        "decode",
                        "demand",
                        "dare",
                        "dare",
                        "draft",
                        "decode",
                        "demand",
                        "dare",
                        "draft",
                        "decode",
                        "demand",
                    ),
            )
        }
    }
}
