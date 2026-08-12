package com.radar.news.util

actual object RadarLog {
    actual fun d(tag: String, msg: String) { android.util.Log.d(tag, msg) }
    actual fun i(tag: String, msg: String) { android.util.Log.i(tag, msg) }
    actual fun w(tag: String, msg: String) { android.util.Log.w(tag, msg) }
    actual fun e(tag: String, msg: String) { android.util.Log.e(tag, msg) }
}
