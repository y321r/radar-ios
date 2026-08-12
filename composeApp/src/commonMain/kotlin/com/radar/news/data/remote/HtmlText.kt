package com.radar.news.data.remote

/**
 * HTML/entity handling for feed payloads.
 *
 * Feeds are XML in name only — in practice they carry HTML fragments inside CDATA and
 * named HTML entities that XML has never heard of. `&nbsp;` alone will abort a strict
 * pull parser mid-document and cost the whole source, so entities are normalised before
 * the parser ever sees the text.
 */
object HtmlText {

    /** The five entities XML actually defines. Everything else has to be translated. */
    private val XML_BUILTIN = setOf("amp", "lt", "gt", "quot", "apos")

    private val NAMED = mapOf(
        "nbsp" to " ", "ensp" to " ", "emsp" to " ", "thinsp" to " ", "shy" to "",
        "ndash" to "–", "mdash" to "—", "hellip" to "…", "middot" to "·", "bull" to "•",
        "lsquo" to "‘", "rsquo" to "’", "sbquo" to "‚",
        "ldquo" to "“", "rdquo" to "”", "bdquo" to "„",
        "laquo" to "«", "raquo" to "»", "lsaquo" to "‹", "rsaquo" to "›",
        "deg" to "°", "plusmn" to "±", "times" to "×", "divide" to "÷",
        "euro" to "€", "pound" to "£", "yen" to "¥", "cent" to "¢",
        "copy" to "©", "reg" to "®", "trade" to "™", "sect" to "§", "para" to "¶",
        "dagger" to "†", "Dagger" to "‡", "permil" to "‰", "prime" to "′", "Prime" to "″",
        "eacute" to "é", "egrave" to "è", "ecirc" to "ê", "agrave" to "à", "acirc" to "â",
        "ccedil" to "ç", "ocirc" to "ô", "ouml" to "ö", "uuml" to "ü", "auml" to "ä",
        "szlig" to "ß", "ntilde" to "ñ", "iacute" to "í", "oacute" to "ó", "uacute" to "ú",
        "aacute" to "á", "rlm" to "‏", "lrm" to "‎", "zwnj" to "‌", "zwj" to "‍",
    )

    /**
     * Any entity reference of two characters or more, **except** a numeric one.
     *
     * The character class is deliberately permissive rather than `[a-zA-Z][a-zA-Z0-9]{1,31}`:
     * an XML name may contain `_`, `-`, `.` and `:`, and the narrower pattern let
     * `&a_8;` through untouched — which is precisely how an entity-expansion bomb reached the
     * parser (see XmlEntityHardeningTest). The leading `#` is excluded so numeric references
     * such as `&#1593;` are left strictly alone; stripping those would corrupt Arabic text.
     *
     * The two-character minimum is unchanged, so prose like `AT&T;` is still not touched.
     */
    private val ENTITY = Regex("&([^#;&<>\\s][^;&<>\\s]{1,63});")
    private val NUMERIC_ENTITY = Regex("&#(x?)([0-9a-fA-F]+);")
    private val DOCTYPE = Regex("<!DOCTYPE", RegexOption.IGNORE_CASE)

    /** First real element start tag — everything before it is prolog. */
    private val ELEMENT_START = Regex("<[A-Za-z]")
    private val TAG = Regex("<[^>]{0,4000}>")
    private val IMG_SRC = Regex("""<img[^>]{0,600}?\ssrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val WHITESPACE = Regex("\\s+")

    /**
     * Makes a feed body safe for a pull parser: the document type declaration is removed, then
     * any named entity XML does not define is replaced by its literal character (or dropped if
     * unknown). The five XML built-ins are left alone so the parser still resolves them itself.
     *
     * Both passes are load-bearing for safety, not just tidiness. Android's pull parser expands
     * internal DTD entities, so an 825-byte feed declaring nine nested entities expands to 10^9
     * characters and takes the process out of memory. Removing the declarations denies the
     * expansion its source; stripping the references denies it a trigger.
     */
    fun sanitizeForXml(xml: String): String = ENTITY.replace(stripDoctype(xml)) { m ->
        val name = m.groupValues[1]
        when {
            name in XML_BUILTIN -> m.value
            else -> NAMED[name] ?: NAMED[name.lowercase()] ?: ""
        }
    }

    /**
     * Removes any `<!DOCTYPE …>` from the prolog, internal subset included.
     *
     * Scoped to the prolog on purpose: a `<!DOCTYPE html>` sitting inside a CDATA description
     * is article content, not a declaration, and is left where it is. (It never reaches the
     * screen regardless — [toPlainText] strips it as markup — but the parser's view of the
     * document should not change either.)
     *
     * Hand-scanned rather than matched with a regex because the internal subset contains `>`
     * characters of its own; a pattern that copes with that invites the catastrophic
     * backtracking this function exists to prevent.
     */
    private fun stripDoctype(xml: String): String {
        var result = xml
        var guard = 0
        // XML permits one declaration; the guard is for a hostile body that ships several.
        while (guard++ < MAX_DOCTYPES) {
            val prologEnd = ELEMENT_START.find(result)?.range?.first ?: result.length
            val start = DOCTYPE.find(result)?.range?.first ?: return result
            if (start >= prologEnd) return result
            result = result.removeRange(start, doctypeEndExclusive(result, start))
        }
        return result
    }

    /** Index just past the `>` that closes the declaration, tracking internal-subset brackets. */
    private fun doctypeEndExclusive(xml: String, start: Int): Int {
        var depth = 0
        var i = start
        while (i < xml.length) {
            when (xml[i]) {
                '[' -> depth++
                ']' -> if (depth > 0) depth--
                '>' -> if (depth == 0) return i + 1
            }
            i++
        }
        // Unterminated declaration: the rest of the document is inside it.
        return xml.length
    }

    private const val MAX_DOCTYPES = 4

    /** Decodes entities in already-extracted text, including numeric ones. */
    fun decodeEntities(text: String): String {
        var out = NUMERIC_ENTITY.replace(text) { m ->
            val radix = if (m.groupValues[1].isEmpty()) 10 else 16
            val code = m.groupValues[2].toIntOrNull(radix)
            if (code != null && code in 1..0x10FFFF) codePointToString(code) else ""
        }
        out = ENTITY.replace(out) { m ->
            when (val name = m.groupValues[1]) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                else -> NAMED[name] ?: NAMED[name.lowercase()] ?: ""
            }
        }
        return out
    }

    /**
     * Strips markup and collapses whitespace — what a two-line summary under a headline needs.
     * Runs the entity pass twice because feeds routinely double-encode (`&amp;quot;`).
     */
    fun toPlainText(html: String?): String? {
        if (html.isNullOrBlank()) return null
        var s = TAG.replace(html, " ")
        s = decodeEntities(s)
        s = decodeEntities(s)
        s = WHITESPACE.replace(s, " ").trim()
        return s.takeIf { it.isNotEmpty() }
    }

    /** First `<img src>` in a fragment — Al Arabiya's only image signal. */
    fun firstImageSrc(html: String?): String? {
        if (html.isNullOrBlank()) return null
        val raw = IMG_SRC.find(html)?.groupValues?.get(1) ?: return null
        val url = decodeEntities(raw).trim()
        return url.takeIf { it.startsWith("http", ignoreCase = true) }
    }

    /**
     * Truncates to [max] characters on a word boundary, appending an ellipsis.
     *
     * This is the legal constraint made concrete: headline + short summary + image + link
     * only, never the article body. The cap is enforced at ingest so an over-long summary
     * cannot even reach the database.
     */
    fun truncate(text: String?, max: Int): String? {
        if (text == null) return null
        if (text.length <= max) return text
        val cut = text.take(max)
        val lastSpace = cut.lastIndexOf(' ')
        val body = if (lastSpace > max / 2) cut.take(lastSpace) else cut
        return body.trimEnd().trimEnd('،', ',', '.', '؛', ';') + "…"
    }
}


/** Common KMP replacement for java.lang.Character.toChars: code point -> String (BMP or surrogate pair). */
internal fun codePointToString(cp: Int): String = when {
    cp in 0x10000..0x10FFFF -> {
        val v = cp - 0x10000
        val high = (0xD800 + (v shr 10)).toChar()
        val low = (0xDC00 + (v and 0x3FF)).toChar()
        "$high$low"
    }
    cp in 1..0xFFFF -> Char(cp).toString()
    else -> ""
}
