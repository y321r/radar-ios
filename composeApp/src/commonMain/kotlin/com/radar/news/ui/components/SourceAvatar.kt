package com.radar.news.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.radar.news.ui.theme.Dimens
import com.radar.news.ui.theme.MetaStyle
import com.radar.news.ui.theme.RadarColors

/**
 * A generated monogram disc standing in for the outlet's logo.
 *
 * The app ships no outlet marks — redistributing the BBC's, CNN's and Al Jazeera's trademarks
 * is not something a source registry should quietly do — so each source carries a brand colour
 * in `sources.json` and the row renders its first Arabic letter instead. See DECISIONS.md T3.
 */
@Composable
fun SourceAvatar(
    monogram: String,
    colorHex: String,
    sourceName: String,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.SourceAvatar,
) {
    val background = remember(colorHex) { parseColor(colorHex) }
    // Pick black or white text from the disc's luminance so a pale brand colour (DW's cyan)
    // stays legible next to a dark one (France 24's navy).
    val content = remember(background) {
        if (background.luminance() > 0.6f) Color.Black else Color.White
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .semantics { contentDescription = sourceName },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text = monogram,
            style = MetaStyle.copy(
                fontSize = (size.value * 0.52f).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            color = content,
            maxLines = 1,
        )
    }
}

private fun parseColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(RadarColors.TextSecondary)

private fun Color.luminance(): Float = (0.299f * red + 0.587f * green + 0.114f * blue)
