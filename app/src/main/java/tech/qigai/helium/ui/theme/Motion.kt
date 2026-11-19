package tech.qigai.helium.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Global motion switches. Infinite (ambient) animations are off when the system disables animations. */
object HeMotion {
    /** Tests flip this off: endlessly repeating animations never let a test clock go idle. */
    @Volatile
    var infiniteAnimations: Boolean = true
}

@Composable
fun rememberAmbientMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        HeMotion.infiniteAnimations &&
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

/** A looping float for ambient motion (orb spin, thinking wave); returns [initial] when motion is off. */
@Composable
fun ambientFloat(initial: Float, target: Float, durationMs: Int, reverse: Boolean = false, enabled: Boolean = rememberAmbientMotion()): Float {
    if (!enabled) return initial
    val t = rememberInfiniteTransition(label = "ambient")
    val v by t.animateFloat(
        initialValue = initial,
        targetValue = target,
        animationSpec = infiniteRepeatable(
            tween(durationMs, easing = if (reverse) FastOutSlowInEasing else LinearEasing),
            if (reverse) RepeatMode.Reverse else RepeatMode.Restart,
        ),
        label = "ambientValue",
    )
    return v
}
