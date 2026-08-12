package com.radar.news.data.remote

import com.radar.news.util.currentTimeMillis
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Turns whatever a feed calls a date into epoch millis.
 *
 * KMP port of the Android `DateParser`: the hand-rolled RFC-822 path (locale-free by
 * design) is kept as the primary, `java.time` formatters are replaced with
 * `kotlinx-datetime` equivalents, and the named-zone backstop is a second regex pass —
 * the original's lesson (textual month/day names must never resolve through platform
 * locale data) is preserved: every month name comes from the RFC-822 table below.
 */
object DateParser {

    /** RFC-822 month abbreviations. Fixed by the spec — never locale-dependent. */
    private val MONTHS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )

    /**
     * `[Day, ]D Mon YYYY HH:MM[:SS] [zone]` — the shape every one of the six live feeds uses.
     * Day-of-week is ignored (it is redundant), and the zone is optional.
     */
    private val RFC822 = Regex(
        """^\s*(?:[A-Za-z]{3,9},\s*)?(\d{1,2})\s+([A-Za-z]{3})[a-z]*\s+(\d{2,4})\s+""" +
            """(\d{1,2}):(\d{2})(?::(\d{2}))?\s*([A-Za-z]{1,5}|[+-]\d{4}|[+-]\d{2}:\d{2})?\s*$""",
    )

    /** ISO-8601 with or without a zone, and the bare `yyyy-MM-dd HH:mm:ss` shape. */
    private val ISO_WITH_SECONDS = Regex(
        """^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})(?:[.,]\d+)?([+-]\d{2}:?\d{2}|Z)?$""",
    )
    private val ISO_NO_SECONDS = Regex(
        """^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?:[+-]\d{2}:?\d{2}|Z)?$""",
    )

    /** Named zones that appear in practice. Anything unrecognised is treated as UTC. */
    private val NAMED_ZONE_OFFSETS = mapOf(
        "gmt" to 0, "ut" to 0, "utc" to 0, "z" to 0,
        "est" to -5, "edt" to -4, "cst" to -6, "cdt" to -5,
        "mst" to -7, "mdt" to -6, "pst" to -8, "pdt" to -7,
    )

    /**
     * How far ahead of the device clock a feed may legitimately be. See the Android
     * project's DateParserSkewTest for the full reasoning.
     */
    const val MAX_CLOCK_SKEW_MILLIS = 60L * 60L * 1000L

    /**
     * Parses [raw] and returns epoch millis, or `null` if it is not a date this app understands
     * or is dated further ahead than [MAX_CLOCK_SKEW_MILLIS]. (Same contract as the original.)
     */
    fun parse(raw: String?, now: Long = currentTimeMillis()): Long? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null

        val parsed = parseIso(s)
            ?: parseRfc822(s)
            ?: return null

        return parsed.takeIf { it <= now + MAX_CLOCK_SKEW_MILLIS }
    }

    /** ISO-8601 — unambiguous, locale-free, and what the RDF feed variants use. */
    private fun parseIso(s: String): Long? {
        ISO_WITH_SECONDS.find(s)?.let { m ->
            return buildEpoch(
                m.groupValues[1].toIntOrNull() ?: return null,
                m.groupValues[2].toIntOrNull() ?: return null,
                m.groupValues[3].toIntOrNull() ?: return null,
                m.groupValues[4].toIntOrNull() ?: return null,
                m.groupValues[5].toIntOrNull() ?: return null,
                m.groupValues[6].toIntOrNull() ?: return null,
                offsetSeconds(m.groupValues[7].takeIf { it.isNotEmpty() }),
            )
        }
        ISO_NO_SECONDS.find(s)?.let { m ->
            return buildEpoch(
                m.groupValues[1].toIntOrNull() ?: return null,
                m.groupValues[2].toIntOrNull() ?: return null,
                m.groupValues[3].toIntOrNull() ?: return null,
                m.groupValues[4].toIntOrNull() ?: return null,
                m.groupValues[5].toIntOrNull() ?: return null,
                0,
                offsetSeconds(null),
            )
        }
        return null
    }

    private fun parseRfc822(s: String): Long? {
        val m = RFC822.find(s) ?: return null
        val (dayText, monthText, yearText, hourText, minuteText) = m.destructured
        val secondText = m.groupValues[6]
        val zoneText = m.groupValues[7]

        val month = MONTHS[monthText.lowercase()] ?: return null
        val day = dayText.toIntOrNull() ?: return null
        val hour = hourText.toIntOrNull() ?: return null
        val minute = minuteText.toIntOrNull() ?: return null
        val second = if (secondText.isEmpty()) 0 else secondText.toIntOrNull() ?: return null

        // RFC-822 allowed two-digit years; a handful of feeds still emit them.
        val year = yearText.toIntOrNull()?.let { if (it < 100) it + 2000 else it } ?: return null

        val offset = offsetSeconds(zoneText.takeIf { it.isNotEmpty() })

        return buildEpoch(year, month, day, hour, minute, second, offset)
    }

    /** Builds epoch millis from a local wall-clock time and a zone offset in seconds. */
    private fun buildEpoch(
        year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int,
        offsetSeconds: Int,
    ): Long? = runCatching {
        val local = LocalDateTime(year, month, day, hour, minute, second, 0)
        // Treat the wall-clock as UTC first, then shift by the real offset:
        // epoch = local-as-UTC − offset (east-of-UTC zones are positive).
        local.toInstant(TimeZone.UTC).toEpochMilliseconds() - offsetSeconds * 1000L
    }.getOrNull()

    /** `+0300`, `+03:00`, `GMT`, `EST` … → offset in seconds; unknown/absent → UTC. */
    private fun offsetSeconds(zone: String?): Int {
        if (zone.isNullOrEmpty()) return 0
        if (zone.startsWith('+') || zone.startsWith('-')) {
            val sign = if (zone.startsWith('-')) -1 else 1
            val digits = zone.drop(1).replace(":", "")
            val h = digits.take(2).toIntOrNull() ?: return 0
            val m = digits.drop(2).take(2).toIntOrNull() ?: 0
            return sign * (h * 3600 + m * 60)
        }
        val named = NAMED_ZONE_OFFSETS[zone.lowercase()] ?: return 0
        return named * 3600
    }
}
