package me.sdi.ethwallet.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalInspectionMode

private const val SCREEN_TRANSITION_MS = 150
private const val EXIT_FADE_MIN_ALPHA = 0.1f

/**
 * Start and exit animation of a screen. The start animation runs one time, when the screen opens.
 * A preview shows the screen after the start animation.
 */
@Composable
fun rememberScreenTransition(): ScreenTransition {
    val isPreview = LocalInspectionMode.current
    val started = rememberSaveable { mutableStateOf(isPreview) }
    LaunchedEffect(Unit) { started.value = true }
    val exitAction = remember { mutableStateOf<(() -> Unit)?>(null) }
    val progress = animateFloatAsState(
        targetValue = if (started.value && exitAction.value == null) 1f else 0f,
        animationSpec = tween(SCREEN_TRANSITION_MS),
        finishedListener = { exitAction.value?.invoke() },
    )
    return remember { ScreenTransition(started, exitAction, progress) }
}

@Stable
class ScreenTransition internal constructor(
    private val started: State<Boolean>,
    private val exitAction: MutableState<(() -> Unit)?>,
    private val progressState: State<Float>,
) {
    /** False before the start animation and after [exit]. Pass it to `buttonsVisible` of `EthButtonsLayout`. */
    val contentVisible: Boolean get() = started.value && exitAction.value == null

    /** From 0 (hidden) to 1 (shown). Read it in a draw or layer block to skip recomposition. */
    val progress: Float get() = progressState.value

    /** Alpha for screen content. It starts at 0, and the exit animation stops at a low visible value. */
    val fadeAlpha: Float
        get() = if (exitAction.value == null) {
            progress
        } else {
            EXIT_FADE_MIN_ALPHA + (1f - EXIT_FADE_MIN_ALPHA) * progress
        }

    /** Plays the exit animation, then calls [action]. Calls during the exit animation do nothing. */
    fun exit(action: () -> Unit) {
        if (exitAction.value == null) {
            exitAction.value = action
        }
    }
}
