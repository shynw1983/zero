package com.zero.todo.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ZeroDateFormatter {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val monthDayFormatter = DateTimeFormatter.ofPattern("MM/dd", Locale.US)
    private val fullDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    fun startOfDayMillis(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Long =
        date.atStartOfDay(zoneId).toInstant().toEpochMilli()

    fun endOfDayExclusiveMillis(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Long =
        date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

    fun formatDueDate(
        dueDateMillis: Long,
        isAllDay: Boolean,
        now: LocalDateTime = LocalDateTime.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): String {
        val target = Instant.ofEpochMilli(dueDateMillis).atZone(zoneId).toLocalDateTime()
        val today = now.toLocalDate()
        val targetDate = target.toLocalDate()
        val lang = locale.language.lowercase(Locale.US)

        val datePart = when (targetDate) {
            today -> when (lang) {
                "zh" -> "今天"
                "ja" -> "今日"
                "ko" -> "오늘"
                else -> "Today"
            }
            today.plusDays(1) -> when (lang) {
                "zh" -> "明天"
                "ja" -> "明日"
                "ko" -> "내일"
                else -> "Tomorrow"
            }
            today.plusDays(2) -> when (lang) {
                "zh" -> "后天"
                "ja" -> "明後日"
                "ko" -> "모레"
                else -> "In 2d"
            }
            today.minusDays(1) -> when (lang) {
                "zh" -> "昨天"
                "ja" -> "昨日"
                "ko" -> "어제"
                else -> "Yesterday"
            }
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
