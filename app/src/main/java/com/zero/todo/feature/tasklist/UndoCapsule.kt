package com.zero.todo.feature.tasklist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zero.todo.R
import com.zero.todo.core.designsystem.ZeroCapsuleShape
import com.zero.todo.core.designsystem.ZeroMotion
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.data.repository.TaskOccurrenceChange
import com.zero.todo.domain.model.TaskPlacementChange

data class UndoBannerState(
    val id: Long = System.currentTimeMillis(),
    val taskId: String,
    val taskTitle: String,
    val undoType: UndoType,
    val spawnedTaskId: String? = null,
    val placementChange: TaskPlacementChange? = null,
    val durationMillis: Long = 3_000L,
    val occurrenceChange: TaskOccurrenceChange? = null
)

enum class UndoType {
    REVERT_COMPLETE,
    REVERT_UNCOMPLETE,
    RESTORE_DELETED_TASK,
    RESTORE_TASK_PLACEMENT,
    RESTORE_TASK_OCCURRENCE
}

/**
 * Floating Undo pill for recent task actions.
 */
@Composable
fun UndoCapsule(
    banner: UndoBannerState?,
    onUndo: (UndoBannerState) -> Unit,
    onTimeout: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors

    AnimatedVisibility(
        visible = banner != null,
        enter = fadeIn(animationSpec = ZeroMotion.FloatSpring) +
            slideInVertically(animationSpec = ZeroMotion.IntOffsetSpring) { it / 2 },
        exit = fadeOut(animationSpec = ZeroMotion.FloatSpring) +
            slideOutVertically(animationSpec = ZeroMotion.IntOffsetSpring) { it / 2 },
        modifier = modifier
    ) {
        val activeBanner = banner ?: return@AnimatedVisibility
        val progress = remember(activeBanner.id) { Animatable(1f) }

        LaunchedEffect(activeBanner.id) {
            progress.snapTo(1f)
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = activeBanner.durationMillis.toInt().coerceAtLeast(1),
                    easing = LinearEasing
                )
            )
            onTimeout(activeBanner.id)
        }

        val messageText = when (activeBanner.undoType) {
            UndoType.REVERT_COMPLETE -> stringResource(R.string.undo_completed, activeBanner.taskTitle)
            UndoType.REVERT_UNCOMPLETE -> stringResource(R.string.undo_restored, activeBanner.taskTitle)
            UndoType.RESTORE_DELETED_TASK -> stringResource(R.string.undo_deleted, activeBanner.taskTitle)
            UndoType.RESTORE_TASK_PLACEMENT -> stringResource(R.string.undo_task_moved, activeBanner.taskTitle)
            UndoType.RESTORE_TASK_OCCURRENCE -> stringResource(R.string.undo_task_occurrence_skipped, activeBanner.taskTitle)
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .height(42.dp)
                    .clip(ZeroCapsuleShape)
                    .background(colors.surfaceElevated)
                    .hairlineBorder(shape = ZeroCapsuleShape, color = colors.hairlineBorder)
                    .padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(modifier = Modifier.size(16.dp)) {
                    val strokePx = 1.5.dp.toPx()
                    val activeStrokePx = 1.75.dp.toPx()
                    val inset = activeStrokePx / 2f
                    val diameter = size.minDimension - activeStrokePx

                    drawCircle(
                        color = colors.hairlineBorder,
                        radius = diameter / 2f,
                        style = Stroke(width = strokePx)
                    )
                    drawArc(
                        color = colors.accent,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.value,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(diameter, diameter),
                        style = Stroke(width = activeStrokePx, cap = StrokeCap.Round)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = messageText,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .width(0.5.dp)
                        .height(16.dp)
                        .background(colors.hairlineBorder)
                )

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clickable(role = Role.Button) { onUndo(activeBanner) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.action_undo),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.accent
                    )
                }
            }
        }
    }
}
