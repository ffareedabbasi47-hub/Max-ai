package com.example.data.api.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class ClaudeProvider(
    private val client: OkHttpClient
) : AIProvider {

    override val type: ProviderType = ProviderType.CLAUDE
    override val name: String = "Anthropic Claude 3.5 Sonnet"

    override fun isConfigured(): Boolean = true

    override suspend fun generateResponse(prompt: String, systemPrompt: String, apiKey: String): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || !apiKey.startsWith("sk-ant-")) {
            return@withContext null
        }

        try {
            val json = JSONObject().apply {
                put("model", "claude-3-5-sonnet-20241022")
                put("max_tokens", 300)
                put("system", systemPrompt)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = json.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseStr = response.body?.string()

            if (response.isSuccessful && responseStr != null) {
                val resObj = JSONObject(responseStr)
                val contentArray = resObj.optJSONArray("content")
                if (contentArray != null && contentArray.length() > 0) {
                    return@withContext contentArray.getJSONObject(0).optString("text")
                }
            }
        } catch (e: Exception) {
            // Fallback
        }
        return@withContext null
    }
}
