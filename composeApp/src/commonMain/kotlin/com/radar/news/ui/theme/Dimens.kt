package com.radar.news.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The timeline's measurements, in one place so a news row and a native ad row cannot drift
 * apart — the ad has to match the surrounding content exactly, and that only holds if both
 * read the same numbers.
 */
object Dimens {
    /** Horizontal page gutter, shared by every row. */
    val GutterHorizontal = 16.dp
    val RowVertical = 12.dp

    /** Full-bleed hairline between rows. */
    val DividerThickness = 1.dp

    val SourceAvatar = 20.dp
    val IconButton = 36.dp
    val Icon = 18.dp
    val IconSmall = 14.dp

    /** Corner radius exists only on images and buttons — never on rows. */
    val ImageCorner = 12.dp

    val SpaceXs = 4.dp
    val SpaceSm = 8.dp
    val SpaceMd = 12.dp
    val SpaceLg = 16.dp
    val SpaceXl = 24.dp

    val TopBarHeight = 52.dp
    val DialogCorner = 16.dp
}
