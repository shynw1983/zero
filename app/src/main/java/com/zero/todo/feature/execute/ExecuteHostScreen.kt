package com.zero.todo.feature.execute

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zero.todo.R
import com.zero.todo.core.designsystem.ZeroAccent
import com.zero.todo.core.designsystem.ZeroCapsuleShape
import com.zero.todo.core.designsystem.ZeroMotion
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.rememberZeroHaptics
import com.zero.todo.domain.model.AtlasMapSettings
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
    onOpenAiCapture: (initialText: String, autoLaunchCamera: Boolean, autoAnalyze: Boolean) -> Unit = { _, _, _ -> },
    onOpenAiBreakdown: (TaskItem) -> Unit = {},
    onUndo: (UndoBannerState) -> Unit,
    onUndoTimeout: (Long) -> Unit,
    isAtlasMapDark: Boolean = state.isDarkTheme,
    onToggleAtlasMapDark: () -> Unit = {},
    atlasBottomModeBarHeight: Dp = 66.dp,
    modifier: Modifier = Modifier,
    onSetAtlasMapSettings: (AtlasMapSettings) -> Unit = {}
) {
    val colors = ZeroTheme.colors
    val haptics = rememberZeroHaptics()

    var showThemePicker by remember { mutableStateOf(false) }
    var showAtlasMapSettings by remember { mutableStateOf(false) }

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

    if (state.executeTheme == ExecuteTheme.ATLAS) {
        var showAtlasQuickInput by remember { mutableStateOf(false) }
        val activeAccent = colors.accent
        val atlasAccent = ZeroAccent.entries.firstOrNull {
            it.colorFor(isAtlasMapDark) == activeAccent
        } ?: ZeroAccent.SlateBlue

        LaunchedEffect(showAtlasQuickInput) {
            if (showAtlasQuickInput) {
                runCatching { quickInputFocusRequester.requestFocus() }
            }
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(if (isAtlasMapDark) Color(0xFF0F141E) else Color(0xFFEAEDF3))
                .imePadding()
        ) {
            // Layer 0 & 1: Architectural Full-Screen 3D Google Map + Minimal Editorial HUD
            AtlasThemeView(
                state = state,
                isMapDark = isAtlasMapDark,
                onToggleMapDark = onToggleAtlasMapDark,
                bottomModeBarHeight = atlasBottomModeBarHeight,
                isQuickInputExpanded = showAtlasQuickInput,
                onToggleQuickInput = { showAtlasQuickInput = !showAtlasQuickInput },
                onOpenTriage = onOpenTriage,
                onOpenThemePicker = { showThemePicker = true },
                onOpenSettings = onOpenSettings,
                onCompleteTask = onCompleteTask,
                onOpenTaskDetail = onOpenTaskDetail,
                onSwitchToVault = onSwitchToVault,
                modifier = Modifier.fillMaxSize()
            )

            // Layer 2: Collapsible Quick Capture & Standalone Undo Dock
            ZeroTheme(
                darkTheme = isAtlasMapDark,
                accent = atlasAccent,
                accentOverride = SignalOrange
            ) {
                if (state.undoBanner != null && !showAtlasQuickInput) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = atlasBottomModeBarHeight + 8.dp)
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 520.dp)
                                .fillMaxWidth()
                        ) {
                            UndoCapsule(
                                banner = state.undoBanner,
                                onUndo = onUndo,
                                onTimeout = onUndoTimeout
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showAtlasQuickInput,
                    enter = fadeIn(tween(180)) + slideInVertically(tween(200)) { it / 3 },
                    exit = fadeOut(tween(150)) + slideOutVertically(tween(160)) { it / 3 },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = atlasBottomModeBarHeight + 8.dp)
                        .padding(horizontal = 14.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 520.dp)
                                .fillMaxWidth(),
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
                                onSubmit = {
                                    onQuickInputSubmit()
                                    if (!state.continuousEntryEnabled) {
                                        showAtlasQuickInput = false
                                    }
                                },
                                onToggleContinuousEntry = onToggleContinuousEntry,
                                onVoiceInputRecognized = onVoiceInputRecognized,
                                aiConfigured = state.aiConfigured,
                                onOpenAiCapture = onOpenAiCapture,
                                currentDate = state.currentDate,
                                frostedGlass = true,
                                frostedDark = isAtlasMapDark
                            )
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MonolithBlack)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NowHeaderBar(
                state = state,
                primaryColor = colors.textPrimary,
                secondaryColor = colors.textMuted,
                buttonBg = Color(0xFF141417),
                settingsBorderColor = Color.White.copy(alpha = 0.12f),
                buttonSize = 44.dp,
                onOpenTriage = onOpenTriage,
                onOpenThemePicker = { showThemePicker = true },
                onOpenSettings = onOpenSettings,
                modifier = Modifier
                    .widthIn(max = ExecuteMaxContentWidth)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = headerAlpha }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )

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

                    ExecuteTheme.ATLAS -> {
                        AtlasThemeView(
                            state = state,
                            isMapDark = isAtlasMapDark,
                            onToggleMapDark = onToggleAtlasMapDark,
                            onCompleteTask = onCompleteTask,
                            onOpenTaskDetail = onOpenTaskDetail,
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
    }

    if (showThemePicker) {
        ExecuteThemePickerSheet(
            activeTheme = state.executeTheme,
            onSelectTheme = { picked ->
                showThemePicker = false
                onSelectExecuteTheme(picked)
            },
            onOpenAtlasSettings = {
                showThemePicker = false
                showAtlasMapSettings = true
            },
            onDismiss = { showThemePicker = false }
        )
    }
    if (showAtlasMapSettings) {
        AtlasMapSettingsSheet(
            settings = state.atlasMapSettings,
            onSave = {
                onSetAtlasMapSettings(it)
                showAtlasMapSettings = false
                showThemePicker = true
            },
            onDismiss = {
                showAtlasMapSettings = false
                showThemePicker = true
            }
        )
    }
}

@Composable
private fun NowHeaderBar(
    state: ExecuteUiState,
    primaryColor: Color,
    secondaryColor: Color,
    buttonBg: Color,
    settingsBorderColor: Color,
    buttonSize: Dp,
    onOpenTriage: () -> Unit,
    onOpenThemePicker: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    titleRes: Int = R.string.now_header_title
) {
    val tabular = ZeroTheme.tabularTypography
    val haptics = rememberZeroHaptics()

    Row(
        modifier = modifier,
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
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = primaryColor
            )
            if (state.runwayQueue.isNotEmpty()) {
                val idxLabel = (state.focusedTaskIndex + 1).toString().padStart(2, '0')
                val totalLabel = state.runwayQueue.size.toString().padStart(2, '0')
                Text(
                    text = "$idxLabel / $totalLabel",
                    style = tabular.dateCompact,
                    color = secondaryColor
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
                        .heightIn(min = buttonSize - 2.dp)
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
                        .padding(horizontal = 11.dp, vertical = 6.dp),
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

            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .clip(ZeroSmallShape)
                    .background(buttonBg)
                    .hairlineBorder(
                        shape = ZeroSmallShape,
                        color = SignalOrange.copy(alpha = 0.45f)
                    )
                    .clickable {
                        haptics.reorderTick()
                        onOpenThemePicker()
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
                    .size(buttonSize)
                    .clip(ZeroSmallShape)
                    .background(buttonBg)
                    .hairlineBorder(
                        shape = ZeroSmallShape,
                        color = settingsBorderColor
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
                    tint = secondaryColor,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExecuteThemePickerSheet(
    activeTheme: ExecuteTheme,
    onSelectTheme: (ExecuteTheme) -> Unit,
    onOpenAtlasSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ZeroTheme.colors
    val haptics = rememberZeroHaptics()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceElevated,
        contentColor = colors.textPrimary,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(ZeroCapsuleShape)
                    .background(colors.textMuted.copy(alpha = 0.25f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = ExecuteMaxContentWidth)
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
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
                val activeHighlight = SignalOrange
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) colors.surface else colors.background)
                        .hairlineBorder(
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) activeHighlight else colors.hairlineBorder
                        )
                        .clickable {
                            haptics.confirm()
                            onSelectTheme(theme)
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ExecuteThemeSwatch(theme = theme, atlasAccent = SignalOrange)

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
                                .background(activeHighlight),
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

            TextButton(onClick = onOpenAtlasSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.atlas_map_settings))
            }
        }
    }
}

@Composable
private fun ExecuteThemeSwatch(
    theme: ExecuteTheme,
    atlasAccent: Color = SignalOrange
) {
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
                for (i in 1..4) {
                    val y = size.height * (i / 5f)
                    drawLine(
                        color = Color(0xFF52525B),
                        start = Offset(size.width * 0.14f, y),
                        end = Offset(size.width * 0.24f, y),
                        strokeWidth = stroke
                    )
                }
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

            ExecuteTheme.ATLAS -> {
                val stroke = 0.9.dp.toPx()
                drawLine(
                    color = Color(0xFF3D4B66),
                    start = Offset(size.width * 0.12f, size.height * 0.72f),
                    end = Offset(size.width * 0.88f, size.height * 0.34f),
                    strokeWidth = stroke
                )
                drawLine(
                    color = Color(0xFF3D4B66),
                    start = Offset(size.width * 0.20f, size.height * 0.24f),
                    end = Offset(size.width * 0.82f, size.height * 0.78f),
                    strokeWidth = stroke
                )
                drawCircle(
                    color = atlasAccent.copy(alpha = 0.25f),
                    radius = size.minDimension * 0.24f,
                    center = Offset(size.width * 0.55f, size.height * 0.48f),
                    style = Stroke(width = 1.1.dp.toPx())
                )
                drawCircle(
                    color = atlasAccent,
                    radius = size.minDimension * 0.10f,
                    center = Offset(size.width * 0.55f, size.height * 0.48f)
                )
            }
        }
    }
}
