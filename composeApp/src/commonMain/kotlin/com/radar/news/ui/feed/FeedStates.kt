package com.radar.news.ui.feed

import com.radar.news.ui.Strings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.radar.news.ui.components.PulseGlyph
import com.radar.news.ui.components.ShimmerRow
import com.radar.news.ui.theme.Dimens
import com.radar.news.ui.theme.HeadlineStyle
import com.radar.news.ui.theme.MetaStyle
import com.radar.news.ui.theme.RadarColors

/** Nothing passed the filter. Not an error — often the correct state at 3am. */
@Composable
fun FeedEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RadarColors.Background)
            .padding(Dimens.SpaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PulseGlyph(modifier = Modifier.size(56.dp), color = RadarColors.AccentRed)
        Spacer(Modifier.height(Dimens.SpaceLg))
        Text(
            text = Strings.empty_title,
            style = HeadlineStyle,
            color = RadarColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Dimens.SpaceSm))
        Text(
            text = Strings.empty_subtitle,
            style = MetaStyle,
            color = RadarColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** Shown only when a sync failed *and* there is nothing cached to fall back on. */
@Composable
fun FeedErrorState(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RadarColors.Background)
            .padding(Dimens.SpaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = Strings.error_title,
            style = HeadlineStyle,
            color = RadarColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Dimens.SpaceLg))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = RadarColors.AccentRed,
                contentColor = RadarColors.TextPrimary,
            ),
        ) {
            Text(text = Strings.error_retry, style = MetaStyle)
        }
    }
}

/**
 * The partial-failure case: some outlets answered, so the timeline still renders and the
 * failure is demoted to a dismissible strip. Replacing readable news with an error page
 * because one of six feeds returned 403 would be the wrong trade.
 */
@Composable
fun FeedErrorStrip(
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().background(RadarColors.Surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Dimens.GutterHorizontal, top = Dimens.SpaceXs, bottom = Dimens.SpaceXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = Strings.error_title,
                style = MetaStyle,
                color = RadarColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = Strings.error_retry,
                style = MetaStyle,
                color = RadarColors.AccentRed,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = Dimens.SpaceSm, vertical = Dimens.SpaceXs),
            )
            Spacer(Modifier.width(Dimens.SpaceXs))
            IconButton(onClick = onDismiss, modifier = Modifier.size(Dimens.IconButton)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = Strings.error_strip_dismiss,
                    tint = RadarColors.TextSecondary,
                    modifier = Modifier.size(Dimens.Icon),
                )
            }
        }
        HorizontalDivider(thickness = Dimens.DividerThickness, color = RadarColors.Divider)
    }
}

/** First-run skeleton: five rows matching the real layout so nothing reflows when data lands. */
@Composable
fun FeedLoadingState(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().background(RadarColors.Background)) {
        repeat(5) { index ->
            // Alternate the image slot — a uniform skeleton implies every story has a picture,
            // and two of the six sources never do.
            ShimmerRow(showImage = index % 2 == 0)
            HorizontalDivider(thickness = Dimens.DividerThickness, color = RadarColors.Divider)
        }
    }
}
