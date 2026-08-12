package com.radar.news.util

import platform.Foundation.NSLog

actual object RadarLog {
    actual fun d(tag: String, msg: String) = NSLog("%@: %@", tag, msg)
    actual fun i(tag: String, msg: String) = NSLog("%@: %@", tag, msg)
    actual fun w(tag: String, msg: String) = NSLog("%@: %@", tag, msg)
    actual fun e(tag: String, msg: String) = NSLog("%@: %@", tag, msg)
}
