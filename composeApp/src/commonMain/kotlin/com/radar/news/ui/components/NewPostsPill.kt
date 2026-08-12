package com.radar.news.ui.components

import com.radar.news.ui.Strings
import com.radar.news.ui.countFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.radar.news.ui.theme.Dimens
import com.radar.news.ui.theme.MetaStyle
import com.radar.news.ui.theme.RadarColors

/**
 * The X-timeline "new posts" pill.
 *
 * This exists to solve a correctness problem, not a decorative one: a background sync every
 * 15 minutes can insert stories above whatever the user is currently reading. Letting the list
 * absorb them silently moves content under their thumb mid-sentence. The pill defers the jump
 * until the user asks for it.
 *
 * Only shown when the user is scrolled away from the top — at position 0 there is nothing to
 * defer, so new items simply fade in.
 */
@Composable
fun NewPostsPill(
    visible: Boolean,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible && count > 0,
        // Drops in from under the top bar and lifts back out — same direction the content
        // it refers to lives in.
        enter = slideInVertically(animationSpec = tween(200)) { -it } +
            fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.92f),
        exit = slideOutVertically(animationSpec = tween(160)) { -it } +
            fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.92f),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val label = newPostsLabel(count)
            Row(
                modifier = Modifier
                    .padding(top = Dimens.SpaceSm)
                    .clip(RoundedCornerShape(50))
                    .background(RadarColors.Surface)
                    .border(1.dp, RadarColors.Divider, RoundedCornerShape(50))
                    .clickable(onClick = onClick)
                    .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceSm)
                    .semantics { contentDescription = label },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = null,
                    tint = RadarColors.AccentRed,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Dimens.SpaceXs))
                Text(
                    text = label,
                    style = MetaStyle,
                    color = RadarColors.AccentRed,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Arabic pluralisation for the pill, matching `ArabicRelativeTime`'s pattern:
 * 1 → the noun alone, 2 → the dual, 3–10 → numeral + plural, 11+ → numeral + singular.
 */
@Composable
private fun newPostsLabel(count: Int): String = when {
    count == 1 -> Strings.new_posts_one
    count == 2 -> Strings.new_posts_two
    count <= 10 -> countFormat(Strings.new_posts_few, count)
    else -> countFormat(Strings.new_posts_many, count)
}
