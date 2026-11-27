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
