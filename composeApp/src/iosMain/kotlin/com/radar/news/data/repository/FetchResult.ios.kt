package com.radar.news.data.repository

/**
 * iOS actual — message heuristic, since `java.net` does not exist on Kotlin/Native.
 *
 * NSURLErrorDomain reports DNS failure and "offline" states as error strings; keep the same
 * deliberately narrow spirit as the Android actual: only "there is no route to anything".
 */
actual fun Throwable?.isNetworkUnavailable(): Boolean {
    var e = this
    var depth = 0
    while (e != null && depth++ < 8) {
        val msg = e.message.orEmpty()
        if (msg.contains("unreachable", ignoreCase = true) ||
            msg.contains("cannot find host", ignoreCase = true) ||
            msg.contains("not connected to the internet", ignoreCase = true) ||
            msg.contains("offline", ignoreCase = true)
        ) {
            return true
        }
        e = e.cause
    }
    return false
}
