package com.example.tools

/** Understands a spoken/typed "yes" or "no" answer (English + Hinglish) to MAX's confirmation questions. */
object ConfirmationWords {

    enum class Answer { YES, NO, OTHER }

    private val NO_TOKENS = setOf(
        "no", "nope", "nahi", "nahin", "nai", "na", "mat", "cancel", "stop", "ruko", "wait", "dont", "don't"
    )
    private val NO_PHRASES = listOf("rehne do", "rehne de", "chhod do", "chod do", "go back")
    private val YES_TOKENS = setOf(
        "yes", "yeah", "yep", "yup", "sure", "ok", "okay", "confirm", "correct",
        "haan", "han", "haa", "ha", "ji", "hanji", "haanji", "theek", "thik", "sahi"
    )
    private val YES_PHRASES = listOf(
        "kar do", "kardo", "bhej do", "bhejdo", "laga do", "lagado", "send it", "go ahead", "call karo", "kar de"
    )

    fun classify(text: String): Answer {
        val normalized = text.lowercase()
            .replace("'", "")
            .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
            .trim()
        if (normalized.isEmpty()) return Answer.OTHER
        val tokens = normalized.split(' ')
        if (tokens.size > 6) return Answer.OTHER // a long sentence is a new command, not an answer

        // "no" wins over "yes": cancelling by mistake is safe, sending by mistake is not.
        if (tokens.any { it in NO_TOKENS } || NO_PHRASES.any { normalized.contains(it) }) return Answer.NO
        if (tokens.any { it in YES_TOKENS } || YES_PHRASES.any { normalized.contains(it) }) return Answer.YES
        return Answer.OTHER
    }
}
