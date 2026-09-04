package com.example.core

/** How much real-world harm a wrong guess could cause. */
enum class ActionRisk {
    /** Opening the wrong app, wrong URL, etc. — a minor, instantly-obvious, reversible mistake. */
    LOW,
    /** Sending a message, making a call — goes to a real person; a wrong guess is silently
     * correct-looking and only discovered after the fact. */
    HIGH
}

/**
 * Ported from Iris's audit work — decides whether a high-risk action executes immediately,
 * needs verbal/UI confirmation, or is rejected outright, based on [FuzzyMatch] confidence.
 * This is what closes the gap where `SystemControlManager.makeCall`/`sendWhatsAppMessage`
 * previously executed directly on whatever the AI parsed out, with no confidence check at all.
 */
object ActionConfirmationPolicy {

    sealed class Decision {
        data object Execute : Decision()
        data class AskConfirmation(val matchedLabel: String, val score: Double) : Decision()
        data object Reject : Decision()
    }

    fun <T> decide(risk: ActionRisk, outcome: FuzzyMatch.MatchOutcome<T>, label: (T) -> String): Decision =
        when (outcome) {
            is FuzzyMatch.MatchOutcome.Confident -> Decision.Execute
            is FuzzyMatch.MatchOutcome.NeedsConfirmation ->
                if (risk == ActionRisk.LOW) Decision.Execute
                else Decision.AskConfirmation(label(outcome.item), outcome.score)
            is FuzzyMatch.MatchOutcome.Rejected -> Decision.Reject
        }
}
