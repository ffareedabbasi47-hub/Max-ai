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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import java.util.Locale

/**
 * Fully local / offline wake word listener — no account, no API key, no payment, no internet
 * call. Uses Android's own on-device speech engine (the same on-device model Gboard voice
 * typing and "offline dictation" use) instead of a paid third-party hotword SDK.
 *
 * The key change vs before is `EXTRA_PREFER_OFFLINE = true` below: this tells
 * SpeechRecognizer to use the phone's on-device model instead of sending audio to Google's
 * cloud servers. Everything else (the always-restart loop listening for "max"/"hey max" in
 * the transcript) works the same as before, just entirely on-device.
 *
 * ONE-TIME FREE SETUP (device settings only, no signup, no cost):
 *   Settings -> System -> Languages & input -> On-device speech recognition (or under
 *   "Voice input" / Gboard settings depending on phone) -> download the English offline pack.
 *   This ships free with every Android phone with Google apps; it just needs to be downloaded
 *   once over Wi-Fi. Without it, some phones will silently ignore EXTRA_PREFER_OFFLINE and
 *   fall back to the cloud recognizer, which still works, just isn't fully offline.
 */
class MaxWakeService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListeningServiceRunning = false
    private var isRestartScheduled = false

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
            e.printStackTrace()
        }

        isListeningServiceRunning = true
        startBackgroundWakeListening()
        return START_STICKY
    }

    private fun startBackgroundWakeListening() {
        mainHandler.post {
            try {
                if (speechRecognizer == null && SpeechRecognizer.isRecognitionAvailable(this)) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                        setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {}
                            override fun onBeginningOfSpeech() {}
                            override fun onRmsChanged(rmsdB: Float) {}
                            override fun onBufferReceived(buffer: ByteArray?) {}

                            override fun onEndOfSpeech() {
                                scheduleRestartListening(300)
                            }

                            override fun onError(error: Int) {
                                Log.d(TAG, "Wake listening error code: $error")
                                scheduleRestartListening(800)
                            }

                            override fun onResults(results: Bundle?) {
                                handleSpeechResults(results)
                                scheduleRestartListening(300)
                            }

                            override fun onPartialResults(partialResults: Bundle?) {
                                handleSpeechResults(partialResults)
                            }

                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })
                    }
                }
                listenInternal()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting wake recognizer", e)
                scheduleRestartListening(2000)
            }
        }
    }

    private fun listenInternal() {
        if (!isListeningServiceRunning) return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                // This is what makes it "bilkul local": forces on-device recognition instead
                // of streaming audio to Google's cloud servers. Free, no account needed —
                // just requires the on-device language pack to be downloaded once (see class
                // doc comment above for where to get it in Settings).
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call startListening", e)
            scheduleRestartListening(2000)
        }
    }

    private fun handleSpeechResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        for (match in matches) {
            val text = match.lowercase(Locale.getDefault())
            if (text.contains("hey max") || text.contains("hey, max") || text.contains("max")) {
                Log.i(TAG, "Wake word 'Hey Max' detected locally! Triggering app...")
                onWakeWordDetected()
                break
            }
        }
    }

    private fun onWakeWordDetected() {
        // Send Broadcast with explicit package name for RECEIVER_NOT_EXPORTED compatibility
        val broadcastIntent = Intent(ACTION_WAKE_WORD_DETECTED).apply {
            setPackage(packageName)
        }
        sendBroadcast(broadcastIntent)

        // Launch / Bring MainActivity to Foreground
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
            if (isListeningServiceRunning) {
                listenInternal()
            }
        }, delayMs)
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
            .setContentText("Listening for 'Max / Hey Max' (offline, on-device)...")
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
        mainHandler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "MaxWakeService"
        const val CHANNEL_ID = "max_jarvis_wake_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_WAKE_WORD_DETECTED = "com.example.MAX_WAKE_WORD_EVENT"
    }
}
