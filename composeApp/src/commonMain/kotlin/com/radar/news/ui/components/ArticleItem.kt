package com.radar.news.ui.components

import com.radar.news.ui.Strings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.radar.news.data.model.Article
import com.radar.news.domain.time.ArabicRelativeTime
import com.radar.news.ui.ArticleActions
import com.radar.news.ui.theme.Dimens
import com.radar.news.ui.theme.HeadlineStyle
import com.radar.news.ui.theme.MetaStyle
import com.radar.news.ui.theme.RadarColors
import com.radar.news.ui.theme.SummaryStyle

/**
 * One timeline row, in the X timeline idiom: flat, full-width, no card, no elevation, no
 * rounded container — separated from its neighbours by a single full-bleed hairline.
 *
 * Layout is authored start-to-end and mirrors automatically under the app's forced RTL, so
 * "start" reads as the right-hand side on screen.
 */
@Composable
fun ArticleItem(
    article: Article,
    now: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val relativeTime = remember(article.publishedAt, now) {
        ArabicRelativeTime.format(article.publishedAt, now)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(RadarColors.Background)
            // Tapping anywhere that is not the share button opens the article.
            .clickable { ArticleActions.openArticle(article.url) }
            .padding(horizontal = Dimens.GutterHorizontal, vertical = Dimens.RowVertical),
    ) {
        HeaderRow(article = article, relativeTime = relativeTime)

        Spacer(Modifier.height(Dimens.SpaceSm))

        Text(
            text = article.title,
            style = HeadlineStyle,
            color = RadarColors.TextPrimary,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )

        if (!article.summary.isNullOrBlank()) {
            Spacer(Modifier.height(Dimens.SpaceXs))
            Text(
                text = article.summary,
                style = SummaryStyle,
                color = RadarColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // The slot is omitted entirely when there is no image — never a grey box. Two of the
        // six sources never supply one, so this is the normal case, not a degraded one.
        if (!article.imageUrl.isNullOrBlank()) {
            Spacer(Modifier.height(Dimens.SpaceMd))
            ArticleImage(url = article.imageUrl, contentDescription = article.title)
        }

        Spacer(Modifier.height(Dimens.SpaceSm))

        ActionRow(article = article)
    }
}

@Composable
private fun HeaderRow(article: Article, relativeTime: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SourceAvatar(
            monogram = article.monogram,
            colorHex = article.sourceColor,
            sourceName = article.sourceName,
        )
        Spacer(Modifier.width(Dimens.SpaceSm))

        Text(
            text = article.sourceName,
            style = MetaStyle,
            color = RadarColors.TextSecondary,
            maxLines = 1,
        )

        Text(
            text = " · ",
            style = MetaStyle,
            color = RadarColors.TextSecondary,
        )

        Text(
            text = relativeTime,
            style = MetaStyle,
            color = RadarColors.TextSecondary,
            maxLines = 1,
        )

        if (article.extraSourceCount > 0) {
            Text(
                text = " · ",
                style = MetaStyle,
                color = RadarColors.TextSecondary,
            )
            Text(
                text = Strings.extra_sources.format(article.extraSourceCount),
                style = MetaStyle,
                color = RadarColors.TextSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ArticleImage(url: String, contentDescription: String) {
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(Dimens.ImageCorner)),
        loading = {
            Box(Modifier.fillMaxWidth().background(shimmerBrush()))
        },
        // A dead image URL collapses the slot rather than leaving a hole where a picture
        // was promised — same rule as an item that never had one.
        error = { Box(Modifier) },
    )
}

@Composable
private fun ActionRow(article: Article) {
    val context = LocalContext.current
    val domain = remember(article.url) {
        article.sourceDomain.ifBlank { ArticleActions.displayDomain(article.url) }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Start (right in RTL): attribution + link to the original.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(Dimens.SpaceLg))
                .clickable { ArticleActions.openArticle(article.url) }
                .padding(vertical = Dimens.SpaceXs, horizontal = Dimens.SpaceXs),
        ) {
            Icon(
                imageVector = Icons.Outlined.Link,
                contentDescription = Strings.cd_open_source,
                tint = RadarColors.TextSecondary,
                modifier = Modifier.size(Dimens.IconSmall),
            )
            Spacer(Modifier.width(Dimens.SpaceXs))
            Text(
                text = domain.ifBlank { article.sourceName },
                style = MetaStyle,
                color = RadarColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // End (left in RTL): share.
        IconButton(
            onClick = { ArticleActions.shareArticle(article.title, article.url) },
            modifier = Modifier.size(Dimens.IconButton),
        ) {
            Icon(
                imageVector = Icons.Outlined.IosShare,
                contentDescription = Strings.cd_share,
                tint = RadarColors.TextSecondary,
                modifier = Modifier.size(Dimens.Icon),
            )
        }
    }
}
