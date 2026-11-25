package com.zero.todo.core.nlp

import com.zero.todo.domain.model.TaskPriority
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class QuickInputTokenType {
    DATE_TIME,
    FOLDER,
    PRIORITY,
    TAG
}

data class QuickInputToken(
    val type: QuickInputTokenType,
    val rawText: String,
    val range: IntRange
)

data class ParsedQuickInput(
    val rawInput: String,
    val title: String,
    val dueDateMillis: Long? = null,
    val isAllDay: Boolean = true,
    val folderPath: List<String>? = null,
    val priority: TaskPriority = TaskPriority.NONE,
    val tags: List<String> = emptyList(),
    val tokens: List<QuickInputToken> = emptyList(),
    /** Non-null when user is actively typing a `/...` folder segment at the end of input. */
    val activeFolderQuery: String? = null
) {
    val hasStructuredTokens: Boolean
        get() = dueDateMillis != null ||
            folderPath != null ||
            priority != TaskPriority.NONE ||
            tags.isNotEmpty()
}

/**
 * Local zero-latency Chinese/English NLP parser for ZERO's Command-First Quick Input.
 *
 * Supported syntax:
 * - Priority: `!1`, `!2`, `!3`, `!4` (also full-width `！1`..`！4`)
 * - Folder routing: `/工作`, `/财务/报销`
 * - Tags: `#重要`, `#UI设计`
 * - Dates: `今天`, `明天`, `后天`, `大后天`, `today`, `tomorrow`,
 *          `周五`, `下周一`, `这周三`, `星期日`, `2026-12-01`, `2026/12/01`, `12月1日`, `12月1号`
 * - Times: `15:00`, `9:30`, `下午3点`, `下午3点半`, `下午3点15分`, `上午10点`, `晚上8点`, `中午12点`,
 *          plus combined shortcuts like `今晚9点`, `明早8点`
 */
object QuickInputParser {

    private val PRIORITY_REGEX = Regex("""(?<!\S)[!！]([1-4])(?!\S)""")
    private val FOLDER_REGEX = Regex("""(?<!\S)/([^\s/!！#]+(?:/[^\s/!！#]+)*)""")
    private val TAG_REGEX = Regex("""(?<!\S)#([^\s#]+)""")
    private val ACTIVE_FOLDER_REGEX = Regex("""(?:^|\s)/([^\s!！#]*)$""")

    private val ISO_DATE_REGEX = Regex("""(?<!\d)(\d{4})[-/](\d{1,2})[-/](\d{1,2})(?!\d)""")
    private val CN_MONTH_DAY_REGEX = Regex("""(?<!\d)(\d{1,2})月(\d{1,2})[日号]?""")
    private val COMBINED_PERIOD_DATE_REGEX = Regex("""(今晚|今早|明早|明晚|后天早|后天晚)""")
    private val RELATIVE_DATE_REGEX = Regex("""(大后天|后天|明天|今天|明後日|明日|今日|모레|내일|오늘|(?i:tomorrow)|(?i:today))""")
    private val WEEKDAY_REGEX = Regex("""(下周|下星期|本周|这周|这星期|周|星期)([一二三四五六日天])""")

    private val COLON_TIME_REGEX = Regex("""(?<!\d)(\d{1,2}):(\d{2})(?!\d)""")
    private val CN_TIME_REGEX = Regex("""(凌晨|早上|早|上午|中午|下午|傍晚|晚上|晚)?(\d{1,2}|[零〇一二两三四五六七八九十]{1,3})[点时](半|(\d{1,2}|[零〇一二两三四五六七八九十]{1,3})分?)?""")

    fun parse(
        input: String,
        now: LocalDateTime = LocalDateTime.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ParsedQuickInput {
        if (input.isBlank()) {
            return ParsedQuickInput(rawInput = input, title = "")
        }

        val tokens = mutableListOf<QuickInputToken>()
        val claimed = BooleanArray(input.length)

        fun claim(range: IntRange, type: QuickInputTokenType): Boolean {
            if (range.first < 0 || range.last >= input.length) return false
            for (i in range) {
                if (claimed[i]) return false
            }
            for (i in range) {
                claimed[i] = true
            }
            tokens.add(
                QuickInputToken(
                    type = type,
                    rawText = input.substring(range),
                    range = range
                )
            )
            return true
        }

        // 1. Detect active folder autocomplete query at end of string
        val activeFolderMatch = ACTIVE_FOLDER_REGEX.find(input)
        val activeFolderQuery = activeFolderMatch?.groupValues?.get(1)

        // 2. Priority (!1..4)
        var priority = TaskPriority.NONE
        PRIORITY_REGEX.find(input)?.let { match ->
            if (claim(match.range, QuickInputTokenType.PRIORITY)) {
                priority = TaskPriority.fromInt(match.groupValues[1].toInt())
            }
        }

        // 3. Folder path (/Work/Finance)
        var folderPath: List<String>? = null
        FOLDER_REGEX.find(input)?.let { match ->
            if (claim(match.range, QuickInputTokenType.FOLDER)) {
                val segments = match.groupValues[1]
                    .split('/')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (segments.isNotEmpty()) {
                    folderPath = segments
                }
            }
        }

        // 4. Tags (#tag)
        val tags = linkedSetOf<String>()
        for (match in TAG_REGEX.findAll(input)) {
            if (claim(match.range, QuickInputTokenType.TAG)) {
                val tagName = match.groupValues[1].trim()
                if (tagName.isNotEmpty()) {
                    tags.add(tagName)
                }
            }
        }

        // Helper returning a same-length view of input with already-claimed indices masked as spaces,
        // preventing partial regex overlap with previously claimed tokens (e.g., "今晚9点" -> "  9点").
        fun maskedInput(): String {
            val chars = input.toCharArray()
            for (i in chars.indices) {
                if (claimed[i]) chars[i] = ' '
            }
            return String(chars)
        }

        // 5. Date parsing
        var parsedDate: LocalDate? = null
        var impliedPeriod: String? = null

        // 5a. Explicit YYYY-MM-DD / YYYY/MM/DD
        for (match in ISO_DATE_REGEX.findAll(maskedInput())) {
            val y = match.groupValues[1].toIntOrNull() ?: continue
            val m = match.groupValues[2].toIntOrNull() ?: continue
            val d = match.groupValues[3].toIntOrNull() ?: continue
            val date = runCatching { LocalDate.of(y, m, d) }.getOrNull() ?: continue
            if (claim(match.range, QuickInputTokenType.DATE_TIME)) {
                parsedDate = date
                break
            }
        }

        // 5b. Chinese M月D日 / M月D号
        if (parsedDate == null) {
            for (match in CN_MONTH_DAY_REGEX.findAll(maskedInput())) {
                val m = match.groupValues[1].toIntOrNull() ?: continue
                val d = match.groupValues[2].toIntOrNull() ?: continue
                val candidate = runCatching { LocalDate.of(now.year, m, d) }.getOrNull() ?: continue
                val resolved = if (candidate.isBefore(now.toLocalDate())) {
                    runCatching { LocalDate.of(now.year + 1, m, d) }.getOrDefault(candidate)
                } else {
                    candidate
                }
                if (claim(match.range, QuickInputTokenType.DATE_TIME)) {
                    parsedDate = resolved
                    break
                }
            }
        }

        // 5c. Combined period + date shortcuts: 今晚 / 今早 / 明早 / 明晚
        if (parsedDate == null) {
            for (match in COMBINED_PERIOD_DATE_REGEX.findAll(maskedInput())) {
                val word = match.groupValues[1]
                val today = now.toLocalDate()
                val date = when {
                    word.startsWith("今") -> today
                    word.startsWith("明") -> today.plusDays(1)
                    word.startsWith("后天") -> today.plusDays(2)
                    else -> continue
                }
                if (claim(match.range, QuickInputTokenType.DATE_TIME)) {
                    parsedDate = date
                    impliedPeriod = if (word.endsWith("晚")) "晚上" else "早上"
                    break
                }
            }
        }

        // 5d. Relative dates: 今天 / 明天 / 后天 / 大后天 / today / tomorrow
        if (parsedDate == null) {
            for (match in RELATIVE_DATE_REGEX.findAll(maskedInput())) {
                val word = match.groupValues[1].lowercase()
                val today = now.toLocalDate()
                val date = when (word) {
                    "今天", "今日", "오늘", "today" -> today
                    "明天", "明日", "내일", "tomorrow" -> today.plusDays(1)
                    "后天", "明後日", "모레" -> today.plusDays(2)
                    "大后天" -> today.plusDays(3)
                    else -> continue
                }
                if (claim(match.range, QuickInputTokenType.DATE_TIME)) {
                    parsedDate = date
                    break
                }
            }
        }

        // 5e. Weekdays: 周五 / 下周一 / 这周三 / 星期日
        if (parsedDate == null) {
            for (match in WEEKDAY_REGEX.findAll(maskedInput())) {
                val prefix = match.groupValues[1]
                val dayChar = match.groupValues[2]
                val targetDow = parseChineseDayOfWeek(dayChar) ?: continue
                val date = resolveWeekday(now.toLocalDate(), prefix, targetDow)
                if (claim(match.range, QuickInputTokenType.DATE_TIME)) {
                    parsedDate = date
                    break
                }
            }
        }

        // 6. Time parsing
        var parsedTime: LocalTime? = null

        // 6a. HH:mm (e.g., 15:00, 9:30)
        for (match in COLON_TIME_REGEX.findAll(maskedInput())) {
            val rawHour = match.groupValues[1].toIntOrNull() ?: continue
            val minute = match.groupValues[2].toIntOrNull() ?: continue
            if (rawHour !in 0..23 || minute !in 0..59) continue
            val adjustedHour = adjustHourByPeriod(rawHour, impliedPeriod)
            if (claim(match.range, QuickInputTokenType.DATE_TIME)) {
                parsedTime = LocalTime.of(adjustedHour, minute)
                break
            }
        }

        // 6b. Chinese time (e.g., 下午3点, 下午3点半, 晚上8点15分, 10点)
        if (parsedTime == null) {
            for (match in CN_TIME_REGEX.findAll(maskedInput())) {
                val explicitPeriod = match.groupValues[1].ifBlank { null }
                val rawHour = parseIntOrChineseNum(match.groupValues[2]) ?: continue
                if (rawHour !in 0..23) continue
                val minutePart = match.groupValues[3]
                val minute = when {
                    minutePart.isEmpty() -> 0
                    minutePart == "半" -> 30
                    else -> {
                        val m = parseIntOrChineseNum(match.groupValues[4]) ?: continue
                        if (m !in 0..59) continue
                        m
                    }
                }
                val period = explicitPeriod ?: impliedPeriod
                val adjustedHour = adjustHourByPeriod(rawHour, period)
                if (adjustedHour !in 0..23) continue
                if (claim(match.range, QuickInputTokenType.DATE_TIME)) {
                    parsedTime = LocalTime.of(adjustedHour, minute)
                    break
                }
            }
        }

        // 7. Combine Date + Time into epoch millis
        val isAllDay = parsedTime == null
        val dueDateMillis: Long? = when {
            parsedDate != null && parsedTime != null -> {
                LocalDateTime.of(parsedDate, parsedTime).atZone(zoneId).toInstant().toEpochMilli()
            }
            parsedDate != null -> {
                parsedDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
            }
            parsedTime != null -> {
                LocalDateTime.of(now.toLocalDate(), parsedTime).atZone(zoneId).toInstant().toEpochMilli()
            }
            else -> null
        }

        // 8. Build cleaned title by stripping claimed token ranges
        val titleBuilder = StringBuilder(input.length)
        for (i in input.indices) {
            if (!claimed[i]) {
                titleBuilder.append(input[i])
            } else {
                titleBuilder.append(' ')
            }
        }
        val cleanedTitle = titleBuilder.toString()
            .replace(Regex("""\s+"""), " ")
            .trim()

        return ParsedQuickInput(
            rawInput = input,
            title = cleanedTitle,
            dueDateMillis = dueDateMillis,
            isAllDay = isAllDay,
            folderPath = folderPath,
            priority = priority,
            tags = tags.toList(),
            tokens = tokens.sortedBy { it.range.first },
            activeFolderQuery = activeFolderQuery
        )
    }

    private fun parseChineseDayOfWeek(ch: String): DayOfWeek? = when (ch) {
        "一" -> DayOfWeek.MONDAY
        "二" -> DayOfWeek.TUESDAY
        "三" -> DayOfWeek.WEDNESDAY
        "四" -> DayOfWeek.THURSDAY
        "五" -> DayOfWeek.FRIDAY
        "六" -> DayOfWeek.SATURDAY
        "日", "天" -> DayOfWeek.SUNDAY
        else -> null
    }

    private fun resolveWeekday(today: LocalDate, prefix: String, target: DayOfWeek): LocalDate {
        val currentWeekMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return when (prefix) {
            "下周", "下星期" -> {
                currentWeekMonday.plusWeeks(1).plusDays((target.value - 1).toLong())
            }
            "本周", "这周", "这星期" -> {
                currentWeekMonday.plusDays((target.value - 1).toLong())
            }
            else -> {
                // "周五" / "星期五": upcoming occurrence (including today if same weekday)
                today.with(TemporalAdjusters.nextOrSame(target))
            }
        }
    }

    private fun adjustHourByPeriod(hour: Int, period: String?): Int = when (period) {
        "下午", "傍晚", "晚上", "晚" -> if (hour in 1..11) hour + 12 else hour
        "中午" -> if (hour in 1..11) hour + 12 else hour
        "凌晨" -> if (hour == 12) 0 else hour
        else -> hour
    }

    private fun parseIntOrChineseNum(raw: String): Int? {
        raw.toIntOrNull()?.let { return it }
        val digits = mapOf(
            '零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2,
            '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7,
            '八' to 8, '九' to 9
        )
        if (raw == "十") return 10
        if (raw.length == 1) return digits[raw[0]]
        if (raw.startsWith("十")) {
            val ones = digits[raw[1]] ?: return null
            return 10 + ones
        }
        val tenIdx = raw.indexOf('十')
        if (tenIdx > 0) {
            val tens = digits[raw[tenIdx - 1]] ?: return null
            val ones = if (tenIdx + 1 < raw.length) digits[raw[tenIdx + 1]] ?: return null else 0
            return tens * 10 + ones
        }
        return null
    }
}
