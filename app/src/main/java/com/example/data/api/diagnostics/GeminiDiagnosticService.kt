package com.example.data.api.diagnostics

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class GeminiDiagnosticResult(
    val isSuccess: Boolean,
    val statusCode: Int?,
    val statusCategory: String,
    val latencyMs: Long,
    val modelTested: String,
    val apiKeySource: String,
    val rawResponseBody: String?,
    val errorMessage: String?,
    val timestamp: Long = System.currentTimeMillis()
)

class GeminiDiagnosticService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "GeminiDiagnostic"
        const val DEFAULT_MODEL = "gemini-3.5-flash"
    }

    /**
     * Attempts a ping request to the Gemini API using the provided key or BuildConfig.GEMINI_API_KEY.
     * Logs specific HTTP status codes and error details if the request fails, rather than falling back to hardcoded responses.
     */
    suspend fun testGeminiConnectivity(
        apiKey: String = BuildConfig.GEMINI_API_KEY,
        model: String = DEFAULT_MODEL
    ): GeminiDiagnosticResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val keySource = if (cleanKey == BuildConfig.GEMINI_API_KEY.trim()) "BuildConfig.GEMINI_API_KEY" else "Custom/Configured Key"
        val startTime = System.currentTimeMillis()

        Log.d(TAG, "==================================================")
        Log.d(TAG, "Starting Gemini API Diagnostic Ping Test...")
        Log.d(TAG, "Source: $keySource | Model: $model")

        if (cleanKey.isBlank() || cleanKey == "MY_GEMINI_API_KEY" || cleanKey == "FALLBACK_KEY_VALID") {
            val errorMsg = "API Key is missing, empty, or placeholder ('$cleanKey')."
            Log.e(TAG, "[GEMINI DIAGNOSTIC FAILED] Status 401 (UNAUTHORIZED_401): $errorMsg")
            return@withContext GeminiDiagnosticResult(
                isSuccess = false,
                statusCode = 401,
                statusCategory = "UNAUTHORIZED_401",
                latencyMs = System.currentTimeMillis() - startTime,
                modelTested = model,
                apiKeySource = keySource,
                rawResponseBody = null,
                errorMessage = errorMsg
            )
        }

        val pingJsonPayload = """
            {
              "contents": [
                {
                  "parts": [
                    { "text": "ping" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = pingJsonPayload.toRequestBody(mediaType)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$cleanKey"

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        try {
            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val code = response.code
            val rawBody = response.body?.string()

            if (response.isSuccessful && !rawBody.isNullOrBlank()) {
                Log.i(TAG, "[GEMINI DIAGNOSTIC PASSED] Status HTTP $code | Latency: ${latency}ms | Model: $model")
                Log.d(TAG, "Response Body Preview: ${rawBody.take(200)}")
                return@withContext GeminiDiagnosticResult(
                    isSuccess = true,
                    statusCode = code,
                    statusCategory = "SUCCESS_200",
                    latencyMs = latency,
                    modelTested = model,
                    apiKeySource = keySource,
                    rawResponseBody = rawBody,
                    errorMessage = null
                )
            } else {
                val category = when (code) {
                    400 -> "INVALID_REQUEST_400"
                    401 -> "UNAUTHORIZED_401"
                    403 -> "FORBIDDEN_403"
                    404 -> "NOT_FOUND_404"
                    429 -> "RATE_LIMITED_429"
                    in 500..599 -> "SERVER_ERROR_$code"
                    else -> "HTTP_ERROR_$code"
                }

                val errorMsg = "HTTP $code $category - Body: ${rawBody?.take(300)}"
                Log.e(TAG, "[GEMINI DIAGNOSTIC FAILED] Status Code: $code | Category: $category")
                Log.e(TAG, "Details: $errorMsg")

                return@withContext GeminiDiagnosticResult(
                    isSuccess = false,
                    statusCode = code,
                    statusCategory = category,
                    latencyMs = latency,
                    modelTested = model,
                    apiKeySource = keySource,
                    rawResponseBody = rawBody,
                    errorMessage = errorMsg
                )
            }
        } catch (e: IOException) {
            val latency = System.currentTimeMillis() - startTime
            val errorMsg = "Network I/O Failure: ${e.localizedMessage ?: e.message}"
            Log.e(TAG, "[GEMINI DIAGNOSTIC FAILED] Network Error: $errorMsg", e)
            return@withContext GeminiDiagnosticResult(
                isSuccess = false,
                statusCode = null,
                statusCategory = "NETWORK_ERROR",
                latencyMs = latency,
                modelTested = model,
                apiKeySource = keySource,
                rawResponseBody = null,
                errorMessage = errorMsg
            )
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            val errorMsg = "Unexpected Error: ${e.localizedMessage ?: e.message}"
            Log.e(TAG, "[GEMINI DIAGNOSTIC FAILED] Exception: $errorMsg", e)
            return@withContext GeminiDiagnosticResult(
                isSuccess = false,
                statusCode = null,
                statusCategory = "UNKNOWN_EXCEPTION",
                latencyMs = latency,
                modelTested = model,
                apiKeySource = keySource,
                rawResponseBody = null,
                errorMessage = errorMsg
            )
        }
    }
}
