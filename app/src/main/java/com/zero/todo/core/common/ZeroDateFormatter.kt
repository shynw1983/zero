package com.zero.todo.core.common

import java.time.DayOfWeek
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

    fun shortWeekday(dayOfWeek: DayOfWeek, locale: Locale = Locale.getDefault()): String {
        val lang = locale.language.lowercase(Locale.US)
        return when (lang) {
            "zh" -> when (dayOfWeek) {
                DayOfWeek.MONDAY -> "周一"
                DayOfWeek.TUESDAY -> "周二"
                DayOfWeek.WEDNESDAY -> "周三"
                DayOfWeek.THURSDAY -> "周四"
                DayOfWeek.FRIDAY -> "周五"
                DayOfWeek.SATURDAY -> "周六"
                DayOfWeek.SUNDAY -> "周日"
            }
            "ja" -> when (dayOfWeek) {
                DayOfWeek.MONDAY -> "月"
                DayOfWeek.TUESDAY -> "火"
                DayOfWeek.WEDNESDAY -> "水"
                DayOfWeek.THURSDAY -> "木"
                DayOfWeek.FRIDAY -> "金"
                DayOfWeek.SATURDAY -> "土"
                DayOfWeek.SUNDAY -> "日"
            }
            "ko" -> when (dayOfWeek) {
                DayOfWeek.MONDAY -> "월"
                DayOfWeek.TUESDAY -> "화"
                DayOfWeek.WEDNESDAY -> "수"
                DayOfWeek.THURSDAY -> "목"
                DayOfWeek.FRIDAY -> "금"
                DayOfWeek.SATURDAY -> "토"
                DayOfWeek.SUNDAY -> "일"
            }
            else -> when (dayOfWeek) {
                DayOfWeek.MONDAY -> "Mon"
                DayOfWeek.TUESDAY -> "Tue"
                DayOfWeek.WEDNESDAY -> "Wed"
                DayOfWeek.THURSDAY -> "Thu"
                DayOfWeek.FRIDAY -> "Fri"
                DayOfWeek.SATURDAY -> "Sat"
                DayOfWeek.SUNDAY -> "Sun"
            }
        }
    }

    /**
     * Compact date + weekday label for quick chips, e.g.:
     * ZH: `11/25 周三`, JA: `11/25 (水)`, KO: `11/25 (수)`, EN: `11/25 Wed`.
     */
    fun formatMonthDayWeekday(date: LocalDate, locale: Locale = Locale.getDefault()): String {
        val md = date.format(monthDayFormatter)
        val wd = shortWeekday(date.dayOfWeek, locale)
        return when (locale.language.lowercase(Locale.US)) {
            "ja", "ko" -> "$md ($wd)"
            else -> "$md $wd"
        }
    }

    /**
     * Rich deadline banner label showing both relative day (when within -1..+2d) AND exact
     * month/day + weekday, e.g. `明天 · 11/26 周四 15:00`.
     */
    fun formatDueDateWithContext(
        dueDateMillis: Long,
        isAllDay: Boolean,
        now: LocalDateTime = LocalDateTime.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): String {
        val target = Instant.ofEpochMilli(dueDateMillis).atZone(zoneId).toLocalDateTime()
        val today = now.toLocalDate()
        val targetDate = target.toLocalDate()
        val relative = relativeDayLabel(targetDate, today, locale)
        val exact = if (targetDate.year == today.year) {
            formatMonthDayWeekday(targetDate, locale)
        } else {
            val wd = shortWeekday(targetDate.dayOfWeek, locale)
            when (locale.language.lowercase(Locale.US)) {
                "ja", "ko" -> "${targetDate.format(fullDateFormatter)} ($wd)"
                else -> "${targetDate.format(fullDateFormatter)} $wd"
            }
        }

        val datePart = if (relative != null) "$relative · $exact" else exact
        return if (isAllDay) datePart else "$datePart ${target.format(timeFormatter)}"
    }

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
        val relative = relativeDayLabel(targetDate, today, locale)

        val datePart = relative ?: if (targetDate.year == today.year) {
            formatMonthDayWeekday(targetDate, locale)
        } else {
            targetDate.format(fullDateFormatter)
        }

        return if (isAllDay) {
            datePart
        } else {
            "$datePart ${target.format(timeFormatter)}"
        }
    }

    private fun relativeDayLabel(targetDate: LocalDate, today: LocalDate, locale: Locale): String? {
        val lang = locale.language.lowercase(Locale.US)
        return when (targetDate) {
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
            else -> null
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
