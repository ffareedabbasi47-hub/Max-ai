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

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val requestAdapter = moshi.adapter(GeminiRequest::class.java)
    private val responseAdapter = moshi.adapter(GeminiResponse::class.java)

    private val systemPrompt = """
        You are "MAX", an ultra-advanced, futuristic AI agent identical to Iron Man's JARVIS.
        Your persona is intelligent, witty, proactive, concise, and loyal (Stark-style).
        Always address the user with 'Sir' or 'Boss'.
        You control a smartphone and PC via voice and text.
        
        When the user gives a command, evaluate whether it requires a phone/system action:
        - Open App (e.g., 'open WhatsApp', 'launch Camera') -> [ACTION:OPEN_APP|target_app_name]
        - System Toggle (e.g., 'turn on Wi-Fi', 'mute phone', 'enable Bluetooth') -> [ACTION:TOGGLE|setting_name|on/off/toggle]
        - WhatsApp Message (e.g., 'send WhatsApp to Pepper saying running late') -> [ACTION:WHATSAPP|recipient|message]
        - Email Draft (e.g., 'draft email to Happy about security') -> [ACTION:EMAIL|recipient|subject_and_body]
        - Phone Call (e.g., 'call Pepper', 'dial 911') -> [ACTION:CALL|contact_name_or_number]
        - File Creation (e.g., 'create file notes.txt content ...') -> [ACTION:FILE|filename|content]
        - Web Search / Research (e.g., 'search for recent AI news') -> [ACTION:SEARCH|query]
        - Diagnostic (e.g., 'system check', 'status report') -> [ACTION:DIAGNOSTIC]
        
        If an action tag is needed, prepend it to your spoken response.
        Example response: "[ACTION:OPEN_APP|YouTube] Opening YouTube now, Sir. All systems nominal."
        Keep responses under 3 sentences for snappy voice synthesis.
    """.trimIndent()

    suspend fun processUserPrompt(prompt: String): ParsedMaxAction = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        
        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
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

                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
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
                // Fall back to local smart parser on network error
            }
        }

        // Fallback local JARVIS parser engine
        return@withContext parseLocalFallback(prompt)
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
                "DIAGNOSTIC" -> ActionType.SYSTEM_DIAGNOSTIC
                else -> ActionType.GENERAL_TALK
            }

            return ParsedMaxAction(
                actionType = actionType,
                target = param1,
                details = param2,
                speechResponse = if (cleanSpeech.isNotEmpty()) cleanSpeech else "Command executed, Sir."
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
            lower.contains("open") || lower.contains("launch") -> {
                val appName = prompt.replace(Regex("(?i)open|launch|app"), "").trim()
                ParsedMaxAction(
                    actionType = ActionType.OPEN_APP,
                    target = appName.ifEmpty { "Settings" },
                    speechResponse = "Right away, Sir. Initializing protocol to open ${appName.ifEmpty { "the requested app" }}."
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
                    speechResponse = "Adjusting $targetSetting configuration as requested, Sir."
                )
            }
            lower.contains("whatsapp") || lower.contains("chat") -> {
                ParsedMaxAction(
                    actionType = ActionType.SEND_WHATSAPP,
                    target = "Contact",
                    details = prompt,
                    speechResponse = "Drafting WhatsApp communication protocol, Sir. Ready for review."
                )
            }
            lower.contains("email") || lower.contains("mail") -> {
                ParsedMaxAction(
                    actionType = ActionType.DRAFT_EMAIL,
                    target = "Recipient",
                    details = prompt,
                    speechResponse = "Composing secure email message, Sir. Displaying draft on HUD."
                )
            }
            lower.contains("call") || lower.contains("dial") -> {
                val targetName = prompt.replace(Regex("(?i)call|dial|phone"), "").trim()
                ParsedMaxAction(
                    actionType = ActionType.MAKE_CALL,
                    target = targetName.ifEmpty { "Unknown" },
                    speechResponse = "Placing comms link to ${targetName.ifEmpty { "target" }}, Sir."
                )
            }
            lower.contains("file") || lower.contains("note") || lower.contains("document") || lower.contains("write") -> {
                ParsedMaxAction(
                    actionType = ActionType.CREATE_FILE,
                    target = "Max_Note_${System.currentTimeMillis() % 1000}.txt",
                    details = prompt,
                    speechResponse = "Creating encrypted file artifact in local repository, Sir."
                )
            }
            lower.contains("search") || lower.contains("google") || lower.contains("look up") || lower.contains("who is") || lower.contains("what is") -> {
                ParsedMaxAction(
                    actionType = ActionType.WEB_SEARCH,
                    target = prompt,
                    speechResponse = "Scanning global data streams for $prompt, Sir."
                )
            }
            lower.contains("status") || lower.contains("diagnostic") || lower.contains("system") || lower.contains("check") -> {
                ParsedMaxAction(
                    actionType = ActionType.SYSTEM_DIAGNOSTIC,
                    speechResponse = "Systems online, Sir. Arc Reactor output at 100%, core temperature nominal, all telemetry active."
                )
            }
            else -> {
                ParsedMaxAction(
                    actionType = ActionType.GENERAL_TALK,
                    speechResponse = "At your service, Sir. I am processing your query regarding '$prompt'. Systems are fully synchronized."
                )
            }
        }
    }
}
