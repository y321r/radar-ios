package com.radar.news.domain.filter

/**
 * Every threshold and weight in the filtering and dedupe pipeline, in one place.
 *
 * These numbers decide what the user does and does not see, so they live together rather
 * than scattered across the classes that apply them — tuning the feed should never mean
 * hunting through five files.
 *
 * The scoring half lives in [WEIGHTS] so that `CalibrationSweep` can evaluate candidate
 * settings against a real corpus without editing constants. The values below are the ones
 * that sweep selected — see DECISIONS.md D14 for the evidence.
 */
object FilterConfig {

    /**
     * Shipped scoring configuration.
     *
     * **Rebalanced so topic relevance carries the decision, not freshness.** The earlier
     * arrangement leaned on a 30-minute freshness bonus, which cannot work for RSS: wire feeds
     * do not update minute-by-minute, and Al Jazeera's runs hours behind its own sitemap. The
     * result was that whole outlets could never contribute. Freshness now spans three bands
     * and merely establishes recency; what an item is *about* — topic keywords plus the section
     * a newsroom filed it under — is what decides whether it belongs in a breaking feed.
     */
    val WEIGHTS = ScoringWeights(
        minBreakingScore = 4,
        minTopicScore = 4,

        breakingKeyword = 5,
        fresh30m = 5,
        fresh2h = 5,
        fresh6h = 4,
        breakingPath = 3,

        topicFirst = 2,
        topicAdditional = 1,
        maxTopicKeyword = 6,
        topicPath = 3,

        negativeKeyword = -4,
        negativePath = -6,
        negativeBreaking = -3,
    )

    // --- convenience aliases, so call sites and tests read naturally ---------------

    val MIN_BREAKING_SCORE get() = WEIGHTS.minBreakingScore
    val MIN_TOPIC_SCORE get() = WEIGHTS.minTopicScore
    val SCORE_BREAKING_KEYWORD get() = WEIGHTS.breakingKeyword
    val SCORE_FRESH_30M get() = WEIGHTS.fresh30m
    val SCORE_FRESH_2H get() = WEIGHTS.fresh2h
    val SCORE_FRESH_6H get() = WEIGHTS.fresh6h
    val SCORE_BREAKING_PATH get() = WEIGHTS.breakingPath
    val SCORE_TOPIC_FIRST get() = WEIGHTS.topicFirst
    val SCORE_TOPIC_ADDITIONAL get() = WEIGHTS.topicAdditional
    val MAX_TOPIC_KEYWORD_SCORE get() = WEIGHTS.maxTopicKeyword
    val SCORE_TOPIC_PATH get() = WEIGHTS.topicPath
    val PENALTY_NEGATIVE_KEYWORD get() = WEIGHTS.negativeKeyword
    val PENALTY_NEGATIVE_PATH get() = WEIGHTS.negativePath
    val PENALTY_NEGATIVE_BREAKING get() = WEIGHTS.negativeBreaking

    // --- freshness bands -----------------------------------------------------------

    val FRESH_30M_MILLIS = 30L * 60L * 1000L
    val FRESH_2H_MILLIS = 2L * 60L * 60L * 1000L
    val FRESH_6H_MILLIS = 6L * 60L * 60L * 1000L

    // ------------------------------------------------------------------ dedupe ---

    /** Token Jaccard at or above this alone means duplicate. */
    const val JACCARD_THRESHOLD = 0.55
    /** Character 4-gram cosine at or above this alone means duplicate. */
    const val COSINE_THRESHOLD = 0.75

    /**
     * Corroboration rule: neither measure is confident alone, but both agree.
     *
     * Measured on realistic headline pairs (`SimilarityDiagnostic`), the 0.55/0.75 thresholds
     * above rejected every unrelated pair but also missed genuine duplicates — a France 24
     * rewrite of a BBC story scored 0.43/0.47, and a Lebanon story where one outlet wrote
     * `5` and the other `خمسة` scored 0.50/0.65. Both are exactly the "same story from
     * Al Jazeera + BBC + France 24 appears once" case the app is judged on.
     *
     * Lowering either single threshold to catch them would have moved it toward the noise.
     * Requiring *both* measures to be moderately high instead keeps a wide margin: across the
     * same sample the worst unrelated pair reached only 0.18 Jaccard / 0.26 cosine, so nothing
     * unrelated comes close to clearing both at once. The two measures fail on different
     * things — Jaccard is blind to morphology, cosine is fooled by shared proper nouns — which
     * is what makes their agreement meaningful rather than merely a lower bar.
     */
    const val COMBINED_JACCARD_THRESHOLD = 0.40
    const val COMBINED_COSINE_THRESHOLD = 0.45

    /** Only items published within this window are ever compared. */
    val DEDUPE_WINDOW_MILLIS = 12L * 60L * 60L * 1000L

    // ------------------------------------------------------------------- other ---

    /** Rows older than this are pruned after every sync. */
    val RETENTION_MILLIS = 48L * 60L * 60L * 1000L
}
