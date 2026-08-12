package com.radar.news.data.repository

import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.UnknownHostException

/** Android actual — the original java.net-based check, unchanged. */
actual fun Throwable?.isNetworkUnavailable(): Boolean {
    var e = this
    var depth = 0
    while (e != null && depth++ < 8) {
        when (e) {
            is UnknownHostException, is NoRouteToHostException -> return true
            is SocketException ->
                if (e.message?.contains("unreachable", ignoreCase = true) == true) return true
        }
        e = e.cause
    }
    return false
}
