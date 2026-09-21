package com.example.voice

import android.speech.SpeechRecognizer

/** Microphone/recognizer session state as it ACTUALLY is — the UI derives LISTENING from this. */
enum class MicState {
    /** No capture in progress. */
    OFF,
    /** Recognizer requested, waiting for it to report it is ready. Mic is NOT yet capturing. */
    STARTING,
    /** Recognizer reported ready — the microphone is really open. */
    LISTENING,
    /** User stopped talking; waiting for the final transcript. Mic is closed. */
    FINALIZING
}

/**
 * A user-presentable voice failure (never a raw stack trace).
 * [hard] = something is actually wrong (show ERROR state); soft = normal outcome such as "heard nothing".
 */
data class VoiceError(val code: Int, val message: String, val hard: Boolean)

object VoiceErrors {
    const val CODE_PERMISSION = -1
    const val CODE_NOT_AVAILABLE = -2
    const val CODE_MIC_BUSY = -3
    const val CODE_START_FAILED = -4
    const val CODE_START_TIMEOUT = -5

    fun permission() = VoiceError(CODE_PERMISSION, "Microphone permission chahiye. Settings me allow kar do.", true)
    fun notAvailable() = VoiceError(CODE_NOT_AVAILABLE, "Is phone par speech recognition available nahi hai.", true)
    fun micBusy() = VoiceError(CODE_MIC_BUSY, "Mic abhi kisi aur MAX feature ke paas hai. Thodi der baad try karo.", false)
    fun startFailed() = VoiceError(CODE_START_FAILED, "Speech recognizer start nahi ho paaya.", true)
    fun startTimeout() = VoiceError(CODE_START_TIMEOUT, "Mic ne respond nahi kiya. Dobara try karo.", true)

    fun fromRecognizerCode(code: Int): VoiceError = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            VoiceError(code, "Kuch sunai nahi diya. Dobara boliye.", false)
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            VoiceError(code, "Speech service se connection nahi ho paa raha. Internet check karo.", true)
        SpeechRecognizer.ERROR_AUDIO ->
            VoiceError(code, "Mic se audio nahi mil raha. Ho sakta hai kisi aur app ne mic pakda ho.", true)
        SpeechRecognizer.ERROR_SERVER ->
            VoiceError(code, "Speech service ne error diya. Thodi der baad try karo.", true)
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            VoiceError(code, "Speech recognizer busy hai.", true)
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> permission()
        else -> VoiceError(code, "Speech recognition fail hui (code $code).", true)
    }
}
