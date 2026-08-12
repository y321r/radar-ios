package com.radar.news.ui

/**
 * Opening and sharing an article. Platform-specific (Android: Custom Tabs/Intent chooser;
 * iOS: openURL/share sheet) — both fail soft rather than crashing the timeline.
 */
expect object ArticleActions {

    /** Opens [url] in the platform browser/tab, http(s) only. */
    fun openArticle(url: String)

    /** `«{title}»\n\n{url}\n\nعبر تطبيق رادار` — the spec's share format. */
    fun shareArticle(title: String, url: String)

    /** `bbc.com` from a full article URL, for the source-link label. Pure, platform-free. */
    fun displayDomain(url: String): String

    /** Opens the device mail client addressed to [address] (mailto:). */
    fun openEmail(address: String)
}

/** Shared implementation of [displayDomain] (host extraction) — kept here so the UI never touches URLs directly. */
internal fun articleHost(url: String): String =
    runCatching { HOST_REGEX.find(url)?.groupValues?.get(1)?.removePrefix("www.") ?: "" }.getOrDefault("")

private val HOST_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://([^/:?#]+)")
