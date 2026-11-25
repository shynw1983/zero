package com.zero.todo.feature.now

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zero.todo.R
import com.zero.todo.core.designsystem.ZeroCardShape
import com.zero.todo.core.designsystem.ZeroMotion
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder

/**
 * Braun / Dieter Rams-inspired precision Day Capacity Ruler (Day Budget).
 * Visualizes today's scheduled energy against the user's realistic daily budget
 * so procrastination cannot hide behind an impossible 20-task fantasy list.
 */
@Composable
fun DayCapacityRuler(
    scheduledMinutes: Int,
    capacityMinutes: Int,
    taskCount: Int,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val safeCapacity = capacityMinutes.coerceAtLeast(60)
    val rawRatio = scheduledMinutes.toFloat() / safeCapacity.toFloat()
    val clampedRatio by animateFloatAsState(
        targetValue = rawRatio.coerceIn(0f, 1f),
        animationSpec = ZeroMotion.FloatSpring,
        label = "capacity_ratio"
    )
    val isOverloaded = rawRatio > 1.0f
    val isNearFull = rawRatio >= 0.85f && !isOverloaded

    val barColor by animateColorAsState(
        targetValue = when {
            isOverloaded -> colors.priorityUrgent
            isNearFull -> colors.priorityHigh
            else -> colors.accent
        },
        animationSpec = ZeroMotion.ColorSpring,
        label = "capacity_color"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ZeroCardShape)
            .background(colors.surface)
            .hairlineBorder(
                shape = ZeroCardShape,
                color = if (isOverloaded) colors.priorityUrgent.copy(alpha = 0.55f) else colors.hairlineBorder
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isOverloaded) {
                    stringResource(R.string.capacity_overloaded_hint)
                } else {
                    stringResource(R.string.capacity_title)
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (isOverloaded) colors.priorityUrgent else colors.textSecondary
            )

            Text(
                text = "${formatMinutesCompact(scheduledMinutes)} / ${formatMinutesCompact(safeCapacity)} · ${taskCount.toString().padStart(2, '0')}",
                style = tabular.dateCompact,
                color = if (isOverloaded) colors.priorityUrgent else colors.textPrimary
            )
        }

        // Precision calibrated hairline ruler bar with tick marks
        val trackColor = colors.surfaceElevated
        val tickColor = colors.hairlineBorder
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
        ) {
            val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            drawRoundRect(
                color = trackColor,
                size = size,
                cornerRadius = radius
            )
            if (clampedRatio > 0f) {
                drawRoundRect(
                    color = barColor,
                    size = Size(size.width * clampedRatio, size.height),
                    cornerRadius = radius
                )
            }
            // Calibrated instrument graduation ticks (8 subdivisions)
            val subdivisions = 8
            val stroke = 1.dp.toPx()
            for (i in 1 until subdivisions) {
                val x = size.width * (i.toFloat() / subdivisions.toFloat())
                val tickTop = if (i % 2 == 0) 0f else size.height * 0.35f
                drawLine(
                    color = tickColor.copy(alpha = 0.7f),
                    start = Offset(x, tickTop),
                    end = Offset(x, size.height),
                    strokeWidth = stroke
                )
            }
        }
    }
}

private fun formatMinutesCompact(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}
