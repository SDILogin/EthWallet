package me.sdi.ethwallet.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieClipSpec
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieAnimatable
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import me.sdi.ethwallet.resources.Res

private const val INTRO_END_FRAME = 60

/**
 * Ethereum logo that assembles one time and then floats in a loop.
 *
 * The logo is three quarters of the height of the square drawing area.
 */
@Composable
fun AnimatedEthLogo(modifier: Modifier) {
    val composition by rememberLottieComposition {
        LottieCompositionSpec.JsonString(Res.readBytes("files/eth_logo.json").decodeToString())
    }
    val animatable = rememberLottieAnimatable()
    LaunchedEffect(composition) {
        composition?.let { loaded ->
            animatable.animate(
                composition = loaded,
                clipSpec = LottieClipSpec.Frame(min = 0, max = INTRO_END_FRAME),
            )
            animatable.animate(
                composition = loaded,
                clipSpec = LottieClipSpec.Frame(min = INTRO_END_FRAME, max = null),
                iterations = Compottie.IterateForever,
            )
        }
    }
    Image(
        painter = rememberLottiePainter(
            composition = composition,
            progress = { animatable.progress },
            enableExpressions = false,
        ),
        contentDescription = null,
        modifier = modifier,
    )
}
