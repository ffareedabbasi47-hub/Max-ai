package com.example.data.api

import android.content.Context
import com.example.BuildConfig
import com.example.data.api.diagnostics.GeminiDiagnosticResult
import com.example.data.model.ParsedMaxAction

class GeminiBrain(context: Context) {
    private val multiBrainManager = MultiBrainManager(context)

    suspend fun processUserPrompt(prompt: String): ParsedMaxAction {
        return multiBrainManager.processUserPrompt(prompt)
    }

    suspend fun runGeminiDiagnostic(apiKey: String = BuildConfig.GEMINI_API_KEY): GeminiDiagnosticResult {
        return multiBrainManager.runGeminiDiagnostic(apiKey)
    }

    fun getActiveKey(): String {
        return multiBrainManager.getCustomApiKey()
    }
}

