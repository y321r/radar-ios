package com.radar.news.data.remote

import com.radar.news.data.model.NewsSource
import com.radar.news.data.model.RawArticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element

/**
 * Last-resort adapter: scrape an outlet's own breaking/latest page when it has neither a
 * feed nor a JSON endpoint.
 *
 * Nothing ships enabled on this adapter — all six live sources have real feeds. It exists
 * because the spec's fallback chain ends here, and because an outlet killing its RSS should
 * be a `sources.json` change rather than an app update.
 *
 * Scraping is intentionally conservative: it reads only the metadata a feed would have
 * given (headline, link, image, timestamp), never article bodies, which keeps it inside
 * the same "headline + short summary + image + link" limit as everything else.
 */
class HtmlAdapter constructor(
    private val fetcher: FeedFetcher,
) : FeedAdapter {

    override suspend fun fetch(source: NewsSource): List<RawArticle> = withContext(Dispatchers.Default) {
        val document = Ksoup.parse(fetcher.get(source.url), source.url)
        // Prefer <article> elements; fall back to any headline link if the page has none.
        val candidates: List<Element> = document.select("article").ifEmpty {
            document.select("h1 a[href], h2 a[href], h3 a[href]")
        }

        candidates.mapNotNull { element -> element.toRawArticle(source, document) }
            .distinctBy { UrlCanonicalizer.canonicalize(it.url) }
    }

    private fun Element.toRawArticle(source: NewsSource, document: Document): RawArticle? {
        val anchor = if (tagName() == "a") this else selectFirst("a[href]") ?: return null
        val link = anchor.absUrl("href").takeIf { it.startsWith("http") } ?: return null

        val title = listOf(
            selectFirst("h1, h2, h3")?.text(),
            anchor.attr("title").takeIf { it.isNotBlank() },
            anchor.text(),
        ).firstOrNull { !it.isNullOrBlank() }?.trim() ?: return null
        if (title.length < 10) return null

        // A listing page rarely carries a per-item timestamp. Without one there is no honest
        // publication time, and inventing one would corrupt both ordering and the freshness
        // score — so the item is dropped, exactly as in the feed adapters.
        val published = DateParser.parse(
            selectFirst("time[datetime]")?.attr("datetime")
                ?: selectFirst("time")?.text()
                ?: document.selectFirst("meta[property=article:published_time]")?.attr("content"),
        ) ?: return null

        val image = selectFirst("img[src]")?.absUrl("src")?.takeIf { it.startsWith("http") }
            ?.let { source.imageRewrite?.apply(it) ?: it }

        val summary = HtmlText.truncate(
            selectFirst("p")?.text()?.trim()?.takeIf { it != title },
            MAX_SUMMARY_CHARS,
        )

        return RawArticle(
            sourceId = source.id,
            title = title,
            summary = summary,
            url = link,
            publishedAt = published,
            imageUrl = image,
        )
    }
}
