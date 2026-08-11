package com.example.data.api.providers

enum class ProviderType {
    GEMINI,
    OPENAI,
    CLAUDE,
    OFFLINE
}

data class ProviderStatus(
    val type: ProviderType,
    val name: String,
    val isConfigured: Boolean,
    val isHealthy: Boolean,
    val lastError: String? = null
)

interface AIProvider {
    val type: ProviderType
    val name: String
    fun isConfigured(): Boolean
    suspend fun generateResponse(prompt: String, systemPrompt: String, apiKey: String): String?
}
