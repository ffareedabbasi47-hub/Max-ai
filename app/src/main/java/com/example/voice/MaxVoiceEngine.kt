package com.example.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import com.example.core.MicArbiter
import com.example.core.MicOwner
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * MAX's foreground speech-to-text + text-to-speech engine.
 *
 * Root causes fixed here (all found by reading the previous version):
 *  1. Partial results used to be written into the SAME flow as final results, and the ViewModel
 *     executed a prompt for every value on that flow. So the first spoken word started a request,
 *     and the reply's `speak()` then called `stopListening()` — the mic died ~1s after you began
 *     talking. Now partials go to [partialText] (display only) and ONLY the final transcript is
 *     emitted on [finalResults].
 *  2. `isListening` was set true right after calling startListening(), even when the recognizer
 *     did not exist yet (it was created asynchronously) — so the UI could say LISTENING with the
 *     mic off. Now [micState] becomes LISTENING only when the recognizer reports
 *     `onReadyForSpeech`, and returns to OFF on every end/error/cancel path.
 *  3. Recognizer errors were swallowed. They are now surfaced on [errors] as user-readable text.
 *  4. Each listening session gets a FRESH SpeechRecognizer and a session id, so late callbacks
 *     from a cancelled session can never flip the state of the next one.
 *  5. The mic is claimed through [MicArbiter] so the wake service and Live mode cannot fight it.
 *
 * All recognizer calls run on the main thread (SpeechRecognizer requirement).
 */
class MaxVoiceEngine(
    context: Context,
    private val onUtteranceFinished: () -> Unit = {}
) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false

    private var speechRecognizer: SpeechRecognizer? = null
    private var activeSession = 0
    private var busyRetryUsed = false
    private var watchdog: Runnable? = null

    @Volatile private var currentUtteranceId: String? = null
    @Volatile private var listenAfterSpeech = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _micState = MutableStateFlow(MicState.OFF)
    /** The real microphone/recognizer state. */
    val micState: StateFlow<MicState> = _micState.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    /** True ONLY while the recognizer has reported it is ready and capturing. */
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialText = MutableStateFlow("")
    /** Live partial transcript — for display only, never acted on. */
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _finalResults = MutableSharedFlow<String>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** One emission per completed utterance (the final transcript). */
    val finalResults: SharedFlow<String> = _finalResults.asSharedFlow()

    private val _errors = MutableSharedFlow<VoiceError>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val errors: SharedFlow<VoiceError> = _errors.asSharedFlow()

    private val _voicePitch = MutableStateFlow(0.88f) // Masculine articulate JARVIS tone
    private val _voiceRate = MutableStateFlow(1.02f)  // Natural speech cadence

    private val _selectedLanguage = MutableStateFlow("AUTO") // "hi_IN", "en_IN", "en_US", "AUTO"
    val selectedLanguage: StateFlow<String> = _selectedLanguage

    init {
        tts = TextToSpeech(appContext, this)
    }

    // ---------------------------------------------------------------------------------------
    // Text-to-speech
    // ---------------------------------------------------------------------------------------

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            ttsReady = false
            return
        }
        tts?.apply {
            applySelectedLanguage()
            setPitch(_voicePitch.value)
            setSpeechRate(_voiceRate.value)
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    if (utteranceId != null && utteranceId == currentUtteranceId) _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) = finishUtterance(utteranceId)

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = finishUtterance(utteranceId)

                override fun onError(utteranceId: String?, errorCode: Int) = finishUtterance(utteranceId)

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    // Only react if the engine itself stopped OUR current utterance. Our own
                    // stopSpeaking()/flush clears currentUtteranceId first, so those are ignored.
                    if (utteranceId != null && utteranceId == currentUtteranceId) {
                        currentUtteranceId = null
                        _isSpeaking.value = false
                    }
                }
            })
        }
        ttsReady = true
    }

    /** Called from TTS binder threads. Stale utterances (already replaced/stopped) are ignored. */
    private fun finishUtterance(utteranceId: String?) {
        if (utteranceId == null || utteranceId != currentUtteranceId) return
        currentUtteranceId = null
        _isSpeaking.value = false
        onUtteranceFinished()
        if (listenAfterSpeech) {
            listenAfterSpeech = false
            startListening()
        }
    }

    fun setLanguagePreference(langCode: String) {
        _selectedLanguage.value = langCode
        applySelectedLanguage()
    }

    private fun applySelectedLanguage() {
        val ttsEngine = tts ?: return
        val targetLocale = when (_selectedLanguage.value) {
            "hi_IN" -> Locale.forLanguageTag("hi-IN")
            "en_IN" -> Locale.forLanguageTag("en-IN")
            "en_US" -> Locale.US
            else -> { // AUTO mode: Prefer hi_IN if installed, fallback to en_IN then Locale.US
                val hiLocale = Locale.forLanguageTag("hi-IN")
                val hiRes = try { ttsEngine.isLanguageAvailable(hiLocale) } catch (e: Exception) { TextToSpeech.LANG_NOT_SUPPORTED }
                if (hiRes >= TextToSpeech.LANG_AVAILABLE) {
                    hiLocale
                } else {
                    val enInLocale = Locale.forLanguageTag("en-IN")
                    val enInRes = try { ttsEngine.isLanguageAvailable(enInLocale) } catch (e: Exception) { TextToSpeech.LANG_NOT_SUPPORTED }
                    if (enInRes >= TextToSpeech.LANG_AVAILABLE) enInLocale else Locale.US
                }
            }
        }

        try {
            val result = ttsEngine.setLanguage(targetLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                ttsEngine.language = Locale.US
            }
        } catch (e: Exception) {
            try { ttsEngine.language = Locale.US } catch (ex: Exception) { ex.printStackTrace() }
        }
    }

    fun setVoiceParams(pitch: Float, rate: Float) {
        _voicePitch.value = pitch
        _voiceRate.value = rate
        tts?.setPitch(pitch)
        tts?.setSpeechRate(rate)
    }

    /**
     * Speak [text]. Listening is always stopped first so the recognizer never transcribes MAX's own
     * voice. With [thenListen] = true the mic is reopened automatically when the utterance
     * finishes (used for the wake acknowledgement), instead of guessing with a fixed delay.
     */
    fun speak(text: String, thenListen: Boolean = false) {
        if (text.isBlank()) return
        mainHandler.post {
            cancelListeningInternal()
            val engine = tts
            if (engine == null || !ttsReady) {
                // TTS unavailable: don't hang the flow — go straight to listening if requested.
                currentUtteranceId = null
                listenAfterSpeech = false
                _isSpeaking.value = false
                if (thenListen) startListeningInternal()
                return@post
            }
            val id = "MAX_UTTERANCE_${System.nanoTime()}"
            currentUtteranceId = id
            listenAfterSpeech = thenListen
            _isSpeaking.value = true
            val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            if (result != TextToSpeech.SUCCESS) {
                currentUtteranceId = null
                listenAfterSpeech = false
                _isSpeaking.value = false
                if (thenListen) startListeningInternal()
            }
        }
    }

    fun stopSpeaking() {
        mainHandler.post { stopSpeakingInternal() }
    }

    private fun stopSpeakingInternal() {
        currentUtteranceId = null // clear first so the engine's onStop is ignored
        listenAfterSpeech = false
        try {
            tts?.stop()
        } catch (e: Exception) {
            // ignore
        }
        _isSpeaking.value = false
    }

    // ---------------------------------------------------------------------------------------
    // Speech-to-text
    // ---------------------------------------------------------------------------------------

    fun startListening() {
        mainHandler.post { startListeningInternal() }
    }

    fun stopListening() {
        mainHandler.post { cancelListeningInternal() }
    }

    private fun sttLanguageTag(): String = when (_selectedLanguage.value) {
        "hi_IN" -> "hi-IN"
        "en_IN" -> "en-IN"
        "en_US" -> "en-US"
        else -> Locale.getDefault().toLanguageTag()
    }

    private fun buildRecognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, sttLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            // Only hints — recognizers may ignore them. The real fix for "stops after 1 second"
            // is that partial results no longer trigger a request (see class doc).
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        }

    private fun startListeningInternal() {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            emitError(VoiceErrors.permission())
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            emitError(VoiceErrors.notAvailable())
            return
        }
        // Claim the mic. Fails only if a Live session owns it; the wake detector yields to us.
        if (!MicArbiter.acquire(MicOwner.ASSISTANT)) {
            emitError(VoiceErrors.micBusy())
            return
        }

        stopSpeakingInternal() // never listen over our own voice
        destroyRecognizer()
        _partialText.value = ""

        val session = ++activeSession
        val recognizer = try {
            SpeechRecognizer.createSpeechRecognizer(appContext)
        } catch (e: Exception) {
            null
        }
        if (recognizer == null) {
            setMic(MicState.OFF)
            emitError(VoiceErrors.startFailed())
            return
        }
        recognizer.setRecognitionListener(listenerFor(session))
        speechRecognizer = recognizer

        setMic(MicState.STARTING)
        armWatchdog(session)
        try {
            recognizer.startListening(buildRecognizerIntent())
        } catch (e: Exception) {
            failSession(session, VoiceErrors.startFailed())
        }
    }

    private fun listenerFor(session: Int) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (session != activeSession) return
            cancelWatchdog()
            busyRetryUsed = false
            setMic(MicState.LISTENING) // the mic is genuinely open only from this point
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            if (session != activeSession) return
            if (_micState.value == MicState.LISTENING) setMic(MicState.FINALIZING)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (session != activeSession) return
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!partial.isNullOrBlank()) _partialText.value = partial
        }

        override fun onResults(results: Bundle?) {
            if (session != activeSession) return
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.trim().orEmpty()
            endSession(session)
            if (text.isEmpty()) {
                emitError(VoiceErrors.fromRecognizerCode(SpeechRecognizer.ERROR_NO_MATCH))
            } else {
                _finalResults.tryEmit(text)
            }
        }

        override fun onError(error: Int) {
            if (session != activeSession) return
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY && !busyRetryUsed) {
                // The system recognizer was still releasing a previous session. Retry once.
                busyRetryUsed = true
                endSession(session)
                mainHandler.postDelayed({ startListeningInternal() }, 500)
                return
            }
            endSession(session)
            emitError(VoiceErrors.fromRecognizerCode(error))
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /** Normal end of a session (result or error): mic is closed, recognizer released. */
    private fun endSession(session: Int) {
        if (session != activeSession) return
        activeSession++ // invalidate any further callbacks from this recognizer
        cancelWatchdog()
        val finished = speechRecognizer
        speechRecognizer = null
        setMic(MicState.OFF)
        // Never destroy a recognizer from inside its own callback.
        if (finished != null) mainHandler.post { runCatching { finished.destroy() } }
    }

    private fun failSession(session: Int, error: VoiceError) {
        if (session != activeSession) return
        activeSession++
        cancelWatchdog()
        destroyRecognizer()
        setMic(MicState.OFF)
        emitError(error)
    }

    private fun cancelListeningInternal() {
        activeSession++ // invalidate callbacks first
        cancelWatchdog()
        try {
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            // ignore
        }
        destroyRecognizer()
        setMic(MicState.OFF)
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // ignore
        }
        speechRecognizer = null
    }

    /** If the recognizer never reports ready (hung service), don't leave the UI waiting forever. */
    private fun armWatchdog(session: Int) {
        cancelWatchdog()
        val r = Runnable {
            if (session == activeSession && _micState.value == MicState.STARTING) {
                failSession(session, VoiceErrors.startTimeout())
            }
        }
        watchdog = r
        mainHandler.postDelayed(r, START_TIMEOUT_MS)
    }

    private fun cancelWatchdog() {
        watchdog?.let { mainHandler.removeCallbacks(it) }
        watchdog = null
    }

    private fun setMic(state: MicState) {
        _micState.value = state
        _isListening.value = state == MicState.LISTENING
        if (state == MicState.OFF) MicArbiter.release(MicOwner.ASSISTANT)
    }

    private fun emitError(error: VoiceError) {
        _errors.tryEmit(error)
    }

    fun release() {
        mainHandler.removeCallbacksAndMessages(null)
        activeSession++
        destroyRecognizer()
        setMic(MicState.OFF)
        currentUtteranceId = null
        listenAfterSpeech = false
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    companion object {
        private const val START_TIMEOUT_MS = 5000L
    }
}
