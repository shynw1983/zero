package com.zero.todo.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class RecurrenceRuleTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun `parses and formats preset and custom RFC 5545 RRULE strings`() {
        assertEquals("每天", RecurrenceRule.formatLabel("FREQ=DAILY"))
        assertEquals("工作日", RecurrenceRule.formatLabel("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"))
        assertEquals("每周", RecurrenceRule.formatLabel("FREQ=WEEKLY"))
        assertEquals("每月", RecurrenceRule.formatLabel("FREQ=MONTHLY"))
        assertEquals("每 2 天", RecurrenceRule.formatLabel("RRULE:FREQ=DAILY;INTERVAL=2"))
        assertEquals("每周 (周一、周三、周五)", RecurrenceRule.formatLabel("FREQ=WEEKLY;BYDAY=MO,WE,FR"))
        assertNull(RecurrenceRule.parse("INVALID_RULE"))
        assertEquals("不重复", RecurrenceRule.formatLabel(null))
    }

    @Test
    fun `computes next occurrences accurately including weekday wrap from Friday to Monday`() {
        // Friday 2026-11-27 09:00 UTC
        val friday = LocalDateTime.of(2026, 11, 27, 9, 0).atZone(zone).toInstant().toEpochMilli()

        val dailyNext = RecurrenceRule.PRESET_DAILY.nextOccurrenceMillis(friday, zone)
        assertEquals(
            LocalDate.of(2026, 11, 28),
            Instant.ofEpochMilli(dailyNext).atZone(zone).toLocalDate()
        )

        val weekdayNext = RecurrenceRule.PRESET_WEEKDAYS.nextOccurrenceMillis(friday, zone)
        val weekdayNextDt = Instant.ofEpochMilli(weekdayNext).atZone(zone).toLocalDateTime()
        assertEquals(DayOfWeek.MONDAY, weekdayNextDt.dayOfWeek)
        assertEquals(LocalDateTime.of(2026, 11, 30, 9, 0), weekdayNextDt)

        val customInterval = RecurrenceRule.parse("FREQ=MONTHLY;INTERVAL=2")!!
        val twoMonthsLater = customInterval.nextOccurrenceMillis(friday, zone)
        assertEquals(
            LocalDate.of(2027, 1, 27),
            Instant.ofEpochMilli(twoMonthsLater).atZone(zone).toLocalDate()
        )
    }
}
