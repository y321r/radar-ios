package com.radar.news.data.remote

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

import com.radar.news.data.model.NewsSource
import com.radar.news.data.model.RawArticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Google News RSS used as an adapter for an outlet with no usable feed of its own
 * (`https://news.google.com/rss/search?q=site:<domain>+when:2h&hl=ar&gl=SA&ceid=SA:ar`).
 *
 * Nothing currently ships enabled on this adapter — Phase 0 established that Reuters, the
 * one source that needed it, has no Arabic inventory in Google News at all. It is
 * implemented and tested so that repointing any source at Google News stays a `sources.json`
 * edit. See DECISIONS.md D1.
 *
 * Two things have to be undone before Google News items are usable:
 *  1. every link is a `news.google.com/rss/articles/<blob>` redirect, not the article, and
 *  2. every title has the outlet appended as `Headline - المصدر`.
 */
class GoogleNewsAdapter constructor(
    private val fetcher: FeedFetcher,
    private val client: OkHttpClient,
) : FeedAdapter {

    override suspend fun fetch(source: NewsSource): List<RawArticle> {
        val items = fetcher.fetchItems(source)
        return items.mapNotNull { item ->
            val article = item.toRawArticle(source) ?: return@mapNotNull null
            article.copy(
                title = stripSourceSuffix(article.title),
                url = resolveArticleUrl(article.url),
            )
        }
    }

    /**
     * Turns a Google redirect into the publisher's own URL.
     *
     * The article id is URL-safe base64 wrapping a small protobuf that, in the widely used
     * `CBMi` form, contains the target URL as a plain string — so decoding locally avoids a
     * network round trip per item. Newer opaque ids do not, and fall back to actually
     * following the redirect. If both fail the Google URL is kept: it still opens the
     * article, which beats dropping the story.
     */
    internal suspend fun resolveArticleUrl(url: String): String {
        if (!url.contains("news.google.com")) return url
        decodeEmbeddedUrl(url)?.let { return it }
        return followRedirect(url) ?: url
    }

    @OptIn(ExperimentalEncodingApi::class)
    internal fun decodeEmbeddedUrl(url: String): String? {
        val id = ARTICLE_ID.find(url)?.groupValues?.get(1) ?: return null
        val bytes = runCatching {
            Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL).decode(id)
        }.getOrNull() ?: return null

        // Latin-1 maps every byte to a char 1:1, so the protobuf framing bytes survive as
        // harmless noise around any ASCII URL rather than being mangled by UTF-8 decoding.
        val text = bytes.joinToString("") { ((it.toInt() and 0xFF).toChar()).toString() }
        val found = EMBEDDED_URL.find(text)?.value ?: return null
        return found.takeIf { !it.contains("news.google.com") }
    }

    private suspend fun followRedirect(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).head().build()
            client.newCall(request).execute().use { response ->
                response.request.url.toString().takeIf { !it.contains("news.google.com") }
            }
        }.getOrNull()
    }

    internal companion object {
        /** Exposes local decoding to LiveFeedProbe without needing a constructed adapter. */
        fun decodeForProbe(url: String): String? {
            val id = ARTICLE_ID.find(url)?.groupValues?.get(1) ?: return null
            val bytes = runCatching {
                Base64.UrlSafe.decode(id.padEnd((id.length + 3) / 4 * 4, '='))
            }.getOrNull() ?: return null
            val text = bytes.joinToString("") { ((it.toInt() and 0xFF).toChar()).toString() }
            return EMBEDDED_URL.find(text)?.value?.takeIf { !it.contains("news.google.com") }
        }

        private val ARTICLE_ID = Regex("""/rss/articles/([A-Za-z0-9_-]+)""")
        private val EMBEDDED_URL = Regex("""https?://[A-Za-z0-9._~:/?#\[\]@!$&'()*+,;=%-]{10,}""")

        /**
         * Google appends ` - <outlet>` to every headline. Only the last segment is removed,
         * and only when it is short enough to be an outlet name — Arabic headlines legitimately
         * contain hyphens, and truncating one at the first dash would mangle it.
         */
        fun stripSourceSuffix(title: String): String {
            val idx = title.lastIndexOf(" - ")
            if (idx <= 0) return title
            val tail = title.substring(idx + 3).trim()
            val head = title.substring(0, idx).trim()
            return if (tail.length in 1..40 && head.length >= 15) head else title
        }
    }
}
