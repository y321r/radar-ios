package com.radar.news.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Reduces a URL to a stable identity so the same article always hashes to the same key.
 *
 * This is load-bearing for dedupe. Every source appends its own tracking parameter —
 * Al Jazeera `?traffic_source=rss`, BBC `?at_medium=RSS&at_campaign=rss`, DW `?maca=…` —
 * and without stripping them an article would not even match *itself* between two syncs
 * if the outlet rotated a campaign id.
 */
object UrlCanonicalizer {

    /** Dropped wholesale. Everything else is kept — some sites need `?id=` to resolve. */
    private val TRACKING_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id",
        "at_medium", "at_campaign", "at_custom1", "at_custom2", "at_custom3", "at_custom4",
        "traffic_source", "maca", "ref", "referrer", "source", "fbclid", "gclid", "igshid",
        "mc_cid", "mc_eid", "ncid", "smid", "cmpid", "ito", "oc", "xtor", "ceid", "_ga",
    )

    fun canonicalize(rawUrl: String): String {
        val url = rawUrl.trim().toHttpUrlOrNull() ?: return rawUrl.trim().lowercase()

        val builder: HttpUrl.Builder = url.newBuilder()
            // Feeds mix http and https for the same article; force one.
            .scheme("https")
            .host(url.host.removePrefix("www.").lowercase())
            .fragment(null)

        // Rebuild the query keeping only non-tracking params, in sorted order so that
        // ?a=1&b=2 and ?b=2&a=1 canonicalize identically.
        val kept = (0 until url.querySize)
            .map { url.queryParameterName(it) to url.queryParameterValue(it) }
            .filter { (name, _) -> name.lowercase() !in TRACKING_PARAMS }
            .sortedBy { it.first }

        builder.query(null)
        if (kept.isNotEmpty()) {
            kept.forEach { (name, value) -> builder.addQueryParameter(name, value) }
        }

        var result = builder.build().toString()
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
        for (b in input.toByteArray(Charsets.UTF_8)) {
            hash = hash xor (b.toLong() and 0xFF)
            hash *= 0x100000001b3L // FNV prime
        }
        return java.lang.Long.toHexString(hash)
    }
}
