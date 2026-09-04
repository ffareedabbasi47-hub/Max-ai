package com.example.core

/**
 * Confidence-tiered fuzzy string matching — ported from the Iris project's audit work.
 * Resolves spoken names (apps, contacts) against on-device data despite speech-recognition
 * mistakes: dropped syllables, extra/missing words, or a similar-sounding substitution.
 *
 * This is what MAX's previous naive `appName.contains(query) || query.contains(appName)`
 * matching (in `SystemControlManager.openAppByName`) is being upgraded to — the old approach
 * had no way to express "this is a shaky guess, maybe confirm" vs "this is clearly right,"
 * which matters a lot more once matching is used for contacts (wrong app = annoyance, wrong
 * contact for a message/call = a real problem).
 */
object FuzzyMatch {

    const val HIGH_CONFIDENCE = 0.85
    const val MEDIUM_CONFIDENCE = 0.60
    const val DEFAULT_THRESHOLD = 0.55

    fun normalize(text: String): String =
        text.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim()

    fun similarity(query: String, candidate: String): Double {
        if (query.isEmpty() || candidate.isEmpty()) return 0.0
        if (query == candidate) return 1.0
        if (candidate.contains(query) || query.contains(candidate)) return 0.9

        val distance = levenshtein(query, candidate)
        val maxLen = maxOf(query.length, candidate.length)
        return 1.0 - (distance.toDouble() / maxLen)
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[a.length][b.length]
    }

    /** Simple threshold match for LOW-risk lookups (e.g. app names) where a wrong guess is a
     * minor, instantly-obvious, reversible mistake. */
    fun <T> bestMatch(
        query: String,
        candidates: List<T>,
        threshold: Double = DEFAULT_THRESHOLD,
        label: (T) -> String
    ): T? {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return null

        var best: T? = null
        var bestScore = 0.0
        for (candidate in candidates) {
            val score = similarity(normalizedQuery, normalize(label(candidate)))
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return if (bestScore >= threshold) best else null
    }

    sealed class MatchOutcome<out T> {
        data class Confident<T>(val item: T, val score: Double, val alternatives: List<T> = emptyList()) : MatchOutcome<T>()
        data class NeedsConfirmation<T>(val item: T, val score: Double, val alternatives: List<T> = emptyList()) : MatchOutcome<T>()
        data class Rejected(val bestScore: Double, val bestGuessLabel: String?, val alternativeLabels: List<String> = emptyList()) : MatchOutcome<Nothing>()
    }

    /** Confidence-tiered match for HIGH-risk lookups (contacts, for messages/calls) — never
     * silently picks the "closest" candidate below [HIGH_CONFIDENCE]. */
    fun <T> match(query: String, candidates: List<T>, label: (T) -> String): MatchOutcome<T> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return MatchOutcome.Rejected(0.0, null)
        if (candidates.isEmpty()) return MatchOutcome.Rejected(0.0, null)

        val scored = candidates
            .map { it to similarity(normalizedQuery, normalize(label(it))) }
            .sortedByDescending { it.second }

        val (bestItem, bestScore) = scored.first()
        val alternativeItems = scored.drop(1).take(3).filter { it.second >= MEDIUM_CONFIDENCE - 0.15 }.map { it.first }

        return when {
            bestScore >= HIGH_CONFIDENCE -> MatchOutcome.Confident(bestItem, bestScore, alternativeItems)
            bestScore >= MEDIUM_CONFIDENCE -> MatchOutcome.NeedsConfirmation(bestItem, bestScore, alternativeItems)
            else -> MatchOutcome.Rejected(bestScore, label(bestItem), alternativeItems.map(label))
        }
    }
}
