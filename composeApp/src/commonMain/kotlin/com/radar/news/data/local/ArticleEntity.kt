package com.radar.news.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A stored article. Only rows that already passed classification live here — items below
 * the breaking/topic thresholds are discarded at sync time and never written, per the spec.
 *
 * Fields exist purely to serve dedupe ([normalizedTitle], [titleHash], [dayBucket]) as well
 * as display; keeping them on the row means the 12-hour duplicate scan is an indexed query
 * rather than a re-normalisation of the whole table on every sync.
 */
@Entity(
    tableName = "articles",
    indices = [
        Index(value = ["canonicalUrl"], unique = true),
        Index(value = ["publishedAt"]),
        Index(value = ["dayBucket"]),
        Index(value = ["titleHash"]),
    ],
)
data class ArticleEntity(
    /** FNV-1a hash of [canonicalUrl] — stable across syncs and devices. */
    @PrimaryKey val id: String,

    val sourceId: String,
    val title: String,
    val summary: String?,
    val url: String,
    /** Tracking params stripped; the identity used for URL-match dedupe. */
    val canonicalUrl: String,
    val imageUrl: String?,
    val publishedAt: Long,
    val fetchedAt: Long,

    val breakingScore: Int,
    val topicScore: Int,

    /** Output of ArabicNormalizer — the token source for Jaccard/cosine comparison. */
    val normalizedTitle: String,
    /** Hash of [normalizedTitle], for the cheap exact-match dedupe step. */
    val titleHash: String,
    /**
     * `publishedAt` floored to a 12-hour bucket. Comparing only against neighbouring buckets
     * is what keeps dedupe off an O(n²) scan of the entire table.
     */
    val dayBucket: Long,

    /** Outlets that also ran this story; drives the `+2 مصادر` label. */
    val duplicateSourceIds: List<String> = emptyList(),

    /** Set once a notification has been posted, so a re-sync cannot notify twice. */
    val notified: Boolean = false,

    /**
     * Set at insert time when the row was ingested by a **foreground** sync — app open or
     * pull-to-refresh. Such a row is on screen by construction: `refresh()` runs only from
     * `FeedViewModel` init and pull-to-refresh, both foreground, and Paging observes the same
     * table, so the story is in the timeline the moment it is written.
     *
     * Deliberately **not** folded into [notified]. That flag means "already announced" and is
     * what gives S3 its crash-recovery property: a row inserted by a sync that died before
     * notifying still has `notified = 0` and gets picked up next time. Overloading it here
     * would say "already announced" about a story nobody was told about, and the two states
     * need to stay distinguishable.
     *
     * Set once and never cleared: it describes how the row entered the database, not which
     * copy it currently displays, so it survives a dedupe merge. That is what stops a later
     * duplicate promoting the row's `breakingScore` past the notify bar and buzzing about a
     * story that has been on screen for hours. See DECISIONS.md S5.
     */
    val seenInFeed: Boolean = false,
) {
    companion object {
        /** The dedupe comparison window from the spec. */
        const val BUCKET_MILLIS = 12L * 60L * 60L * 1000L

        fun bucketOf(publishedAt: Long): Long = publishedAt / BUCKET_MILLIS
    }
}
