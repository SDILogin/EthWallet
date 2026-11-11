package me.sdi.ethwallet.ui.designsystem

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/**
 * The only text styles of the app.
 * Headline: balances and screen titles. Title: names and section headers.
 * Body: default text. Caption: secondary hints.
 */
enum class EthTextStyle(internal val textStyle: TextStyle) {
    Headline(TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)),
    Title(TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)),
    Body(TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal)),
    Caption(TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)),
}

/**
 * The only text composable of the app. Color falls back to the content color of the parent.
 */
@Composable
fun EthText(
    text: String,
    modifier: Modifier = Modifier,
    style: EthTextStyle = EthTextStyle.Body,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
    autoSize: TextAutoSize? = null,
) {
    val resolvedColor = if (color == Color.Unspecified) LocalContentColor.current else color
    BasicText(
        text = text,
        modifier = modifier,
        style = style.textStyle.copy(color = resolvedColor, textAlign = textAlign ?: TextAlign.Unspecified),
        overflow = overflow,
        maxLines = maxLines,
        minLines = minLines,
        autoSize = autoSize,
    )
}
