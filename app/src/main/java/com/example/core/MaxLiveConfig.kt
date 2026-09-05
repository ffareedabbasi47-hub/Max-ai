package com.example.core

import android.content.Context
import com.example.BuildConfig

/**
 * Config for MAX's Gemini Live pipeline (real-time streaming voice — natural Gemini TTS voice
 * and low-latency continuous conversation, replacing the old discrete SpeechRecognizer+TTS
 * turn-based flow for anyone who enables Live Mode).
 *
 * Reuses MAX's EXISTING key storage (same SharedPreferences file/keys as [MaxViewModel]'s
 * `getCustomKey`/`saveCustomKey` and [MultiBrainManager]'s slot system) rather than introducing
 * a second, separate key store — Settings already has "Custom Gemini Key" and 3 key-rotation
 * slots; whichever one is set is what Live Mode uses too.
 */
object MaxLiveConfig {
    private const val PREFS_NAME = "max_jarvis_prefs"

    fun resolveApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val customKey = prefs.getString("custom_gemini_api_key", "")?.trim() ?: ""
        if (customKey.isNotBlank()) return customKey

        for (i in 1..3) {
            val slotKey = prefs.getString("api_key_slot_$i", "")?.trim() ?: ""
            if (slotKey.isNotBlank()) return slotKey
        }

        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        return if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") buildKey else ""
    }

    fun hasApiKey(context: Context): Boolean = resolveApiKey(context).isNotBlank()

    /** VERIFIED against ai.google.dev/api/live as of the Iris project's audit — Live API model
     * names and the WebSocket API version do change; re-check before relying on this long-term.
     * `gemini-2.0-flash-live-001` was shut down 2025-12-09. */
    const val LIVE_MODEL = "models/gemini-3.1-flash-live-preview"
    private const val LIVE_API_VERSION = "v1beta"

    fun liveWsUrl(context: Context): String =
        "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.$LIVE_API_VERSION" +
            ".GenerativeService.BidiGenerateContent?key=${resolveApiKey(context)}"

    const val INPUT_SAMPLE_RATE = 16000
    const val OUTPUT_SAMPLE_RATE = 24000

    const val LIVE_SERVICE_NOTIFICATION_CHANNEL = "max_live_voice_channel"
    const val LIVE_SERVICE_NOTIFICATION_ID = 2002

    /** Whether the user has Live Mode enabled (vs. the original discrete STT+TTS flow) — a
     * Settings toggle, off by default so nothing changes for anyone until they opt in. */
    fun isLiveModeEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("live_mode_enabled", false)

    fun setLiveModeEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("live_mode_enabled", enabled)
            .apply()
    }
}
