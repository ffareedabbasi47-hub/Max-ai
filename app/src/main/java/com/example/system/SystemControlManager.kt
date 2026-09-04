package com.example.system

import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Environment
import android.provider.Settings
import com.example.core.ActionConfirmationPolicy
import com.example.core.ActionRisk
import com.example.core.FuzzyMatch
import com.example.core.MaxContactResolver
import java.io.File

data class InstalledAppInfo(
    val appName: String,
    val packageName: String
)

data class SystemTelemetry(
    val batteryLevel: Int,
    val isCharging: Boolean,
    val ramUsedMb: Long,
    val ramTotalMb: Long,
    val cpuUsagePct: Int?,
    val wifiEnabled: Boolean,
    val bluetoothEnabled: Boolean,
    val ringerMode: String
)

/**
 * AUDIT NOTE (found by actually reading this file, not assumed): the previous version of this
 * class had several places that reported success or plausible-looking data without the
 * underlying action/query actually being real:
 *   - `makeCall` fell back to a hardcoded fake number ("1234567890") when the target didn't
 *     resolve to digits, and its return text ("Dialing link established") implied a call was
 *     placed when ACTION_DIAL only opens the dialer pre-filled — nothing is actually dialed
 *     until the user taps call themselves.
 *   - `sendWhatsAppMessage` never used the `recipient` parameter to target a specific chat —
 *     it just shared text via a generic chooser, so "send to X" didn't actually go to X.
 *   - `getTelemetry` reported `wifiEnabled`/`bluetoothEnabled` as hardcoded `true` and
 *     `cpuUsagePct` as `(20..45).random()` — not real values.
 *   - `openAppByName` used naive substring matching with no confidence signal, and silently
 *     redirected to System Settings on a miss without clearly telling the user the app wasn't
 *     found.
 *   - App discovery used `QUERY_ALL_PACKAGES`, a Play Store-restricted permission requiring a
 *     special declaration form — the modern `<queries>` manifest approach needs no such
 *     approval and isn't flagged in review.
 * All fixed below using patterns ported from the Iris project's audited implementation.
 */
class SystemControlManager(private val context: Context) {

    // ---- App discovery & launching -------------------------------------------------------

    /** Uses the CATEGORY_LAUNCHER <queries> declaration (see AndroidManifest.xml) instead of
     * QUERY_ALL_PACKAGES — same result, no Play Store special-permission review needed. */
    fun getInstalledApps(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)

        return resolveInfos
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                val label = runCatching { info.loadLabel(pm).toString() }.getOrNull() ?: return@mapNotNull null
                InstalledAppInfo(label, pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appName }
    }

    /** Confidence-tiered match (LOW risk — a wrong app opening is a minor, obvious, reversible
     * mistake) instead of the previous naive substring match with a silent Settings fallback. */
    fun openAppByName(queryName: String): String {
        val apps = getInstalledApps()
        val match = FuzzyMatch.bestMatch(queryName, apps) { it.appName }

        if (match == null) {
            return "I couldn't find an app matching '$queryName' on this device, Sir."
        }

        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(match.packageName)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            "Launching ${match.appName}, Sir."
        } else {
            "Found '${match.appName}' but couldn't launch it, Sir."
        }
    }

    // ---- Settings toggles ------------------------------------------------------------------

    fun toggleSystemSetting(setting: String): String {
        val lower = setting.lowercase()
        return when {
            lower.contains("wifi") || lower.contains("wi-fi") -> {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opening Wi-Fi control panel, Sir."
            }
            lower.contains("bluetooth") -> {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opening Bluetooth interface, Sir."
            }
            lower.contains("silent") || lower.contains("mute") || lower.contains("sound") || lower.contains("volume") -> {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val currentMode = audioManager.ringerMode
                if (currentMode == AudioManager.RINGER_MODE_SILENT || currentMode == AudioManager.RINGER_MODE_VIBRATE) {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    "Ringer set to Normal sound mode, Sir."
                } else {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    "System audio switched to Silent/Vibrate mode, Sir."
                }
            }
            else -> {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opening System Settings HUD, Sir."
            }
        }
    }

    // ---- Calls & messaging (now with real contact resolution) ------------------------------

    /** Resolves [target] against the phone's actual contacts (READ_CONTACTS — declared in the
     * manifest, previously unused) instead of stripping digits out of whatever text arrived.
     * HIGH risk: a medium-confidence match reports the ambiguity instead of guessing, rather
     * than silently calling the wrong person or a fake fallback number. */
    fun makeCall(target: String): String {
        val outcome = MaxContactResolver.match(context, target)
        val decision = ActionConfirmationPolicy.decide(ActionRisk.HIGH, outcome) { it.name }

        return when (decision) {
            is ActionConfirmationPolicy.Decision.AskConfirmation ->
                "I found '${decision.matchedLabel}' as a close match for '$target', but I'm " +
                    "not fully certain — please confirm the name and I'll dial, Sir."
            is ActionConfirmationPolicy.Decision.Reject ->
                "I couldn't find a contact matching '$target', Sir."
            is ActionConfirmationPolicy.Decision.Execute -> {
                val contact = when (outcome) {
                    is FuzzyMatch.MatchOutcome.Confident -> outcome.item
                    is FuzzyMatch.MatchOutcome.NeedsConfirmation -> outcome.item
                    is FuzzyMatch.MatchOutcome.Rejected -> null
                } ?: return "I couldn't find a contact matching '$target', Sir."

                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:${contact.phoneNumber}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                    // Honest about what ACTION_DIAL actually does — opens the dialer pre-filled,
                    // does not place the call itself (that needs CALL_PHONE + ACTION_CALL, a
                    // bigger step up in risk this project keeps as a manual tap for now).
                    "Dialer opened for ${contact.name} — tap call to connect, Sir."
                } catch (e: Exception) {
                    "Unable to open the dialer for ${contact.name}, Sir."
                }
            }
        }
    }

    /** Now actually targets the resolved contact via WhatsApp's official per-chat deep link,
     * instead of a generic share-sheet that ignored [recipient] entirely. */
    fun sendWhatsAppMessage(recipient: String, message: String): String {
        val outcome = MaxContactResolver.match(context, recipient)
        val decision = ActionConfirmationPolicy.decide(ActionRisk.HIGH, outcome) { it.name }

        return when (decision) {
            is ActionConfirmationPolicy.Decision.AskConfirmation ->
                "I found '${decision.matchedLabel}' as a close match for '$recipient', but I'm " +
                    "not fully certain — please confirm the name, Sir."
            is ActionConfirmationPolicy.Decision.Reject ->
                "I couldn't find a contact matching '$recipient', Sir."
            is ActionConfirmationPolicy.Decision.Execute -> {
                val contact = when (outcome) {
                    is FuzzyMatch.MatchOutcome.Confident -> outcome.item
                    is FuzzyMatch.MatchOutcome.NeedsConfirmation -> outcome.item
                    is FuzzyMatch.MatchOutcome.Rejected -> null
                } ?: return "I couldn't find a contact matching '$recipient', Sir."

                val cleanNumber = contact.phoneNumber.filter { it.isDigit() || it == '+' }
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=${Uri.encode(message)}")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                    "Opened WhatsApp chat with ${contact.name}, message pre-filled — tap send to deliver, Sir."
                } catch (e: Exception) {
                    "WhatsApp isn't available to message ${contact.name}, Sir."
                }
            }
        }
    }

    // ---- Web search & YouTube (no API key needed — real browser/app intents) --------------

    fun openWebSearch(query: String): Boolean {
        if (query.isBlank()) return false
        val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openYouTubeSearch(query: String): Boolean {
        if (query.isBlank()) return false
        // Try the YouTube app's own search intent first (nicer UX); browser is the fallback.
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:search?q=${Uri.encode(query)}")).apply {
            setPackage("com.google.android.youtube")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (try { context.startActivity(appIntent); true } catch (e: Exception) { false }) {
            return true
        }
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(webIntent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun draftEmail(recipient: String, subjectAndBody: String): String {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            if (recipient.contains("@")) putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            putExtra(Intent.EXTRA_SUBJECT, "MAX AI Transmission")
            putExtra(Intent.EXTRA_TEXT, subjectAndBody)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "Opening email client with compiled draft for $recipient, Sir."
        } catch (e: Exception) {
            "Unable to dispatch email client."
        }
    }

    fun createFileInStorage(fileName: String, content: String): String {
        return try {
            val documentsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val cleanName = if (fileName.contains(".")) fileName else "$fileName.txt"
            val file = File(documentsDir, cleanName)
            file.writeText(content)
            "File '$cleanName' saved in Documents directory (${file.length()} bytes), Sir."
        } catch (e: Exception) {
            "Failed to create file: ${e.message}"
        }
    }

    // ---- Telemetry (now real wifi/bluetooth status; honest about CPU) ----------------------

    fun getTelemetry(): SystemTelemetry {
        val batteryStatus: Intent? = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            }
        } catch (e: Exception) {
            null
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else -1

        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val availRamMb = memInfo.availMem / (1024 * 1024)
        val usedRamMb = (totalRamMb - availRamMb).coerceAtLeast(0L)

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val ringerStr = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "Silent"
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
            else -> "Normal"
        }

        val wifiEnabled = runCatching {
            (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled
        }.getOrNull() ?: false

        val bluetoothEnabled = runCatching {
            BluetoothAdapter.getDefaultAdapter()?.isEnabled
        }.getOrNull() ?: false

        return SystemTelemetry(
            batteryLevel = batteryPct,
            isCharging = isCharging,
            ramUsedMb = usedRamMb,
            ramTotalMb = totalRamMb,
            // Total system CPU usage isn't reliably available to a normal app without root or
            // OEM-specific /proc parsing (which breaks across Android versions/manufacturers)
            // — reporting null and letting the UI show "—" is honest; a random number wasn't.
            cpuUsagePct = null,
            wifiEnabled = wifiEnabled,
            bluetoothEnabled = bluetoothEnabled,
            ringerMode = ringerStr
        )
    }
}
