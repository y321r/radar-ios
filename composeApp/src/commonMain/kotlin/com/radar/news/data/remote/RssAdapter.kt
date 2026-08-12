package com.radar.news.data.remote

import com.radar.news.util.RadarLog

import com.radar.news.data.model.NewsSource
import com.radar.news.data.model.RawArticle

/**
 * RSS 2.0 and RSS 1.0/RDF. Handles all six live sources.
 */
class RssAdapter constructor(
    private val fetcher: FeedFetcher,
) : FeedAdapter {
    override suspend fun fetch(source: NewsSource): List<RawArticle> {
        val items = fetcher.fetchItems(source)
        val articles = items.mapNotNull { it.toRawArticle(source) }
        // Parsed-versus-converted counts, because "0 articles, 0 errors" is otherwise
        // indistinguishable between an empty feed and every item being dropped in conversion.
        if (articles.size < items.size) {
            val sample = items.firstOrNull { it.toRawArticle(source) == null }
            RadarLog.d(
                TAG,
                "${source.id}: parsed=${items.size} converted=${articles.size}; " +
                    "first dropped: title=${sample?.title != null} link=${sample?.link} date='${sample?.dateRaw}'",
            )
        }
        return articles
    }

    private companion object {
        const val TAG = "RssAdapter"
    }
}

/**
 * Atom. [XmlFeedParser] already understands `<entry>`, `<link href>` and `<published>`,
 * so this exists to make the registry's `adapter: "atom"` value explicit and to give Atom
 * somewhere to grow if a source ever needs it.
 */
class AtomAdapter constructor(
    private val fetcher: FeedFetcher,
) : FeedAdapter {
    override suspend fun fetch(source: NewsSource): List<RawArticle> =
        fetcher.fetchItems(source).mapNotNull { it.toRawArticle(source) }
}
