package com.example.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.core.FuzzyMatch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Structured outcome of a tool run. MAX only reports success when [success] is true. */
data class ToolResult(val success: Boolean, val spoken: String, val detail: String = "")

/**
 * Handles simple commands entirely ON THE PHONE — no internet, no AI call, so they answer
 * instantly. Anything it does not recognise returns null and goes to the AI as before.
 *
 * Currently: open an app ("open YouTube", "youtube kholo"), tell the time, tell the date.
 */
object OfflineCommandRouter {

    private val OPEN_EN = Regex(
        """^\s*(?:please\s+)?(?:open|launch|start|run)\s+(?:the\s+)?(.+?)(?:\s+(?:app|application))?\s*[.!?]*\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val OPEN_HI = Regex(
        """^\s*(?:please\s+)?(?:the\s+)?(.+?)\s+(?:kholo|khol\s+do|khol\s+de|kholiye|chalu\s+karo|chalao|open\s+karo|start\s+karo)\s*[.!?]*\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val COMPOUND = Regex("""\b(?:and|aur|then|phir|fir|after)\b""", RegexOption.IGNORE_CASE)

    private val TIME_Q = Regex(
        """\b(?:what(?:'s|\s+is)?\s+(?:the\s+)?time|what\s+time|current\s+time|time\s+kya\s+hai|kitne\s+baje|abhi\s+time|time\s+batao)\b""",
        RegexOption.IGNORE_CASE
    )
    private val DATE_Q = Regex(
        """\b(?:what(?:'s|\s+is)?\s+(?:the\s+)?date|today'?s\s+date|aaj\s+(?:ki\s+)?date|aaj\s+kya\s+date|aaj\s+kaunsa\s+din|which\s+day)\b""",
        RegexOption.IGNORE_CASE
    )

    /** "open YouTube" / "youtube kholo" -> "YouTube"; null if not an open-app command (or a compound one). */
    fun parseOpenTarget(text: String): String? {
        val target = (OPEN_EN.find(text) ?: OPEN_HI.find(text))?.groupValues?.get(1)?.trim().orEmpty()
        if (target.isEmpty()) return null
        if (COMPOUND.containsMatchIn(target)) return null // "open youtube and search x" -> let the AI handle it
        if (target.split(' ').size > 4) return null
        return target
    }

    fun isTimeQuery(text: String) = TIME_Q.containsMatchIn(text)
    fun isDateQuery(text: String) = DATE_Q.containsMatchIn(text)

    /** Returns a result if this was an offline command, or null to send it to the AI. */
    fun handle(context: Context, text: String): ToolResult? {
        parseOpenTarget(text)?.let { target -> return openApp(context, target) }
        if (isTimeQuery(text)) {
            val now = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            return ToolResult(true, "Abhi $now ho rahe hain.")
        }
        if (isDateQuery(text)) {
            val today = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())
            return ToolResult(true, "Aaj $today hai.")
        }
        return null
    }

    private data class AppEntry(val label: String, val pkg: String)

    /**
     * Returns null when no installed app matches confidently, so the AI path can answer instead
     * (it will report the real outcome; MAX never claims an app opened when it did not).
     */
    private fun openApp(context: Context, target: String): ToolResult? {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                val label = runCatching { info.loadLabel(pm).toString() }.getOrNull() ?: return@mapNotNull null
                AppEntry(label, pkg)
            }
            .distinctBy { it.pkg }
        val match = FuzzyMatch.bestMatch(target, apps) { it.label } ?: return null
        val intent = pm.getLaunchIntentForPackage(match.pkg)
            ?: return ToolResult(false, "${match.label} mila, par khul nahi paaya.")
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ToolResult(true, "${match.label} khol raha hoon.", match.pkg)
        } catch (e: Exception) {
            ToolResult(false, "${match.label} kholne me dikkat aayi. Ho sakta hai phone lock ho ya MAX screen par na ho.")
        }
    }
}
