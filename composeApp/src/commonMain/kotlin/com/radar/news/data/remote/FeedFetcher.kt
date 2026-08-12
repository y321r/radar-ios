package com.radar.news.data.remote

import com.radar.news.util.RadarLog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** A feed responded, but with something that is not a usable body. */
class FeedHttpException(val code: Int, url: String) :
    Exception("HTTP $code for $url")

/**
 * The feed answered `200` but contained no items, twice in a row.
 *
 * Distinct from an HTTP failure on purpose. France 24 intermittently serves an empty body with
 * a `200`, which the funnel previously reported as a perfectly healthy source that just happened
 * to contribute nothing — an outlet could vanish from the timeline while every diagnostic said
 * it was fine. Treating it as a failure is what makes that visible.
 */
class EmptyFeedException(url: String) :
    Exception("HTTP 200 but zero items parsed for $url")

/**
 * A feed answered with more bytes than the app is willing to hold in memory.
 *
 * Deliberately **not** a [FeedHttpException]: an oversized body is not something a retry can
 * fix, and routing it through the 403/5xx retry path would double the damage.
 */
class FeedTooLargeException(val url: String, val bytes: Long) :
    Exception("feed body exceeds ${FeedFetcher.MAX_BODY_BYTES} bytes ($bytes) for $url")

/**
 * Fetches a feed body over OkHttp.
 *
 * Kept deliberately thin: every source is an absolute URL returning raw XML, so there is
 * nothing for a typed HTTP layer to buy us here (see DECISIONS.md D5).
 *
 * **One retry on 403/5xx.** Al Arabiya serves a bot-challenge interstitial
 * («خطوة إضافية واحدة») to roughly one request in seven, at random. An A/B over 16 interleaved
 * calls proved it is *not* header-driven — 7/8 succeeded both with and without `Accept-Language`
 * — so the correct response is to ask again rather than to disguise the client. A single retry
 * takes a ~14% per-sync failure rate to about 2%.
 *
 * Deliberately only one, and only for statuses that a retry can plausibly fix: a 404 is
 * permanent, and hammering a source that is genuinely refusing us is how a client earns a real
 * block rather than a random one.
 */
class FeedFetcher constructor(
    private val client: OkHttpClient,
) {
    /**
     * @param allowRetry set false when the caller is *already* inside a retry of its own, so
     *   that an empty-body retry cannot stack a second HTTP retry on top and blow the
     *   per-source time budget.
     */
    suspend fun get(url: String, allowRetry: Boolean = true): String = withContext(Dispatchers.IO) {
        try {
            attempt(url)
        } catch (e: FeedHttpException) {
            if (!allowRetry || !e.code.isWorthRetrying()) throw e
            RadarLog.i(TAG, "HTTP ${e.code} for $url — retrying once in ${RETRY_DELAY_MILLIS}ms")
            delay(RETRY_DELAY_MILLIS)
            try {
                attempt(url).also { RadarLog.i(TAG, "retry succeeded for $url") }
            } catch (retry: FeedHttpException) {
                RadarLog.w(TAG, "retry failed for $url — HTTP ${retry.code}, giving up this cycle")
                throw retry
            }
        }
    }

    companion object {
        /** Shared by the HTTP retry and the empty-body retry, so both back off identically. */
        const val RETRY_DELAY_MILLIS = 2_000L

        /**
         * Ceiling on a single feed body, after any transparent gzip decompression.
         *
         * The largest of the six live feeds is Al Arabiya at ~275 KB, so this leaves roughly
         * 29× headroom over real traffic while keeping a hostile or compressed-bomb response
         * from being materialised as a String.
         */
        const val MAX_BODY_BYTES = 8L * 1024 * 1024

        private const val TAG = "FeedFetcher"
    }

    private fun attempt(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
            .header("Accept-Language", "ar,en;q=0.8")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw FeedHttpException(response.code, url)
            return response.body.readCapped(url)
        }
    }

    /** 403 is the bot-challenge; 5xx is a transient server fault. A 404 is permanent. */
    private fun Int.isWorthRetrying(): Boolean = this == 403 || this in 500..599

    /**
     * Reads at most [MAX_BODY_BYTES] and throws rather than truncating.
     *
     * `callTimeout` bounds how long a response may take, not how large it may be, and OkHttp
     * asks for gzip and inflates transparently — so `body.string()` on a compressed bomb
     * materialises the *decompressed* size as a UTF-16 String, two bytes per character.
     *
     * Truncating instead of throwing would be worse than either: a half-read document parses
     * to a partial item list, which the empty-body retry then cannot distinguish from a healthy
     * short feed. A refused body is a failure the funnel can name.
     *
     * `request()` fills the buffer only until the ceiling is passed, so a bomb is caught after
     * ~8 MB of inflation rather than all of it. The declared length is checked first because
     * an honest oversized response can be refused without reading it at all.
     */
    private fun okhttp3.ResponseBody.readCapped(url: String): String {
        val declared = contentLength()
        if (declared > MAX_BODY_BYTES) throw FeedTooLargeException(url, declared)

        val body = source()
        body.request(MAX_BODY_BYTES + 1)
        val buffered = body.buffer
        if (buffered.size > MAX_BODY_BYTES) throw FeedTooLargeException(url, buffered.size)

        // Same charset rule as body.string(): honour Content-Type, fall back to UTF-8, which
        // every one of the six live feeds declares.
        return buffered.readString(contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
    }
}
