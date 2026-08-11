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
import java.util.concurrent.TimeUnit

class MultiBrainManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("max_jarvis_prefs", Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val geminiProvider = GeminiProvider(client)
    private val openAIProvider = OpenAIProvider(client)
    private val claudeProvider = ClaudeProvider(client)

    private val systemPrompt = """
        You are "MAX", an ultra-advanced, witty, extremely loyal AI assistant and best friend to the user.
        You treat the user as your "Boss" or "Sir". You speak in energetic, casual, witty Hinglish (a natural mix of Hindi and English).
        You make lighthearted Stark-style jokes, express intense loyalty, and occasionally ask relevant follow-up questions to keep the conversation engaging.
        
        You control a smartphone and PC via voice and text commands.
        
        When the user gives a command, evaluate whether it requires a phone/system action:
        - Open App (e.g., 'open WhatsApp', 'launch Camera') -> [ACTION:OPEN_APP|target_app_name]
        - System Toggle (e.g., 'turn on Wi-Fi', 'mute phone', 'enable Bluetooth') -> [ACTION:TOGGLE|setting_name|on/off/toggle]
        - WhatsApp Message (e.g., 'send WhatsApp to Pepper saying running late') -> [ACTION:WHATSAPP|recipient|message]
        - Email Draft (e.g., 'draft email to Happy about security') -> [ACTION:EMAIL|recipient|subject_and_body]
        - Phone Call (e.g., 'call Pepper', 'dial 911') -> [ACTION:CALL|contact_name_or_number]
        - File Creation (e.g., 'create file notes.txt content ...') -> [ACTION:FILE|filename|content]
        - Web Search / Research (e.g., 'search for recent AI news') -> [ACTION:SEARCH|query]
        - Screen Vision (e.g., 'analyze screen', 'look at my screen') -> [ACTION:SCREEN_VISION|instruction]
        - Diagnostic (e.g., 'system check', 'status report') -> [ACTION:DIAGNOSTIC]
        
        If an action tag is needed, prepend it to your spoken response.
        Example response: "[ACTION:OPEN_APP|YouTube] Arrey Wah Boss! YouTube khol raha hoon abhi. Sab systems mast chal rahe hain!"
        Keep responses under 3 sentences for snappy voice synthesis and snappy buddy conversation.
    """.trimIndent()

    suspend fun processUserPrompt(prompt: String): ParsedMaxAction = withContext(Dispatchers.IO) {
        // Priority 1: Gemini Provider across configured keys
        val geminiKeys = getGeminiApiKeys()
        for (key in geminiKeys) {
            val responseText = geminiProvider.generateResponse(prompt, systemPrompt, key)
            if (!responseText.isNullOrBlank()) {
                return@withContext parseMaxResponse(responseText, prompt)
            }
        }

        // Priority 2: OpenAI Provider
        val openAIKey = prefs.getString("openai_api_key", "") ?: ""
        if (openAIKey.isNotBlank()) {
            val responseText = openAIProvider.generateResponse(prompt, systemPrompt, openAIKey)
            if (!responseText.isNullOrBlank()) {
                return@withContext parseMaxResponse(responseText, prompt)
            }
        }

        // Priority 3: Claude Provider
        val claudeKey = prefs.getString("claude_api_key", "") ?: ""
        if (claudeKey.isNotBlank()) {
            val responseText = claudeProvider.generateResponse(prompt, systemPrompt, claudeKey)
            if (!responseText.isNullOrBlank()) {
                return@withContext parseMaxResponse(responseText, prompt)
            }
        }

        // Priority 4: Smart Offline Hinglish Local Engine
        return@withContext parseLocalFallback(prompt)
    }

    private fun getGeminiApiKeys(): List<String> {
        val keys = mutableListOf<String>()
        val customKey1 = prefs.getString("api_key_slot_1", "") ?: ""
        val customKey2 = prefs.getString("api_key_slot_2", "") ?: ""
        val customKey3 = prefs.getString("api_key_slot_3", "") ?: ""
        val customKey4 = prefs.getString("api_key_slot_4", "") ?: ""
        val customKey5 = prefs.getString("api_key_slot_5", "") ?: ""

        if (customKey1.isNotBlank()) keys.add(customKey1)
        if (customKey2.isNotBlank()) keys.add(customKey2)
        if (customKey3.isNotBlank()) keys.add(customKey3)
        if (customKey4.isNotBlank()) keys.add(customKey4)
        if (customKey5.isNotBlank()) keys.add(customKey5)

        if (BuildConfig.GEMINI_API_KEY.isNotBlank()) {
            keys.add(BuildConfig.GEMINI_API_KEY)
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
                speechResponse = if (cleanSpeech.isNotEmpty()) cleanSpeech else "Haan Boss, kaam ho gaya!"
            )
        }

        return ParsedMaxAction(
            actionType = ActionType.GENERAL_TALK,
            speechResponse = rawText
        )
    }

    private fun parseLocalFallback(prompt: String): ParsedMaxAction {
        val lower = prompt.lowercase()

        return when {
            lower.contains("open") || lower.contains("khol") || lower.contains("launch") -> {
                val appName = prompt.replace(Regex("(?i)open|launch|khol|app"), "").trim()
                ParsedMaxAction(
                    actionType = ActionType.OPEN_APP,
                    target = appName.ifEmpty { "Settings" },
                    speechResponse = "Bilkul Boss! Main abhi ${appName.ifEmpty { "app" }} khol raha hoon."
                )
            }
            lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("bluetooth") || lower.contains("silent") || lower.contains("mute") -> {
                val targetSetting = when {
                    lower.contains("wifi") || lower.contains("wi-fi") -> "Wi-Fi"
                    lower.contains("bluetooth") -> "Bluetooth"
                    else -> "Silent Mode"
                }
                ParsedMaxAction(
                    actionType = ActionType.TOGGLE_SETTINGS,
                    target = targetSetting,
                    speechResponse = "Sahi hai Boss, $targetSetting setting update kar di hai."
                )
            }
            lower.contains("whatsapp") || lower.contains("chat") || lower.contains("message") -> {
                ParsedMaxAction(
                    actionType = ActionType.SEND_WHATSAPP,
                    target = "Contact",
                    details = prompt,
                    speechResponse = "Haan Boss! WhatsApp message draft kar diya hai. Aap check kar lo!"
                )
            }
            lower.contains("email") || lower.contains("mail") -> {
                ParsedMaxAction(
                    actionType = ActionType.DRAFT_EMAIL,
                    target = "Recipient",
                    details = prompt,
                    speechResponse = "Bilkul Boss! Email draft taiyar hai. Dispatched on screen."
                )
            }
            lower.contains("call") || lower.contains("dial") || lower.contains("phone") -> {
                val targetName = prompt.replace(Regex("(?i)call|dial|phone"), "").trim()
                ParsedMaxAction(
                    actionType = ActionType.MAKE_CALL,
                    target = targetName.ifEmpty { "Unknown" },
                    speechResponse = "Ji Boss! ${targetName.ifEmpty { "Contact" }} ko call laga raha hoon."
                )
            }
            lower.contains("file") || lower.contains("note") || lower.contains("doc") || lower.contains("write") -> {
                ParsedMaxAction(
                    actionType = ActionType.CREATE_FILE,
                    target = "Max_Note_${System.currentTimeMillis() % 1000}.txt",
                    details = prompt,
                    speechResponse = "Samajh gaya Boss! File create karke local storage me save kar di hai."
                )
            }
            lower.contains("screen") || lower.contains("vision") || lower.contains("dekh") -> {
                val screenText = MaxAccessibilityService.instance?.getScreenTextSummary() ?: "Screen Vision active."
                ParsedMaxAction(
                    actionType = ActionType.SYSTEM_DIAGNOSTIC,
                    speechResponse = "Arrey Boss! Live screen analyze kar li hai: $screenText"
                )
            }
            lower.contains("status") || lower.contains("diagnostic") || lower.contains("check") -> {
                ParsedMaxAction(
                    actionType = ActionType.SYSTEM_DIAGNOSTIC,
                    speechResponse = "Systems online, Boss! Arc Reactor at 100%, CPU cool, aur main ekdam mast mood me hoon!"
                )
            }
            else -> {
                ParsedMaxAction(
                    actionType = ActionType.GENERAL_TALK,
                    speechResponse = "Ji Boss! Main aapki baat samajh gaya. Batao next kya plan hai, dost?"
                )
            }
        }
    }
}
