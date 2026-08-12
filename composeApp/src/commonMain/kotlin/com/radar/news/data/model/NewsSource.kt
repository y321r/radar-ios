package com.radar.news.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One outlet, as declared in `assets/sources.json`. Nothing about a feed is hardcoded in
 * Kotlin — adding or repointing a source is a JSON edit, and the whole file can later be
 * replaced by a remote override without shipping an APK.
 */
@Serializable
data class NewsSource(
    val id: String,
    val name: String,
    /**
     * Dedupe tie-break only, lower wins. Not a display order: the timeline is strictly
     * newest-first regardless of source.
     */
    val priority: Int,
    val adapter: AdapterType,
    val url: String,
    val domain: String,
    /** Hex colour for the generated monogram avatar, e.g. `#BB1919`. */
    val color: String = "#71767B",
    val enabled: Boolean = true,
    /** Rewrites a thumbnail URL into a larger variant — see [imageRewrite] usage in RssAdapter. */
    val imageRewrite: ImageRewrite? = null,
    /** Unused today; reserved for bundling real outlet marks if licensing ever allows it. */
    val logo: String? = null,
    val notes: String? = null,
) {
    /** First character of the Arabic name, used as the monogram glyph. */
    val monogram: String get() = name.trim().take(1)
}

@Serializable
enum class AdapterType {
    @SerialName("rss") RSS,
    @SerialName("atom") ATOM,
    @SerialName("googlenews") GOOGLENEWS,
    @SerialName("html") HTML,
}

/**
 * Some outlets only advertise a thumbnail-sized image (BBC ships 240px) while their CDN
 * will happily serve a larger one from the same URL with one path segment changed.
 */
@Serializable
data class ImageRewrite(
    val pattern: String,
    val replacement: String,
) {
    private val regex by lazy { runCatching { Regex(pattern) }.getOrNull() }

    fun apply(url: String): String = regex?.replace(url, replacement) ?: url
}

/** Top-level shape of `sources.json`. */
@Serializable
data class SourceRegistryFile(
    val version: Int = 1,
    val sources: List<NewsSource> = emptyList(),
)
