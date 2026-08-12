package com.radar.news.ui

import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.UIKit.UIApplication

actual object ArticleActions {

    actual fun openArticle(url: String) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        val nsUrl = NSURL.URLWithString(url) ?: return
        UIApplication.sharedApplication.openURL(nsUrl) { _ -> }
    }

    actual fun shareArticle(title: String, url: String) {
        // Share sheet wiring arrives with the full iOS UI pass; for now it degrades to
        // opening the article, which keeps the row tappable on device.
        openArticle(url)
    }

    actual fun displayDomain(url: String): String = articleHost(url)
}
