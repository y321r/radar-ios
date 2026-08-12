package com.radar.news.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.radar.news.ui.theme.RadarColors

/**
 * The ECG waveform in a 0..100 box, y growing downward: a P bump, the QRS complex, then a
 * T bump. Shared geometry with `res/drawable/ic_pulse.xml`, so this logo, the splash screen
 * and the notification glyph are the same mark.
 *
 * The **launcher** icon is no longer this waveform — it is the designed رادار wordmark in
 * `art/icon-master-1024.png`. If the wordmark should replace the waveform in-app too, this
 * and `ic_pulse.xml` are the two places to change.
 */
private val PULSE_POINTS = listOf(
    6f to 50f, 28f to 50f,
    32f to 44f, 36f to 50f,
    42f to 50f,
    46f to 60f, 52f to 16f, 58f to 68f,
    64f to 50f, 70f to 50f,
    75f to 43f, 80f to 50f,
    94f to 50f,
)

/** A full heartbeat cycle: two beats then a rest, matching a resting pulse. */
private const val CYCLE_MILLIS = 1800

/** The static mark, no animation — for the empty state and anywhere a still glyph is wanted. */
@Composable
fun PulseGlyph(
    modifier: Modifier = Modifier,
    color: Color = RadarColors.AccentRed,
    contentDescription: String? = null,
) {
    Canvas(
        modifier = if (contentDescription != null) {
            modifier.semantics { this.contentDescription = contentDescription }
        } else {
            modifier
        },
    ) {
        drawPulse(color, alpha = 1f)
    }
}

/**
 * The animated top-bar logo.
 *
 * The spec asks for a **double beat, not a sine pulse**, so the scale is driven by keyframes
 * rather than an eased tween: a sharp systolic rise to 1.18, a partial fall, a smaller second
 * beat at 1.10, then a flat rest for the remaining third of the cycle. That rest is what makes
 * it read as a heartbeat — a continuously breathing icon reads as "loading" instead.
 *
 * Alpha is animated on the same keyframe timeline so the glow peaks with the beat rather than
 * drifting against it.
 */
@Composable
fun PulseLogo(
    modifier: Modifier = Modifier.size(28.dp),
    color: Color = RadarColors.AccentRed,
    contentDescription: String? = null,
) {
    val transition = rememberInfiniteTransition(label = "heartbeat")

    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = CYCLE_MILLIS
                1.00f at 0 using LinearEasing
                1.18f at 130 using LinearEasing   // first beat, fast attack
                1.02f at 280 using LinearEasing   // partial relaxation, not all the way down
                1.10f at 400 using LinearEasing   // second, smaller beat
                1.00f at 560 using LinearEasing
                1.00f at CYCLE_MILLIS             // ~1.2s rest before the next cycle
            },
        ),
        label = "heartbeat-scale",
    )

    val alpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = CYCLE_MILLIS
                0.60f at 0 using LinearEasing
                1.00f at 130 using LinearEasing
                0.80f at 280 using LinearEasing
                1.00f at 400 using LinearEasing
                0.60f at 560 using LinearEasing
                0.60f at CYCLE_MILLIS
            },
        ),
        label = "heartbeat-alpha",
    )

    Canvas(
        modifier = if (contentDescription != null) {
            modifier.semantics { this.contentDescription = contentDescription }
        } else {
            modifier
        },
    ) {
        // Scaling about the centre keeps the beat from walking across the top bar.
        scale(scale, scale, pivot = center) {
            drawPulse(color, alpha)
        }
    }
}

private fun DrawScope.drawPulse(color: Color, alpha: Float) {
    val boxSize: Size = size
    val scaleX = boxSize.width / 100f
    val scaleY = boxSize.height / 100f

    val path = Path().apply {
        PULSE_POINTS.forEachIndexed { index, (x, y) ->
            val point = Offset(x * scaleX, y * scaleY)
            if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
        }
    }

    drawPath(
        path = path,
        color = color.copy(alpha = alpha),
        style = Stroke(
            width = boxSize.minDimension * 0.075f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}
