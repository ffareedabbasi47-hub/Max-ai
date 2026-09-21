package com.example.system

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.core.MicArbiter
import com.example.core.MicOwner
import com.example.core.WakePhrase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Background wake-word listener (interim implementation).
 *
 * IMPORTANT LIMITATION — read before trusting this as "low power": this still uses Android's
 * SpeechRecognizer in a listen/restart loop, which is NOT an on-device low-power keyword spotter
 * and may send audio to the system speech service. It is being replaced by a dedicated
 * WakeWordEngine (roadmap step 3). What THIS version fixes:
 *  - It no longer fires on partial results or on "maximum"/"Maxwell" (whole-word match, finals only).
 *  - It no longer schedules a restart from onEndOfSpeech AND onResults (that overlapped sessions).
 *  - It uses a fresh recognizer per cycle with a session id, so stale callbacks are ignored.
 *  - It claims the mic through [MicArbiter] and STOPS listening whenever the assistant (or Live)
 *    owns the mic, resuming only after they release it. Previously this loop kept restarting
 *    under the activity's own recognizer and stole the mic away.
 *  - After a wake it stays quiet for a cooldown so the acknowledgement isn't re-detected.
 */
class MaxWakeService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var arbiterJob: Job? = null

    private var isListeningServiceRunning = false
    private var isRestartScheduled = false
    private var sessionId = 0
    private var cooldownUntilMs = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Android 12+ can refuse to start a microphone foreground service from the background.
            // Without the foreground service we cannot legally hold the mic, so stop cleanly.
            Log.e(TAG, "Could not enter foreground; stopping wake service", e)
            stopSelf()
            return START_NOT_STICKY
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO not granted; wake service stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        isListeningServiceRunning = true
        if (arbiterJob == null) {
            // Emits the current owner immediately: NONE -> start listening; ASSISTANT/LIVE -> stay quiet.
            arbiterJob = serviceScope.launch {
                MicArbiter.owner.collect { owner ->
                    when (owner) {
                        MicOwner.ASSISTANT, MicOwner.LIVE -> stopRecognizer()
                        MicOwner.NONE -> if (isListeningServiceRunning) {
                            val wait = (cooldownUntilMs - SystemClock.elapsedRealtime()).coerceAtLeast(400L)
                            scheduleRestartListening(wait)
                        }
                        MicOwner.WAKE -> Unit
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun listenInternal() {
        if (!isListeningServiceRunning) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "Speech recognition unavailable; wake service stopping")
            stopSelf()
            return
        }
        // If the assistant or Live owns the mic we simply don't listen; the arbiter observer
        // above restarts us when it is released.
        if (!MicArbiter.acquire(MicOwner.WAKE)) return

        destroyRecognizer()
        val session = ++sessionId
        val recognizer = try {
            SpeechRecognizer.createSpeechRecognizer(this)
        } catch (e: Exception) {
            Log.e(TAG, "createSpeechRecognizer failed", e)
            null
        }
        if (recognizer == null) {
            MicArbiter.release(MicOwner.WAKE)
            scheduleRestartListening(3000)
            return
        }
        recognizer.setRecognitionListener(listenerFor(session))
        speechRecognizer = recognizer

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        try {
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startListening failed", e)
            stopRecognizer()
            scheduleRestartListening(2000)
        }
    }

    private fun listenerFor(session: Int) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {} // restart is scheduled from onResults/onError only
        override fun onPartialResults(partialResults: Bundle?) {} // finals only — partials caused repeat triggers
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onResults(results: Bundle?) {
            if (session != sessionId) return
            if (heardWakePhrase(results)) {
                onWakeWordDetected()
            } else {
                stopRecognizer()
                scheduleRestartListening(300)
            }
        }

        override fun onError(error: Int) {
            if (session != sessionId) return
            Log.d(TAG, "Wake recognizer error code: $error")
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                stopSelf()
                return
            }
            stopRecognizer()
            val delay = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 300L
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 2000L
                else -> 1000L
            }
            scheduleRestartListening(delay)
        }
    }

    private fun heardWakePhrase(results: Bundle?): Boolean {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return false
        return matches.any { WakePhrase.containsWake(it) }
    }

    private fun onWakeWordDetected() {
        Log.i(TAG, "Wake phrase detected")
        // Go quiet: release the mic and stay silent for a cooldown, so the assistant can open its
        // own session without us re-grabbing the mic or re-detecting the acknowledgement.
        cooldownUntilMs = SystemClock.elapsedRealtime() + WAKE_COOLDOWN_MS
        stopRecognizer()

        // Send Broadcast with explicit package name for RECEIVER_NOT_EXPORTED compatibility
        sendBroadcast(Intent(ACTION_WAKE_WORD_DETECTED).setPackage(packageName))

        // Launch / bring MainActivity to foreground. NOTE: Android 10+ blocks activity starts from
        // the background, so this can be silently ignored when MAX isn't visible — see roadmap.
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("WAKE_WORD_TRIGGERED", true)
        }
        try {
            startActivity(activityIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not start MainActivity on wake", e)
        }
    }

    private fun scheduleRestartListening(delayMs: Long) {
        if (!isListeningServiceRunning || isRestartScheduled) return
        isRestartScheduled = true
        mainHandler.postDelayed({
            isRestartScheduled = false
            listenInternal()
        }, delayMs)
    }

    /** Stops our recognizer, invalidates its callbacks, cancels pending restarts, releases the mic. */
    private fun stopRecognizer() {
        sessionId++
        mainHandler.removeCallbacksAndMessages(null)
        isRestartScheduled = false
        destroyRecognizer()
        MicArbiter.release(MicOwner.WAKE)
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying wake recognizer", e)
        }
        speechRecognizer = null
    }

    private fun buildNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MAX JARVIS Voice Core Active")
            .setContentText("Listening for 'Max / Hey Max' wake word in background...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MAX JARVIS Background Wake Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps MAX JARVIS active in background for wake word listening"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        isListeningServiceRunning = false
        arbiterJob?.cancel()
        arbiterJob = null
        serviceScope.cancel()
        stopRecognizer()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "MaxWakeService"
        const val CHANNEL_ID = "max_jarvis_wake_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_WAKE_WORD_DETECTED = "com.example.MAX_WAKE_WORD_EVENT"
        private const val WAKE_COOLDOWN_MS = 6000L
    }
}
