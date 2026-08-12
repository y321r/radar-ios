package com.radar.news.data.remote

/**
 * Reduces a URL to a stable identity so the same article always hashes to the same key.
 *
 * This is load-bearing for dedupe. Every source appends its own tracking parameter —
 * Al Jazeera `?traffic_source=rss`, BBC `?at_medium=RSS&at_campaign=rss`, DW `?maca=…` —
 * and without stripping them an article would not even match *itself* between two syncs
 * if the outlet rotated a campaign id.
 *
 * KMP shell: the Android original used OkHttp's `HttpUrl` (JVM-only on iOS); the port parses
 * the URL with a bounded regex instead — same observable behaviour for the feed URLs in play.
 */
object UrlCanonicalizer {

    /** Dropped wholesale. Everything else is kept — some sites need `?id=` to resolve. */
    private val TRACKING_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id",
        "at_medium", "at_campaign", "at_custom1", "at_custom2", "at_custom3", "at_custom4",
        "traffic_source", "maca", "ref", "referrer", "source", "fbclid", "gclid", "igshid",
        "mc_cid", "mc_eid", "ncid", "smid", "cmpid", "ito", "oc", "xtor", "ceid", "_ga",
    )

    // scheme://host[:port]/path?query#fragment
    private val URL_REGEX = Regex(
        """^([a-zA-Z][a-zA-Z0-9+.-]*)://([^/:?#]+)(?::(\d+))?([^?#]*)(?:\?([^#]*))?(?:#(.*))?$""",
    )

    private data class Parsed(
        val scheme: String,
        val host: String,
        val port: String?,
        val path: String,
        val query: String?,
    )

    fun canonicalize(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        val parsed = URL_REGEX.matchEntire(trimmed)?.let { m ->
            Parsed(
                scheme = m.groupValues[1],
                host = m.groupValues[2],
                port = m.groupValues[3].takeIf { it.isNotEmpty() },
                path = m.groupValues[4],
                query = m.groupValues[5].takeIf { it.isNotEmpty() },
            )
        } ?: return trimmed.lowercase()

        // Feeds mix http and https for the same article; force one. Drop www, lowercase the host.
        val scheme = "https"
        val host = parsed.host.removePrefix("www.").lowercase()

        // Rebuild the query keeping only non-tracking params, in sorted order so that
        // ?a=1&b=2 and ?b=2&a=1 canonicalize identically.
        val kept = parsed.query
            ?.split('&')
            ?.mapNotNull { pair ->
                val eq = pair.indexOf('=')
                val name = if (eq >= 0) pair.substring(0, eq) else pair
                val value = if (eq >= 0) pair.substring(eq + 1) else ""
                if (name.lowercase() in TRACKING_PARAMS) null else name to value
            }
            ?.sortedBy { it.first }
            .orEmpty()

        val queryPart = if (kept.isEmpty()) "" else "?" + kept.joinToString("&") { (n, v) -> "$n=$v" }
        val portPart = parsed.port?.let { ":$it" } ?: ""

        var result = "$scheme://$host$portPart${parsed.path}$queryPart"
        // Trailing slash is not meaningful; drop it so /a/b and /a/b/ agree.
        if (result.endsWith("/") && !result.endsWith("://")) result = result.dropLast(1)
        return result
    }

    /** Stable id for an article row, derived from its canonical URL. */
    fun idFor(rawUrl: String): String = stableHash(canonicalize(rawUrl))

    /**
     * FNV-1a 64-bit, rendered as hex.
     *
     * [String.hashCode] is only 32 bits — with tens of thousands of articles over the app's
     * life a birthday collision is a real possibility, and a collision here silently drops
     * a story. 64 bits makes that vanishingly unlikely without pulling in a crypto hash.
     */
    fun stableHash(input: String): String {
        var hash = -0x340d631b7bdddcdbL // FNV offset basis, 14695981039346656037
        for (b in input.encodeToByteArray()) {
            hash = hash xor (b.toLong() and 0xFF)
            hash *= 0x100000001b3L // FNV prime
        }
        return hash.toString(16)
    }
}
