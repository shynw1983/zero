package com.zero.todo.core.common

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Lightweight RFC 5545 RRULE engine supporting presets (Daily, Weekdays, Weekly, Monthly, Yearly)
 * and custom rules (e.g. `FREQ=DAILY;INTERVAL=2`, `FREQ=WEEKLY;BYDAY=MO,WE,FR`).
 */
data class RecurrenceRule(
    val frequency: Frequency,
    val interval: Int = 1,
    val byDays: Set<DayOfWeek> = emptySet()
) {
    enum class Frequency(val rruleValue: String, val labelZh: String) {
        DAILY("DAILY", "天"),
        WEEKLY("WEEKLY", "周"),
        MONTHLY("MONTHLY", "月"),
        YEARLY("YEARLY", "年");

        companion object {
            fun fromRrule(value: String): Frequency? =
                entries.firstOrNull { it.rruleValue.equals(value, ignoreCase = true) }
        }
    }

    fun toRruleString(): String = buildString {
        append("FREQ=").append(frequency.rruleValue)
        if (interval > 1) {
            append(";INTERVAL=").append(interval)
        }
        if (byDays.isNotEmpty()) {
            val ordered = byDays.sortedBy { it.value }.joinToString(",") { it.toRruleCode() }
            append(";BYDAY=").append(ordered)
        }
    }

    fun toDisplayLabel(): String {
        if (frequency == Frequency.WEEKLY && interval == 1 && byDays == WEEKDAYS) {
            return "工作日"
        }
        if (frequency == Frequency.DAILY && interval == 1 && byDays.isEmpty()) {
            return "每天"
        }
        if (frequency == Frequency.WEEKLY && interval == 1 && byDays.isEmpty()) {
            return "每周"
        }
        if (frequency == Frequency.MONTHLY && interval == 1 && byDays.isEmpty()) {
            return "每月"
        }
        if (frequency == Frequency.YEARLY && interval == 1 && byDays.isEmpty()) {
            return "每年"
        }
        val base = if (interval == 1) "每${frequency.labelZh}" else "每 $interval ${frequency.labelZh}"
        return if (byDays.isNotEmpty()) {
            val days = byDays.sortedBy { it.value }.joinToString("、") { it.toShortZh() }
            "$base ($days)"
        } else {
            base
        }
    }

    /**
     * Computes the next due timestamp strictly after [fromMillis] matching this recurrence rule.
     */
    fun nextOccurrenceMillis(
        fromMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val current = Instant.ofEpochMilli(fromMillis).atZone(zoneId).toLocalDateTime()
        val next: LocalDateTime = when (frequency) {
            Frequency.DAILY -> current.plusDays(interval.toLong())
            Frequency.WEEKLY -> {
                if (byDays.isEmpty()) {
                    current.plusWeeks(interval.toLong())
                } else {
                    findNextByDay(current)
                }
            }
            Frequency.MONTHLY -> current.plusMonths(interval.toLong())
            Frequency.YEARLY -> current.plusYears(interval.toLong())
        }
        return next.atZone(zoneId).toInstant().toEpochMilli()
    }

    private fun findNextByDay(current: LocalDateTime): LocalDateTime {
        // Check remaining days in current week first
        for (offset in 1L..6L) {
            val candidate = current.plusDays(offset)
            if (candidate.dayOfWeek == DayOfWeek.MONDAY) break
            if (candidate.dayOfWeek in byDays) return candidate
        }
        // Jump forward by interval weeks to the first matching weekday
        val currentWeekMonday = current.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val targetWeekMonday = currentWeekMonday.plusWeeks(interval.toLong())
        val firstDay = byDays.minBy { it.value }
        return targetWeekMonday
            .plusDays((firstDay.value - 1).toLong())
            .withHour(current.hour)
            .withMinute(current.minute)
            .withSecond(current.second)
            .withNano(current.nano)
    }

    companion object {
        val WEEKDAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY
        )

        val PRESET_DAILY = RecurrenceRule(Frequency.DAILY)
        val PRESET_WEEKDAYS = RecurrenceRule(Frequency.WEEKLY, byDays = WEEKDAYS)
        val PRESET_WEEKLY = RecurrenceRule(Frequency.WEEKLY)
        val PRESET_MONTHLY = RecurrenceRule(Frequency.MONTHLY)

        fun parse(raw: String?): RecurrenceRule? {
            val cleaned = raw?.trim()?.removePrefix("RRULE:")?.takeIf { it.isNotEmpty() } ?: return null
            val parts = cleaned.split(';').mapNotNull { entry ->
                val idx = entry.indexOf('=')
                if (idx <= 0) null
                else entry.substring(0, idx).uppercase().trim() to entry.substring(idx + 1).uppercase().trim()
            }.toMap()

            val freq = parts["FREQ"]?.let { Frequency.fromRrule(it) } ?: return null
            val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val byDays = parts["BYDAY"]
                ?.split(',')
                ?.mapNotNull { parseRruleDay(it.trim()) }
                ?.toSet()
                ?: emptySet()

            return RecurrenceRule(frequency = freq, interval = interval, byDays = byDays)
        }

        fun formatLabel(raw: String?): String = parse(raw)?.toDisplayLabel() ?: "不重复"

        private fun parseRruleDay(code: String): DayOfWeek? = when (code.uppercase()) {
            "MO" -> DayOfWeek.MONDAY
            "TU" -> DayOfWeek.TUESDAY
            "WE" -> DayOfWeek.WEDNESDAY
            "TH" -> DayOfWeek.THURSDAY
            "FR" -> DayOfWeek.FRIDAY
            "SA" -> DayOfWeek.SATURDAY
            "SU" -> DayOfWeek.SUNDAY
            else -> null
        }

        private fun DayOfWeek.toRruleCode(): String = when (this) {
            DayOfWeek.MONDAY -> "MO"
            DayOfWeek.TUESDAY -> "TU"
            DayOfWeek.WEDNESDAY -> "WE"
            DayOfWeek.THURSDAY -> "TH"
            DayOfWeek.FRIDAY -> "FR"
            DayOfWeek.SATURDAY -> "SA"
            DayOfWeek.SUNDAY -> "SU"
        }

        private fun DayOfWeek.toShortZh(): String = when (this) {
            DayOfWeek.MONDAY -> "周一"
            DayOfWeek.TUESDAY -> "周二"
            DayOfWeek.WEDNESDAY -> "周三"
            DayOfWeek.THURSDAY -> "周四"
            DayOfWeek.FRIDAY -> "周五"
            DayOfWeek.SATURDAY -> "周六"
            DayOfWeek.SUNDAY -> "周日"
        }
    }
}
