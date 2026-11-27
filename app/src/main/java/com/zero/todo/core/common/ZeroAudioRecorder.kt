package com.zero.todo.core.common

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import com.zero.todo.data.ai.AiAudioPayload
import java.io.File

/**
 * Lightweight built-in voice recorder (.m4a AAC, 16kHz mono) so ZERO AI voice capture
 * works on every Android device even without Google Speech Services installed.
 */
class ZeroAudioRecorder {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(context: Context): Boolean {
        cancel()
        return runCatching {
            val dir = File(context.cacheDir, AUDIO_DIR_NAME).apply {
                if (!exists()) mkdirs()
                listFiles()?.sortedByDescending { it.lastModified() }?.drop(2)?.forEach { it.delete() }
            }
            val target = File(dir, "voice_${System.currentTimeMillis()}.m4a")
            val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            created.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(16_000)
                setAudioEncodingBitRate(64_000)
                setOutputFile(target.absolutePath)
                prepare()
                start()
            }
            recorder = created
            outputFile = target
            true
        }.getOrElse {
            cancel()
            false
        }
    }

    fun currentAmplitude(): Int = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    fun stopAndReadPayload(): AiAudioPayload? {
        val currentRecorder = recorder
        val file = outputFile
        recorder = null
        outputFile = null
        return try {
            if (currentRecorder != null) {
                runCatching { currentRecorder.stop() }
                runCatching { currentRecorder.release() }
            }
            val bytes = file?.takeIf { it.exists() }?.readBytes()
            file?.delete()
            if (bytes != null && bytes.size >= 512) {
                AiAudioPayload(
                    bytes = bytes,
                    base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP),
                    mimeType = "audio/mp4"
                )
            } else {
                null
            }
        } catch (_: Throwable) {
            file?.delete()
            null
        }
    }

    fun cancel() {
        val currentRecorder = recorder
        val file = outputFile
        recorder = null
        outputFile = null
        if (currentRecorder != null) {
            runCatching { currentRecorder.stop() }
            runCatching { currentRecorder.release() }
        }
        file?.delete()
    }

    private companion object {
        const val AUDIO_DIR_NAME = "ai_audio"
    }
}

enum class VoiceAutoStopDecision {
    CONTINUE,
    AUTO_STOP_AND_ANALYZE,
    AUTO_CANCEL_NO_SPEECH
}

data class VoiceAutoStopTick(
    val decision: VoiceAutoStopDecision,
    val normalizedLevel: Float,
    val hasHeardSpeech: Boolean
)

/**
 * Pure-Kotlin adaptive Voice Activity Detector (VAD) using MediaRecorder peak amplitude.
 * Auto-stops shortly after natural speech pauses so users don't have to tap Done manually.
 */
class VoiceAutoStopDetector(
    private val silenceTimeoutMs: Long = 1_600L,
    private val noSpeechTimeoutMs: Long = 7_000L,
    private val maxDurationMs: Long = 60_000L,
    private val minRecordBeforeAutoStopMs: Long = 1_200L
) {
    private var elapsedMs: Long = 0L
    private var hasHeardSpeech: Boolean = false
    private var silenceMs: Long = 0L
    private var noiseFloor: Float = 500f

    fun onSample(amplitude: Int, stepMs: Long): VoiceAutoStopTick {
        elapsedMs += stepMs
        val clampedAmp = amplitude.coerceAtLeast(0)
        val threshold = maxOf(1_100, (noiseFloor * 2.4f).toInt().coerceAtMost(5_000))

        // Ignore the first 200ms mic-open pop so it only seeds the ambient floor.
        if (elapsedMs <= 200L) {
            if (clampedAmp > 0) {
                noiseFloor = (noiseFloor * 0.7f + clampedAmp * 0.3f).coerceIn(350f, 2_500f)
            }
        } else if (clampedAmp >= threshold) {
            hasHeardSpeech = true
            silenceMs = 0L
        } else if (hasHeardSpeech) {
            silenceMs += stepMs
        } else {
            noiseFloor = (noiseFloor * 0.8f + clampedAmp * 0.2f).coerceIn(350f, 2_500f)
        }

        val decision = when {
            elapsedMs >= maxDurationMs -> {
                if (hasHeardSpeech) VoiceAutoStopDecision.AUTO_STOP_AND_ANALYZE
                else VoiceAutoStopDecision.AUTO_CANCEL_NO_SPEECH
            }
            hasHeardSpeech && elapsedMs >= minRecordBeforeAutoStopMs && silenceMs >= silenceTimeoutMs ->
                VoiceAutoStopDecision.AUTO_STOP_AND_ANALYZE
            !hasHeardSpeech && elapsedMs >= noSpeechTimeoutMs ->
                VoiceAutoStopDecision.AUTO_CANCEL_NO_SPEECH
            else -> VoiceAutoStopDecision.CONTINUE
        }

        val normalizedLevel = ((clampedAmp - noiseFloor).coerceAtLeast(0f) / (threshold * 2.5f))
            .coerceIn(0f, 1f)

        return VoiceAutoStopTick(
            decision = decision,
            normalizedLevel = normalizedLevel,
            hasHeardSpeech = hasHeardSpeech
        )
    }
}
