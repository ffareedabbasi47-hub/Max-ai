package com.example.data.model

import com.squareup.moshi.JsonClass

enum class MaxState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    EXECUTING
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "USER" or "MAX"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class ActionType {

    OPEN_APP,
    TOGGLE_SETTINGS,
    SEND_WHATSAPP,
    DRAFT_EMAIL,
    MAKE_CALL,
    CREATE_FILE,
    WEB_SEARCH,
    SYSTEM_DIAGNOSTIC,
    GENERAL_TALK
}

data class ParsedMaxAction(
    val actionType: ActionType,
    val target: String = "",
    val details: String = "",
    val speechResponse: String
)

// Gemini API REST Request & Response Models for Moshi
@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null,
    val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    val temperature: Float? = 0.4f,
    val topP: Float? = 0.95f,
    val topK: Int? = 40
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent? = null
)
