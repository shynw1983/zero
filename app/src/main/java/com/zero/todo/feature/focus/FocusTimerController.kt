package com.zero.todo.feature.focus

import com.zero.todo.data.repository.FocusRepository
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.domain.model.TaskItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FocusPhase {
    READY,
    RUNNING,
    PAUSED,
    TWO_MIN_FINISHED_PROMPT,
    POMODORO_COMPLETED
}

enum class FocusHapticKind {
    NONE,
    GENTLE_TWO_MIN_DONE,
    HEAVY_POMODORO_DONE
}

data class FocusUiState(
    val isActive: Boolean = false,
    val task: TaskItem? = null,
    val mode: FocusMode = FocusMode.TWO_MIN_STARTER,
    val phase: FocusPhase = FocusPhase.READY,
    val remainingSeconds: Int = DEFAULT_TWO_MIN_SECONDS,
    val elapsedSeconds: Int = 0,
    val totalDurationSeconds: Int = DEFAULT_TWO_MIN_SECONDS,
    val todayFocusSeconds: Int = 0,
    val hapticSignalId: Long = 0L,
    val hapticKind: FocusHapticKind = FocusHapticKind.NONE
) {
    val progress: Float
        get() = when (mode) {
            FocusMode.STOPWATCH -> ((elapsedSeconds % 60) / 60f)
            else -> if (totalDurationSeconds > 0) {
                (elapsedSeconds.toFloat() / totalDurationSeconds.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
        }

    val formattedTimer: String
        get() {
            val seconds = if (mode == FocusMode.STOPWATCH) elapsedSeconds else remainingSeconds
            val mins = seconds / 60
            val secs = seconds % 60
            return "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
        }

    companion object {
        const val DEFAULT_TWO_MIN_SECONDS = 120
        const val DEFAULT_POMODORO_SECONDS = 25 * 60
    }
}

/**
 * Self-contained state machine & ticker for ZERO's Anti-Procrastination Focus System:
 * - Single-task distraction-free focus
 * - "Just 2 Mins" starter (`TWO_MIN_STARTER`) with gentle haptic prompt & one-tap seamless
 *   continuation into a 25-minute `POMODORO`
 * - Automatic persistence to `FocusSessionEntity` via [FocusRepository]
 */
class FocusTimerController(
    private val scope: CoroutineScope,
    private val focusRepository: FocusRepository,
    private val twoMinSeconds: Int = FocusUiState.DEFAULT_TWO_MIN_SECONDS,
    private val pomodoroSeconds: Int = FocusUiState.DEFAULT_POMODORO_SECONDS,
    private val tickIntervalMs: Long = 1_000L
) {
    private val _state = MutableStateFlow(FocusUiState())
    val state: StateFlow<FocusUiState> = _state.asStateFlow()

    private var tickerJob: Job? = null

    fun updateTodayFocusSeconds(seconds: Int) {
        _state.update { it.copy(todayFocusSeconds = seconds) }
    }

    fun syncFocusedTask(task: TaskItem?) {
        _state.update { current ->
            if (current.isActive && task != null && current.task?.id == task.id) {
                current.copy(task = task)
            } else {
                current
            }
        }
    }

    fun openFocus(
        task: TaskItem?,
        initialMode: FocusMode = FocusMode.TWO_MIN_STARTER,
        autoStart: Boolean = true
    ) {
        stopTicker()
        val duration = durationForMode(initialMode)
        _state.update { current ->
            current.copy(
                isActive = true,
                task = task,
                mode = initialMode,
                phase = if (autoStart) FocusPhase.RUNNING else FocusPhase.READY,
                remainingSeconds = duration,
                elapsedSeconds = 0,
                totalDurationSeconds = duration,
                hapticKind = FocusHapticKind.NONE
            )
        }
        if (autoStart) {
            startTicker()
        }
    }

    fun selectMode(mode: FocusMode) {
        stopTicker()
        val duration = durationForMode(mode)
        _state.update { current ->
            current.copy(
                mode = mode,
                phase = FocusPhase.READY,
                remainingSeconds = duration,
                elapsedSeconds = 0,
                totalDurationSeconds = duration,
                hapticKind = FocusHapticKind.NONE
            )
        }
    }

    fun startOrResume() {
        val current = _state.value
        if (current.phase == FocusPhase.RUNNING) return
        if (current.phase == FocusPhase.TWO_MIN_FINISHED_PROMPT || current.phase == FocusPhase.POMODORO_COMPLETED) {
            val duration = durationForMode(current.mode)
            _state.update {
                it.copy(
                    phase = FocusPhase.RUNNING,
                    remainingSeconds = duration,
                    elapsedSeconds = 0,
                    totalDurationSeconds = duration
                )
            }
        } else {
            _state.update { it.copy(phase = FocusPhase.RUNNING) }
        }
        startTicker()
    }

    fun pause() {
        stopTicker()
        _state.update { current ->
            if (current.phase == FocusPhase.RUNNING) current.copy(phase = FocusPhase.PAUSED) else current
        }
    }

    fun reset() {
        stopTicker()
        _state.update { current ->
            val duration = durationForMode(current.mode)
            current.copy(
                phase = FocusPhase.READY,
                remainingSeconds = duration,
                elapsedSeconds = 0,
                totalDurationSeconds = duration
            )
        }
    }

    /**
     * Seamlessly transitions from a completed 2-Minute Starter directly into a running
     * 25-minute Pomodoro session.
     */
    fun continueToPomodoro() {
        stopTicker()
        _state.update { current ->
            current.copy(
                isActive = true,
                mode = FocusMode.POMODORO,
                phase = FocusPhase.RUNNING,
                remainingSeconds = pomodoroSeconds,
                elapsedSeconds = 0,
                totalDurationSeconds = pomodoroSeconds,
                hapticKind = FocusHapticKind.NONE
            )
        }
        startTicker()
    }

    fun stopAndRecordStopwatchIfElapsed() {
        val current = _state.value
        stopTicker()
        if (current.mode == FocusMode.STOPWATCH && current.elapsedSeconds > 0) {
            val taskId = current.task?.id
            val elapsed = current.elapsedSeconds
            scope.launch {
                focusRepository.recordSession(
                    taskId = taskId,
                    durationSeconds = elapsed,
                    mode = FocusMode.STOPWATCH
                )
            }
            _state.update {
                it.copy(
                    phase = FocusPhase.READY,
                    elapsedSeconds = 0,
                    remainingSeconds = 0
                )
            }
        }
    }

    fun exitFocus() {
        val current = _state.value
        stopTicker()
        if (current.mode == FocusMode.STOPWATCH && current.elapsedSeconds >= 10) {
            val taskId = current.task?.id
            val elapsed = current.elapsedSeconds
            scope.launch {
                focusRepository.recordSession(
                    taskId = taskId,
                    durationSeconds = elapsed,
                    mode = FocusMode.STOPWATCH
                )
            }
        }
        _state.update {
            it.copy(
                isActive = false,
                phase = FocusPhase.READY
            )
        }
    }

    private fun startTicker() {
        stopTicker()
        tickerJob = scope.launch {
            while (true) {
                delay(tickIntervalMs)
                val current = _state.value
                if (current.phase != FocusPhase.RUNNING) break

                val nextElapsed = current.elapsedSeconds + 1
                if (current.mode == FocusMode.STOPWATCH) {
                    _state.update { it.copy(elapsedSeconds = nextElapsed) }
                } else {
                    val nextRemaining = (current.remainingSeconds - 1).coerceAtLeast(0)
                    if (nextRemaining == 0) {
                        onCountdownCompleted(current, nextElapsed)
                        break
                    } else {
                        _state.update {
                            it.copy(
                                elapsedSeconds = nextElapsed,
                                remainingSeconds = nextRemaining
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun onCountdownCompleted(snapshot: FocusUiState, completedSeconds: Int) {
        val recordedSeconds = completedSeconds.coerceAtLeast(1)
        focusRepository.recordSession(
            taskId = snapshot.task?.id,
            durationSeconds = recordedSeconds,
            mode = snapshot.mode
        )

        val nextPhase = when (snapshot.mode) {
            FocusMode.TWO_MIN_STARTER -> FocusPhase.TWO_MIN_FINISHED_PROMPT
            FocusMode.POMODORO -> FocusPhase.POMODORO_COMPLETED
            FocusMode.STOPWATCH -> FocusPhase.READY
        }
        val haptic = when (snapshot.mode) {
            FocusMode.TWO_MIN_STARTER -> FocusHapticKind.GENTLE_TWO_MIN_DONE
            FocusMode.POMODORO -> FocusHapticKind.HEAVY_POMODORO_DONE
            FocusMode.STOPWATCH -> FocusHapticKind.NONE
        }

        _state.update {
            it.copy(
                phase = nextPhase,
                elapsedSeconds = recordedSeconds,
                remainingSeconds = 0,
                hapticSignalId = it.hapticSignalId + 1L,
                hapticKind = haptic
            )
        }
    }

    private fun durationForMode(mode: FocusMode): Int = when (mode) {
        FocusMode.TWO_MIN_STARTER -> twoMinSeconds
        FocusMode.POMODORO -> pomodoroSeconds
        FocusMode.STOPWATCH -> 0
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }
}
