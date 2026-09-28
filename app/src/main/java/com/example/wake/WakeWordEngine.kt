package com.example.wake

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Detects "Hey Max" / "Max" ENTIRELY ON THE PHONE. No audio ever leaves the device.
 * Separate from the full assistant pipeline (STT / Live), which only starts after [onWake] fires.
 */
interface WakeWordEngine {
    /** Loads whatever the engine needs (model). Heavy — call off the main thread. Returns false on failure. */
    fun load(): Boolean

    /**
     * Opens the microphone and starts listening. [onWake] receives the phrase that was heard
     * ("max", "hey max", "hello max"); both callbacks run on a background thread.
     */
    fun startListening(onWake: (String) -> Unit, onError: (String) -> Unit)

    /** Stops listening and RELEASES the microphone. Safe to call repeatedly. */
    fun stopListening()

    /** Frees the model. The engine can't be used afterwards. */
    fun close()
}

/** User-visible status of the wake system (for UI / notification). */
object WakeStatus {
    private val _text = MutableStateFlow("Wake word band hai")
    val text: StateFlow<String> = _text.asStateFlow()
    fun set(value: String) { _text.value = value }
}
