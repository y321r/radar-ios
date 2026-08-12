package com.radar.news.ui

import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.UIKit.UIApplication

actual object ArticleActions {

    actual fun openArticle(url: String) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        val nsUrl = NSURL.URLWithString(url) ?: return
        UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }

    actual fun shareArticle(title: String, url: String) {
        // Share sheet wiring arrives with the full iOS UI pass; for now it degrades to
        // opening the article, which keeps the row tappable on device.
        openArticle(url)
    }

    actual fun displayDomain(url: String): String = articleHost(url)

    actual fun openEmail(address: String) {
        val nsUrl = NSURL.URLWithString("mailto:$address") ?: return
        UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }
}
