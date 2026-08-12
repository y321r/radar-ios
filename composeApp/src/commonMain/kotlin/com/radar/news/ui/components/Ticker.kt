package com.radar.news.ui.components

import com.radar.news.util.currentTimeMillis

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

/**
 * A clock that re-emits every [intervalMillis], so relative timestamps age on screen.
 *
 * The spec requires `قبل دقيقة` to become `قبل دقيقتين` without a refresh. Rows read this one
 * shared value rather than each running its own timer — a `LazyColumn` of twenty items would
 * otherwise hold twenty coroutines all waking on slightly different schedules.
 *
 * Hoisted to the screen and passed down, so recomposition on each tick is confined to the
 * timestamp text.
 */
@Composable
fun rememberNowTicker(intervalMillis: Long = DEFAULT_TICK_MILLIS): State<Long> {
    val interval = remember(intervalMillis) { intervalMillis }
    return produceState(initialValue = currentTimeMillis(), interval) {
        while (true) {
            delay(interval)
            value = currentTimeMillis()
        }
    }
}

/** 30s: fine enough that the minute boundary is never visibly stale, coarse enough to be free. */
const val DEFAULT_TICK_MILLIS = 30_000L
