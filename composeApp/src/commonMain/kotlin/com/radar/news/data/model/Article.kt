package com.radar.news.data.model

/**
 * A story as the timeline shows it: one row, one headline, whichever outlet filed first,
 * with any duplicate coverage folded in.
 */
data class Article(
    val id: String,
    val sourceId: String,
    val sourceName: String,
    val sourceColor: String,
    val sourceDomain: String,
    val title: String,
    val summary: String?,
    val url: String,
    val imageUrl: String?,
    val publishedAt: Long,
    val breakingScore: Int,
    val topicScore: Int,
    /** Other outlets that ran the same story; drives the `+2 مصادر` label. */
    val duplicateSourceIds: List<String> = emptyList(),
) {
    val extraSourceCount: Int get() = duplicateSourceIds.size

    /** First letter of the outlet name, for the monogram avatar. */
    val monogram: String get() = sourceName.trim().take(1)
}
