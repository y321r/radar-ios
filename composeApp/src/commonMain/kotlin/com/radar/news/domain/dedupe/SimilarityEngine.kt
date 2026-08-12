package com.radar.news.domain.dedupe

import kotlin.math.sqrt

/**
 * The two similarity measures the dedupe rules are written against.
 *
 * They fail in opposite directions, which is why both are used rather than either alone:
 *
 *  - **Jaccard over tokens** is strong on reworded headlines that share vocabulary, but blind
 *    to morphology — `الحكومة` and `حكومية` are simply different tokens and score zero overlap.
 *  - **Character 4-gram cosine** is strong on exactly that morphological variation, but is
 *    fooled by two unrelated stories that happen to share a long proper noun.
 *
 * A story is a duplicate if *either* clears its threshold, so each covers the other's blind spot.
 */
object SimilarityEngine {

    /** |A ∩ B| / |A ∪ B| over distinct tokens. Returns 0 when either side is empty. */
    fun jaccard(a: Collection<String>, b: Collection<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val setA = a.toSet()
        val setB = b.toSet()
        val intersection = setA.count { it in setB }
        val union = setA.size + setB.size - intersection
        return if (union == 0) 0.0 else intersection.toDouble() / union
    }

    /**
     * Cosine similarity over character n-gram frequency vectors.
     *
     * Frequencies, not just presence: a word repeated in one headline and absent from the
     * other should pull the score down, which a set-based comparison would miss.
     */
    fun charNgramCosine(a: String, b: String, n: Int = DEFAULT_NGRAM): Double {
        if (a.isBlank() || b.isBlank()) return 0.0
        return cosine(NgramVector.of(a, n), NgramVector.of(b, n))
    }

    /**
     * A headline's n-gram profile with its magnitude precomputed.
     *
     * Dedupe compares each new item against everything in the 12-hour window, so a headline's
     * vector is needed hundreds of times per sync. Building it once per article instead of
     * once per comparison turns an O(items × window) cost into O(items).
     */
    class NgramVector(val counts: Map<String, Int>, val magnitude: Double) {
        companion object {
            fun of(text: String, n: Int = DEFAULT_NGRAM): NgramVector {
                val counts = ngramCounts(text, n)
                val magnitude = sqrt(counts.values.sumOf { it.toDouble() * it.toDouble() })
                return NgramVector(counts, magnitude)
            }
        }
    }

    fun cosine(a: NgramVector, b: NgramVector): Double {
        if (a.counts.isEmpty() || b.counts.isEmpty()) return 0.0
        if (a.magnitude == 0.0 || b.magnitude == 0.0) return 0.0

        // Iterate the smaller map; the larger is only probed.
        val (small, large) = if (a.counts.size <= b.counts.size) a.counts to b.counts else b.counts to a.counts
        var dot = 0.0
        for ((gram, count) in small) {
            val other = large[gram] ?: continue
            dot += count.toDouble() * other.toDouble()
        }
        return if (dot == 0.0) 0.0 else dot / (a.magnitude * b.magnitude)
    }

    internal fun ngramCounts(text: String, n: Int): Map<String, Int> {
        // Pad so that the first and last characters carry the same weight as interior ones.
        val padded = " $text "
        if (padded.length < n) return mapOf(padded to 1)
        val counts = HashMap<String, Int>((padded.length - n + 1).coerceAtLeast(1))
        for (i in 0..padded.length - n) {
            val gram = padded.substring(i, i + n)
            counts[gram] = (counts[gram] ?: 0) + 1
        }
        return counts
    }

    const val DEFAULT_NGRAM = 4
}
