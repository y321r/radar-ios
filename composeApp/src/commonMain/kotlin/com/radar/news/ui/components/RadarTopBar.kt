package com.radar.news.ui.components

import com.radar.news.ui.Strings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.radar.news.ui.theme.BrandStyle
import com.radar.news.ui.theme.Dimens
import com.radar.news.ui.theme.MetaStyle
import com.radar.news.ui.theme.RadarColors

/**
 * Pinned top bar: heartbeat logo and wordmark at the start (right in RTL), the live chip and
 * notification bell at the end (left).
 *
 * Translucent black with a 1dp bottom hairline — the same divider colour as the timeline, so
 * the bar reads as part of the list rather than as a floating surface.
 */
@Composable
fun RadarTopBar(
    notificationsEnabled: Boolean,
    onToggleNotifications: () -> Unit,
    onContactClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(RadarColors.Background.copy(alpha = 0.92f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.TopBarHeight)
                .padding(horizontal = Dimens.GutterHorizontal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Start side (right in RTL).
            PulseLogo(
                modifier = Modifier.size(26.dp),
                contentDescription = Strings.cd_pulse_logo,
            )
            Spacer(Modifier.width(Dimens.SpaceSm))
            Text(
                text = Strings.brand,
                style = BrandStyle,
                color = RadarColors.TextPrimary,
            )

            Spacer(Modifier.weight(1f))

            // End side (left in RTL).
            LiveChip()
            Spacer(Modifier.width(Dimens.SpaceXs))
            NotificationBell(
                enabled = notificationsEnabled,
                onClick = onToggleNotifications,
            )
            Spacer(Modifier.width(Dimens.SpaceXs))
            ContactButton(onClick = onContactClick)
        }

        HorizontalDivider(thickness = Dimens.DividerThickness, color = RadarColors.Divider)
    }
}

/**
 * `مباشر` with a slowly blinking red dot.
 *
 * Its blink is intentionally a smooth sine-like fade on a different period from the logo's
 * heartbeat — two elements pulsing in lockstep would read as one animation stuttering.
 */
@Composable
private fun LiveChip(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "live")
    val dotAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "live-dot-alpha",
    )

    Row(
        modifier = modifier.padding(horizontal = Dimens.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Canvas(modifier = Modifier.size(7.dp)) {
            drawCircle(color = RadarColors.AccentRed.copy(alpha = dotAlpha))
        }
        Spacer(Modifier.width(Dimens.SpaceXs))
        Text(
            text = Strings.live,
            style = MetaStyle,
            color = RadarColors.AccentRed,
        )
    }
}

@Composable
private fun NotificationBell(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier.size(Dimens.IconButton)) {
        Icon(
            // Filled + red when on, outlined + grey when off — legible at a glance without
            // needing a label.
            imageVector = if (enabled) Icons.Filled.Notifications else Icons.Outlined.NotificationsNone,
            contentDescription = if (enabled) Strings.cd_notifications_on else Strings.cd_notifications_off,
            tint = if (enabled) RadarColors.AccentRed else RadarColors.TextSecondary,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Opens the in-app contact sheet (email address shown, sends via mailto). This is the
 * News and Magazines policy requirement: contact information must be easy to find in-app.
 */
@Composable
private fun ContactButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier.size(Dimens.IconButton)) {
        Icon(
            imageVector = Icons.Outlined.Email,
            contentDescription = Strings.cd_contact,
            tint = RadarColors.TextPrimary,
            modifier = Modifier.size(22.dp),
        )
    }
}
