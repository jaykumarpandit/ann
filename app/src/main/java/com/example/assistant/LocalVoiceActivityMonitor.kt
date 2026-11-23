package com.example.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Ultra-lightweight on-device PCM Voice Activity Detector (VAD).
 * Keeps the microphone listening silently with zero chimes and zero cloud/AI usage
 * until human speech/sound crosses the adaptive ambient noise threshold.
 */
class LocalVoiceActivityMonitor(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "LocalVadMonitor"
        private const val SAMPLE_RATE = 16_000
        private const val FRAME_SAMPLES = 800 // 50ms per frame
        private const val MIN_TRIGGER_RMS = 420.0
        private const val MAX_TRIGGER_RMS = 2400.0
        private const val CONSECUTIVE_FRAMES_TO_TRIGGER = 2
    }

    private var monitorJob: Job? = null

    val isMonitoring: Boolean
        get() = monitorJob?.isActive == true

    fun start(
        onSpeechDetected: () -> Unit,
        onMonitorUnavailable: (String) -> Unit
    ) {
        stop()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onMonitorUnavailable("Microphone permission required.")
            return
        }

        monitorJob = scope.launch(Dispatchers.IO) {
            var recorder: AudioRecord? = null
            try {
                val minBufBytes = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBufBytes <= 0) {
                    launch(Dispatchers.Main) {
                        onMonitorUnavailable("AudioRecord unavailable on this device.")
                    }
                    return@launch
                }

                val bufferSizeBytes = maxOf(minBufBytes * 2, FRAME_SAMPLES * 2 * 2)
                recorder = createRecorder(bufferSizeBytes)
                if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                    launch(Dispatchers.Main) {
                        onMonitorUnavailable("Could not initialize silent microphone monitor.")
                    }
                    return@launch
                }

                val pcmBuffer = ShortArray(FRAME_SAMPLES)
                recorder.startRecording()

                var ambientRms = 180.0
                var warmUpFrames = 0
                var consecutiveLoudFrames = 0

                while (isActive) {
                    val read = recorder.read(pcmBuffer, 0, pcmBuffer.size)
                    if (read <= 0) continue

                    var sumSq = 0.0
                    for (i in 0 until read) {
                        val s = pcmBuffer[i].toDouble()
                        sumSq += s * s
                    }
                    val rms = sqrt(sumSq / read.toDouble())

                    if (warmUpFrames < 8) {
                        // Calibrate room noise floor during initial ~400ms
                        ambientRms = if (warmUpFrames == 0) rms else (ambientRms * 0.7 + rms * 0.3)
                        warmUpFrames++
                        continue
                    }

                    val threshold = (ambientRms * 2.5).coerceIn(MIN_TRIGGER_RMS, MAX_TRIGGER_RMS)
                    if (rms > threshold) {
                        consecutiveLoudFrames++
                        if (consecutiveLoudFrames >= CONSECUTIVE_FRAMES_TO_TRIGGER) {
                            break
                        }
                    } else {
                        consecutiveLoudFrames = 0
                        // Slowly track gradual ambient room shifts
                        ambientRms = (ambientRms * 0.96 + rms * 0.04).coerceAtMost(900.0)
                    }
                }

                if (isActive && consecutiveLoudFrames >= CONSECUTIVE_FRAMES_TO_TRIGGER) {
                    // Release AudioRecord BEFORE invoking SpeechRecognizer on Main thread
                    releaseSafely(recorder)
                    recorder = null
                    launch(Dispatchers.Main) {
                        onSpeechDetected()
                    }
                }
            } catch (se: SecurityException) {
                Log.w(TAG, "Mic permission missing during VAD monitor", se)
                launch(Dispatchers.Main) {
                    onMonitorUnavailable("Microphone permission is needed.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "VAD monitor stopped: ${e.message}")
                launch(Dispatchers.Main) {
                    onMonitorUnavailable(e.localizedMessage ?: "Audio monitor unavailable")
                }
            } finally {
                releaseSafely(recorder)
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }

    @Suppress("MissingPermission")
    private fun createRecorder(bufferSizeBytes: Int): AudioRecord? {
        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        )
        for (source in sources) {
            try {
                val candidate = AudioRecord(
                    source,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSizeBytes
                )
                if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                    return candidate
                }
                candidate.release()
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun releaseSafely(recorder: AudioRecord?) {
        if (recorder == null) return
        try {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }
        } catch (_: Exception) {
        }
        try {
            recorder.release()
        } catch (_: Exception) {
        }
    }
}
