package com.zero.todo.feature.execute

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zero.todo.R
import com.zero.todo.core.designsystem.ZeroCapsuleShape
import com.zero.todo.core.designsystem.ZeroMotion
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.rememberZeroHaptics
import com.zero.todo.domain.model.ExecuteTheme
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.feature.focus.FocusHapticKind
import com.zero.todo.feature.tasklist.QuickInputCapsule
import com.zero.todo.feature.tasklist.UndoBannerState
import com.zero.todo.feature.tasklist.UndoCapsule

/**
 * Shared Execution Theme Engine host screen.
 * All themes read exclusively from [state] (`ExecuteUiState`) and dispatch to the shared
 * `ExecuteViewModel` callbacks so switching themes preserves every timer, queue index, and draft.
 */
@Composable
fun ExecuteHostScreen(
    state: ExecuteUiState,
    quickInputFocusRequester: FocusRequester,
    onSelectExecuteTheme: (ExecuteTheme) -> Unit,
    onSelectFocusedTask: (String) -> Unit,
    onFocusPreviousTask: () -> Unit,
    onFocusNextTask: () -> Unit,
    onCompleteTask: (TaskItem) -> Unit,
    onSkipTask: (TaskItem) -> Unit,
    onOpenTaskDetail: (TaskItem) -> Unit,
    onStartFocus: (TaskItem?, FocusMode) -> Unit,
    onSelectFocusMode: (FocusMode) -> Unit,
    onStartOrResumeFocus: () -> Unit,
    onPauseFocus: () -> Unit,
    onResetFocus: () -> Unit,
    onContinueFocusToPomodoro: () -> Unit,
    onStopStopwatchFocus: () -> Unit,
    onCompleteFocusedTask: (TaskItem) -> Unit,
    onExitFocus: () -> Unit,
    onUpdateFirstStep: (TaskItem, String?) -> Unit,
    onCompleteFirstStepAndStartFlow: (TaskItem) -> Unit,
    onUpdateEffort: (TaskItem, Int?) -> Unit,
    onOpenTriage: () -> Unit,
    onSwitchToVault: () -> Unit,
    onOpenSettings: () -> Unit,
    onQuickInputEdit: ((String) -> String) -> Unit,
    onQuickInputSubmit: () -> Unit,
    onToggleContinuousEntry: () -> Unit,
    onVoiceInputRecognized: (String) -> Unit,
    onOpenAiCapture: (initialText: String, autoLaunchCamera: Boolean) -> Unit = { _, _ -> },
    onOpenAiBreakdown: (TaskItem) -> Unit = {},
    onUndo: (UndoBannerState) -> Unit,
    onUndoTimeout: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val haptics = rememberZeroHaptics()

    var showThemePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.focusState.hapticSignalId, state.isInlineFlowActive) {
        if (!state.isInlineFlowActive || state.focusState.hapticSignalId == 0L) return@LaunchedEffect
        when (state.focusState.hapticKind) {
            FocusHapticKind.GENTLE_TWO_MIN_DONE -> haptics.gentlePrompt()
            FocusHapticKind.HEAVY_POMODORO_DONE -> haptics.confirm()
            FocusHapticKind.NONE -> Unit
        }
    }

    val headerAlpha by animateFloatAsState(
        targetValue = if (state.isInlineFlowActive) 0.26f else 1f,
        animationSpec = ZeroMotion.FloatSpring,
        label = "execute_header_alpha"
    )
    val dockTargetAlpha = when {
        state.undoBanner != null || state.quickInputText.isNotBlank() -> 1f
        state.isInlineFlowActive -> 0.22f
        state.executeTheme == ExecuteTheme.MONOLITH -> 0.56f
        else -> 0.88f
    }
    val bottomDockAlpha by animateFloatAsState(
        targetValue = dockTargetAlpha,
        animationSpec = ZeroMotion.FloatSpring,
        label = "execute_dock_alpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MonolithBlack)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Minimalist Top Bar (Constrained to 600.dp across phones, foldables, and tablets)
        Row(
            modifier = Modifier
                .widthIn(max = ExecuteMaxContentWidth)
                .fillMaxWidth()
                .graphicsLayer { alpha = headerAlpha }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(SignalOrange)
                )
                Text(
                    text = stringResource(R.string.now_header_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary
                )
                if (state.runwayQueue.isNotEmpty()) {
                    val idxLabel = (state.focusedTaskIndex + 1).toString().padStart(2, '0')
                    val totalLabel = state.runwayQueue.size.toString().padStart(2, '0')
                    Text(
                        text = "$idxLabel / $totalLabel",
                        style = tabular.dateCompact,
                        color = colors.textMuted
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.triageCount > 0) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 42.dp)
                            .clip(ZeroCapsuleShape)
                            .background(SignalOrange.copy(alpha = 0.14f))
                            .hairlineBorder(
                                shape = ZeroCapsuleShape,
                                color = SignalOrange.copy(alpha = 0.65f)
                            )
                            .clickable {
                                haptics.confirm()
                                onOpenTriage()
                            }
                            .padding(horizontal = 11.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Layers,
                            contentDescription = null,
                            tint = SignalOrange,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = stringResource(R.string.now_triage_pill, state.triageCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = SignalOrange
                        )
                    }
                }

                // Minimalist Execution Theme Switcher Button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(ZeroSmallShape)
                        .background(Color(0xFF141417))
                        .hairlineBorder(
                            shape = ZeroSmallShape,
                            color = SignalOrange.copy(alpha = 0.45f)
                        )
                        .clickable {
                            haptics.reorderTick()
                            showThemePicker = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = stringResource(R.string.cd_switch_execute_theme),
                        tint = SignalOrange,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(ZeroSmallShape)
                        .background(Color(0xFF141417))
                        .hairlineBorder(
                            shape = ZeroSmallShape,
                            color = Color.White.copy(alpha = 0.12f)
                        )
                        .clickable {
                            haptics.reorderTick()
                            onOpenSettings()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.cd_open_settings),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        // Theme Engine Stage — Crossfades smoothly while keeping shared state intact
        Crossfade(
            targetState = state.executeTheme,
            animationSpec = tween(durationMillis = 300),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            label = "execute_theme_crossfade"
        ) { theme ->
            when (theme) {
                ExecuteTheme.MONOLITH -> {
                    MonolithThemeView(
                        state = state,
                        onFocusPreviousTask = onFocusPreviousTask,
                        onFocusNextTask = onFocusNextTask,
                        onStartFocus = onStartFocus,
                        onPauseFocus = onPauseFocus,
                        onStartOrResumeFocus = onStartOrResumeFocus,
                        onContinueFocusToPomodoro = onContinueFocusToPomodoro,
                        onCompleteTask = onCompleteTask,
                        onCompleteFocusedTask = onCompleteFocusedTask,
                        onExitFocus = onExitFocus,
                        onSkipTask = onSkipTask,
                        onUpdateEffort = onUpdateEffort,
                        onUpdateFirstStep = onUpdateFirstStep,
                        onCompleteFirstStepAndStartFlow = onCompleteFirstStepAndStartFlow,
                        onOpenTaskDetail = onOpenTaskDetail,
                        onOpenAiBreakdown = onOpenAiBreakdown,
                        onOpenTriage = onOpenTriage,
                        onSwitchToVault = onSwitchToVault
                    )
                }

                ExecuteTheme.RUNWAY -> {
                    RunwayThemeView(
                        state = state,
                        onSelectFocusedTask = onSelectFocusedTask,
                        onCompleteTask = onCompleteTask,
                        onSkipTask = onSkipTask,
                        onOpenTaskDetail = onOpenTaskDetail,
                        onStartFocus = onStartFocus,
                        onPauseFocus = onPauseFocus,
                        onStartOrResumeFocus = onStartOrResumeFocus,
                        onContinueFocusToPomodoro = onContinueFocusToPomodoro,
                        onCompleteFocusedTask = onCompleteFocusedTask,
                        onExitFocus = onExitFocus,
                        onOpenTriage = onOpenTriage,
                        onSwitchToVault = onSwitchToVault
                    )
                }
            }
        }

        // Shared Bottom Quick Capture & Undo Dock (Subdued in MONOLITH)
        Column(
            modifier = Modifier
                .widthIn(max = ExecuteMaxContentWidth)
                .fillMaxWidth()
                .graphicsLayer { alpha = bottomDockAlpha }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UndoCapsule(
                banner = state.undoBanner,
                onUndo = onUndo,
                onTimeout = onUndoTimeout
            )
            QuickInputCapsule(
                text = state.quickInputText,
                parsed = state.parsedQuickInput,
                folderSuggestions = state.folderSuggestions,
                continuousEntryEnabled = state.continuousEntryEnabled,
                focusRequester = quickInputFocusRequester,
                onEditText = onQuickInputEdit,
                onSubmit = onQuickInputSubmit,
                onToggleContinuousEntry = onToggleContinuousEntry,
                onVoiceInputRecognized = onVoiceInputRecognized,
                aiConfigured = state.aiConfigured,
                onOpenAiCapture = onOpenAiCapture,
                currentDate = state.currentDate
            )
        }
    }

    if (showThemePicker) {
        ExecuteThemePickerSheet(
            activeTheme = state.executeTheme,
            onSelectTheme = { picked ->
                showThemePicker = false
                onSelectExecuteTheme(picked)
            },
            onDismiss = { showThemePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExecuteThemePickerSheet(
    activeTheme: ExecuteTheme,
    onSelectTheme: (ExecuteTheme) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ZeroTheme.colors
    val haptics = rememberZeroHaptics()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141417),
        contentColor = colors.textPrimary,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(ZeroCapsuleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = ExecuteMaxContentWidth)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.execute_theme_picker_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary
            )

            ExecuteTheme.entries.forEach { theme ->
                val selected = theme == activeTheme
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) Color(0xFF1C1C22) else Color(0xFF0E0E12))
                        .hairlineBorder(
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) SignalOrange else Color(0xFF27272A)
                        )
                        .clickable {
                            haptics.confirm()
                            onSelectTheme(theme)
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ExecuteThemeSwatch(theme = theme)

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(theme.titleRes),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary
                        )
                        Text(
                            text = stringResource(theme.subtitleRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }

                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(SignalOrange),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(26.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ExecuteThemeSwatch(theme: ExecuteTheme) {
    val shape = RoundedCornerShape(10.dp)
    Canvas(
        modifier = Modifier
            .size(56.dp)
            .clip(shape)
            .background(MonolithBlack)
            .hairlineBorder(shape = shape, color = Color(0xFF27272A))
    ) {
        when (theme) {
            ExecuteTheme.MONOLITH -> {
                if (size.minDimension <= 0f) return@Canvas
                val c = Offset(size.width * 0.5f, size.height * 0.5f)
                val radius = (size.minDimension * 0.44f).coerceAtLeast(1f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SignalOrange.copy(alpha = 0.55f),
                            SignalOrange.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        center = c,
                        radius = radius
                    ),
                    radius = radius,
                    center = c
                )
                drawLine(
                    color = Color(0xFFF4F4F5),
                    start = Offset(size.width * 0.26f, size.height * 0.5f),
                    end = Offset(size.width * 0.74f, size.height * 0.5f),
                    strokeWidth = 2.5.dp.toPx()
                )
            }

            ExecuteTheme.RUNWAY -> {
                val stroke = 0.8.dp.toPx()
                // Left time ticks
                for (i in 1..4) {
                    val y = size.height * (i / 5f)
                    drawLine(
                        color = Color(0xFF52525B),
                        start = Offset(size.width * 0.14f, y),
                        end = Offset(size.width * 0.24f, y),
                        strokeWidth = stroke
                    )
                }
                // Proportional task blocks
                drawRoundRect(
                    color = Color(0xFF1C1C21),
                    topLeft = Offset(size.width * 0.32f, size.height * 0.14f),
                    size = Size(size.width * 0.54f, size.height * 0.18f),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFF1C1C21),
                    topLeft = Offset(size.width * 0.32f, size.height * 0.38f),
                    size = Size(size.width * 0.54f, size.height * 0.24f),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
                drawRoundRect(
                    color = SignalOrange,
                    topLeft = Offset(size.width * 0.32f, size.height * 0.38f),
                    size = Size(size.width * 0.54f, size.height * 0.24f),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(width = 1.2.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFF141417),
                    topLeft = Offset(size.width * 0.32f, size.height * 0.68f),
                    size = Size(size.width * 0.54f, size.height * 0.18f),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
            }
        }
    }
}
