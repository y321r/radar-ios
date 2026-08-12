package com.radar.news.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * IBM Plex Sans Arabic, bundled in `res/font` rather than downloaded, so the very first
 * frame on a cold start already renders in the right face — a webfont swap is extremely
 * visible on a black background.
 */
// KMP shell: the Android app bundled IBM Plex Sans Arabic weights; the port falls back to
// the platform default until the font files move into Compose Multiplatform resources.
val PlexArabic = FontFamily.Default

/**
 * Arabic script has tall ascenders and deep descenders, so the default Compose line-height
 * behaviour (extra leading below only) makes multi-line headlines look bottom-heavy.
 * Distributing the leading proportionally and trimming the first/last line's extra space
 * is what makes the 1.5 line-height read correctly.
 */
private val ArabicLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Proportional,
    trim = LineHeightStyle.Trim.None,
)

/** Headline in a timeline row: 16sp SemiBold, line-height 1.5 (= 24sp). */
val HeadlineStyle = TextStyle(
    fontFamily = PlexArabic,
    fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    lineHeightStyle = ArabicLineHeightStyle,
)

/** Two-line summary under the headline. */
val SummaryStyle = TextStyle(
    fontFamily = PlexArabic,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 21.sp,
    lineHeightStyle = ArabicLineHeightStyle,
)

/** Source name, relative time, إعلان badge, مباشر chip. */
val MetaStyle = TextStyle(
    fontFamily = PlexArabic,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    lineHeightStyle = ArabicLineHeightStyle,
)

/** The word رادار in the top bar. */
val BrandStyle = TextStyle(
    fontFamily = PlexArabic,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    lineHeight = 28.sp,
    lineHeightStyle = ArabicLineHeightStyle,
)

val RadarTypography = Typography(
    bodyLarge = HeadlineStyle,
    bodyMedium = SummaryStyle,
    bodySmall = MetaStyle,
    titleLarge = BrandStyle,
    labelLarge = TextStyle(
        fontFamily = PlexArabic,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        lineHeightStyle = ArabicLineHeightStyle,
    ),
)
