package com.zero.todo.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zero.todo.R
import com.zero.todo.domain.model.PriorityDisplayStyle
import com.zero.todo.domain.model.TaskPriority

val LocalPriorityDisplayStyle = staticCompositionLocalOf { PriorityDisplayStyle.DOTS }

/**
 * Renders a calm, personal-friendly priority indicator following the active [PriorityDisplayStyle].
 * By default (`showWhenNone = false`), [TaskPriority.NONE] renders nothing so normal tasks stay quiet.
 */
@Composable
fun ZeroPriorityBadge(
    priority: TaskPriority,
    color: Color,
    modifier: Modifier = Modifier,
    style: PriorityDisplayStyle = ZeroTheme.priorityDisplayStyle,
    showWhenNone: Boolean = false,
    isSelector: Boolean = false
) {
    if (priority == TaskPriority.NONE && !showWhenNone) return

    if (priority == TaskPriority.NONE) {
        Text(
            text = stringResource(R.string.priority_label_none),
            style = if (isSelector) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
            color = color,
            modifier = modifier
        )
        return
    }

    when (style) {
        PriorityDisplayStyle.DOTS -> {
            val dotCount = when (priority) {
                TaskPriority.URGENT -> 3
                TaskPriority.HIGH -> 2
                TaskPriority.MEDIUM -> 1
                TaskPriority.NONE -> 0
            }
            val dotSize = if (isSelector) 6.dp else 5.dp
            val dotSpacing = if (isSelector) 3.5.dp else 3.dp
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(dotSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(dotCount) {
                    Box(
                        modifier = Modifier
                            .size(dotSize)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
        }

        PriorityDisplayStyle.WORDS -> {
            val textRes = when (priority) {
                TaskPriority.URGENT -> R.string.priority_label_urgent
                TaskPriority.HIGH -> R.string.priority_label_high
                TaskPriority.MEDIUM -> R.string.priority_label_medium
                TaskPriority.NONE -> R.string.priority_label_none
            }
            Text(
                text = stringResource(textRes),
                style = if (isSelector) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                color = color,
                modifier = modifier
            )
        }

        PriorityDisplayStyle.MARKS -> {
            val marks = when (priority) {
                TaskPriority.URGENT -> "!!!"
                TaskPriority.HIGH -> "!!"
                TaskPriority.MEDIUM -> "!"
                TaskPriority.NONE -> ""
            }
            Text(
                text = marks,
                style = ZeroTheme.tabularTypography.priorityTag,
                color = color,
                modifier = modifier
            )
        }
    }
}

@Composable
fun priorityTextLabel(
    priority: TaskPriority,
    style: PriorityDisplayStyle = ZeroTheme.priorityDisplayStyle
): String = when (style) {
    PriorityDisplayStyle.DOTS -> when (priority) {
        TaskPriority.URGENT -> "●●●"
        TaskPriority.HIGH -> "●●"
        TaskPriority.MEDIUM -> "●"
        TaskPriority.NONE -> stringResource(R.string.priority_label_none)
    }

    PriorityDisplayStyle.WORDS -> when (priority) {
        TaskPriority.URGENT -> stringResource(R.string.priority_label_urgent)
        TaskPriority.HIGH -> stringResource(R.string.priority_label_high)
        TaskPriority.MEDIUM -> stringResource(R.string.priority_label_medium)
        TaskPriority.NONE -> stringResource(R.string.priority_label_none)
    }

    PriorityDisplayStyle.MARKS -> when (priority) {
        TaskPriority.URGENT -> "!!!"
        TaskPriority.HIGH -> "!!"
        TaskPriority.MEDIUM -> "!"
        TaskPriority.NONE -> stringResource(R.string.priority_label_none)
    }
}
