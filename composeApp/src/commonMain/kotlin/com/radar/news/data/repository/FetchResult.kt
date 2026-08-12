package com.radar.news.data.repository

import com.radar.news.data.model.RawArticle
import com.radar.news.data.remote.EmptyFeedException

/**
 * The device could not reach the network at all, as opposed to a source misbehaving.
 *
 * In Doze an app that is not battery-whitelisted has no network, and every host fails DNS at
 * once. That is not six broken outlets, and treating it as one put the sync worker onto an
 * exponential retry ladder that deferred it for hours — see the Android project's DECISIONS.md
 * D30.
 *
 * Deliberately narrow. A connection refused, a read timeout or an HTTP status are all faults the
 * *source* owns and must keep counting as failures; only "there is no route to anything"
 * qualifies. The cause chain is walked because OkHttp wraps.
 *
 * KMP: the JVM actual keeps the original `java.net` exception checks; the native actual uses a
 * message heuristic, since `java.net` does not exist on Kotlin/Native.
 */
expect fun Throwable?.isNetworkUnavailable(): Boolean

/** What one source produced in a sync — either items, or the reason it failed. */
data class SourceFetch(
    val sourceId: String,
    val sourceName: String,
    val articles: List<RawArticle> = emptyList(),
    val error: Throwable? = null,
    /** HTTP status when the failure was an HTTP one; null for timeouts, DNS, parse errors. */
    val httpStatus: Int? = null,
    val durationMillis: Long = 0L,
) {
    val succeeded: Boolean get() = error == null

    /**
     * `200`, `403`, `empty_body`, or the exception class name — whatever actually happened.
     *
     * `empty_body` is called out separately because it is the failure that used to hide: a
     * source answering 200 with nothing in it looked identical to a healthy quiet source.
     */
    val statusLabel: String
        get() = when {
            succeeded -> "200"
            error is EmptyFeedException -> "empty_body"
            httpStatus != null -> httpStatus.toString()
            // Named rather than left to the class name: release builds are minified, so the
            // fallback below prints something like "pa0" in the funnel.
            error.isNetworkUnavailable() -> "offline"
            else -> error?.let { it::class.simpleName }.orEmpty()
        }
}

/**
 * The outcome of fetching every enabled source.
 *
 * A sync is a partial success by design: [anySucceeded] is what the UI keys off, so one
 * dead outlet shows the timeline as normal rather than an error, and only a total failure
 * surfaces the error state.
 */
data class FetchResult(
    val sources: List<SourceFetch>,
) {
    val articles: List<RawArticle> get() = sources.flatMap { it.articles }
    val failures: List<SourceFetch> get() = sources.filter { !it.succeeded }
    val anySucceeded: Boolean get() = sources.any { it.succeeded }
    val allFailed: Boolean get() = sources.isNotEmpty() && sources.none { it.succeeded }

    /**
     * Every source failed, and every one of them failed because there was no network.
     *
     * Requires *all* of them: if a single source failed for its own reasons the network was up,
     * so this was not an outage. Drives two decisions — the worker must not start a retry
     * ladder over it and the UI must not blame the outlets for it.
     */
    val offline: Boolean
        get() = sources.isNotEmpty() && sources.all { !it.succeeded && it.error.isNetworkUnavailable() }
}

/**
 * Where a source's items went, stage by stage.
 *
 * Built because "الجزيرة never appears in the feed" has at least four distinct causes — the
 * fetch failed, nothing passed the topic filter, nothing passed the breaking filter, or dedupe
 * gave every story to a faster outlet — and they are indistinguishable from the timeline alone.
 */
data class SourceFunnel(
    val sourceId: String,
    val sourceName: String,
    val status: String,
    /** Items that parsed into a complete article (title + link + usable date). */
    val parsed: Int = 0,
    val breakingPass: Int = 0,
    val topicPass: Int = 0,
    /** Passed both thresholds. */
    val accepted: Int = 0,
    /** Accepted, but a **different** outlet already owned the story. Genuine loss. */
    val dedupedAway: Int = 0,
    /** Accepted, but it was the same article this source already supplied. Steady state. */
    val alreadyStored: Int = 0,
    val inserted: Int = 0,
    /** Published within 30 minutes of the sync — the only reliable route past the breaking bar. */
    val fresh30m: Int = 0,
    val fresh2h: Int = 0,
    /** Age of this source's newest item, in minutes. */
    val newestAgeMinutes: Long = 0,
    val error: String? = null,
) {
    /** Share of this source's accepted items that a *different* outlet won. */
    val dedupeLossPercent: Int
        get() = if (accepted == 0) 0 else (dedupedAway * 100) / accepted
}

/** Renders the funnel as a fixed-width table for logcat and the debug screen. */
fun List<SourceFunnel>.renderTable(): String {
    // KMP shell: String.format is JVM-only; padStart/padEnd do the fixed-width work.
    fun c(v: Any?, w: Int, left: Boolean = false): String {
        val s = v?.toString().orEmpty()
        return if (left) s.padEnd(w) else s.padStart(w)
    }
    val header = listOf(
        c("source", 13, true), c("status", 7, true), c("parsed", 6), c("<30m", 6), c("<2h", 6),
        c("newest", 6), c("brk", 5), c("top", 5), c("accept", 6), c("lost", 6), c("ins", 5),
    ).joinToString(" ")
    val rows = sortedBy { it.sourceId }.joinToString("\n") { f ->
        listOf(
            c(f.sourceId, 13, true), c(f.status, 7, true), c(f.parsed, 6), c(f.fresh30m, 6),
            c(f.fresh2h, 6), c("${f.newestAgeMinutes}m", 6), c(f.breakingPass, 5), c(f.topicPass, 5),
            c(f.accepted, 6), c(f.dedupedAway, 6), c(f.inserted, 5),
        ).joinToString(" ") + (f.error?.let { "  <- $it" } ?: "")
    }
    val totals = listOf(
        c("TOTAL", 13, true), c("", 7, true), c(sumOf { it.parsed }, 6), c(sumOf { it.fresh30m }, 6),
        c(sumOf { it.fresh2h }, 6), c("", 6), c(sumOf { it.breakingPass }, 5), c(sumOf { it.topicPass }, 5),
        c(sumOf { it.accepted }, 6), c(sumOf { it.dedupedAway }, 6), c(sumOf { it.inserted }, 5),
    ).joinToString(" ")
    val rule = "-".repeat(header.length)
    return "$header\n$rule\n$rows\n$rule\n$totals\n" +
        "  brk=passed breaking>=${'$'}MIN  top=passed topic  lost=story won by ANOTHER source  " +
        "ins=new rows (already-stored re-fetches are not losses)"
}
