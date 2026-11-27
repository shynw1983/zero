package com.zero.todo.feature.calendar

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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.zero.todo.R
import com.zero.todo.core.designsystem.ZeroCardShape
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.rememberZeroHaptics
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private val MINI_CAL_WEEKDAY_RES = listOf(
    R.string.cal_weekday_mon,
    R.string.cal_weekday_tue,
    R.string.cal_weekday_wed,
    R.string.cal_weekday_thu,
    R.string.cal_weekday_fri,
    R.string.cal_weekday_sat,
    R.string.cal_weekday_sun
)

/**
 * Compact, tabular-aligned inline mini calendar for effortless 1-tap deadline picking.
 * Displays exact day numbers and weekday columns across any month.
 */
@Composable
fun ZeroMiniCalendar(
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val haptics = rememberZeroHaptics()
    val today = remember { LocalDate.now() }

    var displayedMonth by remember(selectedDate) {
        mutableStateOf(YearMonth.from(selectedDate ?: today))
    }

    val firstOfMonth = displayedMonth.atDay(1)
    val gridStart = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val days = remember(displayedMonth) { List(42) { offset -> gridStart.plusDays(offset.toLong()) } }
    val monthLabel = remember(displayedMonth) {
        displayedMonth.format(DateTimeFormatter.ofPattern("yyyy.MM"))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ZeroSmallShape)
            .background(colors.background)
            .hairlineBorder(shape = ZeroSmallShape)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Month Switcher Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { displayedMonth = displayedMonth.minusMonths(1) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.cd_prev_period),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = monthLabel,
                    style = tabular.counter,
                    color = colors.textPrimary
                )
                if (displayedMonth != YearMonth.from(today)) {
                    Box(
                        modifier = Modifier
                            .clip(ZeroSmallShape)
                            .background(colors.surface)
                            .hairlineBorder(shape = ZeroSmallShape)
                            .clickable {
                                displayedMonth = YearMonth.from(today)
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.cal_today_short),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.accent
                        )
                    }
                }
            }

            IconButton(
                onClick = { displayedMonth = displayedMonth.plusMonths(1) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.cd_next_period),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Weekday Columns
        Row(modifier = Modifier.fillMaxWidth()) {
            MINI_CAL_WEEKDAY_RES.forEach { resId ->
                Text(
                    text = stringResource(resId),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 6x7 Tabular Day Cells
        days.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                week.forEach { date ->
                    val inMonth = date.month == displayedMonth.month
                    val isSelected = date == selectedDate
                    val isToday = date == today

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(ZeroSmallShape)
                            .background(
                                when {
                                    isSelected -> colors.accent
                                    isToday -> colors.surfaceElevated
                                    else -> colors.background
                                }
                            )
                            .then(
                                if (isToday && !isSelected) {
                                    Modifier.hairlineBorder(shape = ZeroSmallShape, color = colors.accent)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable {
                                haptics.reorderTick()
                                onSelectDate(date)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = date.dayOfMonth.toString().padStart(2, '0'),
                            style = tabular.dateCompact,
                            color = when {
                                isSelected -> colors.onAccent
                                isToday -> colors.accent
                                inMonth -> colors.textPrimary
                                else -> colors.textMuted.copy(alpha = 0.45f)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ZeroMiniCalendarDialog(
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    onClearDate: (() -> Unit)? = null
) {
    val colors = ZeroTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ZeroCardShape)
                .background(colors.surfaceElevated)
                .hairlineBorder(shape = ZeroCardShape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.picker_select_date_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary
            )
            ZeroMiniCalendar(
                selectedDate = selectedDate,
                onSelectDate = { date ->
                    onSelectDate(date)
                    onDismiss()
                }
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                if (onClearDate != null && selectedDate != null) {
                    Box(
                        modifier = Modifier
                            .clip(ZeroSmallShape)
                            .hairlineBorder(shape = ZeroSmallShape)
                            .clickable {
                                onClearDate()
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.action_clear_date),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.priorityUrgent
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(ZeroSmallShape)
                        .hairlineBorder(shape = ZeroSmallShape)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textSecondary
                    )
                }
            }
        }
    }
}
