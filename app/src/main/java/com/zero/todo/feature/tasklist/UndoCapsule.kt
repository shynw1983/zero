package com.zero.todo.feature.tasklist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zero.todo.R
import com.zero.todo.core.designsystem.ZeroCapsuleShape
import com.zero.todo.core.designsystem.ZeroMotion
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import kotlinx.coroutines.delay
import java.util.Locale

data class UndoBannerState(
    val id: Long = System.currentTimeMillis(),
    val taskId: String,
    val taskTitle: String,
    val undoType: UndoType,
    val spawnedTaskId: String? = null,
    val durationMillis: Long = 3_000L
)

enum class UndoType {
    REVERT_COMPLETE,
    REVERT_UNCOMPLETE,
    RESTORE_DELETED_TASK
}

/**
 * 3-Second floating bottom Undo capsule shown whenever a task is completed or deleted.
 */
@Composable
fun UndoCapsule(
    banner: UndoBannerState?,
    onUndo: (UndoBannerState) -> Unit,
    onTimeout: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography

    AnimatedVisibility(
        visible = banner != null,
        enter = fadeIn(animationSpec = ZeroMotion.FloatSpring) +
            slideInVertically(animationSpec = ZeroMotion.IntOffsetSpring) { it / 2 },
        exit = fadeOut(animationSpec = ZeroMotion.FloatSpring) +
            slideOutVertically(animationSpec = ZeroMotion.IntOffsetSpring) { it / 2 },
        modifier = modifier
    ) {
        val activeBanner = banner ?: return@AnimatedVisibility
        var remainingMs by remember(activeBanner.id) { mutableLongStateOf(activeBanner.durationMillis) }

        LaunchedEffect(activeBanner.id) {
            val stepMs = 50L
            while (remainingMs > 0L) {
                delay(stepMs)
                remainingMs = (remainingMs - stepMs).coerceAtLeast(0L)
            }
            onTimeout(activeBanner.id)
        }

        val progress = (remainingMs.toFloat() / activeBanner.durationMillis.toFloat()).coerceIn(0f, 1f)
        val secondsText = String.format(Locale.US, "%.1fs", remainingMs / 1000f)
        val messageText = when (activeBanner.undoType) {
            UndoType.REVERT_COMPLETE -> stringResource(R.string.undo_completed, activeBanner.taskTitle)
            UndoType.REVERT_UNCOMPLETE -> stringResource(R.string.undo_restored, activeBanner.taskTitle)
            UndoType.RESTORE_DELETED_TASK -> stringResource(R.string.undo_deleted, activeBanner.taskTitle)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ZeroCapsuleShape)
                .background(colors.surfaceElevated)
                .hairlineBorder(shape = ZeroCapsuleShape, color = colors.hairlineBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = secondsText,
                        style = tabular.dateCompact,
                        color = colors.accent
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = messageText,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .clip(ZeroSmallShape)
                        .background(colors.accentSubtle)
                        .hairlineBorder(shape = ZeroSmallShape, color = colors.accent)
                        .clickable { onUndo(activeBanner) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_undo),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.accent
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(colors.surface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(1.5.dp)
                        .background(colors.accent)
                )
            }
        }
    }
}
