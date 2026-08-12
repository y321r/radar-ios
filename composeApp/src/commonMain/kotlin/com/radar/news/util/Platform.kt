package com.radar.news.util

/**
 * Wall-clock epoch millis, KMP style. kotlinx-datetime 0.7 retired its own `Clock` in
 * favour of the stdlib's experimental `kotlin.time.Clock`, so a tiny expect/actual is
 * the version-proof way to keep `now` defaults stable across targets.
 */
expect fun currentTimeMillis(): Long
