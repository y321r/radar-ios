package com.radar.news.domain.dedupe


/**
 * Reduces Arabic text to a comparable form.
 *
 * Two different consumers need two different depths, so there are two entry points:
 *
 *  - [stemmedHaystack] — for keyword matching. Keeps stopwords, because seed phrases like
 *    `وقف إطلاق النار` would fall apart without them.
 *  - [forDedupe] — for similarity. Drops stopwords and outlet prefixes, because they are
 *    pure noise when comparing two newsrooms' wording of the same event.
 *
 * The shared core handles what makes Arabic hard to compare: the same word is routinely
 * written with or without hamza (`أعلن` / `اعلن`), with ta marbuta or ha (`الحكومة` / `الحكومه`),
 * with or without diacritics, and with either digit set.
 */
object ArabicNormalizer {

    // U+064B..U+0652 tashkeel, U+0653..U+0655 hamza marks, U+0670 superscript alef.
    private val DIACRITICS = Regex("[ً-ٰٕ]")
    private const val TATWEEL = 'ـ'

    private val URLS = Regex("""https?://\S+|www\.\S+""")

    /**
     * Anything that is not an Arabic letter, a Latin letter, a digit or a space.
     *
     * This single class covers punctuation *and* emoji: Java matches character classes by
     * code point, so a supplementary-plane pictograph is one non-matching code point and is
     * replaced wholesale. No separate emoji pass is needed.
     */
    private val PUNCTUATION = Regex("[^\\p{IsArabic}a-zA-Z0-9 ]")
    private val WHITESPACE = Regex("\\s+")

    /**
     * Outlet self-labelling that appears at the head of a headline. Stripped for dedupe so
     * `عاجل: قصف على...` and `قصف على...` compare as the same story — which is exactly the
     * case that matters, since one outlet's "breaking" prefix is another's plain headline.
     */
    private val SOURCE_PREFIXES = listOf(
        "عاجل", "خبر عاجل", "الآن", "حصري", "متابعة", "مباشر", "تحديث",
        "رويترز", "الجزيرة", "العربية", "بي بي سي", "فرانس", "سي ان ان", "دويتشه فيله",
        "breaking", "urgent", "update", "exclusive",
    )

    /** High-frequency function words. Carry no topical signal, so they only dilute similarity. */
    private val STOPWORDS = setOf(
        "في", "من", "على", "الى", "عن", "ان", "مع", "هذا", "هذه", "ذلك", "التي", "الذي",
        "ما", "لا", "لم", "لن", "قد", "كان", "كانت", "يكون", "بعد", "قبل", "بين", "عند",
        "او", "ثم", "حتى", "كل", "بعض", "غير", "بها", "به", "له", "لها", "هو", "هي",
        "هم", "هن", "نحن", "انا", "انت", "كما", "اذا", "الا", "ايضا", "حول", "خلال",
        "ضد", "دون", "منذ", "لدى", "وفق", "نحو", "الي", "و", "ب", "ل", "ك", "ف",
        "the", "a", "an", "of", "in", "on", "to", "for", "and", "or", "is", "are", "at",
    )

    /**
     * Clitics that attach to the front of an Arabic word. Stripping them is what lets the
     * seed keyword `رئيس` match `الرئيس` and `وزير` match `والوزير` without keeping a dozen
     * inflected variants in `keywords.json`.
     */
    private val PREFIXES = listOf("وال", "بال", "كال", "فال", "لل", "ال", "و", "ف", "ب", "ك", "ل")

    /**
     * The shared pipeline: diacritics, tatweel, letter unification, digit conversion,
     * URL/emoji/punctuation removal, whitespace collapse, Latin lowercasing.
     */
    fun normalize(input: String): String {
        if (input.isBlank()) return ""
        var s = input

        s = URLS.replace(s, " ")
        s = DIACRITICS.replace(s, "")
        s = s.replace(TATWEEL.toString(), "")

        val sb = StringBuilder(s.length)
        for (ch in s) {
            sb.append(
                when (ch) {
                    'أ', 'إ', 'آ', 'ٱ', 'ا' -> 'ا'
                    'ى' -> 'ي'
                    'ئ' -> 'ي'
                    'ة' -> 'ه'
                    'ؤ' -> 'و'
                    // Arabic-Indic ٠-٩ and Extended (Persian/Urdu) ۰-۹ both appear in feeds.
                    in '٠'..'٩' -> ('0' + (ch - '٠'))
                    in '۰'..'۹' -> ('0' + (ch - '۰'))
                    else -> ch
                },
            )
        }
        s = sb.toString()

        s = PUNCTUATION.replace(s, " ")
        s = WHITESPACE.replace(s, " ").trim()
        return s.lowercase()
    }

    fun tokens(input: String): List<String> =
        normalize(input).split(' ').filter { it.isNotBlank() }

    /** Strips one leading clitic. Never shortens a token below three characters. */
    fun stem(token: String): String {
        for (prefix in PREFIXES) {
            if (token.length >= prefix.length + 3 && token.startsWith(prefix)) {
                return token.removePrefix(prefix)
            }
        }
        return token
    }

    /**
     * Normalised, stemmed, stopwords **kept** — the surface keyword matching runs against.
     * Padded with spaces by the caller so phrase matches respect word boundaries.
     */
    fun stemmedHaystack(input: String): String =
        tokens(input).joinToString(" ") { stem(it) }

    /**
     * Normalised, stemmed, stopwords and outlet prefixes **removed** — the surface
     * similarity runs against.
     */
    fun forDedupe(input: String): String = dedupeTokens(input).joinToString(" ")

    fun dedupeTokens(input: String): List<String> {
        var text = normalize(input)
        text = stripSourcePrefix(text)
        return text.split(' ')
            .asSequence()
            .filter { it.isNotBlank() }
            .map { stem(it) }
            .filter { it !in STOPWORDS && it.length > 1 }
            .toList()
    }

    /**
     * Removes a leading outlet or urgency label. Only the first few tokens are considered,
     * so a headline that legitimately mentions `الجزيرة` in the middle keeps it.
     */
    private fun stripSourcePrefix(normalized: String): String {
        var s = normalized
        var changed = true
        var guard = 0
        while (changed && guard++ < 3) {
            changed = false
            for (prefix in SOURCE_PREFIXES) {
                val normalizedPrefix = normalize(prefix)
                if (normalizedPrefix.isNotEmpty() && s.startsWith("$normalizedPrefix ")) {
                    s = s.removePrefix("$normalizedPrefix ").trim()
                    changed = true
                    break
                }
            }
        }
        return s
    }
}
