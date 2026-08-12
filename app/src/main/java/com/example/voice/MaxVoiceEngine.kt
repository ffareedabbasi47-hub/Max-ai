package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class MaxVoiceEngine(
    private val context: Context,
    private val onUtteranceFinished: () -> Unit = {}
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _speechRecognizedText = MutableStateFlow("")
    val speechRecognizedText: StateFlow<String> = _speechRecognizedText

    private val _voicePitch = MutableStateFlow(0.88f) // Masculine articulate JARVIS tone
    private val _voiceRate = MutableStateFlow(1.02f)  // Natural speech cadence

    private val _selectedLanguage = MutableStateFlow("AUTO") // "hi_IN", "en_IN", "en_US", "AUTO"
    val selectedLanguage: StateFlow<String> = _selectedLanguage

    init {
        tts = TextToSpeech(context, this)
        initSpeechRecognizer()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.apply {
                applySelectedLanguage()
                setPitch(_voicePitch.value)
                setSpeechRate(_voiceRate.value)
                setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                        onUtteranceFinished()
                    }

                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }
                })
            }
        }
    }

    fun setLanguagePreference(langCode: String) {
        _selectedLanguage.value = langCode
        applySelectedLanguage()
    }

    private fun applySelectedLanguage() {
        val ttsEngine = tts ?: return
        val targetLocale = when (_selectedLanguage.value) {
            "hi_IN" -> Locale("hi", "IN")
            "en_IN" -> Locale("en", "IN")
            "en_US" -> Locale.US
            else -> { // AUTO mode: Prefer hi_IN if installed, fallback to en_IN then Locale.US
                val hiLocale = Locale("hi", "IN")
                val hiRes = try { ttsEngine.isLanguageAvailable(hiLocale) } catch (e: Exception) { TextToSpeech.LANG_NOT_SUPPORTED }
                if (hiRes >= TextToSpeech.LANG_AVAILABLE) {
                    hiLocale
                } else {
                    val enInLocale = Locale("en", "IN")
                    val enInRes = try { ttsEngine.isLanguageAvailable(enInLocale) } catch (e: Exception) { TextToSpeech.LANG_NOT_SUPPORTED }
                    if (enInRes >= TextToSpeech.LANG_AVAILABLE) {
                        enInLocale
                    } else {
                        Locale.US
                    }
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

    fun speak(text: String) {
        if (text.isBlank()) return
        stopListening()
        _isSpeaking.value = true
        tts?.stop() // Flush previous audio immediately
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "MAX_UTTERANCE_${System.currentTimeMillis()}")
    }


    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            // ignore
        }
        _isSpeaking.value = false
    }


    private fun initSpeechRecognizer() {
        try {
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            mainHandler.post {
                try {
                    if (SpeechRecognizer.isRecognitionAvailable(context)) {
                        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                            setRecognitionListener(object : RecognitionListener {
                                override fun onReadyForSpeech(params: Bundle?) {
                                    _isListening.value = true
                                }

                                override fun onBeginningOfSpeech() {}
                                override fun onRmsChanged(rmsdB: Float) {}
                                override fun onBufferReceived(buffer: ByteArray?) {}

                                override fun onEndOfSpeech() {
                                    _isListening.value = false
                                }

                                override fun onError(error: Int) {
                                    _isListening.value = false
                                }

                                override fun onResults(results: Bundle?) {
                                    _isListening.value = false
                                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                    if (!matches.isNullOrEmpty()) {
                                        _speechRecognizedText.value = matches[0]
                                    }
                                }

                                override fun onPartialResults(partialResults: Bundle?) {
                                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                    if (!matches.isNullOrEmpty()) {
                                        _speechRecognizedText.value = matches[0]
                                    }
                                }

                                override fun onEvent(eventType: Int, params: Bundle?) {}
                            })
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startListening() {
        stopSpeaking()
        _speechRecognizedText.value = ""
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "MAX is listening, Sir...")
        }
        try {
            speechRecognizer?.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            _isListening.value = false
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            // ignore
        }
        _isListening.value = false
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
    }
}
