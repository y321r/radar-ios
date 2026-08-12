package com.radar.news.data.remote

import com.radar.news.util.RadarLog

/**
 * One item as it literally appears in a feed — every field still a raw string, nothing
 * interpreted. Turning this into a [com.radar.news.data.model.RawArticle] (parsing the
 * date, stripping the HTML, truncating the summary) is the adapter's job, which keeps
 * this class testable without a source registry.
 */
data class FeedItem(
    val title: String? = null,
    val link: String? = null,
    val descriptionHtml: String? = null,
    val dateRaw: String? = null,
    val guid: String? = null,
    val imageUrl: String? = null,
    val categories: List<String> = emptyList(),
)

/**
 * A single tolerant parser for every feed dialect the app meets: RSS 2.0, RSS 1.0/RDF and
 * Atom. Phase 0 confirmed all six live sources are RSS 2.0, but Atom and RDF handling
 * costs almost nothing here and means a source can be repointed in `sources.json` without
 * a code change — which is the whole point of the registry.
 *
 * Namespace prefixes are kept verbatim in tag names on purpose. With namespace processing
 * on, `media:content` and `content:encoded` would arrive as bare local names (`content`
 * for both) and become impossible to tell apart without tracking namespace URIs. Off, tag
 * names arrive exactly as written in the feed.
 *
 * KMP note: this is a rewrite of the Android original, which used `XmlPullParser` (JVM-only).
 * The scanner below is a deliberately small pull-style XML tokenizer covering exactly what
 * RSS/Atom feeds use: elements, attributes, text, CDATA, comments, entities. Malformed
 * input aborts the scan rather than throwing — the caller already sanitised the body.
 */
object XmlFeedParser {

    private val ITEM_TAGS = setOf("item", "entry")

    /** Ceiling on items taken from one feed body. See the Android project's notes. */
    const val MAX_ITEMS_PER_FEED = 200

    private const val TAG = "XmlFeedParser"

    /** @param sourceId names the outlet in the truncation warning; null when not known. */
    fun parse(xml: String, sourceId: String? = null): List<FeedItem> {
        val cleaned = HtmlText.sanitizeForXml(xml)
        val scanner = XmlScanner(cleaned)

        val items = mutableListOf<FeedItem>()
        // Counts every item the document offered, including those past the cap, so the warning
        // can say how far over the feed actually was rather than just "at the limit".
        var seen = 0
        while (true) {
            val token = runCatching { scanner.next() }.getOrElse { break }
            when (token) {
                is XmlToken.EndDoc -> break
                is XmlToken.Start -> {
                    if (token.name.lowercase() in ITEM_TAGS) {
                        seen++
                        if (items.size < MAX_ITEMS_PER_FEED) {
                            runCatching { items += readItem(scanner, token) }
                        }
                    }
                }
                else -> Unit
            }
        }

        if (seen > MAX_ITEMS_PER_FEED) {
            // Warn, not debug: a live source reaching this is either a feed that changed shape
            // or an attempt to exhaust the device, and both need to be visible.
            RadarLog.w(
                TAG,
                "${sourceId ?: "feed"}: truncated to $MAX_ITEMS_PER_FEED items — the body " +
                    "offered $seen. A legitimate source should never reach this.",
            )
        }
        return items
    }

    private fun readItem(scanner: XmlScanner, itemStart: XmlToken.Start): FeedItem {
        val containerTag = itemStart.name
        var title: String? = null
        var link: String? = null
        var description: String? = null
        var contentEncoded: String? = null
        var date: String? = null
        var guid: String? = null
        var image: String? = null
        val categories = mutableListOf<String>()

        var depth = 1
        while (depth > 0) {
            val token = runCatching { scanner.next() }.getOrElse { break }
            when (token) {
                is XmlToken.EndDoc -> break
                is XmlToken.End -> {
                    if (token.name.equals(containerTag, ignoreCase = true)) depth--
                }
                is XmlToken.Start -> when (token.name.lowercase()) {
                    "title" -> title = title ?: readText(scanner, token)
                    // RSS puts the URL in the element text; Atom puts it in an href attribute
                    // on a self-closing tag, where readText() would return nothing.
                    "link" -> {
                        val href = token.attrs["href"]
                        if (href != null) {
                            val rel = token.attrs["rel"]
                            if (link == null && (rel == null || rel == "alternate")) link = href
                        } else if (!token.selfClosing) {
                            val text = readText(scanner, token)
                            if (link == null && !text.isNullOrBlank()) link = text
                        }
                    }
                    "description", "summary" -> description = description ?: readText(scanner, token)
                    "content:encoded", "content" -> contentEncoded = contentEncoded ?: readText(scanner, token)
                    "pubdate", "dc:date", "published", "updated" -> date = date ?: readText(scanner, token)
                    "guid", "id" -> guid = guid ?: readText(scanner, token)
                    "category", "dc:subject" -> {
                        // Atom carries the value in a `term` attribute, RSS in the element text.
                        val term = token.attrs["term"]
                        val value = if (term != null) term else readText(scanner, token)
                        if (!value.isNullOrBlank()) categories += value.trim()
                    }
                    "media:content", "media:thumbnail" -> {
                        val url = token.attrs["url"]
                        val type = token.attrs["type"].orEmpty()
                        val medium = token.attrs["medium"].orEmpty()
                        // media:content is also used for video and audio enclosures.
                        val isImage = medium.equals("image", true) ||
                            type.startsWith("image", true) ||
                            (medium.isBlank() && type.isBlank())
                        if (image == null && !url.isNullOrBlank() && isImage) image = url
                    }
                    "enclosure" -> {
                        val url = token.attrs["url"]
                        val type = token.attrs["type"].orEmpty()
                        if (image == null && !url.isNullOrBlank() && type.startsWith("image", true)) {
                            image = url
                        }
                    }
                    // An <image> *inside* an item is a real article image. The channel-level
                    // <image> (Al Jazeera's site logo) is never reached — this only runs
                    // between an item's start and end tags.
                    "image" -> {
                        val url = readText(scanner, token)
                        if (image == null && !url.isNullOrBlank() && url.startsWith("http")) {
                            image = url.trim()
                        }
                    }
                    else -> Unit
                }
                else -> Unit
            }
        }

        val html = description ?: contentEncoded
        // Al Arabiya ships no media elements at all; its only image is an <img> buried in
        // the description HTML, so that is the last place to look.
        val inlineImage = HtmlText.firstImageSrc(contentEncoded ?: description)

        return FeedItem(
            title = title?.trim()?.takeIf { it.isNotEmpty() },
            link = link?.trim()?.takeIf { it.isNotEmpty() },
            descriptionHtml = html,
            dateRaw = date?.trim(),
            guid = guid?.trim(),
            imageUrl = (image ?: inlineImage)?.trim(),
            categories = categories,
        )
    }

    /**
     * Reads an element's text content, tolerating nested markup — the equivalent of walking
     * to the matching end tag and keeping only text nodes, which survives `<content
     * type="xhtml">` and feeds that leak unescaped HTML into a description.
     */
    private fun readText(scanner: XmlScanner, start: XmlToken.Start): String? {
        if (start.selfClosing) return null
        val tag = start.name
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            val token = runCatching { scanner.next() }.getOrElse { break }
            when (token) {
                is XmlToken.EndDoc -> break
                is XmlToken.Start -> if (token.name.equals(tag, true)) depth++
                is XmlToken.End -> if (token.name.equals(tag, true)) depth--
                is XmlToken.Text -> sb.append(token.text)
                is XmlToken.CData -> sb.append(token.text)
            }
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }
}

// ---------------------------------------------------------------------------
// Minimal pull-style XML scanner (KMP — no XmlPullParser dependency)
// ---------------------------------------------------------------------------

internal sealed class XmlToken {
    class Start(val name: String, val attrs: Map<String, String>, val selfClosing: Boolean) : XmlToken()
    class End(val name: String) : XmlToken()
    class Text(val text: String) : XmlToken()
    class CData(val text: String) : XmlToken()
    object EndDoc : XmlToken()
}

/**
 * A hand-rolled, tolerant, non-validating XML scanner sufficient for RSS 2.0 / RDF / Atom.
 * Produces a stream of [XmlToken]. Malformed constructs are skipped, never thrown — the
 * parser above decides when to stop.
 */
internal class XmlScanner(private val input: String) {

    private var pos = 0

    fun next(): XmlToken {
        while (pos < input.length) {
            val lt = input.indexOf('<', pos)
            if (lt < 0) {
                pos = input.length
                return XmlToken.EndDoc
            }
            if (lt > pos) {
                val text = input.substring(pos, lt)
                pos = lt
                val decoded = decodeEntities(text)
                if (decoded.isNotBlank()) return XmlToken.Text(decoded)
                continue
            }

            // input[lt] == '<'
            if (input.startsWith("<!--", lt)) {
                val end = input.indexOf("-->", lt + 4)
                pos = if (end < 0) input.length else end + 3
                continue
            }
            if (input.startsWith("<![CDATA[", lt)) {
                val end = input.indexOf("]]>", lt + 9)
                if (end < 0) {
                    pos = input.length
                    return XmlToken.EndDoc
                }
                val data = input.substring(lt + 9, end)
                pos = end + 3
                return XmlToken.CData(data)
            }
            if (input.startsWith("<?", lt)) {
                val end = input.indexOf('>', lt)
                pos = if (end < 0) input.length else end + 1
                continue
            }
            if (input.startsWith("<!", lt)) { // DOCTYPE and friends — skip the whole declaration
                val end = input.indexOf('>', lt)
                pos = if (end < 0) input.length else end + 1
                continue
            }
            if (input.startsWith("</", lt)) {
                val end = input.indexOf('>', lt)
                if (end < 0) {
                    pos = input.length
                    return XmlToken.EndDoc
                }
                val name = input.substring(lt + 2, end).trim()
                pos = end + 1
                if (name.isNotEmpty()) return XmlToken.End(name)
                continue
            }

            // Start tag (or bogus markup — skip what cannot be parsed).
            val tagEnd = findTagEnd(lt)
            if (tagEnd < 0) {
                pos = input.length
                return XmlToken.EndDoc
            }
            val raw = input.substring(lt + 1, tagEnd).trim()
            pos = tagEnd + 1
            if (raw.isEmpty()) continue

            val selfClosing = raw.endsWith("/")
            val body = if (selfClosing) raw.dropLast(1).trimEnd() else raw
            val name = body.substringBefore(' ').substringBefore('\t').substringBefore('\n')
            if (name.isEmpty()) continue
            val attrs = parseAttrs(body.substring(name.length))
            return XmlToken.Start(name, attrs, selfClosing)
        }
        return XmlToken.EndDoc
    }

    /** Finds the `>` ending the current start tag, honouring quoted attribute values. */
    private fun findTagEnd(from: Int): Int {
        var i = from + 1
        var quote: Char? = null
        while (i < input.length) {
            val c = input[i]
            when {
                quote != null -> if (c == quote) quote = null
                c == '"' || c == '\'' -> quote = c
                c == '>' -> return i
            }
            i++
        }
        return -1
    }

    private fun parseAttrs(attrSource: String): Map<String, String> {
        if (attrSource.isBlank()) return emptyMap()
        val attrs = mutableMapOf<String, String>()
        var i = 0
        while (i < attrSource.length) {
            // Skip whitespace
            while (i < attrSource.length && attrSource[i].isWhitespace()) i++
            if (i >= attrSource.length) break
            // Attribute name
            val nameStart = i
            while (i < attrSource.length && attrSource[i] != '=' && !attrSource[i].isWhitespace()) i++
            if (i >= attrSource.length) break
            val name = attrSource.substring(nameStart, i)
            while (i < attrSource.length && attrSource[i].isWhitespace()) i++
            if (i >= attrSource.length || attrSource[i] != '=') {
                // Attribute without a value (e.g. `rel=nofollow` never appears in feeds,
                // but boolean-ish attrs do) — record it as empty.
                if (name.isNotEmpty()) attrs[name] = ""
                continue
            }
            i++ // consume '='
            while (i < attrSource.length && attrSource[i].isWhitespace()) i++
            if (i >= attrSource.length) break
            val quote = attrSource[i]
            if (quote == '"' || quote == '\'') {
                val valueStart = i + 1
                val valueEnd = attrSource.indexOf(quote, valueStart)
                if (valueEnd < 0) break
                attrs[name] = decodeEntities(attrSource.substring(valueStart, valueEnd))
                i = valueEnd + 1
            } else {
                val valueStart = i
                while (i < attrSource.length && !attrSource[i].isWhitespace() && attrSource[i] != '>') i++
                attrs[name] = decodeEntities(attrSource.substring(valueStart, i))
            }
        }
        return attrs
    }

    /** Decodes the five XML entities plus numeric character references. */
    private fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val amp = text.indexOf('&', i)
            if (amp < 0) {
                sb.append(text, i, text.length)
                break
            }
            sb.append(text, i, amp)
            val semi = text.indexOf(';', amp)
            if (semi < 0 || semi - amp > 10) {
                sb.append('&')
                i = amp + 1
                continue
            }
            val entity = text.substring(amp + 1, semi)
            when (entity) {
                "amp" -> sb.append('&')
                "lt" -> sb.append('<')
                "gt" -> sb.append('>')
                "quot" -> sb.append('"')
                "apos" -> sb.append('\'')
                else -> {
                    val cp = when {
                        entity.startsWith("#x") || entity.startsWith("#X") ->
                            entity.drop(2).toIntOrNull(16)
                        entity.startsWith("#") -> entity.drop(1).toIntOrNull(10)
                        else -> null
                    }
                    if (cp != null && cp > 0) sb.append(codePointToString(cp)) else sb.append('&').append(entity).append(';')
                }
            }
            i = semi + 1
        }
        return sb.toString()
    }
}
