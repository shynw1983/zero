package com.zero.todo.feature.execute

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zero.todo.R
import com.zero.todo.core.designsystem.ZeroCapsuleShape
import com.zero.todo.core.designsystem.ZeroMotion
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.parseFolderColorHex
import com.zero.todo.core.designsystem.rememberZeroHaptics
import com.zero.todo.domain.model.EffortTier
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.feature.focus.FocusPhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Execution Theme 1: MONOLITH
 * Pure matte obsidian #09090B canvas with zero card borders, forms, or button clutter.
 * - Center task title floats over a Signal Orange (#FF4D00) breathing radial aura scaled by effort.
 * - Long-press center: aura expands with linear haptics -> directly starts 2-Min Starter countdown.
 * - Horizontal swipe left/right: glides seamlessly to previous / next runway task.
 * - Tap title: opens a minimalist BottomSheet for 5m/25m/1h effort, First Tiny Step, Complete, or Defer.
 */
@Composable
fun MonolithThemeView(
    state: ExecuteUiState,
    onFocusPreviousTask: () -> Unit,
    onFocusNextTask: () -> Unit,
    onStartFocus: (TaskItem?, FocusMode) -> Unit,
    onPauseFocus: () -> Unit,
    onStartOrResumeFocus: () -> Unit,
    onContinueFocusToPomodoro: () -> Unit,
    onCompleteTask: (TaskItem) -> Unit,
    onCompleteFocusedTask: (TaskItem) -> Unit,
    onExitFocus: () -> Unit,
    onSkipTask: (TaskItem) -> Unit,
    onUpdateEffort: (TaskItem, Int?) -> Unit,
    onUpdateFirstStep: (TaskItem, String?) -> Unit,
    onCompleteFirstStepAndStartFlow: (TaskItem) -> Unit,
    onOpenTaskDetail: (TaskItem) -> Unit,
    onOpenTriage: () -> Unit,
    onSwitchToVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val haptics = rememberZeroHaptics()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val activeTask = state.focusedTask
    val canCycle = state.runwayQueue.size > 1
    var showTaskSheet by remember { mutableStateOf(false) }

    val longPressProgress = remember(activeTask?.id) { Animatable(0f) }
    val swipeOffsetX = remember(activeTask?.id) { Animatable(0f) }
    var stageWidthPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(activeTask?.id) {
        longPressProgress.snapTo(0f)
        swipeOffsetX.snapTo(0f)
    }

    val effortScale = when (activeTask?.estimatedMinutes) {
        EffortTier.MICRO.minutes -> 0.82f
        EffortTier.MEDIUM.minutes -> 1.04f
        EffortTier.HEAVY.minutes -> 1.24f
        else -> 0.94f
    }
    val effortAlphaBoost = when (activeTask?.estimatedMinutes) {
        EffortTier.MICRO.minutes -> 0.16f
        EffortTier.MEDIUM.minutes -> 0.24f
        EffortTier.HEAVY.minutes -> 0.34f
        else -> 0.20f
    }
    val flowBoost by animateFloatAsState(
        targetValue = if (state.isInlineFlowActive) 1f else 0f,
        animationSpec = ZeroMotion.FloatSpring,
        label = "monolith_flow_boost"
    )

    val currentPrev by rememberUpdatedState(onFocusPreviousTask)
    val currentNext by rememberUpdatedState(onFocusNextTask)
    val currentStartTwoMin by rememberUpdatedState {
        if (activeTask != null) {
            onStartFocus(activeTask, FocusMode.TWO_MIN_STARTER)
        }
    }

    val swipeThresholdPx = with(density) { 72.dp.toPx() }
    val touchSlopPx = with(density) { 18.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MonolithBlack),
        contentAlignment = Alignment.Center
    ) {
        // Breathing Signal Orange (#FF4D00) Radial Aura
        MonolithBreathingAura(
            baseScale = effortScale,
            baseAlpha = effortAlphaBoost,
            holdCharge = longPressProgress.value,
            flowBoost = flowBoost,
            modifier = Modifier.fillMaxSize()
        )

        if (activeTask == null) {
            Column(
                modifier = Modifier
                    .widthIn(max = ExecuteMaxContentWidth)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.now_empty_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.now_empty_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.triageCount > 0) {
                        Box(
                            modifier = Modifier
                                .height(48.dp)
                                .clip(ZeroCapsuleShape)
                                .background(SignalOrange)
                                .clickable {
                                    haptics.confirm()
                                    onOpenTriage()
                                }
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.now_start_triage),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .clip(ZeroCapsuleShape)
                            .background(Color.White.copy(alpha = 0.06f))
                            .hairlineBorder(shape = ZeroCapsuleShape, color = Color.White.copy(alpha = 0.16f))
                            .clickable {
                                haptics.reorderTick()
                                onSwitchToVault()
                            }
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.now_open_vault),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        } else {
            // Pure Gesture-Driven Centered Single-Task Sanctuary
            Box(
                modifier = Modifier
                    .widthIn(max = ExecuteMaxContentWidth)
                    .fillMaxSize()
                    .pointerInput(activeTask.id, canCycle, state.isInlineFlowActive) {
                        stageWidthPx = size.width.toFloat()
                        if (!canCycle || state.isInlineFlowActive) return@pointerInput
                        val velocityTracker = VelocityTracker()
                        detectHorizontalDragGestures(
                            onDragStart = {
                                velocityTracker.resetTracking()
                            },
                            onDragEnd = {
                                val velocityX = velocityTracker.calculateVelocity().x
                                val dx = swipeOffsetX.value
                                val triggered = abs(dx) >= swipeThresholdPx ||
                                    (abs(velocityX) >= 750f && abs(dx) >= swipeThresholdPx * 0.35f)
                                if (triggered) {
                                    haptics.reorderTick()
                                    val goNext = dx < 0f || (abs(dx) < 8f && velocityX < 0f)
                                    scope.launch {
                                        val exitX = (if (goNext) -1f else 1f) * maxOf(stageWidthPx * 0.65f, 260f)
                                        swipeOffsetX.animateTo(exitX, tween(140))
                                        if (goNext) currentNext() else currentPrev()
                                        swipeOffsetX.snapTo(0f)
                                    }
                                } else {
                                    scope.launch {
                                        swipeOffsetX.animateTo(0f, ZeroMotion.FloatSpring)
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    swipeOffsetX.animateTo(0f, ZeroMotion.FloatSpring)
                                }
                            }
                        ) { change, dragAmount ->
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            scope.launch {
                                swipeOffsetX.snapTo(swipeOffsetX.value + dragAmount)
                            }
                        }
                    }
                    .pointerInput(activeTask.id, state.isInlineFlowActive) {
                        if (state.isInlineFlowActive) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var launched = false
                            val holdJob = scope.launch {
                                // Wait brief slop window so quick taps/swipes don't pulse unnecessarily
                                delay(95L)
                                haptics.reorderTick()
                                longPressProgress.snapTo(0.15f)
                                longPressProgress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(durationMillis = 260, easing = LinearEasing)
                                )
                                launched = true
                                haptics.confirm()
                                currentStartTwoMin()
                            }
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!pointer.pressed) break
                                    if ((pointer.position - down.position).getDistance() > touchSlopPx) {
                                        break
                                    }
                                }
                            } finally {
                                if (!launched) {
                                    holdJob.cancel(CancellationException("Gesture released or moved"))
                                }
                                scope.launch {
                                    longPressProgress.animateTo(0f, ZeroMotion.FloatSpring)
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val dx = swipeOffsetX.value
                            val ref = maxOf(stageWidthPx, 1f)
                            translationX = dx * 0.85f
                            alpha = (1f - (abs(dx) / (ref * 0.65f))).coerceIn(0.15f, 1f)
                        }
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val folderBadge = activeTask.folderId?.let(state.monolithFolderBadgeById::get)
                    val fallbackFolderName = activeTask.folderId?.let(state.folderNameById::get)
                    val effortLabel = activeTask.estimatedMinutes?.let { "${it}m" }
                    val dotColor = parseFolderColorHex(folderBadge?.colorHex)
                    if (folderBadge != null || fallbackFolderName != null || effortLabel != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (dotColor != null) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                            }
                            val caption = buildAnnotatedString {
                                if (folderBadge != null) {
                                    if (!folderBadge.prefix.isNullOrBlank()) {
                                        withStyle(SpanStyle(color = colors.textMuted.copy(alpha = 0.60f))) {
                                            append(folderBadge.prefix)
                                        }
                                    }
                                    withStyle(SpanStyle(color = colors.textMuted)) {
                                        append(folderBadge.leaf)
                                    }
                                } else if (fallbackFolderName != null) {
                                    withStyle(SpanStyle(color = colors.textMuted)) {
                                        append(fallbackFolderName)
                                    }
                                }
                                if (effortLabel != null) {
                                    if (folderBadge != null || fallbackFolderName != null) {
                                        withStyle(SpanStyle(color = colors.textMuted.copy(alpha = 0.80f))) {
                                            append(" · ")
                                        }
                                    }
                                    withStyle(SpanStyle(color = colors.textMuted.copy(alpha = 0.80f))) {
                                        append(effortLabel)
                                    }
                                }
                            }
                            Text(
                                text = caption,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    val parentTitle = state.parentTaskTitleById[activeTask.id]
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (!parentTitle.isNullOrBlank()) {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(color = colors.textMuted)) { append("↳ ") }
                                    withStyle(SpanStyle(color = colors.textPrimary.copy(alpha = 0.78f))) {
                                        append(parentTitle)
                                    }
                                },
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Centered Large Title (Tap -> Minimalist BottomSheet)
                        Text(
                            text = activeTask.title,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontSize = 30.sp,
                                lineHeight = 38.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = colors.textPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .clip(ZeroSmallShape)
                                .clickable {
                                    haptics.reorderTick()
                                    showTaskSheet = true
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    if (!activeTask.firstStep.isNullOrBlank() && !state.isInlineFlowActive) {
                        Text(
                            text = activeTask.firstStep,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SignalOrange.copy(alpha = 0.88f),
                            textAlign = TextAlign.Center
                        )
                    }

                    // Active 2-Minute Countdown & Flow Controls Directly Below Title
                    if (state.isInlineFlowActive) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = state.focusState.formattedTimer,
                                style = tabular.timerHero,
                                color = colors.textPrimary
                            )
                            Box(
                                modifier = Modifier
                                    .width(180.dp)
                                    .height(2.dp)
                                    .clip(ZeroCapsuleShape)
                                    .background(Color.White.copy(alpha = 0.12f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(state.focusState.progress.coerceIn(0f, 1f))
                                        .height(2.dp)
                                        .background(SignalOrange)
                                )
                            }

                            if (state.focusState.phase == FocusPhase.TWO_MIN_FINISHED_PROMPT) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .height(44.dp)
                                            .clip(ZeroCapsuleShape)
                                            .background(SignalOrange)
                                            .clickable {
                                                haptics.confirm()
                                                onContinueFocusToPomodoro()
                                            }
                                            .padding(horizontal = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.focus_btn_continue_pomodoro),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .clickable {
                                                haptics.taskComplete()
                                                onCompleteFocusedTask(activeTask)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = stringResource(R.string.cd_complete_task),
                                            tint = SignalOrange
                                        )
                                    }
                                }
                            } else {
                                val isRunning = state.focusState.phase == FocusPhase.RUNNING
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.07f))
                                            .clickable {
                                                haptics.reorderTick()
                                                onExitFocus()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.now_exit_flow),
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(SignalOrange.copy(alpha = 0.22f))
                                            .hairlineBorder(shape = CircleShape, color = SignalOrange)
                                            .clickable {
                                                haptics.confirm()
                                                if (isRunning) onPauseFocus() else onStartOrResumeFocus()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            tint = SignalOrange,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(SignalOrange)
                                            .clickable {
                                                haptics.taskComplete()
                                                onCompleteFocusedTask(activeTask)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = stringResource(R.string.cd_complete_task),
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (!state.isInlineFlowActive) {
                    Text(
                        text = stringResource(R.string.monolith_gesture_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 24.dp, vertical = 14.dp)
                            .alpha(0.65f)
                    )
                }
            }
        }
    }

    if (showTaskSheet && activeTask != null) {
        MonolithTaskActionSheet(
            task = activeTask,
            onDismiss = { showTaskSheet = false },
            onSelectEffort = { onUpdateEffort(activeTask, it) },
            onUpdateFirstStep = { onUpdateFirstStep(activeTask, it) },
            onCompleteFirstStep = {
                showTaskSheet = false
                onCompleteFirstStepAndStartFlow(activeTask)
            },
            onStartTwoMin = {
                showTaskSheet = false
                onStartFocus(activeTask, FocusMode.TWO_MIN_STARTER)
            },
            onComplete = {
                showTaskSheet = false
                onCompleteTask(activeTask)
            },
            onDefer = {
                showTaskSheet = false
                onSkipTask(activeTask)
            },
            onOpenDetail = {
                showTaskSheet = false
                onOpenTaskDetail(activeTask)
            }
        )
    }
}

@Composable
private fun MonolithBreathingAura(
    baseScale: Float,
    baseAlpha: Float,
    holdCharge: Float,
    flowBoost: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "monolith_aura_breathe")
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "monolith_breathe"
    )

    Canvas(modifier = modifier) {
        if (size.minDimension <= 0f) return@Canvas
        val center = Offset(size.width * 0.5f, size.height * 0.46f)
        val radius = (size.minDimension * (0.42f * baseScale + 0.08f * breathe + 0.24f * holdCharge + 0.14f * flowBoost))
            .coerceAtLeast(1f)
        val alpha = (baseAlpha + 0.10f * breathe + 0.30f * holdCharge + 0.14f * flowBoost).coerceAtMost(0.68f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    SignalOrange.copy(alpha = alpha),
                    SignalOrange.copy(alpha = alpha * 0.35f),
                    Color.Transparent
                ),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonolithTaskActionSheet(
    task: TaskItem,
    onDismiss: () -> Unit,
    onSelectEffort: (Int?) -> Unit,
    onUpdateFirstStep: (String?) -> Unit,
    onCompleteFirstStep: () -> Unit,
    onStartTwoMin: () -> Unit,
    onComplete: () -> Unit,
    onDefer: () -> Unit,
    onOpenDetail: () -> Unit
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val haptics = rememberZeroHaptics()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var firstStepDraft by remember(task.id, task.firstStep) { mutableStateOf(task.firstStep.orEmpty()) }

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
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(ZeroSmallShape)
                        .background(MonolithBlack)
                        .hairlineBorder(shape = ZeroSmallShape, color = Color(0xFF27272A))
                        .clickable {
                            haptics.reorderTick()
                            onOpenDetail()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = stringResource(R.string.cd_open_task_detail),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 5m / 25m / 1h Weight Adjustment
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EffortTier.entries.forEach { tier ->
                    val selected = task.estimatedMinutes == tier.minutes
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(ZeroSmallShape)
                            .background(if (selected) SignalOrange.copy(alpha = 0.16f) else MonolithBlack)
                            .hairlineBorder(
                                shape = ZeroSmallShape,
                                color = if (selected) SignalOrange else Color(0xFF27272A)
                            )
                            .clickable {
                                haptics.reorderTick()
                                onSelectEffort(if (selected) null else tier.minutes)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${tier.shortCode} · ${stringResource(tier.labelRes)}",
                            style = tabular.dateCompact,
                            color = if (selected) SignalOrange else colors.textSecondary
                        )
                    }
                }
            }

            // First Tiny Step (Icebreaker)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ZeroSmallShape)
                    .background(MonolithBlack)
                    .hairlineBorder(shape = ZeroSmallShape, color = Color(0xFF27272A))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = SignalOrange,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(R.string.now_first_step_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = SignalOrange
                    )
                }

                if (!task.firstStep.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 42.dp)
                            .clip(ZeroSmallShape)
                            .background(Color(0xFF141417))
                            .clickable {
                                haptics.confirm()
                                onCompleteFirstStep()
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = SignalOrange,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = task.firstStep,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                textDecoration = if (task.firstStepCompleted) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            color = colors.textPrimary
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 42.dp)
                        .clip(ZeroSmallShape)
                        .background(Color(0xFF141417))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (firstStepDraft.isEmpty()) {
                            Text(
                                text = stringResource(R.string.now_first_step_placeholder),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = firstStepDraft,
                            onValueChange = { firstStepDraft = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
                            cursorBrush = SolidColor(SignalOrange),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    haptics.reorderTick()
                                    onUpdateFirstStep(firstStepDraft)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    AnimatedVisibility(visible = firstStepDraft.trim() != task.firstStep.orEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(SignalOrange)
                                .clickable {
                                    haptics.confirm()
                                    onUpdateFirstStep(firstStepDraft)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.action_save),
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            // Defer / 2m Starter / Complete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(ZeroCapsuleShape)
                        .background(MonolithBlack)
                        .hairlineBorder(shape = ZeroCapsuleShape, color = Color(0xFF27272A))
                        .clickable {
                            haptics.reorderTick()
                            onDefer()
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = stringResource(R.string.now_skip_card),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.textSecondary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(ZeroCapsuleShape)
                        .background(SignalOrange.copy(alpha = 0.14f))
                        .hairlineBorder(shape = ZeroCapsuleShape, color = SignalOrange.copy(alpha = 0.7f))
                        .clickable {
                            haptics.confirm()
                            onStartTwoMin()
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.detail_start_2min),
                        style = MaterialTheme.typography.labelLarge,
                        color = SignalOrange
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(ZeroCapsuleShape)
                        .background(SignalOrange)
                        .clickable {
                            haptics.taskComplete()
                            onComplete()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.cd_complete_task),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
