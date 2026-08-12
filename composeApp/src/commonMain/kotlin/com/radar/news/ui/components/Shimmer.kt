package com.radar.news.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.radar.news.ui.theme.Dimens
import com.radar.news.ui.theme.RadarColors

/**
 * A left-to-right sweep used for both the first-run skeleton rows and the image placeholder.
 *
 * The gradient is built in raw pixel coordinates rather than layout coordinates, so it sweeps
 * the same physical direction regardless of the app's forced RTL — a shimmer that reverses
 * with layout direction reads as a glitch rather than as loading.
 */
@Composable
fun shimmerBrush(widthPx: Float = 900f): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -widthPx,
        targetValue = widthPx * 2,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-offset",
    )

    return Brush.linearGradient(
        colors = listOf(
            RadarColors.ShimmerBase,
            RadarColors.ShimmerHighlight,
            RadarColors.ShimmerBase,
        ),
        start = Offset(offset, 0f),
        end = Offset(offset + widthPx, 0f),
    )
}

@Composable
private fun ShimmerBlock(modifier: Modifier) {
    Spacer(modifier = modifier.background(shimmerBrush()))
}

/**
 * A skeleton row matching the real article layout — avatar, header line, two headline lines
 * and a 16:9 image slot — so the timeline does not visibly reflow when real content arrives.
 */
@Composable
fun ShimmerRow(modifier: Modifier = Modifier, showImage: Boolean = true) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.GutterHorizontal, vertical = Dimens.RowVertical),
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            ShimmerBlock(Modifier.size(Dimens.SourceAvatar).clip(CircleShape))
            Spacer(Modifier.width(Dimens.SpaceSm))
            ShimmerBlock(Modifier.width(120.dp).height(12.dp).clip(RoundedCornerShape(4.dp)))
        }

        Spacer(Modifier.height(Dimens.SpaceMd))
        ShimmerBlock(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(4.dp)))
        Spacer(Modifier.height(Dimens.SpaceSm))
        ShimmerBlock(Modifier.fillMaxWidth(0.7f).height(16.dp).clip(RoundedCornerShape(4.dp)))

        if (showImage) {
            Spacer(Modifier.height(Dimens.SpaceMd))
            ShimmerBlock(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(Dimens.ImageCorner)),
            )
        }
    }
}
