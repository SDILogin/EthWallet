package me.sdi.ethwallet.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
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
import me.sdi.ethwallet.ui.theme.EthWalletTheme

/**
 * A card that displays a mnemonic seed phrase.
 */
@Composable
fun MnemonicCard(
    seedPhrase: List<String>,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.bodyMedium
    Card(
        modifier =
            modifier
                .sensitiveContent(isContentSensitive = true)
                .semantics {
                    contentDescription =
                        "${seedPhrase.size} words mnemonic. ${seedPhrase.joinToString(separator = " ") { it }}"
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
                .padding(16.dp),
    ) {
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
                val modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp)
                WordsColumn(words, textStyle, textMeasurer, modifier)
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                // second half of the mnemonic
                val words = seedPhrase.drop(seedPhrase.size / 2)
                val modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp)
                WordsColumn(words, textStyle, textMeasurer, modifier)
            }
        }
    }
}

@Composable
private fun WordsColumn(
    words: List<String>,
    textStyle: TextStyle,
    textMeasurer: TextMeasurer,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        words.forEachIndexed { index, word ->
            IndexedText(
                index = index,
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
                        "word1",
                        "word2",
                        "word3",
                        "word4",
                        "word5",
                        "word6",
                        "word7",
                        "word8",
                        "word9",
                        "word10",
                        "word11",
                        "word12",
                    ),
            )
        }
    }
}
