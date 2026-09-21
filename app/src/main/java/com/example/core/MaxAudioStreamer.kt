package com.example.core

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Captures 16kHz PCM16 mono mic audio for Gemini Live's realtime input, and plays back the
 * 24kHz PCM16 audio Gemini streams in response — this is what gives Live Mode its natural
 * Gemini voice instead of the Android TextToSpeech engine's robotic default voice. Ported from
 * the Iris project's audited audio pipeline.
 */
class MaxAudioStreamer(
    private val onAudioChunkCaptured: (ByteArray) -> Unit
) {
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var captureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startCapture() {
        val minBuf = AudioRecord.getMinBufferSize(
            MaxLiveConfig.INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = if (minBuf > 0) minBuf * 2 else 4096

        recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MaxLiveConfig.INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        recorder?.startRecording()

        captureJob = scope.launch {
            val buffer = ByteArray(bufferSize)
            while (isActive && recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = recorder?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) onAudioChunkCaptured(buffer.copyOf(read))
            }
        }
    }

    fun stopCapture() {
        captureJob?.cancel()
        captureJob = null
        recorder?.let {
            if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop()
            it.release()
        }
        recorder = null
    }

    /** Feed raw PCM16 bytes (24kHz mono, as Gemini Live sends) to play through the speaker. */
    fun playChunk(pcmData: ByteArray) {
        if (player == null) {
            val bufferSize = AudioTrack.getMinBufferSize(
                MaxLiveConfig.OUTPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).let { if (it > 0) it * 2 else 4096 }

            player = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(MaxLiveConfig.OUTPUT_SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            player?.play()
        }
        player?.write(pcmData, 0, pcmData.size)
    }

    fun stopPlayback() {
        player?.let {
            if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.stop()
            it.release()
        }
        player = null
    }

    fun release() {
        stopCapture()
        stopPlayback()
    }
}
