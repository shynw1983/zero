package com.zero.todo.core.common

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Lightweight RFC 5545 RRULE engine supporting presets (Daily, Weekdays, Weekly, Monthly, Yearly)
 * and custom rules (e.g. `FREQ=DAILY;INTERVAL=2`, `FREQ=WEEKLY;BYDAY=MO,WE,FR`) with localized
 * display labels for EN / ZH / JA / KO.
 */
data class RecurrenceRule(
    val frequency: Frequency,
    val interval: Int = 1,
    val byDays: Set<DayOfWeek> = emptySet()
) {
    enum class Frequency(val rruleValue: String) {
        DAILY("DAILY"),
        WEEKLY("WEEKLY"),
        MONTHLY("MONTHLY"),
        YEARLY("YEARLY");

        fun unitLabel(lang: String, plural: Boolean): String = when (lang) {
            "zh" -> when (this) {
                DAILY -> "天"
                WEEKLY -> "周"
                MONTHLY -> "月"
                YEARLY -> "年"
            }
            "ja" -> when (this) {
                DAILY -> "日"
                WEEKLY -> "週"
                MONTHLY -> "か月"
                YEARLY -> "年"
            }
            "ko" -> when (this) {
                DAILY -> "일"
                WEEKLY -> "주"
                MONTHLY -> "개월"
                YEARLY -> "년"
            }
            else -> when (this) {
                DAILY -> if (plural) "days" else "day"
                WEEKLY -> if (plural) "weeks" else "week"
                MONTHLY -> if (plural) "months" else "month"
                YEARLY -> if (plural) "years" else "year"
            }
        }

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

    fun toDisplayLabel(locale: Locale = Locale.getDefault()): String {
        val lang = locale.language.lowercase(Locale.US)
        if (frequency == Frequency.WEEKLY && interval == 1 && byDays == WEEKDAYS) {
            return when (lang) {
                "zh" -> "工作日"
                "ja" -> "平日"
                "ko" -> "평일"
                else -> "Weekdays"
            }
        }
        if (interval == 1 && byDays.isEmpty()) {
            return when (lang) {
                "zh" -> when (frequency) {
                    Frequency.DAILY -> "每天"
                    Frequency.WEEKLY -> "每周"
                    Frequency.MONTHLY -> "每月"
                    Frequency.YEARLY -> "每年"
                }
                "ja" -> when (frequency) {
                    Frequency.DAILY -> "毎日"
                    Frequency.WEEKLY -> "毎週"
                    Frequency.MONTHLY -> "毎月"
                    Frequency.YEARLY -> "毎年"
                }
                "ko" -> when (frequency) {
                    Frequency.DAILY -> "매일"
                    Frequency.WEEKLY -> "매주"
                    Frequency.MONTHLY -> "매월"
                    Frequency.YEARLY -> "매년"
                }
                else -> when (frequency) {
                    Frequency.DAILY -> "Daily"
                    Frequency.WEEKLY -> "Weekly"
                    Frequency.MONTHLY -> "Monthly"
                    Frequency.YEARLY -> "Yearly"
                }
            }
        }
        val base = when (lang) {
            "zh" -> if (interval == 1) "每${frequency.unitLabel(lang, false)}" else "每 $interval ${frequency.unitLabel(lang, true)}"
            "ja" -> if (interval == 1) "毎${frequency.unitLabel(lang, false)}" else "${interval}${frequency.unitLabel(lang, true)}ごと"
            "ko" -> if (interval == 1) "매${frequency.unitLabel(lang, false)}" else "${interval}${frequency.unitLabel(lang, true)}마다"
            else -> if (interval == 1) "Every ${frequency.unitLabel(lang, false)}" else "Every $interval ${frequency.unitLabel(lang, true)}"
        }
        return if (byDays.isNotEmpty()) {
            val separator = when (lang) {
                "zh", "ja" -> "、"
                else -> ", "
            }
            val days = byDays.sortedBy { it.value }.joinToString(separator) { it.toShortLabel(lang) }
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
        for (offset in 1L..6L) {
            val candidate = current.plusDays(offset)
            if (candidate.dayOfWeek == DayOfWeek.MONDAY) break
            if (candidate.dayOfWeek in byDays) return candidate
        }
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
                else entry.substring(0, idx).uppercase(Locale.US).trim() to entry.substring(idx + 1).uppercase(Locale.US).trim()
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

        fun formatLabel(raw: String?, locale: Locale = Locale.getDefault()): String {
            parse(raw)?.let { return it.toDisplayLabel(locale) }
            return when (locale.language.lowercase(Locale.US)) {
                "zh" -> "不重复"
                "ja" -> "なし"
                "ko" -> "반복 안 함"
                else -> "None"
            }
        }

        private fun parseRruleDay(code: String): DayOfWeek? = when (code.uppercase(Locale.US)) {
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

        private fun DayOfWeek.toShortLabel(lang: String): String = when (lang) {
            "zh" -> when (this) {
                DayOfWeek.MONDAY -> "周一"
                DayOfWeek.TUESDAY -> "周二"
                DayOfWeek.WEDNESDAY -> "周三"
                DayOfWeek.THURSDAY -> "周四"
                DayOfWeek.FRIDAY -> "周五"
                DayOfWeek.SATURDAY -> "周六"
                DayOfWeek.SUNDAY -> "周日"
            }
            "ja" -> when (this) {
                DayOfWeek.MONDAY -> "月"
                DayOfWeek.TUESDAY -> "火"
                DayOfWeek.WEDNESDAY -> "水"
                DayOfWeek.THURSDAY -> "木"
                DayOfWeek.FRIDAY -> "金"
                DayOfWeek.SATURDAY -> "土"
                DayOfWeek.SUNDAY -> "日"
            }
            "ko" -> when (this) {
                DayOfWeek.MONDAY -> "월"
                DayOfWeek.TUESDAY -> "화"
                DayOfWeek.WEDNESDAY -> "수"
                DayOfWeek.THURSDAY -> "목"
                DayOfWeek.FRIDAY -> "금"
                DayOfWeek.SATURDAY -> "토"
                DayOfWeek.SUNDAY -> "일"
            }
            else -> when (this) {
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
}
