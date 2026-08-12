package com.radar.news.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The X (Twitter) dark palette, verbatim from the spec.
 *
 * These are the only colours in the app. Anything that needs a colour picks from here —
 * no ad-hoc hex literals in composables, so the timeline and the native ad slot cannot
 * drift apart visually.
 */
object RadarColors {
    /** Pure black page background. Not a near-black — the spec is specific. */
    val Background = Color(0xFF000000)

    /** Sheets, dialogs, bottom surfaces. The only non-black surface. */
    val Surface = Color(0xFF16181C)

    /** 1dp hairline between timeline rows, full-bleed. */
    val Divider = Color(0xFF2F3336)

    /** Headlines and primary content. */
    val TextPrimary = Color(0xFFE7E9EA)

    /** Source names, timestamps, summaries, the إعلان badge. */
    val TextSecondary = Color(0xFF71767B)

    /** The one accent: pulse logo, live dot, primary button, notification accent. */
    val AccentRed = Color(0xFFF4212E)

    /** Ripple / pressed state — white at 8%. */
    val Ripple = Color(0x14FFFFFF)

    /** Shimmer skeleton base and highlight, derived from Surface so they stay in family. */
    val ShimmerBase = Color(0xFF16181C)
    val ShimmerHighlight = Color(0xFF22262B)
}
