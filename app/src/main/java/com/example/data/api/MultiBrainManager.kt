package com.example.data.api

import android.content.Context
import com.example.BuildConfig
import com.example.data.api.providers.*
import com.example.data.model.ActionType
import com.example.data.model.ParsedMaxAction
import com.example.system.MaxAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.data.api.diagnostics.GeminiDiagnosticResult
import com.example.data.api.diagnostics.GeminiDiagnosticService
import java.util.concurrent.TimeUnit

class MultiBrainManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("max_jarvis_prefs", Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val geminiProvider = GeminiProvider(client)
    private val openAIProvider = OpenAIProvider(client)
    private val claudeProvider = ClaudeProvider(client)
    private val diagnosticService = GeminiDiagnosticService(client)

    suspend fun runGeminiDiagnostic(apiKey: String = BuildConfig.GEMINI_API_KEY): GeminiDiagnosticResult {
        return diagnosticService.testGeminiConnectivity(apiKey)
    }

    // Short-term conversation history for context awareness
    private val recentContextHistory = mutableListOf<String>()

    private val systemPrompt: String
        get() = """
        You are "MAX", a highly intelligent, witty, loyal personal AI assistant and best friend to the user.
        You treat the user as "Boss". You speak naturally in Hinglish (a mix of Hindi and English) or English as appropriate.
        You can answer general questions (science, history, date/time, math, technology, sports) and also control device actions.
        
        Current Device Time: ${SimpleDateFormat("EEEE, dd MMMM yyyy HH:mm", Locale.getDefault()).format(Date())}

        When responding:
        - Call the user "Boss" naturally (do not repeat it in every single phrase).
        - Keep answers concise, clear, and direct (2-4 sentences max for easy speech).
        - Answer general knowledge and conversation questions accurately.
        
        If a device action is requested, prepend one of these action tags:
        - Open App -> [ACTION:OPEN_APP|target_app_name]
        - System Toggle -> [ACTION:TOGGLE|setting_name|on/off/toggle]
        - WhatsApp -> [ACTION:WHATSAPP|recipient|message]
        - Email -> [ACTION:EMAIL|recipient|subject_and_body]
        - Call -> [ACTION:CALL|contact_name_or_number]
        - File Creation -> [ACTION:FILE|filename|content]
        - Web Search -> [ACTION:SEARCH|query]
        - Screen Vision -> [ACTION:SCREEN_VISION|instruction]
        - Diagnostic -> [ACTION:DIAGNOSTIC]
        
        Example: "[ACTION:OPEN_APP|YouTube] Bilkul Boss! YouTube open kar raha hoon."
    """.trimIndent()

    fun getCustomApiKey(): String {
        return prefs.getString("custom_gemini_api_key", "") ?: ""
    }

    suspend fun processUserPrompt(prompt: String): ParsedMaxAction = withContext(Dispatchers.IO) {

        val trimmedPrompt = prompt.trim()
        val contextualPrompt = if (recentContextHistory.isNotEmpty()) {
            "Previous Context:\n${recentContextHistory.takeLast(4).joinToString("\n")}\n\nUser Question: $trimmedPrompt"
        } else {
            trimmedPrompt
        }

        // Priority 1: Gemini Provider across configured keys
        val geminiKeys = getGeminiApiKeys()
        for (key in geminiKeys) {
            val responseText = geminiProvider.generateResponse(contextualPrompt, systemPrompt, key)
            if (!responseText.isNullOrBlank()) {
                recordHistory(trimmedPrompt, responseText)
                return@withContext parseMaxResponse(responseText, prompt)
            }
        }

        // Priority 2: OpenAI Provider
        val openAIKey = prefs.getString("openai_api_key", "") ?: ""
        if (openAIKey.isNotBlank()) {
            val responseText = openAIProvider.generateResponse(contextualPrompt, systemPrompt, openAIKey)
            if (!responseText.isNullOrBlank()) {
                recordHistory(trimmedPrompt, responseText)
                return@withContext parseMaxResponse(responseText, prompt)
            }
        }

        // Priority 3: Claude Provider
        val claudeKey = prefs.getString("claude_api_key", "") ?: ""
        if (claudeKey.isNotBlank()) {
            val responseText = claudeProvider.generateResponse(contextualPrompt, systemPrompt, claudeKey)
            if (!responseText.isNullOrBlank()) {
                recordHistory(trimmedPrompt, responseText)
                return@withContext parseMaxResponse(responseText, prompt)
            }
        }

        // Priority 4: Smart Local Fallback Router
        val fallback = parseLocalFallback(prompt, geminiKeys.isEmpty())
        recordHistory(trimmedPrompt, fallback.speechResponse)
        return@withContext fallback
    }

    private fun recordHistory(userQuery: String, maxReply: String) {
        synchronized(recentContextHistory) {
            recentContextHistory.add("User: $userQuery")
            recentContextHistory.add("MAX: $maxReply")
            if (recentContextHistory.size > 10) {
                recentContextHistory.removeAt(0)
                recentContextHistory.removeAt(0)
            }
        }
    }

    private fun getGeminiApiKeys(): List<String> {
        val keys = mutableListOf<String>()
        val customKey1 = prefs.getString("api_key_slot_1", "") ?: ""
        val customKey2 = prefs.getString("api_key_slot_2", "") ?: ""
        val customKey3 = prefs.getString("api_key_slot_3", "") ?: ""
        val customGeminiKey = prefs.getString("custom_gemini_api_key", "") ?: ""

        if (customKey1.isNotBlank()) keys.add(customKey1.trim())
        if (customKey2.isNotBlank()) keys.add(customKey2.trim())
        if (customKey3.isNotBlank()) keys.add(customKey3.trim())
        if (customGeminiKey.isNotBlank()) keys.add(customGeminiKey.trim())

        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") {
            keys.add(buildKey)
        }
        return keys.distinct()
    }

    private fun parseMaxResponse(rawText: String, prompt: String): ParsedMaxAction {
        val actionRegex = Regex("\\[ACTION:([A-Z_]+)(?:\\|([^|\\]]*))?(?:\\|([^|\\]]*))?\\]")
        val match = actionRegex.find(rawText)

        if (match != null) {
            val typeStr = match.groupValues[1]
            val param1 = match.groupValues.getOrNull(2) ?: ""
            val param2 = match.groupValues.getOrNull(3) ?: ""
            val cleanSpeech = rawText.replace(match.value, "").trim()

            val actionType = when (typeStr) {
                "OPEN_APP" -> ActionType.OPEN_APP
                "TOGGLE" -> ActionType.TOGGLE_SETTINGS
                "WHATSAPP" -> ActionType.SEND_WHATSAPP
                "EMAIL" -> ActionType.DRAFT_EMAIL
                "CALL" -> ActionType.MAKE_CALL
                "FILE" -> ActionType.CREATE_FILE
                "SEARCH" -> ActionType.WEB_SEARCH
                "SCREEN_VISION" -> ActionType.SYSTEM_DIAGNOSTIC
                "DIAGNOSTIC" -> ActionType.SYSTEM_DIAGNOSTIC
                else -> ActionType.GENERAL_TALK
            }

            return ParsedMaxAction(
                actionType = actionType,
                target = param1,
                details = param2,
                speechResponse = cleanSpeech.ifEmpty { "Haan Boss, kaam ho gaya!" },
                isFallback = false
            )
        }

        return ParsedMaxAction(
            actionType = ActionType.GENERAL_TALK,
            speechResponse = rawText,
            isFallback = false
        )
    }

    private fun parseLocalFallback(prompt: String, missingKey: Boolean): ParsedMaxAction {
        val lower = prompt.lowercase(Locale.getDefault())

        val dateStr = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date())
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())

        val baseAction = when {
            // General Date & Time queries
            lower.contains("date") || lower.contains("tareekh") || lower.contains("din") -> {
                ParsedMaxAction(
                    actionType = ActionType.GENERAL_TALK,
                    speechResponse = "Aaj $dateStr hai, Boss."
                )
            }
            lower.contains("time") || lower.contains("samay") || lower.contains("waqt") || lower.contains("baja") -> {
                ParsedMaxAction(
                    actionType = ActionType.GENERAL_TALK,
                    speechResponse = "Abhi time $timeStr ho raha hai, Boss."
                )
            }
            // Capital & Knowledge queries
            lower.contains("capital of india") || lower.contains("india ki capital") -> {
                ParsedMaxAction(
                    actionType = ActionType.GENERAL_TALK,
                    speechResponse = "India ki capital New Delhi hai, Boss."
                )
            }
            lower.contains("gravity") -> {
                ParsedMaxAction(
                    actionType = ActionType.GENERAL_TALK,
                    speechResponse = "Gravity ek natural force hai jo mass wali cheezon ko ek doosre ki taraf khinchti hai. Earth par iski acceleration 9.8 m/s² hai, Boss."
                )
            }
            // Device actions
            lower.contains("open") || lower.contains("khol") || lower.contains("launch") -> {
                val appName = prompt.replace(Regex("(?i)open|launch|khol|app|max"), "").trim()
                ParsedMaxAction(
                    actionType = ActionType.OPEN_APP,
                    target = appName.ifEmpty { "Settings" },
                    speechResponse = "Bilkul Boss! Main abhi ${appName.ifEmpty { "app" }} open kar raha hoon."
                )
            }
            lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("bluetooth") || lower.contains("silent") || lower.contains("mute") || lower.contains("flashlight") || lower.contains("torch") -> {
                val targetSetting = when {
                    lower.contains("wifi") || lower.contains("wi-fi") -> "Wi-Fi"
                    lower.contains("bluetooth") -> "Bluetooth"
                    lower.contains("flashlight") || lower.contains("torch") -> "Flashlight"
                    else -> "Silent Mode"
                }
                ParsedMaxAction(
                    actionType = ActionType.TOGGLE_SETTINGS,
                    target = targetSetting,
                    speechResponse = "Ji Boss, $targetSetting control settings update kar di hain."
                )
            }
            lower.contains("whatsapp") || lower.contains("chat") || lower.contains("message") -> {
                ParsedMaxAction(
                    actionType = ActionType.SEND_WHATSAPP,
                    target = "Contact",
                    details = prompt,
                    speechResponse = "Haan Boss! WhatsApp message draft kar diya hai."
                )
            }
            lower.contains("call") || lower.contains("dial") -> {
                val targetName = prompt.replace(Regex("(?i)call|dial|phone|max"), "").trim()
                ParsedMaxAction(
                    actionType = ActionType.MAKE_CALL,
                    target = targetName.ifEmpty { "Contact" },
                    speechResponse = "Ji Boss! ${targetName.ifEmpty { "Contact" }} ko call laga raha hoon."
                )
            }
            lower.contains("status") || lower.contains("diagnostic") || lower.contains("check") -> {
                ParsedMaxAction(
                    actionType = ActionType.SYSTEM_DIAGNOSTIC,
                    speechResponse = "Systems fully operational, Boss! MAX Core running smooth."
                )
            }
            else -> {
                val notice = if (missingKey) " (Tip: Configure API Key in Settings for live AI answers)" else ""
                ParsedMaxAction(
                    actionType = ActionType.GENERAL_TALK,
                    speechResponse = "Haan Boss! Main aapki baat samajh gaya. Kya command hai?$notice"
                )
            }
        }

        return baseAction.copy(isFallback = true)
    }
}

