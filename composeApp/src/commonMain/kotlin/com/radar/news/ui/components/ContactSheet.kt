package com.radar.news.ui.components

import com.radar.news.ui.Strings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.radar.news.ui.ArticleActions
import com.radar.news.ui.theme.Dimens
import com.radar.news.ui.theme.HeadlineStyle
import com.radar.news.ui.theme.MetaStyle
import com.radar.news.ui.theme.RadarColors

/**
 * In-app contact sheet — the News and Magazines policy requirement that contact
 * information be easy to find inside the app.
 *
 * Shows the developer email address in plain text and sends via `mailto:` on tap.
 * The privacy policy link reuses the same Custom Tab path as article links.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RadarColors.Surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.GutterHorizontal)
                .padding(bottom = Dimens.SpaceXl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = Strings.contact_title,
                style = HeadlineStyle,
                color = RadarColors.TextPrimary,
            )

            Spacer(Modifier.height(Dimens.SpaceXs))

            Text(
                text = Strings.contact_subtitle,
                style = MetaStyle,
                color = RadarColors.TextSecondary,
            )

            Spacer(Modifier.height(Dimens.SpaceMd))

            // The email, in plain text — visible before any tap, in LTR order as written.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RadarColors.Background, RoundedCornerShape(Dimens.ImageCorner))
                    .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceMd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Email,
                    contentDescription = null,
                    tint = RadarColors.AccentRed,
                    modifier = Modifier.size(Dimens.Icon),
                )
                Spacer(Modifier.width(Dimens.SpaceSm))
                Text(
                    text = Strings.contact_email,
                    style = HeadlineStyle,
                    color = RadarColors.TextPrimary,
                )
            }

            Spacer(Modifier.height(Dimens.SpaceMd))

            // Sends via the device's mail client directly to the developer address.
            Button(
                onClick = {
                    ArticleActions.openEmail(Strings.contact_email)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RadarColors.AccentRed),
                shape = RoundedCornerShape(Dimens.ImageCorner),
            ) {
                Text(
                    text = Strings.contact_send,
                    style = HeadlineStyle,
                    color = RadarColors.TextPrimary,
                )
            }

            Spacer(Modifier.height(Dimens.SpaceXs))

            TextButton(
                onClick = {
                    ArticleActions.openArticle(Strings.contact_privacy_url)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = Strings.contact_privacy,
                    style = MetaStyle,
                    color = RadarColors.TextSecondary,
                )
            }

            Spacer(Modifier.height(Dimens.SpaceMd))

            Text(
                text = Strings.contact_developer,
                style = MetaStyle,
                color = RadarColors.TextSecondary,
            )
        }
    }
}
