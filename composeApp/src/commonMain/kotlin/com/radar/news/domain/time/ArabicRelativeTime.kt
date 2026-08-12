package com.radar.news.domain.time

import com.radar.news.util.currentTimeMillis
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Grammatically correct Arabic relative timestamps.
 *
 * Arabic counts in three number categories, not two, which is why this cannot be a simple
 * `"قبل $n $unit"` template:
 *
 *  - **1** — the noun alone, no numeral: `قبل دقيقة`, never `قبل 1 دقيقة`
 *  - **2** — the dual form, again with no numeral: `قبل دقيقتين`
 *  - **3–10** — the numeral with a *plural* noun: `قبل 5 دقائق`
 *  - **11+** — the numeral with a *singular* noun: `قبل 25 دقيقة`
 *
 * KMP port: `java.time` replaced with `kotlinx-datetime`; the branch logic is unchanged.
 */
object ArabicRelativeTime {

    private val MONTHS = arrayOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
    )

    fun format(
        publishedAt: Long,
        now: Long = currentTimeMillis(),
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): String {
        // A feed clock running ahead would otherwise produce a negative age and fall through
        // every branch; treat anything in the future as "just now".
        val elapsed = (now - publishedAt).coerceAtLeast(0L)

        val seconds = elapsed / 1000
        if (seconds < 60) return "الآن"

        val minutes = elapsed / 60_000
        if (minutes < 60) return counted(minutes.toInt(), "دقيقة", "دقيقتين", "دقائق")

        val hours = elapsed / 3_600_000
        if (hours < 24) return counted(hours.toInt(), "ساعة", "ساعتين", "ساعات")

        val days = elapsed / 86_400_000
        if (days == 1L) return "أمس"
        if (days == 2L) return "قبل يومين"
        if (days <= 10L) return "قبل $days أيام"

        return absoluteDate(publishedAt, zone)
    }

    /**
     * Applies the singular / dual / plural / singular-again pattern.
     *
     * @param singular the bare noun, used for 1 and for 11+
     * @param dual the dual form, used alone for exactly 2
     * @param plural the broken plural, used for 3–10
     */
    private fun counted(value: Int, singular: String, dual: String, plural: String): String = when {
        value == 1 -> "قبل $singular"
        value == 2 -> "قبل $dual"
        value <= 10 -> "قبل $value $plural"
        else -> "قبل $value $singular"
    }

    /** `12 مارس` — day and month, no year, matching the spec's example. */
    fun absoluteDate(epochMillis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
        val date = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone).date
        return "${date.dayOfMonth} ${MONTHS[date.monthNumber - 1]}"
    }
}
