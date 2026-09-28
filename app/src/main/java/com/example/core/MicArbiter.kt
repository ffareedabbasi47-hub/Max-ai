package com.example.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Who currently holds MAX's microphone. Exactly one owner at a time. */
enum class MicOwner {
    NONE,
    /** Low-power background wake-word detector ([com.example.system.MaxWakeService]). */
    WAKE,
    /** Foreground push-to-talk / post-wake speech-to-text ([com.example.voice.MaxVoiceEngine]). */
    ASSISTANT,
    /** Streaming Gemini Live session ([com.example.system.MaxLiveService]). */
    LIVE
}

/**
 * Single source of truth for microphone ownership.
 *
 * Root cause this fixes: MaxWakeService, MaxLiveService and MaxVoiceEngine each ran their own
 * SpeechRecognizer / AudioRecord with no knowledge of each other, so they kept stealing the mic
 * from one another (the "mic turns off ~1 second after wake" bug).
 *
 * Rules:
 *  - The user-facing owners (ASSISTANT, LIVE) may pre-empt WAKE, because wake detection is a
 *    background convenience and must yield the moment a real session starts.
 *  - WAKE can never pre-empt anything, and ASSISTANT/LIVE never pre-empt each other.
 *  - Whoever loses the mic must observe [owner] and stop its own capture.
 *  - An owner only releases what it owns; stray releases are ignored.
 */
object MicArbiter {
    private val _owner = MutableStateFlow(MicOwner.NONE)
    val owner: StateFlow<MicOwner> = _owner.asStateFlow()

    private val _speaking = MutableStateFlow(false)

    /**
     * True while MAX's own voice (TTS) is playing. The wake detector MUST stay off the mic during
     * this time: otherwise it hears MAX saying "Max"/"Boss..." through the speaker, treats it as a
     * wake word, cuts MAX's sentence off and starts over. That was the self-trigger loop.
     */
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    fun setSpeaking(value: Boolean) {
        _speaking.value = value
    }

    @Synchronized
    fun acquire(who: MicOwner): Boolean {
        require(who != MicOwner.NONE) { "Use release() to give the microphone up" }
        val current = _owner.value
        return when {
            current == MicOwner.NONE || current == who -> { _owner.value = who; true }
            current == MicOwner.WAKE -> { _owner.value = who; true } // WAKE yields to real sessions
            else -> false
        }
    }

    @Synchronized
    fun release(who: MicOwner) {
        if (_owner.value == who) _owner.value = MicOwner.NONE
    }

    /** Test hook only. */
    @Synchronized
    internal fun resetForTest() {
        _owner.value = MicOwner.NONE
        _speaking.value = false
    }
}
