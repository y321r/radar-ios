package com.radar.news.data.model

/**
 * A single item exactly as it came out of a feed, before classification or dedupe.
 *
 * Deliberately holds only what the spec's legal constraint permits to be stored:
 * headline, a short summary, an image URL and a link. Full article text is never
 * fetched and never persisted.
 */
data class RawArticle(
    val sourceId: String,
    val title: String,
    val summary: String?,
    val url: String,
    /** Epoch millis, already normalised out of whatever timezone the feed used. */
    val publishedAt: Long,
    val imageUrl: String?,
    /** `<category>` values plus anything adapter-specific worth scoring on. */
    val categories: List<String> = emptyList(),
)
