package com.example.data.api

import android.content.Context
import com.example.BuildConfig
import com.example.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GeminiBrain(private val context: Context) {

    private val prefs = context.getSharedPreferences("max_jarvis_prefs", Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val requestAdapter = moshi.adapter(GeminiRequest::class.java)
    private val responseAdapter = moshi.adapter(GeminiResponse::class.java)

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
        val keysList = getActiveApiKeys()

        for (key in keysList) {
            if (key.isNotBlank() && key != "MY_GEMINI_API_KEY" && key != "FALLBACK_KEY_VALID" && key != "NON_EXISTENT_KEY") {
                try {
                    val geminiRequest = GeminiRequest(
                        contents = listOf(
                            GeminiContent(
                                parts = listOf(GeminiPart(text = prompt))
                            )
                        ),
                        systemInstruction = GeminiContent(
                            parts = listOf(GeminiPart(text = systemPrompt))
                        )
                    )

                    val jsonBody = requestAdapter.toJson(geminiRequest)
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val body = jsonBody.toRequestBody(mediaType)

                    val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$key"
                    val request = Request.Builder()
                        .url(url)
                        .post(body)
                        .build()

                    val response = client.newCall(request).execute()
                    val responseStr = response.body?.string()

                    if (response.isSuccessful && responseStr != null) {
                        val geminiResponse = responseAdapter.fromJson(responseStr)
                        val rawText = geminiResponse?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        if (!rawText.isNullOrBlank()) {
                            return@withContext parseMaxResponse(rawText, prompt)
                        }
                    }
                } catch (e: Exception) {
                    // Try next key slot in rotation if this key encounters network or quota error
                }
            }
        }

        // Fallback local JARVIS parser engine in Hinglish
        return@withContext parseLocalFallback(prompt)
    }

    private fun getActiveApiKeys(): List<String> {
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
                ParsedMaxAction(
                    actionType = ActionType.SYSTEM_DIAGNOSTIC,
                    speechResponse = "Arrey Boss! Live screen analyze kar li hai. Yahan tap karke direct control kar sakte ho!"
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

