package com.radar.news.domain.dedupe

import com.radar.news.util.currentTimeMillis
import com.radar.news.data.local.ArticleEntity
import com.radar.news.data.model.NewsSource
import com.radar.news.data.model.RawArticle
import com.radar.news.data.remote.UrlCanonicalizer
import com.radar.news.domain.filter.Classification
import com.radar.news.domain.filter.FilterConfig

/** A fetched article that already passed classification, ready for dedupe. */
data class ScoredArticle(
    val article: RawArticle,
    val classification: Classification,
)

/**
 * What a dedupe pass decided.
 *
 * @property toInsert genuinely new stories
 * @property toUpdate rows already stored whose winning copy or duplicate list changed
 */
data class DedupeResult(
    val toInsert: List<ArticleEntity> = emptyList(),
    val toUpdate: List<ArticleEntity> = emptyList(),
    /**
     * Per source: accepted items whose story was already owned by a **different** outlet.
     * This is genuine cross-source loss — the number that says whether the "earliest wins"
     * rule is systematically erasing a slower newsroom.
     */
    val mergedIntoOtherSource: Map<String, Int> = emptyMap(),
    /**
     * Per source: accepted items that were simply the same article re-fetched from the same
     * outlet on a later sync. Steady-state behaviour, **not** a loss — counting these as
     * dedupe casualties makes every healthy source look like it is being erased.
     */
    val alreadyStored: Map<String, Int> = emptyMap(),
)

/**
 * Folds the same story from several outlets into one timeline row.
 *
 * Matching runs cheapest-first and stops at the first hit, because the expensive measures
 * are only worth running on pairs the cheap ones could not settle:
 *
 *  1. canonical URL equality — an O(1) map probe
 *  2. normalised-title hash equality — also O(1)
 *  3. token Jaccard ≥ 0.55
 *  4. character 4-gram cosine ≥ 0.75
 *
 * Only items within a 12-hour window are ever compared, and candidates are drawn by day
 * bucket rather than by scanning the table, so cost stays proportional to one window's
 * traffic instead of the whole database.
 *
 * **Which copy wins:** earliest `publishedAt`, tie-broken by source priority. The winner's
 * content is what the row displays; every other outlet that ran the story is recorded in
 * `duplicateSourceIds`, which drives the `+2 مصادر` label. When a winning row has no image
 * and a duplicate does, the image is adopted — the story is the same, so a picture from the
 * outlet that supplied one is better than an empty slot.
 */
class Deduplicator constructor() {

    /**
     * @param seenInFeed true when this pass belongs to a **foreground** sync, so every row it
     *   inserts is on screen the moment it is written and must never be announced later. The
     *   default is false because that is the safe direction: a new call site that forgets to
     *   pass it still gets a notifiable row, it does not silently lose notifications.
     */
    fun dedupe(
        scored: List<ScoredArticle>,
        existing: List<ArticleEntity>,
        sources: Map<String, NewsSource>,
        now: Long = currentTimeMillis(),
        seenInFeed: Boolean = false,
    ): DedupeResult {
        val pool = mutableListOf<Candidate>()
        val byCanonicalUrl = HashMap<String, Candidate>()
        val byTitleHash = HashMap<String, Candidate>()

        existing.forEach { entity ->
            val candidate = Candidate.fromEntity(entity, sources[entity.sourceId]?.priority ?: DEFAULT_PRIORITY)
            pool += candidate
            byCanonicalUrl.putIfAbsent(candidate.canonicalUrl, candidate)
            byTitleHash.putIfAbsent(candidate.titleHash, candidate)
        }

        // Earliest first, so the copy that should win is already in the pool by the time a
        // later duplicate arrives and there is never a need to re-crown a winner.
        val incoming = scored.sortedWith(
            compareBy({ it.article.publishedAt }, { sources[it.article.sourceId]?.priority ?: DEFAULT_PRIORITY }),
        )

        val inserted = mutableListOf<Candidate>()
        val mergedIntoOther = mutableMapOf<String, Int>()
        val alreadyStored = mutableMapOf<String, Int>()

        for (item in incoming) {
            val candidate = Candidate.fromScored(
                scored = item,
                priority = sources[item.article.sourceId]?.priority ?: DEFAULT_PRIORITY,
                fetchedAt = now,
                seenInFeed = seenInFeed,
            )

            val match = findDuplicate(candidate, pool, byCanonicalUrl, byTitleHash)
            if (match == null) {
                pool += candidate
                inserted += candidate
                byCanonicalUrl.putIfAbsent(candidate.canonicalUrl, candidate)
                byTitleHash.putIfAbsent(candidate.titleHash, candidate)
                continue
            }

            // The identical article re-fetched from the same outlet in a later sync. Not a
            // second source — recording it would inflate the `+n مصادر` label every 15 minutes.
            if (match.sourceId == candidate.sourceId && match.canonicalUrl == candidate.canonicalUrl) {
                alreadyStored.merge(candidate.sourceId, 1, Int::plus)
                continue
            }

            val ownerBefore = match.sourceId
            merge(row = match, candidate = candidate)
            // If the row still belongs to someone else, this candidate's outlet lost the story.
            if (match.sourceId != candidate.sourceId) {
                mergedIntoOther[candidate.sourceId] = (mergedIntoOther[candidate.sourceId] ?: 0) + 1
            } else if (ownerBefore != candidate.sourceId) {
                // The candidate took the row over, so the previous owner is the one that lost it.
                mergedIntoOther[ownerBefore] = (mergedIntoOther[ownerBefore] ?: 0) + 1
            }
        }

        return DedupeResult(
            toInsert = inserted.map { it.toEntity() },
            toUpdate = pool.filter { it.isStored && it.dirty }.map { it.toEntity() },
            mergedIntoOtherSource = mergedIntoOther,
            alreadyStored = alreadyStored,
        )
    }

    /**
     * Folds a newly fetched [candidate] into the existing [row] it duplicates.
     *
     * The row keeps its primary key whichever copy wins. Deleting and re-inserting to promote
     * a winner would lose the `notified` flag and re-notify a story the user has already been
     * told about — and would make the timeline jump under them for no visible reason.
     */
    private fun merge(row: Candidate, candidate: Candidate) {
        val previousSourceId = row.sourceId

        if (winnerOf(row, candidate) === candidate) {
            // The incoming copy was published earlier, so per "earliest publishedAt wins" it
            // becomes what this row displays — in place, keeping the row's identity.
            row.title = candidate.title
            row.summary = candidate.summary
            row.url = candidate.url
            row.canonicalUrl = candidate.canonicalUrl
            row.publishedAt = candidate.publishedAt
            row.sourceId = candidate.sourceId
            row.priority = candidate.priority
            row.normalizedTitle = candidate.normalizedTitle
            row.titleHash = candidate.titleHash
            row.tokens = candidate.tokens
            row.ngrams = candidate.ngrams
            row.breakingScore = maxOf(row.breakingScore, candidate.breakingScore)
            row.topicScore = maxOf(row.topicScore, candidate.topicScore)
            row.duplicateSourceIds += previousSourceId
            row.dirty = true
        } else {
            row.duplicateSourceIds += candidate.sourceId
            row.dirty = true
        }

        // Whichever outlet now owns the row is not one of its own duplicates.
        row.duplicateSourceIds.remove(row.sourceId)

        // A duplicate that has a picture beats a winner that does not — same story, and an
        // image slot is hidden entirely when empty, so adopting one is a straight improvement.
        if (row.imageUrl.isNullOrBlank() && !candidate.imageUrl.isNullOrBlank()) {
            row.imageUrl = candidate.imageUrl
            row.dirty = true
        }

        // `seenInFeed` is deliberately absent from the block above, even when the candidate
        // wins and rewrites everything else on the row. It describes how the *row* entered the
        // database, not which copy it currently displays, so it survives a promotion — which is
        // what stops `breakingScore = maxOf(row, candidate)` pushing a row stored at 4 past the
        // notify bar and buzzing about a story that has been on screen for hours.
        //
        // Nor is it propagated the other way: a foreground-fetched duplicate does not mark an
        // existing background row as seen. That row may be a story a killed sync never announced
        // (S3), and same-source re-fetches short-circuit before this point anyway, so setting it
        // here would suppress notifications inconsistently depending on which outlet re-ran the
        // story. See DECISIONS.md S5.
    }

    /** Earliest publication wins; equal timestamps fall back to the lower source priority. */
    private fun winnerOf(a: Candidate, b: Candidate): Candidate = when {
        a.publishedAt != b.publishedAt -> if (a.publishedAt < b.publishedAt) a else b
        else -> if (a.priority <= b.priority) a else b
    }

    private fun findDuplicate(
        candidate: Candidate,
        pool: List<Candidate>,
        byCanonicalUrl: Map<String, Candidate>,
        byTitleHash: Map<String, Candidate>,
    ): Candidate? {
        byCanonicalUrl[candidate.canonicalUrl]?.let { if (it.withinWindow(candidate)) return it }
        byTitleHash[candidate.titleHash]?.let { if (it.withinWindow(candidate)) return it }

        for (other in pool) {
            if (!other.withinWindow(candidate)) continue

            val jaccard = SimilarityEngine.jaccard(candidate.tokens, other.tokens)
            if (jaccard >= FilterConfig.JACCARD_THRESHOLD) return other

            // Cosine is evaluated even when Jaccard is low: the two measures fail on
            // different things, and a heavily reworded headline can clear cosine alone.
            // Both vectors are precomputed, so this is a map intersection, not a re-parse.
            val cosine = SimilarityEngine.cosine(candidate.ngrams, other.ngrams)
            if (cosine >= FilterConfig.COSINE_THRESHOLD) return other

            if (jaccard >= FilterConfig.COMBINED_JACCARD_THRESHOLD &&
                cosine >= FilterConfig.COMBINED_COSINE_THRESHOLD
            ) {
                return other
            }
        }

        return null
    }

    private companion object {
        /** Used when a stored row references a source that has since left the registry. */
        const val DEFAULT_PRIORITY = 99
    }

    /**
     * Mutable working copy of a row during a dedupe pass — a stored [ArticleEntity] and a
     * freshly fetched article behave identically here, so both become one of these.
     */
    private class Candidate(
        val id: String,
        var sourceId: String,
        var priority: Int,
        var title: String,
        var summary: String?,
        var url: String,
        var canonicalUrl: String,
        var imageUrl: String?,
        var publishedAt: Long,
        val fetchedAt: Long,
        var breakingScore: Int,
        var topicScore: Int,
        var normalizedTitle: String,
        var titleHash: String,
        var tokens: Set<String>,
        /** Built once per article rather than once per comparison — see [SimilarityEngine.NgramVector]. */
        var ngrams: SimilarityEngine.NgramVector,
        val duplicateSourceIds: MutableSet<String>,
        val notified: Boolean,
        /** Foreground-ingested, so already on screen — never a notification candidate. */
        val seenInFeed: Boolean,
        /** True when this row already exists in the database. */
        val isStored: Boolean,
        var dirty: Boolean = false,
    ) {
        fun withinWindow(other: Candidate): Boolean =
            kotlin.math.abs(publishedAt - other.publishedAt) <= FilterConfig.DEDUPE_WINDOW_MILLIS

        fun toEntity() = ArticleEntity(
            id = id,
            sourceId = sourceId,
            title = title,
            summary = summary,
            url = url,
            canonicalUrl = canonicalUrl,
            imageUrl = imageUrl,
            publishedAt = publishedAt,
            fetchedAt = fetchedAt,
            breakingScore = breakingScore,
            topicScore = topicScore,
            normalizedTitle = normalizedTitle,
            titleHash = titleHash,
            dayBucket = ArticleEntity.bucketOf(publishedAt),
            duplicateSourceIds = duplicateSourceIds.toList(),
            notified = notified,
            seenInFeed = seenInFeed,
        )

        companion object {
            fun fromEntity(entity: ArticleEntity, priority: Int) = Candidate(
                id = entity.id,
                sourceId = entity.sourceId,
                priority = priority,
                title = entity.title,
                summary = entity.summary,
                url = entity.url,
                canonicalUrl = entity.canonicalUrl,
                imageUrl = entity.imageUrl,
                publishedAt = entity.publishedAt,
                fetchedAt = entity.fetchedAt,
                breakingScore = entity.breakingScore,
                topicScore = entity.topicScore,
                normalizedTitle = entity.normalizedTitle,
                titleHash = entity.titleHash,
                tokens = entity.normalizedTitle.split(' ').filter { it.isNotBlank() }.toSet(),
                ngrams = SimilarityEngine.NgramVector.of(entity.normalizedTitle),
                duplicateSourceIds = entity.duplicateSourceIds.toMutableSet(),
                notified = entity.notified,
                seenInFeed = entity.seenInFeed,
                isStored = true,
            )

            fun fromScored(
                scored: ScoredArticle,
                priority: Int,
                fetchedAt: Long,
                seenInFeed: Boolean,
            ): Candidate {
                val article = scored.article
                val canonical = UrlCanonicalizer.canonicalize(article.url)
                val tokens = ArabicNormalizer.dedupeTokens(article.title)
                val normalized = tokens.joinToString(" ")
                return Candidate(
                    id = UrlCanonicalizer.stableHash(canonical),
                    sourceId = article.sourceId,
                    priority = priority,
                    title = article.title,
                    summary = article.summary,
                    url = article.url,
                    canonicalUrl = canonical,
                    imageUrl = article.imageUrl,
                    publishedAt = article.publishedAt,
                    fetchedAt = fetchedAt,
                    breakingScore = scored.classification.breakingScore,
                    topicScore = scored.classification.topicScore,
                    normalizedTitle = normalized,
                    titleHash = UrlCanonicalizer.stableHash(normalized),
                    tokens = tokens.toSet(),
                    ngrams = SimilarityEngine.NgramVector.of(normalized),
                    duplicateSourceIds = mutableSetOf(),
                    notified = false,
                    seenInFeed = seenInFeed,
                    isStored = false,
                )
            }
        }
    }
}
