package com.radar.news.data.repository

import com.radar.news.util.currentTimeMillis
import com.radar.news.util.RadarLog
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.radar.news.data.local.ArticleDao
import com.radar.news.data.local.ArticleEntity
import com.radar.news.data.model.Article
import com.radar.news.data.model.NewsSource
import com.radar.news.data.remote.FeedHttpException
import com.radar.news.data.remote.SourceRegistry
import com.radar.news.domain.dedupe.Deduplicator
import com.radar.news.domain.dedupe.ScoredArticle
import com.radar.news.domain.filter.BreakingNewsClassifier
import com.radar.news.domain.filter.FilterConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout

/** What one sync did, in enough detail for the worker to decide whether to notify. */
data class SyncOutcome(
    val fetch: FetchResult,
    val fetched: Int = 0,
    val accepted: Int = 0,
    val inserted: List<ArticleEntity> = emptyList(),
    val updated: Int = 0,
    val pruned: Int = 0,
    /** Per-source stage-by-stage attribution — see [SourceFunnel]. */
    val funnel: List<SourceFunnel> = emptyList(),
) {
    val succeeded: Boolean get() = fetch.anySucceeded
}

/**
 * Owns the whole path from feeds to stored timeline rows.
 *
 * Two guarantees shape this class:
 *
 *  1. **A failing source can never break the sync.** Each source runs in its own `async`,
 *     inside its own timeout, with its own `try/catch`, so a 403, DNS failure, malformed XML
 *     or hang costs exactly that outlet's items and the others still land.
 *  2. **Rejected items are never stored.** Classification happens before persistence, so the
 *     database only ever holds articles the user is meant to see — the feed cannot leak
 *     sports or entertainment even transiently.
 */
class NewsRepository constructor(
    private val registry: SourceRegistry,
    private val dao: ArticleDao,
    private val classifier: BreakingNewsClassifier,
    private val deduplicator: Deduplicator,
) {

    // -------------------------------------------------------------- observation ---

    /** The timeline, paged from Room 20 at a time. */
    fun pagedArticles(): Flow<PagingData<Article>> = flow {
        // Resolved once up front rather than read from a cache that may not be warm yet —
        // otherwise the first page renders with raw source ids instead of outlet names.
        val sources = sources()
        val pager = Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = PAGE_SIZE / 2,
                enablePlaceholders = false,
                initialLoadSize = PAGE_SIZE * 2,
            ),
            pagingSourceFactory = { dao.pagingSource() },
        )
        emitAll(pager.flow.map { paging -> paging.map { entity -> entity.toArticle(sources) } })
    }

    fun observeCount(): Flow<Int> = dao.observeCount()

    /** Stories newer than [watermark] — what the "new posts" pill counts. */
    fun observeCountNewerThan(watermark: Long): Flow<Int> = dao.observeCountNewerThan(watermark)

    suspend fun newestPublishedAt(): Long = dao.newestPublishedAt() ?: 0L

    /**
     * The timeline as a plain list flow (non-paged) — the KMP shell's UI renders this
     * directly; paging remains available via [pagedArticles] for later refinement.
     */
    fun observeLatest(limit: Int = 200): Flow<List<Article>> = dao.observeLatest(limit).map { rows ->
        val sources = sources()
        rows.map { it.toArticle(sources) }
    }

    suspend fun articleById(id: String): Article? = dao.byId(id)?.toArticle(sources())

    // --------------------------------------------------------------------- sync ---

    /**
     * Fetch → classify → dedupe → insert → prune.
     *
     * Notifications are deliberately *not* raised here; the worker owns that, so a
     * pull-to-refresh in the foreground cannot push a notification for a story the user is
     * already looking at.
     *
     * @param foreground true for app-open and pull-to-refresh. Rows this sync inserts are
     *   marked `seenInFeed` so the *next* background worker cannot announce them either —
     *   skipping the notification step here only suppressed publishing, not eligibility, and
     *   the worker re-reads eligibility from the database by design (S3). See DECISIONS.md S5.
     */
    suspend fun sync(
        now: Long = currentTimeMillis(),
        foreground: Boolean = false,
    ): SyncOutcome {
        val fetch = fetchAll()
        if (!fetch.anySucceeded) {
            RadarLog.w(TAG, "sync: every source failed")
            return SyncOutcome(fetch = fetch)
        }

        val sources = sources()

        // Filter before anything touches the database. Per-source tallies are kept as we go so
        // the funnel can attribute every drop to the stage that caused it.
        val breakingPass = mutableMapOf<String, Int>()
        val topicPass = mutableMapOf<String, Int>()
        val acceptedBySource = mutableMapOf<String, Int>()

        val scored = fetch.articles.mapNotNull { raw ->
            val classification = classifier.classify(raw, now)
            if (classification.breakingScore >= FilterConfig.MIN_BREAKING_SCORE) {
                breakingPass.merge(raw.sourceId, 1, Int::plus)
            }
            if (classification.topicScore >= FilterConfig.MIN_TOPIC_SCORE) {
                topicPass.merge(raw.sourceId, 1, Int::plus)
            }
            if (classification.accepted) {
                acceptedBySource.merge(raw.sourceId, 1, Int::plus)
                ScoredArticle(raw, classification)
            } else {
                null
            }
        }

        // Only the buckets overlapping the dedupe window are read — never the whole table.
        val newestBucket = ArticleEntity.bucketOf(now)
        val oldestBucket = ArticleEntity.bucketOf(now - FilterConfig.DEDUPE_WINDOW_MILLIS) - 1
        val existing = dao.inBuckets(oldestBucket, newestBucket + 1)

        val dedupe = deduplicator.dedupe(scored, existing, sources, now, seenInFeed = foreground)

        val inserted = dao.insertAndReturnNew(dedupe.toInsert)
        if (dedupe.toUpdate.isNotEmpty()) dao.updateAll(dedupe.toUpdate)
        val pruned = dao.pruneOlderThan(now - FilterConfig.RETENTION_MILLIS)

        // An accepted item that did not become a row lost its story to another outlet. This is
        // the number that distinguishes "filtered out" from "erased by a faster source".
        val insertedBySource = dedupe.toInsert.groupingBy { it.sourceId }.eachCount()

        val funnel = fetch.sources.map { source ->
            val ages = source.articles.map { now - it.publishedAt }
            SourceFunnel(
                sourceId = source.sourceId,
                sourceName = source.sourceName,
                status = source.statusLabel,
                parsed = source.articles.size,
                breakingPass = breakingPass[source.sourceId] ?: 0,
                topicPass = topicPass[source.sourceId] ?: 0,
                accepted = acceptedBySource[source.sourceId] ?: 0,
                // Only genuine cross-source loss. A re-fetch of an article this source already
                // supplied is steady state, and counting it as a loss made every healthy
                // source look like it was being erased.
                dedupedAway = dedupe.mergedIntoOtherSource[source.sourceId] ?: 0,
                alreadyStored = dedupe.alreadyStored[source.sourceId] ?: 0,
                inserted = insertedBySource[source.sourceId] ?: 0,
                fresh30m = ages.count { it in 0..FilterConfig.FRESH_30M_MILLIS },
                fresh2h = ages.count { it in 0..FilterConfig.FRESH_2H_MILLIS },
                newestAgeMinutes = (ages.minOrNull() ?: 0L) / 60_000L,
                error = source.error?.let { "${it.javaClass.simpleName}: ${it.message}" },
            )
        }

        RadarLog.i(TAG, "[FUNNEL] sync complete — inserted=${inserted.size} pruned=$pruned\n${funnel.renderTable()}")
        funnel.filter { it.accepted > 0 && it.dedupeLossPercent > 60 }.forEach {
            RadarLog.w(
                TAG,
                "[FUNNEL] ${it.sourceId} lost ${it.dedupeLossPercent}% of its accepted items to " +
                    "other sources (${it.dedupedAway}/${it.accepted}) — priority tie-break may be " +
                    "systematically favouring a faster outlet",
            )
        }

        return SyncOutcome(
            fetch = fetch,
            fetched = fetch.articles.size,
            accepted = scored.size,
            inserted = inserted,
            updated = dedupe.toUpdate.size,
            pruned = pruned,
            funnel = funnel,
        )
    }

    // -------------------------------------------------------------------- fetch ---

    suspend fun fetchAll(): FetchResult = coroutineScope {
        registry.enabled()
            .map { source -> async { fetchOne(source) } }
            .awaitAll()
            .let(::FetchResult)
    }

    private suspend fun fetchOne(source: NewsSource): SourceFetch {
        val started = currentTimeMillis()
        return try {
            val articles = withTimeout(PER_SOURCE_TIMEOUT_MILLIS) {
                registry.adapterFor(source).fetch(source)
            }
            RadarLog.d(TAG, "${source.id}: ${articles.size} articles in ${currentTimeMillis() - started}ms")
            SourceFetch(
                sourceId = source.id,
                sourceName = source.name,
                articles = articles,
                durationMillis = currentTimeMillis() - started,
            )
        } catch (e: Exception) {
            // Deliberately broad. Anything a feed can throw — IO, XML, timeout, a parser NPE
            // on malformed markup — must degrade to "this source contributed nothing".
            RadarLog.w(TAG, "${source.id} failed: ${e::class.simpleName}: ${e.message}")
            SourceFetch(
                sourceId = source.id,
                sourceName = source.name,
                error = e,
                httpStatus = (e as? FeedHttpException)?.code,
                durationMillis = currentTimeMillis() - started,
            )
        }
    }

    // ------------------------------------------------------------------- lookups ---

    /**
     * `all()`, not `enabled()`: a stored row may reference a source that has since been
     * disabled, and it still needs its display name and colour to render.
     */
    private suspend fun sources(): Map<String, NewsSource> = registry.all().associateBy { it.id }

    private companion object {
        const val TAG = "NewsRepository"

        /**
         * Budget for one source including **both** kinds of retry.
         *
         * The spec's 10s applies per network call and is enforced by OkHttp's `callTimeout`.
         * This outer budget covers the worst legitimate path:
         *
         *   HTTP retry     10s + 2s backoff + 10s  = 22s
         *   empty-body retry     + 2s backoff + 10s  = 34s
         *
         * The empty-body retry is issued with `allowRetry = false` precisely so it cannot add
         * a second HTTP retry and push this past 35s. Sources run in parallel, so the fetch
         * phase costs one source's budget rather than six — and in practice a source answers in
         * under two seconds.
         *
         * **This does not bound a sync.** It wraps `adapterFor(source).fetch(source)` and
         * nothing else; classification, dedupe, insert, update and prune all run after the
         * fetches join, outside any timeout of ours. In the worker that leaves WorkManager's
         * ~10-minute execution budget as the only ceiling, and on the foreground path there is
         * none. What actually keeps those stages cheap is
         * [com.radar.news.data.remote.XmlFeedParser.MAX_ITEMS_PER_FEED] and the bucket-indexed
         * dedupe window, not this timeout. An earlier version of this comment claimed the
         * opposite, and `AUDIT_CONTEXT.md` §3 repeated it.
         */
        const val PER_SOURCE_TIMEOUT_MILLIS = 35_000L
        const val PAGE_SIZE = 20
    }
}

/** Maps a stored row onto the display model, resolving its outlet from the registry. */
internal fun ArticleEntity.toArticle(sources: Map<String, NewsSource>): Article {
    val source = sources[sourceId]
    return Article(
        id = id,
        sourceId = sourceId,
        sourceName = source?.name ?: sourceId,
        sourceColor = source?.color ?: "#71767B",
        sourceDomain = source?.domain.orEmpty(),
        title = title,
        summary = summary,
        url = url,
        imageUrl = imageUrl,
        publishedAt = publishedAt,
        breakingScore = breakingScore,
        topicScore = topicScore,
        duplicateSourceIds = duplicateSourceIds,
    )
}
