package com.example.data.api

import android.content.Context
import com.example.data.model.ParsedMaxAction

class GeminiBrain(context: Context) {
    private val multiBrainManager = MultiBrainManager(context)

    suspend fun processUserPrompt(prompt: String): ParsedMaxAction {
        return multiBrainManager.processUserPrompt(prompt)
    }
}
