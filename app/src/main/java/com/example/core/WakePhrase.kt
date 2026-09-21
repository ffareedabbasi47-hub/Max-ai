package com.example.core

/**
 * Pure text matching for MAX's wake phrase ("Max" / "Hey Max").
 *
 * The old code used `text.contains("max")`, which also fired on "maximum", "Maxwell", "climax"
 * and so on, and on every partial result. This matches whole words only.
 */
object WakePhrase {

    /** Leading wake phrase, e.g. "Max", "hey max,", "OK Max!" — plus trailing punctuation/space. */
    private val LEADING = Regex("""^\s*(?:(?:hey|hi|ok|okay)\s+)?max\b[\s,.!?:;\-]*""", RegexOption.IGNORE_CASE)

    /** "hey max" anywhere in the utterance (used by the background detector). */
    private val HEY_MAX_ANYWHERE = Regex("""\b(?:hey|hi|ok|okay)\s+max\b""", RegexOption.IGNORE_CASE)

    fun startsWithWake(text: String): Boolean = LEADING.containsMatchIn(text)

    /** True if the utterance addresses MAX: starts with the phrase, or contains "hey max". */
    fun containsWake(text: String): Boolean = startsWithWake(text) || HEY_MAX_ANYWHERE.containsMatchIn(text)

    /** The utterance with a leading wake phrase removed ("Hey Max open YouTube" -> "open YouTube"). */
    fun stripWake(text: String): String = text.replaceFirst(LEADING, "").trim()

    /** True if the user said only the wake phrase and nothing else ("Max", "hey max!"). */
    fun isWakeOnly(text: String): Boolean = startsWithWake(text) && stripWake(text).isEmpty()
}
