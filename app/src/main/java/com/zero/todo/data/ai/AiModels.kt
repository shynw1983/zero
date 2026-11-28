package com.zero.todo.data.ai

import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.TaskPriority

enum class AiProvider {
    GEMINI,
    OPENAI;

    companion object {
        fun fromStorage(raw: String?): AiProvider =
            entries.firstOrNull { it.name == raw } ?: GEMINI
    }
}

enum class AiReasoningEffort(val openAiValue: String) {
    NONE("none"),
    LOW("low"),
    MEDIUM("medium");

    companion object {
        fun fromStorage(raw: String?): AiReasoningEffort =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: NONE
    }
}

data class AiConfig(
    val provider: AiProvider = AiProvider.GEMINI,
    val geminiApiKey: String = "",
    val geminiModel: String = DEFAULT_GEMINI_MODEL,
    val openAiApiKey: String = "",
    val openAiModel: String = DEFAULT_OPENAI_MODEL,
    val openAiBaseUrl: String = DEFAULT_OPENAI_BASE_URL,
    val customInstructions: String = "",
    val reasoningEffort: AiReasoningEffort = AiReasoningEffort.NONE
) {
    val activeApiKey: String
        get() = when (provider) {
            AiProvider.GEMINI -> geminiApiKey.trim()
            AiProvider.OPENAI -> openAiApiKey.trim()
        }

    val activeModel: String
        get() = when (provider) {
            AiProvider.GEMINI -> geminiModel.trim().ifBlank { DEFAULT_GEMINI_MODEL }
            AiProvider.OPENAI -> openAiModel.trim().ifBlank { DEFAULT_OPENAI_MODEL }
        }

    val isConfigured: Boolean
        get() = activeApiKey.isNotBlank()

    companion object {
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
        const val DEFAULT_OPENAI_MODEL = "gpt-4o-mini"
        const val DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1"
    }
}

data class AiImagePayload(
    val base64Jpeg: String,
    val mimeType: String = "image/jpeg"
)

class AiAudioPayload(
    val bytes: ByteArray,
    val base64Data: String,
    val mimeType: String = "audio/mp4"
)

data class AiWorkspaceContext(
    val currentDateIso: String,
    val currentWeekday: String,
    val languageTag: String,
    val folderPaths: List<String>,
    val tagPaths: List<String>,
    val customInstructions: String = ""
)

data class AiSubtaskDraft(
    val title: String,
    val tags: List<String> = emptyList(),
    val dueDateIso: String? = null,
    val priority: TaskPriority = TaskPriority.NONE,
    val estimatedMinutes: Int? = null
)

data class AiCapturedTaskDraft(
    val title: String,
    val folderPath: String? = null,
    val tags: List<String> = emptyList(),
    val dueDateIso: String? = null,
    val priority: TaskPriority = TaskPriority.NONE,
    val estimatedMinutes: Int? = null,
    val firstStep: String? = null,
    val description: String = "",
    val subtasks: List<AiSubtaskDraft> = emptyList(),
    val checklistItems: List<String> = emptyList()
)

data class AiBreakdownDraft(
    val firstStep: String? = null,
    val estimatedMinutes: Int? = null,
    val subtasks: List<AiSubtaskDraft> = emptyList(),
    val checklistItems: List<String> = emptyList()
)

sealed interface AiTestStatus {
    data object Working : AiTestStatus
    data class Success(val model: String) : AiTestStatus
    data class Error(val detail: String) : AiTestStatus
}

data class AiCaptureDialogState(
    val sessionId: Long = 0L,
    val isOpen: Boolean = false,
    val initialText: String = "",
    val initialImageUri: String? = null,
    val autoLaunchCamera: Boolean = false,
    val autoStartVoice: Boolean = false,
    val autoAnalyzeOnOpen: Boolean = false,
    val isAnalyzing: Boolean = false,
    val results: List<AiCapturedTaskDraft> = emptyList(),
    val errorMessage: String? = null
)

data class AiBreakdownDialogState(
    val sessionId: Long = 0L,
    val task: TaskItem? = null,
    val isAnalyzing: Boolean = false,
    val draft: AiBreakdownDraft? = null,
    val errorMessage: String? = null
)
