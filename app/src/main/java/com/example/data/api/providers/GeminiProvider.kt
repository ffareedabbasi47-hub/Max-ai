package com.example.data.api.providers

import com.example.data.model.GeminiContent
import com.example.data.model.GeminiPart
import com.example.data.model.GeminiRequest
import com.example.data.model.GeminiResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GeminiProvider(
    private val client: OkHttpClient
) : AIProvider {

    override val type: ProviderType = ProviderType.GEMINI
    override val name: String = "Google Gemini AI"

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val requestAdapter = moshi.adapter(GeminiRequest::class.java)
    private val responseAdapter = moshi.adapter(GeminiResponse::class.java)

    override fun isConfigured(): Boolean = true

    override suspend fun generateResponse(prompt: String, systemPrompt: String, apiKey: String): String? = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank() || cleanKey == "MY_GEMINI_API_KEY" || cleanKey == "FALLBACK_KEY_VALID" || cleanKey == "NON_EXISTENT_KEY") {
            return@withContext null
        }

        // List of modern supported Gemini model endpoints in order of preference
        val modelsToTry = listOf(
            "gemini-3.5-flash",
            "gemini-2.5-flash-preview-12-2025",
            "gemini-1.5-flash",
            "gemini-2.0-flash"
        )

        for (model in modelsToTry) {
            try {
                val geminiRequest = GeminiRequest(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                    systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt)))
                )

                val jsonBody = requestAdapter.toJson(geminiRequest)
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = jsonBody.toRequestBody(mediaType)

                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$cleanKey"
                val request = Request.Builder().url(url).post(body).build()

                val response = client.newCall(request).execute()
                val responseStr = response.body?.string()

                if (response.isSuccessful && responseStr != null) {
                    val geminiResponse = responseAdapter.fromJson(responseStr)
                    val text = geminiResponse?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (!text.isNullOrBlank()) {
                        return@withContext text.trim()
                    }
                }
            } catch (e: Exception) {
                // Try next model if any endpoint fails
            }
        }
        return@withContext null
    }
}

