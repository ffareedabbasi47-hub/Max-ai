package com.example.data.api.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * NVIDIA's NIM API (build.nvidia.com) exposes an OpenAI-compatible `/chat/completions`
 * endpoint — same request/response JSON shape as [OpenAIProvider], just a different base URL,
 * default model, and key prefix (`nvapi-` instead of `sk-`). Gives access to hosted models like
 * Llama 3.1 without needing an OpenAI or Anthropic account.
 */
class NVIDIAProvider(
    private val client: OkHttpClient
) : AIProvider {

    override val type: ProviderType = ProviderType.NVIDIA
    override val name: String = "NVIDIA NIM (Llama 3.1)"

    // Reasonable default hosted on NIM; swap if a different model is preferred — see
    // https://build.nvidia.com for the current catalog of available model names.
    private val model = "meta/llama-3.1-70b-instruct"

    override fun isConfigured(): Boolean = true

    override suspend fun generateResponse(prompt: String, systemPrompt: String, apiKey: String): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || !apiKey.startsWith("nvapi-")) {
            return@withContext null
        }

        try {
            val json = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("max_tokens", 300)
                put("temperature", 0.6)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = json.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url("https://integrate.api.nvidia.com/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseStr = response.body?.string()

            if (response.isSuccessful && responseStr != null) {
                val resObj = JSONObject(responseStr)
                val choices = resObj.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val message = choices.getJSONObject(0).optJSONObject("message")
                    return@withContext message?.optString("content")
                }
            }
        } catch (e: Exception) {
            // Fallback — MultiBrainManager moves on to the next provider/local fallback.
        }
        return@withContext null
    }
}
