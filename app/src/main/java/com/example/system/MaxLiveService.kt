package com.example.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.core.LiveEvent
import com.example.core.MaxAudioStreamer
import com.example.core.MaxLiveClient
import com.example.core.MaxLiveConfig
import com.example.core.MaxLiveStateBus
import com.example.data.model.MaxState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Real, always-on background listening + continuous Gemini Live conversation — this is what
 * "background wake listening", "live", and "the voice sounds bad" were all pointing at:
 * [MaxWakeService] (the original) just detects the wake word and brings the app to the
 * foreground, where the user still talks through the old discrete SpeechRecognizer+TTS flow.
 * This service instead opens a real Gemini Live WebSocket session on wake-word detection, so
 * the whole conversation streams both ways with Gemini's own natural voice.
 *
 * Opt-in via [MaxLiveConfig.isLiveModeEnabled] (Settings toggle) — off by default, so nothing
 * changes for anyone who hasn't turned it on. Wired to the SAME [SystemControlManager] actions
 * the classic REST flow uses (see [MaxLiveClient]'s tool declarations), so both modes stay
 * behaviorally consistent — Live Mode isn't a second, different assistant.
 */
class MaxLiveService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main)

    private var liveClient: MaxLiveClient? = null
    private var audioStreamer: MaxAudioStreamer? = null
    private var isInActiveSession = false

    private lateinit var systemManager: SystemControlManager

    override fun onCreate() {
        super.onCreate()
        instance = this
        systemManager = SystemControlManager(applicationContext)
        createNotificationChannel()
        startForeground(
            MaxLiveConfig.LIVE_SERVICE_NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        )
        MaxLiveStateBus.setServiceArmed(true)
        MaxLiveStateBus.setState(MaxState.IDLE)
        startWakeWordLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
        MaxLiveStateBus.setServiceArmed(false)
        speechRecognizer?.destroy()
        endActiveSession()
    }

    // ---- Wake-word loop (same MVP pattern as MaxWakeService — restart-on-result/error) -----

    private fun startWakeWordLoop() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.joinToString(" ")?.lowercase() ?: ""
                    if (heard.contains("max")) onWakeWordDetected() else relisten()
                }
                override fun onError(error: Int) { relisten(delayMs = 400) }
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onRmsChanged(rms: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
            })
        }
        relisten()
    }

    private fun relisten(delayMs: Long = 150) {
        if (isInActiveSession) return
        mainHandler.postDelayed({
            if (!isInActiveSession) {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                }
                runCatching { speechRecognizer?.startListening(intent) }
            }
        }, delayMs)
    }

    private fun onWakeWordDetected() {
        speechRecognizer?.stopListening()
        scope.launch { MaxLiveStateBus.emit(LiveEvent.WakeWordDetected) }
        startActiveSession()
    }

    // ---- Live session --------------------------------------------------------------------

    private fun startActiveSession() {
        if (!MaxLiveConfig.hasApiKey(this)) {
            scope.launch {
                MaxLiveStateBus.setState(MaxState.ERROR)
                MaxLiveStateBus.emit(LiveEvent.Error("No Gemini API key set — add one in Settings"))
            }
            mainHandler.postDelayed({ if (!isInActiveSession) MaxLiveStateBus.setState(MaxState.IDLE) }, 2500)
            relisten()
            return
        }

        isInActiveSession = true
        MaxLiveStateBus.setState(MaxState.LISTENING)

        audioStreamer = MaxAudioStreamer(onAudioChunkCaptured = { chunk -> liveClient?.sendAudioChunk(chunk) })

        liveClient = MaxLiveClient(
            wsUrl = MaxLiveConfig.liveWsUrl(this),
            systemInstructionText = "You are MAX, a sharp and capable AI agent running on the " +
                "user's phone in real-time voice mode. Address the user as 'Boss' or 'Sir' " +
                "occasionally, keep replies short (1-2 sentences), and use your tools for any " +
                "phone action rather than just describing what you'd do. Speech is transcribed " +
                "by an on-device recognizer that sometimes mishears — make your best-guess tool " +
                "call rather than asking the user to repeat themselves.",
            onAudioReceived = { pcm -> audioStreamer?.playChunk(pcm) },
            onToolCall = { name, id, args -> handleToolCall(name, id, args) },
            onSessionEnded = { if (isInActiveSession) endActiveSession() },
            onInterrupted = { audioStreamer?.stopPlayback() }
        ).also { it.connect() }

        audioStreamer?.startCapture()
    }

    fun endActiveSession() {
        audioStreamer?.release()
        audioStreamer = null
        liveClient?.disconnect()
        liveClient = null
        isInActiveSession = false
        MaxLiveStateBus.setState(MaxState.IDLE)
        relisten()
    }

    // ---- Tool dispatch — same SystemControlManager actions as the classic REST flow --------

    private fun handleToolCall(name: String, id: String?, args: JSONObject) {
        MaxLiveStateBus.setState(MaxState.EXECUTING)
        val result = JSONObject()
        try {
            when (name) {
                "open_app" -> result.put("message", systemManager.openAppByName(args.optString("app_name")))
                "toggle_setting" -> result.put("message", systemManager.toggleSystemSetting(args.optString("setting")))
                "send_whatsapp_message" -> result.put(
                    "message",
                    systemManager.sendWhatsAppMessage(args.optString("contact_name"), args.optString("message"))
                )
                "make_call" -> result.put("message", systemManager.makeCall(args.optString("contact_name")))
                "draft_email" -> result.put(
                    "message",
                    systemManager.draftEmail(args.optString("recipient"), args.optString("body"))
                )
                "get_weather" -> {
                    val city = args.optString("city")
                    val weather = com.example.core.WeatherTask.getWeather(city)
                    result.put(
                        "message",
                        if (weather != null) {
                            val desc = com.example.core.WeatherTask.describeCode(weather.weatherCode)
                            "${weather.resolvedLocationName}: ${weather.temperatureCelsius}\u00b0C, $desc"
                        } else "Couldn't find weather for '$city'"
                    )
                }
                "youtube_search" -> {
                    val opened = systemManager.openYouTubeSearch(args.optString("query"))
                    result.put("message", if (opened) "Opened YouTube" else "Couldn't open YouTube")
                }
                "web_search" -> {
                    val opened = systemManager.openWebSearch(args.optString("query"))
                    result.put("message", if (opened) "Opened browser search" else "Couldn't open browser")
                }
                "end_conversation" -> {
                    liveClient?.sendToolResponse("end_conversation", id, JSONObject().put("status", "ended"))
                    endActiveSession()
                    return
                }
                else -> result.put("message", "Unsupported action")
            }
            result.put("status", "done")
        } catch (e: Exception) {
            result.put("status", "error").put("message", e.message ?: "Action failed")
        }
        MaxLiveStateBus.setState(MaxState.LISTENING)
        liveClient?.sendToolResponse(name, id, result)
    }

    // ---- Notification ----------------------------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MaxLiveConfig.LIVE_SERVICE_NOTIFICATION_CHANNEL,
                "MAX Live Voice", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Keeps MAX listening for the wake word in Live Mode" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, MaxLiveConfig.LIVE_SERVICE_NOTIFICATION_CHANNEL)
            .setContentTitle("MAX Live is listening")
            .setContentText("Say \"MAX\" any time to start a live conversation")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        @Volatile
        private var instance: MaxLiveService? = null

        fun isRunning(): Boolean = instance != null
    }
}
