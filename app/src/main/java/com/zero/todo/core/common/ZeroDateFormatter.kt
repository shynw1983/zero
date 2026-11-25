package com.zero.todo.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ZeroDateFormatter {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    private val monthDayFormatter = DateTimeFormatter.ofPattern("MM/dd", Locale.getDefault())
    private val fullDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())

    fun startOfDayMillis(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Long =
        date.atStartOfDay(zoneId).toInstant().toEpochMilli()

    fun endOfDayExclusiveMillis(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Long =
        date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

    fun formatDueDate(
        dueDateMillis: Long,
        isAllDay: Boolean,
        now: LocalDateTime = LocalDateTime.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val target = Instant.ofEpochMilli(dueDateMillis).atZone(zoneId).toLocalDateTime()
        val today = now.toLocalDate()
        val targetDate = target.toLocalDate()

        val datePart = when (targetDate) {
            today -> "今天"
            today.plusDays(1) -> "明天"
            today.plusDays(2) -> "后天"
            today.minusDays(1) -> "昨天"
            else -> if (targetDate.year == today.year) {
                targetDate.format(monthDayFormatter)
            } else {
                targetDate.format(fullDateFormatter)
            }
        }

        return if (isAllDay) {
            datePart
        } else {
            "$datePart ${target.format(timeFormatter)}"
        }
    }

    fun isOverdue(
        dueDateMillis: Long,
        isAllDay: Boolean,
        now: LocalDateTime = LocalDateTime.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        val target = Instant.ofEpochMilli(dueDateMillis).atZone(zoneId).toLocalDateTime()
        return if (isAllDay) {
            target.toLocalDate().isBefore(now.toLocalDate())
        } else {
            target.isBefore(now)
        }
    }
}
