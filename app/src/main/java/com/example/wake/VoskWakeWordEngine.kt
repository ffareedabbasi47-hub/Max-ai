package com.example.wake

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.example.core.WakePhrase
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

/**
 * On-device wake word using Vosk (free, offline, Apache-2.0) restricted to a tiny grammar:
 * only "hey max", "hello max", "okay max", "max" (everything else maps to [unk]). Audio is read from the mic in
 * 100 ms chunks, fed to the local recognizer and thrown away. Nothing is stored or uploaded.
 *
 * Honest limits: this runs on the CPU, not on the phone's always-on DSP (that hardware path is
 * reserved for the phone maker's own assistant). It uses much less power than continuous cloud
 * speech recognition, but more than a DSP hotword would.
 */
class VoskWakeWordEngine(
    private val context: Context,
    private val modelDir: File
) : WakeWordEngine {

    @Volatile private var model: Model? = null
    @Volatile private var worker: Thread? = null
    @Volatile private var running = false

    override fun load(): Boolean {
        if (model != null) return true
        return try {
            model = Model(modelDir.absolutePath)
            true
        } catch (e: Throwable) {
            false
        }
    }

    @Synchronized
    override fun startListening(onWake: (String) -> Unit, onError: (String) -> Unit) {
        if (running) return
        val m = model
        if (m == null) { onError("Wake model load nahi hua"); return }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError("Microphone permission chahiye"); return
        }
        running = true
        val t = Thread({ runLoop(m, onWake, onError) }, "max-wake-detector")
        worker = t
        t.start()
    }

    private fun runLoop(m: Model, onWake: (String) -> Unit, onError: (String) -> Unit) {
        var record: AudioRecord? = null
        var recognizer: Recognizer? = null
        try {
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuf <= 0) { onError("Mic buffer available nahi"); return }
            record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, SAMPLE_RATE / 2) * 2
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                onError("Mic open nahi hua. Kisi aur app ne mic pakda ho sakta hai.")
                return
            }
            recognizer = Recognizer(m, SAMPLE_RATE.toFloat(), GRAMMAR)
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                onError("Mic start nahi hua")
                return
            }
            val chunk = ShortArray(SAMPLE_RATE / 10)
            var lastPartial = ""
            var stable = 0
            while (running) {
                val n = record.read(chunk, 0, chunk.size)
                if (n < 0) { onError("Mic read error ($n)"); return }
                if (n == 0) continue
                val isFinal = recognizer.acceptWaveForm(chunk, n)
                val text = WakePhrase.extractVoskText(if (isFinal) recognizer.result else recognizer.partialResult)
                if (WakePhrase.matchesRestrictedGrammar(text)) {
                    // A finished utterance fires immediately. A partial must repeat on two
                    // consecutive 100 ms chunks first, so a single noisy blip can't wake MAX.
                    if (!isFinal) {
                        stable = if (text == lastPartial) stable + 1 else 1
                        lastPartial = text
                    }
                    if (isFinal || stable >= 2) {
                        running = false
                        onWake(text)
                        return
                    }
                } else {
                    stable = 0
                    lastPartial = ""
                }
            }
        } catch (e: Throwable) {
            if (running) onError("Wake detector error")
        } finally {
            running = false
            try { record?.stop() } catch (_: Throwable) {}
            try { record?.release() } catch (_: Throwable) {}
            try { recognizer?.close() } catch (_: Throwable) {}
        }
    }

    override fun stopListening() {
        running = false
        val t = worker
        worker = null
        // The loop exits within one 100 ms read and releases the mic itself; wait briefly so the
        // mic is truly free before another component (assistant/Live) opens it.
        if (t != null && t !== Thread.currentThread()) {
            try { t.join(800) } catch (_: InterruptedException) {}
        }
    }

    override fun close() {
        stopListening()
        try { model?.close() } catch (_: Throwable) {}
        model = null
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val GRAMMAR = "[\"hey max\", \"hello max\", \"okay max\", \"max\", \"[unk]\"]"
    }
}
