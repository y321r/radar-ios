package com.radar.news.util

/**
 * KMP replacement for `android.util.Log`. Platform actuals:
 * - Android: delegates to `android.util.Log`
 * - iOS: prints with a tag prefix (stderr)
 */
expect object RadarLog {
    fun d(tag: String, msg: String)
    fun i(tag: String, msg: String)
    fun w(tag: String, msg: String)
    fun e(tag: String, msg: String)
}
