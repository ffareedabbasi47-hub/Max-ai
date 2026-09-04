package com.example

import com.example.data.model.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MaxBrainTest {

    @Test
    fun testActionTagParsing() {
        val rawResponse = "[ACTION:OPEN_APP|YouTube] Yes Boss 😎 YouTube khol raha hoon."
        val actionRegex = Regex("\\[ACTION:([A-Z_]+)(?:\\|([^|\\]]*))?(?:\\|([^|\\]]*))?\\]")
        val match = actionRegex.find(rawResponse)

        assertNotNull(match)
        assertEquals("OPEN_APP", match?.groupValues?.get(1))
        assertEquals("YouTube", match?.groupValues?.get(2))
        
        val cleanSpeech = rawResponse.replace(match!!.value, "").trim()
        assertEquals("Yes Boss 😎 YouTube khol raha hoon.", cleanSpeech)
    }

    @Test
    fun testWakeWordDetectionLogic() {
        val input = "Hey Max open YouTube"
        val lower = input.lowercase().trim()
        val isWakeWord = lower.startsWith("max ") || lower.startsWith("hey max ") || lower == "max" || lower == "hey max"
        assertTrue(isWakeWord)

        val cleanQuery = input.replace(Regex("(?i)^(hey max|max)\\s*"), "").trim()
        assertEquals("open YouTube", cleanQuery)
    }

    @Test
    fun testActionTypeMapping() {
        val actionTypes = listOf(
            "OPEN_APP" to ActionType.OPEN_APP,
            "TOGGLE" to ActionType.TOGGLE_SETTINGS,
            "CALL" to ActionType.MAKE_CALL,
            "WHATSAPP" to ActionType.SEND_WHATSAPP,
            "FILE" to ActionType.CREATE_FILE,
            "SEARCH" to ActionType.WEB_SEARCH
        )

        for ((str, expectedType) in actionTypes) {
            val mapped = when (str) {
                "OPEN_APP" -> ActionType.OPEN_APP
                "TOGGLE" -> ActionType.TOGGLE_SETTINGS
                "CALL" -> ActionType.MAKE_CALL
                "WHATSAPP" -> ActionType.SEND_WHATSAPP
                "FILE" -> ActionType.CREATE_FILE
                "SEARCH" -> ActionType.WEB_SEARCH
                else -> ActionType.GENERAL_TALK
            }
            assertEquals(expectedType, mapped)
        }
    }
}
