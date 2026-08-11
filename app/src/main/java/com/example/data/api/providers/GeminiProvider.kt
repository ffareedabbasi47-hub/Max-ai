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
import java.util.concurrent.TimeUnit

class GeminiProvider(
    private val client: OkHttpClient
) : AIProvider {

    override val type: ProviderType = ProviderType.GEMINI
    override val name: String = "Google Gemini 2.5 Flash"

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val requestAdapter = moshi.adapter(GeminiRequest::class.java)
    private val responseAdapter = moshi.adapter(GeminiResponse::class.java)

    override fun isConfigured(): Boolean = true

    override suspend fun generateResponse(prompt: String, systemPrompt: String, apiKey: String): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY" || apiKey == "FALLBACK_KEY_VALID" || apiKey == "NON_EXISTENT_KEY") {
            return@withContext null
        }

        try {
            val geminiRequest = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt)))
            )

            val jsonBody = requestAdapter.toJson(geminiRequest)
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = jsonBody.toRequestBody(mediaType)

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val request = Request.Builder().url(url).post(body).build()

            val response = client.newCall(request).execute()
            val responseStr = response.body?.string()

            if (response.isSuccessful && responseStr != null) {
                val geminiResponse = responseAdapter.fromJson(responseStr)
                return@withContext geminiResponse?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            }
        } catch (e: Exception) {
            // Error handling fallback
        }
        return@withContext null
    }
}
