package com.zero.todo.feature.now

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.zero.todo.R
import com.zero.todo.core.common.ZeroDateFormatter
import com.zero.todo.core.designsystem.ZeroCapsuleShape
import com.zero.todo.core.designsystem.ZeroMotion
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.rememberZeroHaptics
import com.zero.todo.core.nlp.ParsedQuickInput
import com.zero.todo.domain.model.EffortTier
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.TaskPriority
import com.zero.todo.feature.focus.FocusHapticKind
import com.zero.todo.feature.focus.FocusPhase
import com.zero.todo.feature.focus.FocusUiState
import com.zero.todo.feature.tasklist.FolderPathSuggestion
import com.zero.todo.feature.tasklist.QuickInputCapsule
import com.zero.todo.feature.tasklist.UndoBannerState
import com.zero.todo.feature.tasklist.UndoCapsule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

private val DarkroomVoid = Color(0xFF060608)
private val DarkroomHeroSurface = Color(0xFF101015)
private val DarkroomBackCardTwo = Color(0xFF0C0C10)
private val DarkroomBackCardThree = Color(0xFF09090D)
private val DarkroomDeckShape = RoundedCornerShape(22.dp)

/**
 * ZERO v2.2.0 "NOW" Darkroom Execution Runway (Direction B — Darkroom Spotlight & Breathing Aura).
 * Intentionally contrasts the ultra-OCD Swiss VAULT Studio with a pure sensory darkroom stage:
 * - Always matte #060608 darkroom backdrop + breathing overhead spotlight & hairline concentric aura (no particles)
 * - 3D stacked physical task deck with dynamic rear-card rise, fling-to-next, and mechanical Blind Draw
 * - Inline Darkroom Flow inside the hero card + progressive tactile charge hold-to-complete
 */
@Composable
fun NowRunwayScreen(
    currentDate: LocalDate,
    runwayQueue: List<TaskItem>,
    scheduledTodayMinutes: Int,
    dailyCapacityMinutes: Int,
    todayCompletedCount: Int,
    triageCount: Int,
    folderNameById: Map<String, String>,
    quickInputText: String,
    parsedQuickInput: ParsedQuickInput,
    folderSuggestions: List<FolderPathSuggestion>,
    continuousEntryEnabled: Boolean,
    undoBanner: UndoBannerState?,
    focusState: FocusUiState,
    isInlineFlowActive: Boolean,
    quickInputFocusRequester: FocusRequester,
    onCompleteTask: (TaskItem) -> Unit,
    onSkipTask: (TaskItem) -> Unit,
    onShuffleRunway: () -> Unit,
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
    onUndo: (UndoBannerState) -> Unit,
    onUndoTimeout: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val haptics = rememberZeroHaptics()
    val scope = rememberCoroutineScope()

    val visibleDeck = remember(runwayQueue) { runwayQueue.take(3) }
    val activeTask = visibleDeck.firstOrNull()

    // Reactive spotlight charge boost driven by Hero Card Hold-to-Complete progress
    var activeHoldCharge by remember(activeTask?.id) { mutableFloatStateOf(0f) }

    // Fire tactile feedback on focus timer transitions while inline darkroom flow is open
    LaunchedEffect(focusState.hapticSignalId, isInlineFlowActive) {
        if (!isInlineFlowActive || focusState.hapticSignalId == 0L) return@LaunchedEffect
        when (focusState.hapticKind) {
            FocusHapticKind.GENTLE_TWO_MIN_DONE -> haptics.gentlePrompt()
            FocusHapticKind.HEAVY_POMODORO_DONE -> haptics.confirm()
            FocusHapticKind.NONE -> Unit
        }
    }

    val headerAlpha by animateFloatAsState(
        targetValue = if (isInlineFlowActive) 0.24f else 1f,
        animationSpec = ZeroMotion.FloatSpring,
        label = "darkroom_header_alpha"
    )
    val bottomDockAlpha by animateFloatAsState(
        targetValue = if (isInlineFlowActive) 0.18f else 1f,
        animationSpec = ZeroMotion.FloatSpring,
        label = "darkroom_dock_alpha"
    )
    val flowBoost by animateFloatAsState(
        targetValue = if (isInlineFlowActive) 1f else 0f,
        animationSpec = ZeroMotion.FloatSpring,
        label = "darkroom_flow_boost"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkroomVoid)
            .imePadding()
    ) {
        // Full-Bleed Breathing Overhead Spotlight & Concentric Hairline Aura (NO particles)
        DarkroomSpotlightBackdrop(
            accentColor = colors.accent,
            flowBoost = flowBoost,
            chargeBoost = activeHoldCharge,
            modifier = Modifier.fillMaxSize()
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Airy Darkroom Top Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = headerAlpha }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colors.accent)
                        )
                        Text(
                            text = stringResource(R.string.now_header_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary
                        )
                        if (runwayQueue.isNotEmpty()) {
                            Text(
                                text = "01 / ${runwayQueue.size.toString().padStart(2, '0')}",
                                style = tabular.dateCompact,
                                color = colors.textMuted
                            )
                        }
                        if (todayCompletedCount > 0) {
                            TodayRhythmDots(
                                completedCount = todayCompletedCount,
                                accentColor = colors.accent,
                                mutedColor = colors.textMuted
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (runwayQueue.size > 1 && !isInlineFlowActive) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(ZeroSmallShape)
                                    .background(DarkroomHeroSurface)
                                    .hairlineBorder(
                                        shape = ZeroSmallShape,
                                        color = colors.accent.copy(alpha = 0.45f)
                                    )
                                    .clickable {
                                        scope.launch {
                                            haptics.reorderTick()
                                            delay(45L)
                                            haptics.reorderTick()
                                            delay(55L)
                                            haptics.confirm()
                                            onShuffleRunway()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = stringResource(R.string.now_shuffle_draw),
                                    tint = colors.accent,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        if (triageCount > 0) {
                            Row(
                                modifier = Modifier
                                    .heightIn(min = 42.dp)
                                    .clip(ZeroCapsuleShape)
                                    .background(colors.accentSubtle)
                                    .hairlineBorder(shape = ZeroCapsuleShape, color = colors.accent)
                                    .clickable {
                                        haptics.confirm()
                                        onOpenTriage()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Layers,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.now_triage_pill, triageCount),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.accent
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(ZeroSmallShape)
                                .background(DarkroomHeroSurface)
                                .hairlineBorder(shape = ZeroSmallShape, color = Color.White.copy(alpha = 0.12f))
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
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Ultra-Slim Luminous Day Capacity Bar
                SlimLuminousCapacityBar(
                    scheduledMinutes = scheduledTodayMinutes,
                    capacityMinutes = dailyCapacityMinutes,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Center Spotlight Stage
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (activeTask == null) {
                    DarkroomEmptyRunwayCard(
                        triageCount = triageCount,
                        todayCompletedCount = todayCompletedCount,
                        onOpenTriage = onOpenTriage,
                        onSwitchToVault = onSwitchToVault,
                        modifier = Modifier.widthIn(max = 560.dp)
                    )
                } else {
                    StackedDarkroomDeck(
                        visibleDeck = visibleDeck,
                        totalRunwayCount = runwayQueue.size,
                        folderNameById = folderNameById,
                        focusState = focusState,
                        isInlineFlowActive = isInlineFlowActive,
                        onChargeProgressChanged = { activeHoldCharge = it },
                        onCompleteTask = onCompleteTask,
                        onSkipTask = onSkipTask,
                        onOpenTaskDetail = onOpenTaskDetail,
                        onStartFocus = onStartFocus,
                        onSelectFocusMode = onSelectFocusMode,
                        onStartOrResumeFocus = onStartOrResumeFocus,
                        onPauseFocus = onPauseFocus,
                        onResetFocus = onResetFocus,
                        onContinueFocusToPomodoro = onContinueFocusToPomodoro,
                        onStopStopwatchFocus = onStopStopwatchFocus,
                        onCompleteFocusedTask = onCompleteFocusedTask,
                        onExitFocus = onExitFocus,
                        onUpdateFirstStep = onUpdateFirstStep,
                        onCompleteFirstStepAndStartFlow = onCompleteFirstStepAndStartFlow,
                        onUpdateEffort = onUpdateEffort,
                        modifier = Modifier.widthIn(max = 560.dp)
                    )
                }
            }

            // Bottom Undo + Quick Capture (dims quietly during Inline Darkroom Flow)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = bottomDockAlpha }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UndoCapsule(
                    banner = undoBanner,
                    onUndo = onUndo,
                    onTimeout = onUndoTimeout
                )
                QuickInputCapsule(
                    text = quickInputText,
                    parsed = parsedQuickInput,
                    folderSuggestions = folderSuggestions,
                    continuousEntryEnabled = continuousEntryEnabled,
                    focusRequester = quickInputFocusRequester,
                    onEditText = onQuickInputEdit,
                    onSubmit = onQuickInputSubmit,
                    onToggleContinuousEntry = onToggleContinuousEntry,
                    onVoiceInputRecognized = onVoiceInputRecognized,
                    currentDate = currentDate
                )
            }
        }
    }
}

@Composable
private fun DarkroomSpotlightBackdrop(
    accentColor: Color,
    flowBoost: Float,
    chargeBoost: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "darkroom_spotlight_breathe")
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe_phase"
    )

    Canvas(modifier = modifier) {
        val center = Offset(x = size.width * 0.5f, y = size.height * 0.36f)
        val deckCore = Offset(x = size.width * 0.5f, y = size.height * 0.45f)
        val baseRadius = size.minDimension * (0.72f + 0.08f * breathe + 0.16f * flowBoost + 0.22f * chargeBoost)
        val outerAlpha = (0.14f + 0.06f * breathe + 0.12f * flowBoost + 0.18f * chargeBoost).coerceAtMost(0.45f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = outerAlpha),
                    accentColor.copy(alpha = outerAlpha * 0.35f),
                    Color.Transparent
                ),
                center = center,
                radius = baseRadius
            ),
            radius = baseRadius,
            center = center
        )

        val coreRadius = size.minDimension * (0.42f + 0.05f * breathe + 0.10f * chargeBoost)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = (0.08f + 0.08f * flowBoost + 0.16f * chargeBoost).coerceAtMost(0.32f)),
                    Color.Transparent
                ),
                center = deckCore,
                radius = coreRadius
            ),
            radius = coreRadius,
            center = deckCore
        )

        // Two precision 0.5.dp hairline concentric spotlight rings (strictly no particles)
        val ringStroke = 0.5.dp.toPx()
        val innerRingRadius = size.minDimension * (0.34f + 0.025f * breathe + 0.04f * flowBoost)
        val outerRingRadius = size.minDimension * (0.50f + 0.035f * breathe + 0.06f * flowBoost)

        drawCircle(
            color = accentColor.copy(alpha = (0.11f + 0.08f * flowBoost + 0.16f * chargeBoost).coerceAtMost(0.35f)),
            radius = innerRingRadius,
            center = center,
            style = Stroke(width = ringStroke)
        )
        drawCircle(
            color = accentColor.copy(alpha = (0.05f + 0.05f * flowBoost + 0.10f * chargeBoost).coerceAtMost(0.22f)),
            radius = outerRingRadius,
            center = center,
            style = Stroke(width = ringStroke)
        )
    }
}

@Composable
private fun TodayRhythmDots(
    completedCount: Int,
    accentColor: Color,
    mutedColor: Color
) {
    val tabular = ZeroTheme.tabularTypography
    val shownDots = completedCount.coerceAtMost(6)
    val overflow = (completedCount - 6).coerceAtLeast(0)
    Row(
        modifier = Modifier
            .clip(ZeroCapsuleShape)
            .background(Color.White.copy(alpha = 0.05f))
            .hairlineBorder(shape = ZeroCapsuleShape, color = Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(shownDots) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
        }
        if (overflow > 0) {
            Text(
                text = "+$overflow",
                style = tabular.dateCompact,
                color = mutedColor
            )
        }
    }
}

@Composable
private fun SlimLuminousCapacityBar(
    scheduledMinutes: Int,
    capacityMinutes: Int,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val ratio = if (capacityMinutes > 0) (scheduledMinutes.toFloat() / capacityMinutes.toFloat()).coerceIn(0f, 1.3f) else 0f
    val clampedRatio by animateFloatAsState(
        targetValue = ratio.coerceIn(0f, 1f),
        animationSpec = ZeroMotion.FloatSpring,
        label = "slim_capacity_fill"
    )
    val overloaded = scheduledMinutes > capacityMinutes && capacityMinutes > 0
    val barColor = if (overloaded) colors.priorityUrgent else colors.accent

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.capacity_title),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted
            )
            Text(
                text = "${formatShortDuration(scheduledMinutes)} / ${formatShortDuration(capacityMinutes)}",
                style = tabular.dateCompact,
                color = if (overloaded) colors.priorityUrgent else colors.textSecondary
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(ZeroCapsuleShape)
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            if (clampedRatio > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(clampedRatio)
                        .height(3.dp)
                        .clip(ZeroCapsuleShape)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(barColor.copy(alpha = 0.55f), barColor)
                            )
                        )
                )
            }
        }
    }
}

private fun formatShortDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h${m}m"
        h > 0 -> "${h}h"
        else -> "${minutes}m"
    }
}

@Composable
private fun DarkroomEmptyRunwayCard(
    triageCount: Int,
    todayCompletedCount: Int,
    onOpenTriage: () -> Unit,
    onSwitchToVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val haptics = rememberZeroHaptics()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(DarkroomDeckShape)
            .background(DarkroomHeroSurface.copy(alpha = 0.94f))
            .hairlineBorder(shape = DarkroomDeckShape, color = colors.accent.copy(alpha = 0.35f))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.accentSubtle)
                .hairlineBorder(shape = CircleShape, color = colors.accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = stringResource(R.string.now_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
            textAlign = TextAlign.Center
        )
        if (todayCompletedCount > 0) {
            Text(
                text = stringResource(R.string.now_rhythm_done, todayCompletedCount),
                style = ZeroTheme.tabularTypography.dateCompact,
                color = colors.accent
            )
        }
        Text(
            text = stringResource(R.string.now_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (triageCount > 0) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(ZeroCapsuleShape)
                        .background(colors.accent)
                        .clickable {
                            haptics.confirm()
                            onOpenTriage()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.now_start_triage),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onAccent
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(ZeroCapsuleShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .hairlineBorder(shape = ZeroCapsuleShape, color = Color.White.copy(alpha = 0.14f))
                    .clickable {
                        haptics.reorderTick()
                        onSwitchToVault()
                    },
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
}

@Composable
private fun StackedDarkroomDeck(
    visibleDeck: List<TaskItem>,
    totalRunwayCount: Int,
    folderNameById: Map<String, String>,
    focusState: FocusUiState,
    isInlineFlowActive: Boolean,
    onChargeProgressChanged: (Float) -> Unit,
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
    modifier: Modifier = Modifier
) {
    val activeTask = visibleDeck.first()
    val secondTask = visibleDeck.getOrNull(1)
    val thirdTask = visibleDeck.getOrNull(2)
    val colors = ZeroTheme.colors
    val density = LocalDensity.current
    val dragThresholdPx = with(density) { 96.dp.toPx() }
    var dragRatio by remember(activeTask.id) { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (visibleDeck.size >= 3) 26.dp else if (visibleDeck.size == 2) 14.dp else 0.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            // Card #3 — Deepest deck anchor layer
            if (thirdTask != null && !isInlineFlowActive) {
                DeckBackingCard(
                    task = thirdTask,
                    accentColor = colors.accent,
                    surfaceColor = DarkroomBackCardThree,
                    modifier = Modifier
                        .zIndex(1f)
                        .graphicsLayer {
                            val rise = dragRatio.coerceIn(0f, 1f)
                            translationY = (26.dp.toPx() - 10.dp.toPx() * rise)
                            val scale = 0.88f + 0.04f * rise
                            scaleX = scale
                            scaleY = scale
                            alpha = 0.36f + 0.16f * rise
                        }
                )
            }

            // Card #2 — Rises dynamically as the hero card is flicked aside
            if (secondTask != null && !isInlineFlowActive) {
                DeckBackingCard(
                    task = secondTask,
                    accentColor = colors.accent,
                    surfaceColor = DarkroomBackCardTwo,
                    modifier = Modifier
                        .zIndex(2f)
                        .graphicsLayer {
                            val rise = dragRatio.coerceIn(0f, 1f)
                            translationY = 14.dp.toPx() * (1f - rise)
                            val scale = 0.94f + 0.06f * rise
                            scaleX = scale
                            scaleY = scale
                            alpha = 0.65f + 0.35f * rise
                        }
                )
            }

            // Card #1 — Hero Darkroom Spotlight Card
            HeroDarkroomCard(
                task = activeTask,
                folderName = activeTask.folderId?.let(folderNameById::get),
                canSkip = totalRunwayCount > 1,
                focusState = focusState,
                isInlineFlowActive = isInlineFlowActive,
                dragThresholdPx = dragThresholdPx,
                onDragFractionChanged = { dragRatio = it },
                onChargeProgressChanged = onChargeProgressChanged,
                onComplete = { onCompleteTask(activeTask) },
                onSkip = { onSkipTask(activeTask) },
                onOpenDetail = { onOpenTaskDetail(activeTask) },
                onStartTwoMin = { onStartFocus(activeTask, FocusMode.TWO_MIN_STARTER) },
                onSelectFocusMode = onSelectFocusMode,
                onStartOrResumeFocus = onStartOrResumeFocus,
                onPauseFocus = onPauseFocus,
                onResetFocus = onResetFocus,
                onContinueFocusToPomodoro = onContinueFocusToPomodoro,
                onStopStopwatchFocus = onStopStopwatchFocus,
                onCompleteFocusedTask = { onCompleteFocusedTask(activeTask) },
                onExitFocus = onExitFocus,
                onUpdateFirstStep = { onUpdateFirstStep(activeTask, it) },
                onCompleteFirstStep = { onCompleteFirstStepAndStartFlow(activeTask) },
                onSelectEffort = { onUpdateEffort(activeTask, it) },
                modifier = Modifier.zIndex(3f)
            )
        }

        if (totalRunwayCount > 1 && !isInlineFlowActive) {
            Text(
                text = stringResource(R.string.now_swipe_hint),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(0.75f)
            )
        }
    }
}

@Composable
private fun DeckBackingCard(
    task: TaskItem,
    accentColor: Color,
    surfaceColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(DarkroomDeckShape)
            .background(surfaceColor)
            .hairlineBorder(
                shape = DarkroomDeckShape,
                color = accentColor.copy(alpha = 0.16f)
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(R.string.now_up_next),
                style = tabular.dateCompact,
                color = accentColor
            )
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (task.estimatedMinutes != null) {
            Text(
                text = "${task.estimatedMinutes}m",
                style = tabular.dateCompact,
                color = colors.textMuted
            )
        }
    }
}

@Composable
private fun HeroDarkroomCard(
    task: TaskItem,
    folderName: String?,
    canSkip: Boolean,
    focusState: FocusUiState,
    isInlineFlowActive: Boolean,
    dragThresholdPx: Float,
    onDragFractionChanged: (Float) -> Unit,
    onChargeProgressChanged: (Float) -> Unit,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onOpenDetail: () -> Unit,
    onStartTwoMin: () -> Unit,
    onSelectFocusMode: (FocusMode) -> Unit,
    onStartOrResumeFocus: () -> Unit,
    onPauseFocus: () -> Unit,
    onResetFocus: () -> Unit,
    onContinueFocusToPomodoro: () -> Unit,
    onStopStopwatchFocus: () -> Unit,
    onCompleteFocusedTask: () -> Unit,
    onExitFocus: () -> Unit,
    onUpdateFirstStep: (String?) -> Unit,
    onCompleteFirstStep: () -> Unit,
    onSelectEffort: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val haptics = rememberZeroHaptics()
    val scope = rememberCoroutineScope()
    val activeLocale = LocalConfiguration.current.locales[0]

    val currentComplete by rememberUpdatedState(onComplete)
    val currentSkip by rememberUpdatedState(onSkip)

    var firstStepDraft by remember(task.id, task.firstStep) { mutableStateOf(task.firstStep.orEmpty()) }
    val swipeOffsetX = remember(task.id) { Animatable(0f) }
    val holdProgress = remember(task.id) { Animatable(0f) }

    LaunchedEffect(task.id) {
        swipeOffsetX.snapTo(0f)
        holdProgress.snapTo(0f)
        onDragFractionChanged(0f)
        onChargeProgressChanged(0f)
    }

    LaunchedEffect(swipeOffsetX.value) {
        onDragFractionChanged((abs(swipeOffsetX.value) / dragThresholdPx).coerceIn(0f, 1f))
    }

    LaunchedEffect(holdProgress.value) {
        onChargeProgressChanged(holdProgress.value)
    }

    val priorityColor = when (task.priority) {
        TaskPriority.URGENT -> colors.priorityUrgent
        TaskPriority.HIGH -> colors.priorityHigh
        TaskPriority.MEDIUM -> colors.priorityMedium
        TaskPriority.NONE -> colors.textMuted
    }

    val deckCornerPx = with(LocalDensity.current) { 22.dp.toPx() }
    val glowStrokePx = with(LocalDensity.current) { 1.5.dp.toPx() }
    val hairlineStrokePx = with(LocalDensity.current) { 0.5.dp.toPx() }
    val maxDragPx = dragThresholdPx * 2.2f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(swipeOffsetX.value.roundToInt(), 0) }
            .graphicsLayer {
                rotationZ = (swipeOffsetX.value / (dragThresholdPx * 0.35f)).coerceIn(-7f, 7f)
            }
            .clip(DarkroomDeckShape)
            .background(DarkroomHeroSurface)
            .drawWithContent {
                drawContent()
                val charge = holdProgress.value
                // Overhead spotlight rim border (accent beam from top to subtle bottom rim)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            colors.accent.copy(alpha = if (isInlineFlowActive) 0.72f else 0.50f),
                            Color.White.copy(alpha = 0.14f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    ),
                    cornerRadius = CornerRadius(deckCornerPx, deckCornerPx),
                    style = Stroke(width = hairlineStrokePx)
                )
                // Luminous charge perimeter rim on Hold-to-Complete
                if (charge > 0f) {
                    drawRoundRect(
                        color = colors.accent.copy(alpha = (0.28f + 0.68f * charge).coerceAtMost(0.96f)),
                        cornerRadius = CornerRadius(deckCornerPx, deckCornerPx),
                        style = Stroke(width = glowStrokePx * (0.6f + charge))
                    )
                }
            }
            .pointerInput(task.id, canSkip, isInlineFlowActive) {
                if (!canSkip || isInlineFlowActive) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(swipeOffsetX.value) >= dragThresholdPx) {
                            haptics.reorderTick()
                            currentSkip()
                        }
                        scope.launch {
                            swipeOffsetX.animateTo(0f, animationSpec = ZeroMotion.FloatSpring)
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            swipeOffsetX.animateTo(0f, animationSpec = ZeroMotion.FloatSpring)
                        }
                    }
                ) { _, dragAmount ->
                    scope.launch {
                        swipeOffsetX.snapTo((swipeOffsetX.value + dragAmount).coerceIn(-maxDragPx, maxDragPx))
                    }
                }
            }
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card Header Meta Row + Open Detail (44dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (task.priority != TaskPriority.NONE) {
                    Box(
                        modifier = Modifier
                            .clip(ZeroCapsuleShape)
                            .background(priorityColor.copy(alpha = 0.16f))
                            .hairlineBorder(shape = ZeroCapsuleShape, color = priorityColor.copy(alpha = 0.7f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = task.priority.name,
                            style = tabular.priorityTag,
                            color = priorityColor
                        )
                    }
                }

                val dueMillis = task.dueDate
                if (dueMillis != null) {
                    val dueText = ZeroDateFormatter.formatDueDate(
                        dueDateMillis = dueMillis,
                        isAllDay = task.isAllDay,
                        locale = activeLocale
                    )
                    val overdue = ZeroDateFormatter.isOverdue(
                        dueDateMillis = dueMillis,
                        isAllDay = task.isAllDay
                    )
                    Text(
                        text = dueText,
                        style = tabular.dateCompact,
                        color = if (overdue) colors.priorityUrgent else colors.textSecondary
                    )
                }

                if (!folderName.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = folderName,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(ZeroSmallShape)
                    .background(Color.White.copy(alpha = 0.04f))
                    .hairlineBorder(shape = ZeroSmallShape, color = Color.White.copy(alpha = 0.10f))
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

        // High-Contrast Hero Task Title
        Text(
            text = task.title,
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimary
        )

        // Morphing Body: Execution Prep Deck <-> Inline Darkroom Flow Deck
        AnimatedContent(
            targetState = isInlineFlowActive,
            transitionSpec = {
                (fadeIn(ZeroMotion.FloatSpring) + scaleIn(
                    initialScale = 0.97f,
                    animationSpec = ZeroMotion.FloatSpring
                )) togetherWith (fadeOut(ZeroMotion.FloatSpring) + scaleOut(
                    targetScale = 0.97f,
                    animationSpec = ZeroMotion.FloatSpring
                ))
            },
            label = "hero_card_flow_morph"
        ) { flowActive ->
            if (flowActive) {
                InlineDarkroomFlowDeck(
                    focusState = focusState,
                    onSelectMode = onSelectFocusMode,
                    onStartOrResume = onStartOrResumeFocus,
                    onPause = onPauseFocus,
                    onReset = onResetFocus,
                    onContinueToPomodoro = onContinueFocusToPomodoro,
                    onStopStopwatch = onStopStopwatchFocus,
                    onCompleteTask = onCompleteFocusedTask,
                    onExit = onExitFocus
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Effort Tier Selector (5m / 25m / 1h — generous 42dp touch height)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EffortTier.entries.forEach { tier ->
                            val selected = task.estimatedMinutes == tier.minutes
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 42.dp)
                                    .clip(ZeroSmallShape)
                                    .background(
                                        if (selected) colors.accentSubtle else Color.White.copy(alpha = 0.03f)
                                    )
                                    .hairlineBorder(
                                        shape = ZeroSmallShape,
                                        color = if (selected) colors.accent else Color.White.copy(alpha = 0.10f)
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
                                    color = if (selected) colors.accent else colors.textSecondary
                                )
                            }
                        }
                    }

                    // First Tiny Step (Icebreaker) — checking it slides into Forward Momentum Flow
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ZeroSmallShape)
                            .background(Color.White.copy(alpha = 0.03f))
                            .hairlineBorder(
                                shape = ZeroSmallShape,
                                color = if (!task.firstStep.isNullOrBlank()) colors.accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.10f)
                            )
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
                                tint = colors.accent,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = stringResource(R.string.now_first_step_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.accent
                            )
                        }

                        if (!task.firstStep.isNullOrBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 46.dp)
                                    .clip(ZeroSmallShape)
                                    .background(DarkroomVoid)
                                    .hairlineBorder(shape = ZeroSmallShape, color = Color.White.copy(alpha = 0.08f))
                                    .clickable {
                                        haptics.confirm()
                                        onCompleteFirstStep()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(if (task.firstStepCompleted) colors.accent else Color.Transparent)
                                        .hairlineBorder(
                                            shape = CircleShape,
                                            color = if (task.firstStepCompleted) colors.accent else colors.accent.copy(alpha = 0.7f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (task.firstStepCompleted) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = colors.onAccent,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = task.firstStep,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        textDecoration = if (task.firstStepCompleted) TextDecoration.LineThrough else TextDecoration.None
                                    ),
                                    color = if (task.firstStepCompleted) colors.textMuted else colors.textPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(
                                text = stringResource(
                                    if (task.firstStepCompleted) R.string.now_first_step_done_hint
                                    else R.string.now_first_step_flow_hint
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .clip(ZeroSmallShape)
                                .background(DarkroomVoid)
                                .hairlineBorder(shape = ZeroSmallShape, color = Color.White.copy(alpha = 0.08f))
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
                                    cursorBrush = SolidColor(colors.accent),
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
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(colors.accent)
                                        .clickable {
                                            haptics.confirm()
                                            onUpdateFirstStep(firstStepDraft)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = stringResource(R.string.action_save),
                                        tint = colors.onAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Ergonomic Thumb Action Bar (Skip 52dp / 2m 52dp / Hold Charge Complete 52dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .height(52.dp)
                                .widthIn(min = 76.dp)
                                .clip(ZeroCapsuleShape)
                                .background(Color.White.copy(alpha = 0.04f))
                                .hairlineBorder(shape = ZeroCapsuleShape, color = Color.White.copy(alpha = 0.12f))
                                .clickable(enabled = canSkip) {
                                    haptics.reorderTick()
                                    onSkip()
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
                                    tint = if (canSkip) colors.textSecondary else colors.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(R.string.now_skip_card),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (canSkip) colors.textSecondary else colors.textMuted
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .height(52.dp)
                                .clip(ZeroCapsuleShape)
                                .background(Color.White.copy(alpha = 0.05f))
                                .hairlineBorder(shape = ZeroCapsuleShape, color = colors.accent.copy(alpha = 0.5f))
                                .clickable {
                                    haptics.confirm()
                                    onStartTwoMin()
                                }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(R.string.detail_start_2min),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.accent
                                )
                            }
                        }

                        HoldChargeDoneButton(
                            holdProgress = holdProgress,
                            onComplete = {
                                haptics.taskComplete()
                                currentComplete()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HoldChargeDoneButton(
    holdProgress: Animatable<Float, *>,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val haptics = rememberZeroHaptics()
    val scope = rememberCoroutineScope()
    val currentComplete by rememberUpdatedState(onComplete)

    Box(
        modifier = modifier
            .height(52.dp)
            .clip(ZeroCapsuleShape)
            .background(DarkroomVoid)
            .hairlineBorder(shape = ZeroCapsuleShape, color = colors.accent)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var triggered = false
                    val fillJob = scope.launch {
                        holdProgress.snapTo(0f)
                        val tick30 = launch {
                            delay(105L)
                            haptics.reorderTick()
                        }
                        val tick70 = launch {
                            delay(240L)
                            haptics.reorderTick()
                        }
                        try {
                            holdProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 350, easing = LinearEasing)
                            )
                            triggered = true
                            currentComplete()
                        } finally {
                            tick30.cancel()
                            tick70.cancel()
                        }
                    }
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        if (!triggered) {
                            fillJob.cancel(CancellationException("Released before 350ms charge"))
                        }
                        scope.launch {
                            holdProgress.animateTo(0f, animationSpec = ZeroMotion.FloatSpring)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (holdProgress.value > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(holdProgress.value)
                    .height(52.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(colors.accent.copy(alpha = 0.85f), colors.accent)
                        )
                    )
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = if (holdProgress.value > 0.55f) colors.onAccent else colors.accent,
                modifier = Modifier.size(19.dp)
            )
            Text(
                text = stringResource(R.string.now_hold_complete),
                style = MaterialTheme.typography.labelLarge,
                color = if (holdProgress.value > 0.55f) colors.onAccent else colors.textPrimary
            )
        }
    }
}

@Composable
private fun InlineDarkroomFlowDeck(
    focusState: FocusUiState,
    onSelectMode: (FocusMode) -> Unit,
    onStartOrResume: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onContinueToPomodoro: () -> Unit,
    onStopStopwatch: () -> Unit,
    onCompleteTask: () -> Unit,
    onExit: () -> Unit
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val haptics = rememberZeroHaptics()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Inline Flow Header & Minimize Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .clip(ZeroCapsuleShape)
                    .background(colors.accentSubtle)
                    .hairlineBorder(shape = ZeroCapsuleShape, color = colors.accent.copy(alpha = 0.7f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(colors.accent)
                )
                Text(
                    text = stringResource(R.string.now_inline_flow_active),
                    style = tabular.priorityTag,
                    color = colors.accent
                )
            }

            Row(
                modifier = Modifier
                    .heightIn(min = 38.dp)
                    .clip(ZeroCapsuleShape)
                    .background(Color.White.copy(alpha = 0.04f))
                    .hairlineBorder(shape = ZeroCapsuleShape, color = Color.White.copy(alpha = 0.12f))
                    .clickable {
                        haptics.reorderTick()
                        onExit()
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(R.string.now_exit_flow),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary
                )
            }
        }

        // Mode Tabs (2m Starter / 25m Focus / Stopwatch Flow)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ZeroCapsuleShape)
                .background(DarkroomVoid)
                .hairlineBorder(shape = ZeroCapsuleShape, color = Color.White.copy(alpha = 0.10f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            InlineFlowModeTab(
                label = stringResource(R.string.focus_tab_two_min),
                selected = focusState.mode == FocusMode.TWO_MIN_STARTER,
                onClick = {
                    haptics.reorderTick()
                    onSelectMode(FocusMode.TWO_MIN_STARTER)
                },
                modifier = Modifier.weight(1f)
            )
            InlineFlowModeTab(
                label = stringResource(R.string.focus_tab_pomodoro),
                selected = focusState.mode == FocusMode.POMODORO,
                onClick = {
                    haptics.reorderTick()
                    onSelectMode(FocusMode.POMODORO)
                },
                modifier = Modifier.weight(1f)
            )
            InlineFlowModeTab(
                label = stringResource(R.string.focus_tab_stopwatch),
                selected = focusState.mode == FocusMode.STOPWATCH,
                onClick = {
                    haptics.reorderTick()
                    onSelectMode(FocusMode.STOPWATCH)
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Giant Tabular Timer Hero
        Text(
            text = focusState.formattedTimer,
            style = tabular.timerHero.copy(fontWeight = FontWeight.Medium),
            color = colors.textPrimary,
            textAlign = TextAlign.Center
        )

        // Luminous Flow Progress Hairline
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(ZeroCapsuleShape)
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            val animProgress by animateFloatAsState(
                targetValue = focusState.progress.coerceIn(0f, 1f),
                animationSpec = ZeroMotion.FloatSpring,
                label = "inline_flow_progress"
            )
            if (animProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animProgress)
                        .height(3.dp)
                        .clip(ZeroCapsuleShape)
                        .background(colors.accent)
                )
            }
        }

        val statusText = when (focusState.phase) {
            FocusPhase.RUNNING -> stringResource(R.string.focus_status_running)
            FocusPhase.PAUSED -> stringResource(R.string.focus_status_paused)
            FocusPhase.TWO_MIN_FINISHED_PROMPT -> stringResource(R.string.focus_status_two_min_done)
            FocusPhase.POMODORO_COMPLETED -> stringResource(R.string.focus_status_pomodoro_done)
            FocusPhase.READY -> when (focusState.mode) {
                FocusMode.TWO_MIN_STARTER -> stringResource(R.string.focus_hint_two_min)
                FocusMode.POMODORO -> stringResource(R.string.focus_hint_pomodoro)
                FocusMode.STOPWATCH -> stringResource(R.string.focus_hint_stopwatch)
            }
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )

        // Contextual Phase Actions
        when (focusState.phase) {
            FocusPhase.TWO_MIN_FINISHED_PROMPT -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.focus_prompt_continue_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(ZeroCapsuleShape)
                            .background(colors.accent)
                            .clickable {
                                haptics.confirm()
                                onContinueToPomodoro()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.focus_btn_continue_pomodoro),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onAccent
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(ZeroCapsuleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                            .hairlineBorder(shape = ZeroCapsuleShape, color = Color.White.copy(alpha = 0.12f))
                            .clickable {
                                haptics.taskComplete()
                                onCompleteTask()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.cd_complete_task),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.textPrimary
                        )
                    }
                }
            }

            FocusPhase.POMODORO_COMPLETED -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(ZeroCapsuleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                            .hairlineBorder(shape = ZeroCapsuleShape, color = Color.White.copy(alpha = 0.14f))
                            .clickable {
                                haptics.reorderTick()
                                onReset()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.cd_reset_timer),
                            tint = colors.textSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(2f)
                            .height(52.dp)
                            .clip(ZeroCapsuleShape)
                            .background(colors.accent)
                            .clickable {
                                haptics.taskComplete()
                                onCompleteTask()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.cd_complete_task),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onAccent
                        )
                    }
                }
            }

            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (focusState.mode == FocusMode.STOPWATCH && focusState.elapsedSeconds > 0) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.05f))
                                .hairlineBorder(shape = CircleShape, color = Color.White.copy(alpha = 0.14f))
                                .clickable {
                                    haptics.confirm()
                                    onStopStopwatch()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Stop,
                                contentDescription = stringResource(R.string.cd_reset_timer),
                                tint = colors.textSecondary
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.05f))
                                .hairlineBorder(shape = CircleShape, color = Color.White.copy(alpha = 0.14f))
                                .clickable {
                                    haptics.reorderTick()
                                    onReset()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.cd_reset_timer),
                                tint = colors.textSecondary
                            )
                        }
                    }

                    val isRunning = focusState.phase == FocusPhase.RUNNING
                    Box(
                        modifier = Modifier
                            .weight(1.4f)
                            .height(52.dp)
                            .clip(ZeroCapsuleShape)
                            .background(if (isRunning) Color.White.copy(alpha = 0.08f) else colors.accent)
                            .hairlineBorder(
                                shape = ZeroCapsuleShape,
                                color = if (isRunning) colors.accent else Color.Transparent
                            )
                            .clickable {
                                haptics.confirm()
                                if (isRunning) onPause() else onStartOrResume()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = if (isRunning) colors.accent else colors.onAccent,
                                modifier = Modifier.size(19.dp)
                            )
                            Text(
                                text = when {
                                    isRunning -> stringResource(R.string.focus_btn_pause)
                                    focusState.phase == FocusPhase.PAUSED -> stringResource(R.string.focus_btn_resume)
                                    focusState.mode == FocusMode.TWO_MIN_STARTER -> stringResource(R.string.focus_btn_start_two_min)
                                    focusState.mode == FocusMode.POMODORO -> stringResource(R.string.focus_btn_start_pomodoro)
                                    else -> stringResource(R.string.focus_btn_start_stopwatch)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isRunning) colors.accent else colors.onAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(colors.accentSubtle)
                            .hairlineBorder(shape = CircleShape, color = colors.accent)
                            .clickable {
                                haptics.taskComplete()
                                onCompleteTask()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.cd_complete_task),
                            tint = colors.accent,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineFlowModeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(ZeroCapsuleShape)
            .background(if (selected) colors.accentSubtle else Color.Transparent)
            .hairlineBorder(
                shape = ZeroCapsuleShape,
                color = if (selected) colors.accent.copy(alpha = 0.7f) else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.accent else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
