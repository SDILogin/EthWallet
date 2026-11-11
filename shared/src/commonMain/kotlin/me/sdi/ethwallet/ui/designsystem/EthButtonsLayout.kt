package me.sdi.ethwallet.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import me.sdi.ethwallet.ui.components.FormFactorPreviews
import me.sdi.ethwallet.ui.theme.EthWalletTheme

private const val BUTTONS_PADDING_DP = 16
private const val BUTTONS_SPACING_DP = 8
private const val BUTTONS_MAX_WIDTH_DP = 420
private const val BLUR_RADIUS_DP = 12
private const val TINT_ALPHA = 0.3f

/**
 * Places [buttons] over the bottom of [content] and blurs the part of [content] under them.
 *
 * [content] gets the buttons area height as bottom padding. Pass it to `contentPadding` of a
 * lazy list so the last item can scroll above the buttons. The padding changes during layout,
 * so read it in layout, not in composition.
 *
 * Buttons are centered and no wider than the max width. Put the primary button last.
 *
 * Blur needs Android 12 or newer. Older Android versions show a translucent surface color.
 */
@Composable
fun EthButtonsLayout(
    buttons: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (contentPadding: PaddingValues) -> Unit,
) {
    val contentPadding = remember { ButtonsAreaPadding() }
    val hazeState = rememberHazeState()
    val surfaceColor = MaterialTheme.colorScheme.surface
    val hazeStyle = HazeStyle(
        backgroundColor = surfaceColor,
        tint = HazeTint(surfaceColor.copy(alpha = TINT_ALPHA)),
        blurRadius = BLUR_RADIUS_DP.dp,
    )
    Layout(
        modifier = modifier,
        content = {
            Box(modifier = Modifier.hazeSource(hazeState)) {
                content(contentPadding)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .hazeEffect(state = hazeState, style = hazeStyle)
                    .navigationBarsPadding()
                    .padding(
                        horizontal = BUTTONS_PADDING_DP.dp,
                        vertical = 8.dp,
                    )
                    .wrapContentWidth()
                    .widthIn(max = BUTTONS_MAX_WIDTH_DP.dp),
                verticalArrangement = Arrangement.spacedBy(BUTTONS_SPACING_DP.dp),
                content = buttons,
            )
        },
    ) { measurables, constraints ->
        val buttonsPlaceable = measurables[1].measure(constraints.copy(minHeight = 0))
        contentPadding.bottomDp = buttonsPlaceable.height.toDp() + 8.dp
        val contentPlaceable = measurables[0].measure(constraints)
        layout(contentPlaceable.width, contentPlaceable.height) {
            contentPlaceable.place(0, 0)
            buttonsPlaceable.place(0, contentPlaceable.height - buttonsPlaceable.height)
        }
    }
}

private class ButtonsAreaPadding : PaddingValues {
    var bottomDp by mutableStateOf(0.dp)

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 0.dp

    override fun calculateTopPadding(): Dp = 0.dp

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 0.dp

    override fun calculateBottomPadding(): Dp = bottomDp
}

@FormFactorPreviews
@Composable
private fun EthButtonsLayoutPreview() {
    EthWalletTheme(dynamicColor = false) {
        Surface {
            EthButtonsLayout(
                buttons = {
                    EthButton(
                        text = "Receive",
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                        style = EthButtonStyle.Secondary,
                    )
                    EthButton(text = "Send", onClick = {}, modifier = Modifier.fillMaxWidth())
                },
                modifier = Modifier.fillMaxSize(),
            ) { contentPadding ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                ) {
                    items(count = 30) { index ->
                        EthCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                            EthText(text = "Token ${index + 1}", modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            }
        }
    }
}
