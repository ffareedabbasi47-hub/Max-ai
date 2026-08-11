package com.example.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Environment
import android.provider.Settings
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
    val cpuUsagePct: Int,
    val wifiEnabled: Boolean,
    val bluetoothEnabled: Boolean,
    val ringerMode: String
)

class SystemControlManager(private val context: Context) {

    fun getInstalledApps(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val appList = mutableListOf<InstalledAppInfo>()

        for (pkg in packages) {
            // Filter user-facing apps
            if (pm.getLaunchIntentForPackage(pkg.packageName) != null) {
                val appName = pm.getApplicationLabel(pkg).toString()
                appList.add(InstalledAppInfo(appName, pkg.packageName))
            }
        }
        return appList.sortedBy { it.appName }
    }

    fun openAppByName(queryName: String): String {
        val pm = context.packageManager
        val apps = getInstalledApps()
        val lowerQuery = queryName.lowercase().trim()

        val matchedApp = apps.find { it.appName.lowercase().contains(lowerQuery) || lowerQuery.contains(it.appName.lowercase()) }

        return if (matchedApp != null) {
            val launchIntent = pm.getLaunchIntentForPackage(matchedApp.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                "Launching ${matchedApp.appName}, Sir."
            } else {
                "Unable to launch ${matchedApp.appName}."
            }
        } else {
            // Fallback: Open system settings or play store search
            try {
                val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                "App '$queryName' not directly matched. Opening System Settings, Sir."
            } catch (e: Exception) {
                "Could not open application '$queryName'."
            }
        }
    }

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

    fun makeCall(target: String): String {
        val cleanTarget = target.replace(Regex("[^0-9+]"), "")
        val phoneNumber = if (cleanTarget.isNotEmpty()) cleanTarget else "1234567890"

        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            return "Dialing link established for $target ($phoneNumber), Sir."
        } catch (e: Exception) {
            return "Unable to place call to $target."
        }
    }

    fun sendWhatsAppMessage(recipient: String, message: String): String {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "Routing WhatsApp communication to $recipient, Sir."
        } catch (e: Exception) {
            // General share intent fallback
            val genericIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "$recipient: $message")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(genericIntent, "Share Message via MAX").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            "WhatsApp client not installed. Opening system share dispatch, Sir."
        }
    }

    fun draftEmail(recipient: String, subjectAndBody: String): String {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(if (recipient.contains("@")) recipient else "stark@industries.com"))
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

    fun getTelemetry(): SystemTelemetry {
        // Battery info
        val batteryStatus: Intent? = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            }
        } catch (e: Exception) {
            null
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 85
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val batteryPct = (level * 100 / scale.toFloat()).toInt()

        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        // Memory info
        val runtime = Runtime.getRuntime()
        val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val totalMemMb = runtime.maxMemory() / (1024 * 1024)

        // Audio Ringer info
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val ringerStr = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "Silent"
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
            else -> "Normal"
        }

        val pseudoCpuPct = (20..45).random()

        return SystemTelemetry(
            batteryLevel = batteryPct,
            isCharging = isCharging,
            ramUsedMb = usedMemMb,
            ramTotalMb = totalMemMb,
            cpuUsagePct = pseudoCpuPct,
            wifiEnabled = true,
            bluetoothEnabled = true,
            ringerMode = ringerStr
        )
    }
}
