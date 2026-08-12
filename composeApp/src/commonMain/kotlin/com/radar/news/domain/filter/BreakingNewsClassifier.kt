package com.radar.news.domain.filter

import com.radar.news.util.currentTimeMillis
import com.radar.news.data.model.RawArticle
import com.radar.news.domain.dedupe.ArabicNormalizer

/**
 * Why an item scored what it did. Carried alongside the numbers so a surprising decision can
 * be explained without re-running the classifier under a debugger — and so tests can assert
 * on the *reason*, not just the total.
 */
data class Classification(
    val breakingScore: Int,
    val topicScore: Int,
    val signals: List<String> = emptyList(),
    /**
     * Something actually happened: a verb of occurrence, or a newsroom's own breaking marker.
     *
     * Defaults true so that constructing a [Classification] by hand — tests, diagnostics — keeps
     * the old two-threshold meaning unless the event gate is deliberately exercised.
     */
    val hasEventSignal: Boolean = true,
    private val weights: ScoringWeights = FilterConfig.WEIGHTS,
) {
    val accepted: Boolean
        get() = breakingScore >= weights.minBreakingScore &&
            topicScore >= weights.minTopicScore &&
            (hasEventSignal || !weights.requireEventSignal)
}

/**
 * Decides whether an item is breaking political or economic news.
 *
 * Deliberately a transparent additive score rather than anything learned: the thresholds are
 * in [FilterConfig], the vocabulary is in `assets/keywords.json`, and every contribution is
 * recorded in [Classification.signals]. Both can be tuned without touching this class.
 *
 * The rule is `breakingScore >= 5 && topicScore >= 2`. Everything else is discarded outright —
 * never stored, so the database only ever holds items the user is meant to see.
 */
class BreakingNewsClassifier constructor(
    private val keywordStore: KeywordStore,
) {

    suspend fun classify(
        article: RawArticle,
        now: Long = currentTimeMillis(),
        weights: ScoringWeights = FilterConfig.WEIGHTS,
    ): Classification =
        classify(
            title = article.title,
            summary = article.summary,
            url = article.url,
            categories = article.categories,
            publishedAt = article.publishedAt,
            now = now,
            weights = weights,
        )

    suspend fun classify(
        title: String,
        summary: String?,
        url: String,
        categories: List<String>,
        publishedAt: Long,
        now: Long = currentTimeMillis(),
        weights: ScoringWeights = FilterConfig.WEIGHTS,
    ): Classification {
        val vocabulary = keywordStore.vocabulary()
        val signals = mutableListOf<String>()

        // Text surface: normalised and stemmed, space-padded so phrase lookups respect
        // word boundaries rather than matching inside a longer word.
        val text = ArabicNormalizer.stemmedHaystack(
            listOfNotNull(title, summary, categories.joinToString(" ").takeIf { it.isNotBlank() })
                .joinToString(" "),
        )
        val haystack = " $text "

        // URL and category surface: matched as raw lowercase substrings, since these are
        // path fragments like `/sport/`, not Arabic prose.
        val pathSurface = buildString {
            append(url.lowercase())
            append(' ')
            append(categories.joinToString(" ") { it.lowercase() })
        }

        // ------------------------------------------------------------- breaking ---

        var breaking = 0

        // Idioms that merely contain a breaking marker are removed first. `حتى الآن` means
        // "so far" and is ordinary prose; leaving it in handed the full +5 urgency bonus to
        // any story whose summary used the phrase, regardless of age or topic.
        val breakingSurface = vocabulary.breakingExclusions
            .fold(haystack) { text, idiom -> text.replace(" $idiom ", " ") }

        val explicitlyMarked = vocabulary.breaking.any { breakingSurface.contains(" $it ") }
        if (explicitlyMarked) {
            breaking += weights.breakingKeyword
            signals += "breaking-keyword +${weights.breakingKeyword}"
        }

        val age = now - publishedAt
        // A future timestamp is a feed bug, not freshness — treat it as age zero rather than
        // letting a clock-skewed item claim the top of the timeline forever.
        val effectiveAge = if (age < 0) 0 else age
        when {
            effectiveAge <= FilterConfig.FRESH_30M_MILLIS -> {
                breaking += weights.fresh30m
                signals += "fresh-30m +${weights.fresh30m}"
            }
            effectiveAge <= FilterConfig.FRESH_2H_MILLIS -> {
                breaking += weights.fresh2h
                signals += "fresh-2h +${weights.fresh2h}"
            }
            // Third band: an outlet whose feed lags by hours can still contribute. Without it,
            // Al Jazeera — whose RSS runs ~3.5h behind its own sitemap — never appears at all.
            effectiveAge <= FilterConfig.FRESH_6H_MILLIS -> {
                breaking += weights.fresh6h
                signals += "fresh-6h +${weights.fresh6h}"
            }
        }

        val markedByPath = vocabulary.breakingPaths.any { pathSurface.contains(it) } ||
            vocabulary.breakingPaths.any { breakingSurface.contains(" $it ") }
        if (markedByPath) {
            breaking += weights.breakingPath
            signals += "breaking-path +${weights.breakingPath}"
        }

        // ------------------------------------------------------- event requirement ---
        // Whether anything actually *happened*. Freshness alone cannot answer that: fresh6h
        // is worth exactly the breaking threshold, so before this every item under six hours
        // old cleared the gate with no other signal at all, and a human-interest feature
        // scored the same as a bombardment. See ScoringWeights.requireEventSignal.
        //
        // An outlet's own `عاجل` marker or a /breaking/ path is the newsroom saying so
        // outright, and always wins over inference from grammar.
        val eventVerb = vocabulary.eventVerbs.firstOrNull { haystack.contains(" $it ") }
        val hasEventSignal = explicitlyMarked || markedByPath || eventVerb != null
        signals += when {
            explicitlyMarked || markedByPath -> "event-signal(explicit breaking marker)"
            eventVerb != null -> "event-signal(verb '$eventVerb')"
            else -> "NO event signal — nothing happened, this is description not news"
        }

        // ---------------------------------------------------------------- topic ---

        val politicsHits = vocabulary.politics.count { haystack.contains(" $it ") }
        val economyHits = vocabulary.economy.count { haystack.contains(" $it ") }
        val topicHits = politicsHits + economyHits

        var topic = 0
        if (topicHits > 0) {
            val keywordScore = (
                weights.topicFirst + (topicHits - 1) * weights.topicAdditional
                ).coerceAtMost(weights.maxTopicKeyword)
            topic += keywordScore
            signals += "topic-keywords(politics=$politicsHits,economy=$economyHits) +$keywordScore"
        }

        if (vocabulary.topicPaths.any { pathSurface.contains(it) }) {
            topic += weights.topicPath
            signals += "topic-path +${weights.topicPath}"
        }

        // ------------------------------------------------------------- negative ---

        val negativeHits = vocabulary.negative.count { haystack.contains(" $it ") }
        if (negativeHits > 0) {
            val penalty = negativeHits * weights.negativeKeyword
            topic += penalty
            breaking += weights.negativeBreaking
            signals += "negative-keywords($negativeHits) $penalty"
        }

        val negativePathHits = vocabulary.negativePaths.count { pathSurface.contains(it) }
        if (negativePathHits > 0) {
            val penalty = negativePathHits * weights.negativePath
            topic += penalty
            breaking += weights.negativeBreaking
            signals += "negative-path($negativePathHits) $penalty"
        }

        // ---------------------------------------------------------- structural ---
        // Format signals, not vocabulary. An explainer and a news report can share every
        // keyword; what separates them is that one asks a question and the other states
        // a fact. No amount of extra keywords catches «هل يواجه ترامب مأزقا…؟».

        if (title.trimEnd().endsWith('؟') || title.trimEnd().endsWith('?')) {
            topic += weights.penaltyQuestionHeadline
            signals += "question-headline ${weights.penaltyQuestionHeadline}"
        }

        // Only the opening word counts — «لماذا» first marks an explainer, mid-sentence it
        // is ordinary reporting.
        val normalizedTitle = " ${ArabicNormalizer.stemmedHaystack(title)} "
        val openingMarker = vocabulary.analysisPrefixes.firstOrNull { normalizedTitle.startsWith(" $it ") }
        if (openingMarker != null) {
            topic += weights.penaltyAnalysisPrefix
            signals += "analysis-prefix('$openingMarker') ${weights.penaltyAnalysisPrefix}"
        }

        return Classification(
            breakingScore = breaking,
            topicScore = topic,
            signals = signals,
            hasEventSignal = hasEventSignal,
            weights = weights,
        )
    }
}
