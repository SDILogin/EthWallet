package me.sdi.ethwallet.ui.wallet

import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun NoPersonalizedLearningInput(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { request, nextHandler ->
            nextHandler.startInputMethod(
                PlatformTextInputMethodRequest { outAttributes ->
                    // The delegate assigns imeOptions, so the flag must be added after it runs.
                    request.createInputConnection(outAttributes).also {
                        outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                    }
                },
            )
        },
        content = content,
    )
}
