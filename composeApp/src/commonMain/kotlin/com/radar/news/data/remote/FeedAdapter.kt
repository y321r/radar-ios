package com.radar.news.data.remote

import com.radar.news.util.RadarLog

import com.radar.news.data.model.NewsSource
import com.radar.news.data.model.RawArticle
import kotlinx.coroutines.delay
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * How one kind of source is turned into articles. One implementation per `adapter` value
 * in `sources.json` — `rss`, `atom`, `googlenews`, `html`.
 *
 * Implementations may throw freely: [com.radar.news.data.repository.NewsRepository] catches
 * per source, so a dead feed costs its own items and nothing else.
 */
interface FeedAdapter {
    suspend fun fetch(source: NewsSource): List<RawArticle>
}

/** Maximum stored summary length. The spec's legal cap, enforced at ingest. */
const val MAX_SUMMARY_CHARS = 200

/**
 * Maximum stored headline length.
 *
 * Measured worst case across all six shipping feeds is 90 characters, or 150 through the
 * Google News adapter (which appends ` - <outlet>` before it is stripped). 300 is therefore
 * twice the largest headline ever observed and cannot clip real traffic — while an uncapped
 * title reached the database, the notification and the share intent at a million characters.
 */
const val MAX_TITLE_CHARS = 300

/**
 * Maximum length of an article or image URL.
 *
 * Deliberately generous: Google News wraps each article in a base64 redirect blob, the longest
 * measured being 1,136 characters, so a tighter cap would silently disable that adapter. The
 * longest link among the six RSS sources is 538 (France 24).
 */
const val MAX_URL_CHARS = 2_048

/**
 * Fetches and parses a feed, treating **`200` with zero items as a failure**.
 *
 * A source that answers `200` with an empty body used to be indistinguishable from a source
 * that simply had nothing new: the funnel logged `status=200, parsed=0` and every diagnostic
 * said the outlet was healthy while it silently contributed nothing to the timeline. France 24
 * does this intermittently.
 *
 * One retry, on the same 2-second backoff as the HTTP path. The retry is issued with
 * `allowRetry = false` so it cannot stack another HTTP retry underneath and overrun the
 * per-source budget. Still empty the second time, [EmptyFeedException] is thrown and the source
 * is reported as `empty_body` rather than as a healthy 200.
 */
internal suspend fun FeedFetcher.fetchItems(source: NewsSource): List<FeedItem> {
    val first = XmlFeedParser.parse(get(source.url), sourceId = source.id)
    if (first.isNotEmpty()) return first

    RadarLog.w(
        TAG_EMPTY,
        "${source.id}: HTTP 200 but zero items parsed — retrying once in " +
            "${FeedFetcher.RETRY_DELAY_MILLIS}ms",
    )
    delay(FeedFetcher.RETRY_DELAY_MILLIS)

    val second = XmlFeedParser.parse(get(source.url, allowRetry = false), sourceId = source.id)
    if (second.isEmpty()) {
        RadarLog.w(TAG_EMPTY, "${source.id}: still zero items after retry — reporting empty_body")
        throw EmptyFeedException(source.url)
    }
    RadarLog.i(TAG_EMPTY, "${source.id}: retry recovered ${second.size} items")
    return second
}

private const val TAG_EMPTY = "FeedAdapter"

/**
 * Shared conversion from a parsed [FeedItem] to a [RawArticle]: resolve the link, parse the
 * date, flatten the description to plain text, cap it, and apply any per-source image rewrite.
 *
 * Returns `null` for items that cannot become a valid article — no title, no link, or an
 * unparseable date. Dropping them here rather than storing a half-item keeps every
 * downstream stage (scoring, dedupe, ordering) working on complete rows.
 */
internal fun FeedItem.toRawArticle(source: NewsSource): RawArticle? {
    // Capped like the summary, and for the same reason: an over-long headline is not a headline.
    // Truncating rather than dropping keeps the story, which a real outlet publishing a long
    // title still deserves.
    val resolvedTitle = HtmlText.truncate(HtmlText.toPlainText(title), MAX_TITLE_CHARS)
        ?: return null
    // A URL cannot be truncated — a clipped link is a broken link — so an absurd one drops
    // the item outright. http is still accepted here: this URL is handed to the browser, which
    // applies its own transport policy, and some outlets still publish http article links.
    val resolvedLink = link?.validUrl()
        ?: guid?.validUrl()
        ?: return null

    // A missing, unparseable or future-dated date is fatal on purpose — see DateParser.parse.
    val published = DateParser.parse(dateRaw) ?: return null

    val summary = HtmlText.truncate(HtmlText.toPlainText(descriptionHtml), MAX_SUMMARY_CHARS)
        // Some feeds repeat the headline as the description; showing it twice is noise.
        ?.takeIf { !it.equals(resolvedTitle, ignoreCase = true) }

    // An unusable image URL costs the picture, not the story — the slot is hidden when empty
    // anyway, and two of the six sources never supply one.
    //
    // https only, unlike the article link: this URL is dereferenced *inside the app* by Coil,
    // against whatever host the feed names. Measured across all six shipping feeds, all 97
    // image-bearing items already use https, so the rule costs no pictures.
    val image = imageUrl
        ?.validUrl(httpsOnly = true)
        ?.let { source.imageRewrite?.apply(it) ?: it }
        ?.validUrl(httpsOnly = true)

    return RawArticle(
        sourceId = source.id,
        title = resolvedTitle,
        summary = summary,
        url = resolvedLink,
        publishedAt = published,
        imageUrl = image,
        categories = categories,
    )
}

/**
 * The string back if it is a real, bounded http(s) URL, otherwise null.
 *
 * `startsWith("http")` was a prefix test, not a scheme test: it accepted `http://` where https
 * was wanted, and accepted strings like `httpfoo/../..` that are not URLs at all. `toHttpUrlOrNull`
 * both parses and restricts the scheme to http/https.
 *
 * The original string is returned rather than the parsed form — canonicalisation is
 * [UrlCanonicalizer]'s job and happens later, and rewriting the stored URL here would change
 * what the share sheet and the source-domain label show.
 */
private fun String.validUrl(httpsOnly: Boolean = false): String? {
    if (length > MAX_URL_CHARS) return null
    val parsed = toHttpUrlOrNull() ?: return null
    if (httpsOnly && parsed.scheme != "https") return null
    return this
}
