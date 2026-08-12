package com.radar.news.domain.filter

/**
 * Every weight and threshold the classifier applies, as data rather than constants.
 *
 * Extracted so calibration can sweep candidate settings over a real corpus in one run
 * (`CalibrationSweep`) instead of editing constants and rebuilding. The shipped values live in
 * [FilterConfig.WEIGHTS]; nothing else constructs one except tests.
 */
data class ScoringWeights(
    // --- acceptance ---
    val minBreakingScore: Int,
    val minTopicScore: Int,

    // --- breaking signals ---
    val breakingKeyword: Int,
    val fresh30m: Int,
    val fresh2h: Int,
    /**
     * Third freshness band. Wire feeds do not publish minute-by-minute — Al Jazeera's RSS runs
     * hours behind its own sitemap — so without a band this wide, whole outlets can never
     * contribute at all.
     */
    val fresh6h: Int,
    val breakingPath: Int,

    // --- topic signals ---
    val topicFirst: Int,
    val topicAdditional: Int,
    val maxTopicKeyword: Int,
    val topicPath: Int,

    // --- negative signals ---
    val negativeKeyword: Int,
    val negativePath: Int,
    val negativeBreaking: Int,

    /**
     * Headline is phrased as a question (`…؟`).
     *
     * Structural rather than lexical: news reports a fact, analysis poses a question. No
     * keyword list catches «هل يواجه ترامب مأزقا…؟» but the question mark always does.
     */
    val penaltyQuestionHeadline: Int = -6,

    /**
     * Headline *opens* with an analysis marker — `تحليل`, `تقرير`, `كيف`, `لماذا`, `هكذا`,
     * `ماذا لو`. Only in first position: «لماذا» leading a headline signals an explainer,
     * while the same word mid-sentence is ordinary reporting.
     */
    val penaltyAnalysisPrefix: Int = -6,

    /**
     * Require a **verb of occurrence, announcement or decision** before an item may be
     * called breaking.
     *
     * This is a gate, not a weight, because the breaking side had no real signal to weigh.
     * Measured over the 175-item corpus, *every* accepted item derived its entire breaking
     * score from freshness alone: `fresh6h` is +4 and the bar is 4, so anything under six
     * hours old cleared it with nothing else. A widows feature and a bombardment scored
     * identically (b=4, t=5). Tightening the threshold cannot fix that — raising it to 5
     * leaves 5 of 20 items and guts the feed — because there was nothing behind the gate to
     * tighten. Adding a signal is the only lever that distinguishes them.
     *
     * News is something that *happened*; a feature is something that *is*. «إسرائيل تستهدف»
     * happened, «خيام غزة تروي وجع الأرامل» does not. See DECISIONS.md D28.
     *
     * An explicit `عاجل` marker or a `/breaking/` path exempts an item: that is a newsroom
     * stating outright that this is breaking, which outranks any inference from grammar.
     */
    val requireEventSignal: Boolean = true,
) {
    /** Human-readable one-liner, for sweep output. */
    val label: String
        get() = "brk>=$minBreakingScore top>=$minTopicScore | 30m+$fresh30m 2h+$fresh2h 6h+$fresh6h | " +
            "kw+$breakingKeyword path+$breakingPath | topic first+$topicFirst path+$topicPath"
}
