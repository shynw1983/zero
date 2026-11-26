package com.zero.todo.feature.execute

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zero.todo.core.nlp.ParsedQuickInput
import com.zero.todo.domain.model.ExecuteTheme
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.WorkspaceMode
import com.zero.todo.feature.focus.FocusUiState
import com.zero.todo.feature.tasklist.FolderPathSuggestion
import com.zero.todo.feature.tasklist.UndoBannerState
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

val SignalOrange = Color(0xFFFF4D00)
val MonolithBlack = Color(0xFF09090B)
val ExecuteMaxContentWidth = 600.dp

/**
 * Unified UI state shared seamlessly across every theme in the Execution Theme Engine
 * (`MONOLITH`, `RUNWAY`, and future themes). Switching themes preserves every active field.
 */
data class ExecuteUiState(
    val executeTheme: ExecuteTheme = ExecuteTheme.MONOLITH,
    val currentDate: LocalDate = LocalDate.now(),
    val runwayQueue: List<TaskItem> = emptyList(),
    val focusedTaskId: String? = null,
    val focusedTaskIndex: Int = 0,
    val focusedTask: TaskItem? = null,
    val scheduledTodayMinutes: Int = 0,
    val dailyCapacityMinutes: Int = 240,
    val todayCompletedCount: Int = 0,
    val triageCount: Int = 0,
    val folderNameById: Map<String, String> = emptyMap(),
    val focusState: FocusUiState = FocusUiState(),
    val isInlineFlowActive: Boolean = false,
    val quickInputText: String = "",
    val parsedQuickInput: ParsedQuickInput = ParsedQuickInput(rawInput = "", title = ""),
    val folderSuggestions: List<FolderPathSuggestion> = emptyList(),
    val continuousEntryEnabled: Boolean = true,
    val undoBanner: UndoBannerState? = null
)

/**
 * Shared ViewModel contract consumed by all Execution themes so switching between
 * MONOLITH, RUNWAY, or future themes retains active queue position, timers, and drafts.
 */
interface ExecuteViewModel {
    val executeUiState: StateFlow<ExecuteUiState>

    fun setExecuteTheme(theme: ExecuteTheme)
    fun selectFocusedRunwayTask(taskId: String)
    fun focusPreviousRunwayTask()
    fun focusNextRunwayTask()
    fun skipTaskInRunway(task: TaskItem)
    fun toggleTaskCompletion(task: TaskItem)
    fun openTaskDetailFromNow(task: TaskItem)
    fun openFocusForTask(task: TaskItem?, initialMode: FocusMode = FocusMode.TWO_MIN_STARTER)
    fun selectFocusMode(mode: FocusMode)
    fun startOrResumeFocus()
    fun pauseFocus()
    fun resetFocus()
    fun continueFocusToPomodoro()
    fun stopStopwatchFocus()
    fun completeFocusedTask(task: TaskItem)
    fun exitFocus()
    fun updateTaskFirstStep(task: TaskItem, firstStep: String?)
    fun completeFirstStepAndStartFlow(task: TaskItem)
    fun updateTaskEffort(task: TaskItem, estimatedMinutes: Int?)
    fun openTriage()
    fun setWorkspaceMode(mode: WorkspaceMode)
    fun openSettings()
    fun editQuickInputText(edit: (String) -> String)
    fun submitQuickInput()
    fun toggleContinuousEntry()
    fun submitVoiceInput(spokenText: String)
    fun performUndo(banner: UndoBannerState)
    fun dismissUndoBanner(bannerId: Long)
}
