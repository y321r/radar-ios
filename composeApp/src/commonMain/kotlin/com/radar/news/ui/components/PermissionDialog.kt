package com.radar.news.ui.components

import com.radar.news.ui.Strings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.radar.news.ui.theme.Dimens
import com.radar.news.ui.theme.HeadlineStyle
import com.radar.news.ui.theme.MetaStyle
import com.radar.news.ui.theme.RadarColors
import com.radar.news.ui.theme.SummaryStyle

/**
 * The first-launch notification prompt, shown once and only once.
 *
 * Dismissal by back press or outside tap is disabled: both would leave the "have they
 * answered?" flag ambiguous, and the spec requires exactly one appearance. The user must
 * pick `موافق` or `لاحقاً`, and either answer settles it permanently — after that the bell
 * in the top bar is the only way to change it.
 */
@Composable
fun NotificationPermissionDialog(
    onAccept: () -> Unit,
    onLater: () -> Unit,
) {
    Dialog(
        onDismissRequest = { /* answering is mandatory — see the class comment */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.DialogCorner))
                .background(RadarColors.Surface)
                .padding(Dimens.SpaceXl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PulseGlyph(modifier = Modifier.size(44.dp))

            Spacer(Modifier.height(Dimens.SpaceLg))

            Text(
                text = Strings.onboarding_title,
                style = HeadlineStyle,
                color = RadarColors.TextPrimary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Dimens.SpaceSm))

            Text(
                text = Strings.onboarding_body,
                style = SummaryStyle,
                color = RadarColors.TextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Dimens.SpaceXl))

            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RadarColors.AccentRed,
                    contentColor = androidx.compose.ui.graphics.Color.White,
                ),
            ) {
                Text(text = Strings.onboarding_accept, style = MetaStyle)
            }

            Spacer(Modifier.height(Dimens.SpaceXs))

            TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = Strings.onboarding_later,
                    style = MetaStyle,
                    color = RadarColors.TextSecondary,
                )
            }
        }
    }
}
