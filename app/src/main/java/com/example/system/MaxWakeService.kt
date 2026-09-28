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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.core.MicArbiter
import com.example.core.MicOwner
import com.example.wake.VoskWakeWordEngine
import com.example.wake.WakeModelManager
import com.example.wake.WakeStatus
import com.example.wake.WakeWordEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Background wake-word service: keeps a lightweight ON-DEVICE detector ([WakeWordEngine]) listening
 * for "Hey Max" / "Max". No audio is uploaded anywhere. The full assistant pipeline (STT / Live)
 * only starts after a wake event.
 *
 *   WakeWordEngine (offline, this service)  ->  WAKE event  ->  MainActivity/MaxViewModel  ->  STT / Live
 *
 * Microphone ownership goes through [MicArbiter]: the detector yields the mic the instant the
 * assistant or Live mode needs it, and resumes after they release it.
 *
 * The first run downloads a ~40 MB free offline model once (needs internet once).
 * Must be started while MAX is visible (Android 14+ refuses to start a microphone foreground
 * service from the background).
 */
class MaxWakeService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var arbiterJob: Job? = null
    private var setupJob: Job? = null

    private var engine: WakeWordEngine? = null
    private var engineReady = false
    private var detecting = false
    private var running = false
    private var cooldownUntilMs = 0L
    private var pendingStart: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val n = buildNotification("Wake word taiyaar ho raha hai...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, n)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not enter foreground; stopping wake service", e)
            WakeStatus.set("Wake service start nahi hui. MAX app kholkar dobara ON karo.")
            stopSelf()
            return START_NOT_STICKY
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            WakeStatus.set("Microphone permission chahiye")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!running) {
            running = true
            setupJob = scope.launch { prepareEngine() }
        }
        return START_STICKY
    }

    private suspend fun prepareEngine() {
        val models = WakeModelManager(applicationContext)
        if (!models.isReady()) {
            updateStatus("Wake model download ho raha hai (sirf ek baar)...")
            val error = withContext(Dispatchers.IO) {
                models.download { pct -> updateStatus("Wake model download: $pct%") }
            }
            if (error != null) { fail(error); return }
        }
        updateStatus("Wake model load ho raha hai...")
        val eng = VoskWakeWordEngine(applicationContext, models.modelDir)
        val loaded = withContext(Dispatchers.Default) { eng.load() }
        if (!loaded) { eng.close(); fail("Wake model load nahi hua. App data clear karke dobara try karo."); return }
        engine = eng
        engineReady = true
        startArbiterObserver()
    }

    private fun startArbiterObserver() {
        arbiterJob?.cancel()
        // Emits the current owner immediately. NONE -> listen; ASSISTANT/LIVE -> stay off the mic.
        arbiterJob = scope.launch {
            MicArbiter.owner.collect { owner ->
                when (owner) {
                    MicOwner.ASSISTANT, MicOwner.LIVE -> {
                        cancelPendingStart()
                        stopDetector(releaseMic = false)
                        updateStatus("Assistant mic use kar raha hai")
                    }
                    MicOwner.NONE -> if (running && engineReady && !detecting) {
                        scheduleStart((cooldownUntilMs - SystemClock.elapsedRealtime()).coerceAtLeast(300L))
                    }
                    MicOwner.WAKE -> Unit
                }
            }
        }
    }

    private fun scheduleStart(delayMs: Long) {
        cancelPendingStart()
        val r = Runnable { pendingStart = null; startDetector() }
        pendingStart = r
        mainHandler.postDelayed(r, delayMs)
    }

    private fun cancelPendingStart() {
        pendingStart?.let { mainHandler.removeCallbacks(it) }
        pendingStart = null
    }

    private fun startDetector() {
        val eng = engine ?: return
        if (!running || detecting) return
        if (!MicArbiter.acquire(MicOwner.WAKE)) return // assistant/Live has the mic; observer retries later
        detecting = true
        updateStatus("Hey Max / Max sun raha hai (sirf phone ke andar)")
        eng.startListening(
            onWake = { mainHandler.post { onWakeDetected() } },
            onError = { msg -> mainHandler.post { onDetectorError(msg) } }
        )
    }

    private fun stopDetector(releaseMic: Boolean) {
        val eng = engine
        if (detecting) {
            detecting = false
            eng?.stopListening() // blocks briefly until the AudioRecord is really released
        }
        if (releaseMic) MicArbiter.release(MicOwner.WAKE)
    }

    private fun onWakeDetected() {
        if (!detecting) return
        Log.i(TAG, "Wake phrase detected")
        cooldownUntilMs = SystemClock.elapsedRealtime() + WAKE_COOLDOWN_MS
        detecting = false
        MicArbiter.release(MicOwner.WAKE) // engine already released its AudioRecord and exited

        sendBroadcast(Intent(ACTION_WAKE_WORD_DETECTED).setPackage(packageName))

        // Android 10+ may silently block starting an activity from the background; the broadcast
        // above covers the case where MAX is already visible. The default-assistant role (roadmap)
        // is the official way to open MAX from anywhere.
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

    private fun onDetectorError(message: String) {
        Log.w(TAG, "Wake detector error: $message")
        if (!detecting) return
        detecting = false
        MicArbiter.release(MicOwner.WAKE)
        updateStatus("Wake detector: $message. Dobara koshish...")
        scheduleStart(3000) // e.g. another app briefly held the mic
    }

    private fun fail(message: String) {
        Log.w(TAG, message)
        updateStatus(message)
        stopSelf()
    }

    private fun updateStatus(text: String) {
        WakeStatus.set(text)
        try {
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification(text))
        } catch (_: Exception) {}
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MAX wake word")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "MAX Wake Word", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "On-device wake word detection for MAX" }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        running = false
        cancelPendingStart()
        arbiterJob?.cancel()
        setupJob?.cancel()
        stopDetector(releaseMic = true)
        engine?.close()
        engine = null
        engineReady = false
        scope.cancel()
        WakeStatus.set("Wake word band hai")
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
