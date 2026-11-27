package com.zero.todo.core.common

import android.content.Intent
import android.net.Uri
import android.os.Build

data class SharedCapturePayload(
    val id: Long = System.nanoTime(),
    val text: String = "",
    val imageUri: String? = null
)

/**
 * Extracts text notes and shared image URIs from Android `ACTION_SEND` share intents
 * so users can forward chats, bills, or screenshots from WeChat/LINE/Gallery straight into ZERO AI.
 */
object ZeroShareIntentParser {

    fun extract(intent: Intent?): SharedCapturePayload? {
        if (intent == null || intent.action != Intent.ACTION_SEND) return null

        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val combinedText = when {
            subject.isNotEmpty() && extraText.isNotEmpty() -> {
                if (extraText.startsWith(subject) || extraText.contains(subject)) extraText
                else "$subject\n$extraText"
            }
            extraText.isNotEmpty() -> extraText
            subject.isNotEmpty() -> subject
            else -> ""
        }

        val streamUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        } ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri

        if (combinedText.isEmpty() && streamUri == null) return null
        return SharedCapturePayload(
            text = combinedText,
            imageUri = streamUri?.toString()
        )
    }
}
