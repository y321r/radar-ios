package com.radar.news.domain.filter

import com.radar.news.domain.dedupe.ArabicNormalizer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class KeywordsFile(
    val version: Int = 1,
    val breaking: List<String> = emptyList(),
    @SerialName("breakingExclusions") val breakingExclusions: List<String> = emptyList(),
    @SerialName("breakingPaths") val breakingPaths: List<String> = emptyList(),
    val politics: List<String> = emptyList(),
    val economy: List<String> = emptyList(),
    @SerialName("topicPaths") val topicPaths: List<String> = emptyList(),
    val negative: List<String> = emptyList(),
    @SerialName("negativePaths") val negativePaths: List<String> = emptyList(),
    @SerialName("analysisPrefixes") val analysisPrefixes: List<String> = emptyList(),
    @SerialName("eventVerbs") val eventVerbs: List<String> = emptyList(),
    @SerialName("religiousEvents") val religiousEvents: List<String> = emptyList(),
)

/**
 * The keyword vocabulary from `keywords.json`, pre-normalised once at load.
 *
 * KMP note: the Android original loaded `keywords.json` from `assets` via `Context`. Here the
 * raw JSON text is injected by the platform layer (Android `assets`, iOS bundle resource).
 */
class KeywordStore(
    private val keywordsJson: String,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private var cached: Vocabulary? = null

    /** Seed phrases in the same normalised, stemmed form headlines are reduced to. */
    data class Vocabulary(
        val breaking: List<String>,
        /** Idioms containing a breaking marker; stripped before breaking keywords are matched. */
        val breakingExclusions: List<String>,
        val breakingPaths: List<String>,
        val politics: List<String>,
        val economy: List<String>,
        val topicPaths: List<String>,
        val negative: List<String>,
        val negativePaths: List<String>,
        /** Matched only against the *start* of a headline. */
        val analysisPrefixes: List<String>,
        /**
         * Verbs of occurrence, announcement or decision. The breaking side's only real signal —
         * see [ScoringWeights.requireEventSignal].
         */
        val eventVerbs: List<String>,
        /** Folded into the negative vocabulary; kept separate so it can be tuned alone. */
        val religiousEvents: List<String>,
    )

    fun vocabulary(): Vocabulary = cached ?: run {
        val file = runCatching {
            json.decodeFromString<KeywordsFile>(keywordsJson)
        }.getOrElse { KeywordsFile() }

        val vocabulary = Vocabulary(
            breaking = file.breaking.normalizePhrases(),
            // Longest first, so `حتى الآن لم` is consumed before the shorter `حتى الآن`
            // can strip only part of it and leave a fragment behind.
            breakingExclusions = file.breakingExclusions.normalizePhrases()
                .sortedByDescending { it.length },
            // Paths are URL fragments, not Arabic prose — lowercase only, never stemmed.
            breakingPaths = file.breakingPaths.map { it.lowercase() },
            politics = file.politics.normalizePhrases(),
            economy = file.economy.normalizePhrases(),
            topicPaths = file.topicPaths.map { it.lowercase() },
            // Religious observances behave exactly like other off-topic vocabulary, so they
            // join the same list; they are declared separately only for tunability.
            negative = (file.negative + file.religiousEvents).normalizePhrases(),
            negativePaths = file.negativePaths.map { it.lowercase() },
            // Longest first, so "ماذا لو" is tested before the shorter "ماذا".
            analysisPrefixes = file.analysisPrefixes.normalizePhrases()
                .sortedByDescending { it.length },
            eventVerbs = file.eventVerbs.normalizePhrases(),
            religiousEvents = file.religiousEvents.normalizePhrases(),
        )
        cached = vocabulary
        vocabulary
    }

    private fun List<String>.normalizePhrases(): List<String> =
        map { ArabicNormalizer.stemmedHaystack(it) }
            .filter { it.isNotBlank() }
            .distinct()
}
