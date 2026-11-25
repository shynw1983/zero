package com.zero.todo.core.nlp

import com.zero.todo.domain.model.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class QuickInputParserTest {

    // Fixed anchor: Wednesday, 2026-11-25 10:00:00 UTC
    private val zone = ZoneId.of("UTC")
    private val now = LocalDateTime.of(2026, 11, 25, 10, 0)

    @Test
    fun `parses full Chinese command with date, time, nested folder, priority, and multiple tags`() {
        val result = QuickInputParser.parse(
            input = "明天下午3点半 提交差旅报销单 /工作/财务/报销 !1 #财务 #报销",
            now = now,
            zoneId = zone
        )

        assertEquals("提交差旅报销单", result.title)
        assertEquals(listOf("工作", "财务", "报销"), result.folderPath)
        assertEquals(TaskPriority.URGENT, result.priority)
        assertEquals(listOf("财务", "报销"), result.tags)
        assertFalse(result.isAllDay)

        val expected = LocalDateTime.of(2026, 11, 26, 15, 30)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        assertEquals(expected, result.dueDateMillis)
        assertTrue(result.tokens.size >= 5)
    }

    @Test
    fun `parses weekdays such as 周五 and 下周一 accurately`() {
        val friday = QuickInputParser.parse("周五 评审设计稿", now = now, zoneId = zone)
        assertEquals("评审设计稿", friday.title)
        assertTrue(friday.isAllDay)
        assertEquals(
            LocalDate.of(2026, 11, 27),
            Instant.ofEpochMilli(friday.dueDateMillis!!).atZone(zone).toLocalDate()
        )

        val nextMonday = QuickInputParser.parse("下周一 15:00 发版准备 !2", now = now, zoneId = zone)
        assertEquals("发版准备", nextMonday.title)
        assertEquals(TaskPriority.HIGH, nextMonday.priority)
        assertFalse(nextMonday.isAllDay)
        assertEquals(
            LocalDateTime.of(2026, 11, 30, 15, 0),
            Instant.ofEpochMilli(nextMonday.dueDateMillis!!).atZone(zone).toLocalDateTime()
        )
    }

    @Test
    fun `parses ISO date, combined period shortcuts, and full-width exclamation priority`() {
        val iso = QuickInputParser.parse("2026-12-01 季度总结 ！3", now = now, zoneId = zone)
        assertEquals("季度总结", iso.title)
        assertEquals(TaskPriority.MEDIUM, iso.priority)
        assertTrue(iso.isAllDay)
        assertEquals(
            LocalDate.of(2026, 12, 1),
            Instant.ofEpochMilli(iso.dueDateMillis!!).atZone(zone).toLocalDate()
        )

        val tonight = QuickInputParser.parse("今晚9点 看论文", now = now, zoneId = zone)
        assertEquals("看论文", tonight.title)
        assertFalse(tonight.isAllDay)
        assertEquals(
            LocalDateTime.of(2026, 11, 25, 21, 0),
            Instant.ofEpochMilli(tonight.dueDateMillis!!).atZone(zone).toLocalDateTime()
        )
    }

    @Test
    fun `detects active folder autocomplete query at end of input`() {
        val typingRootSlash = QuickInputParser.parse("买牛奶 /", now = now, zoneId = zone)
        assertEquals("", typingRootSlash.activeFolderQuery)

        val typingPath = QuickInputParser.parse("提交材料 /工作/财", now = now, zoneId = zone)
        assertEquals("工作/财", typingPath.activeFolderQuery)

        val completedWithSpace = QuickInputParser.parse("提交材料 /工作/财务 ", now = now, zoneId = zone)
        assertNull(completedWithSpace.activeFolderQuery)
        assertEquals(listOf("工作", "财务"), completedWithSpace.folderPath)
    }

    @Test
    fun `priority shortcut replaces existing flags and stays idempotent`() {
        val draft = "デザイン確認 ！2 !1 today #UI /Work "
        val updated = QuickInputParser.setPriorityShortcut(draft, TaskPriority.URGENT)

        assertEquals("デザイン確認 !1 today #UI /Work ", updated)
        assertEquals(updated, QuickInputParser.setPriorityShortcut(updated, TaskPriority.URGENT))
        assertEquals("!1 ", QuickInputParser.setPriorityShortcut("！2", TaskPriority.URGENT))

        val parsed = QuickInputParser.parse(updated, now, zone)
        assertEquals("デザイン確認", parsed.title)
        assertEquals(TaskPriority.URGENT, parsed.priority)
        assertEquals(listOf("UI"), parsed.tags)
        assertEquals(listOf("Work"), parsed.folderPath)
    }

    @Test
    fun `folder shortcut reuses the active query and replaces the previous path on selection`() {
        val draft = "提交报销 /旧目录 #票据 "
        val opened = QuickInputParser.startFolderQuery(draft)
        assertEquals("提交报销 /旧目录 #票据 /", opened)
        assertEquals(opened, QuickInputParser.startFolderQuery(opened))
        assertEquals("提交报销", QuickInputParser.parse(opened, now, zone).title)
        assertEquals(listOf("旧目录"), QuickInputParser.parse(opened, now, zone).folderPath)

        val typing = opened + "财"
        assertEquals(typing, QuickInputParser.startFolderQuery(typing))
        val selected = QuickInputParser.selectFolderSuggestion(typing, "财务/差旅")
        assertEquals("提交报销 #票据 /财务/差旅 ", selected)
        assertEquals(selected, QuickInputParser.selectFolderSuggestion(selected, "旧目录"))

        val parsed = QuickInputParser.parse(selected, now, zone)
        assertEquals("提交报销", parsed.title)
        assertEquals(listOf("财务", "差旅"), parsed.folderPath)
        assertNull(parsed.activeFolderQuery)
    }

    @Test
    fun `priority and date shortcuts keep a folder query open`() {
        val prioritized = QuickInputParser.setPriorityShortcut("Review tomorrow /Fin", TaskPriority.URGENT)
        val dated = QuickInputParser.setDateShortcut(prioritized, "2026-11-25")

        assertEquals("Review !1 2026-11-25 /Fin", dated)
        assertEquals("Fin", QuickInputParser.parse(dated, now, zone).activeFolderQuery)
        val selected = QuickInputParser.selectFolderSuggestion(dated, "Finance")
        val parsed = QuickInputParser.parse(selected, now, zone)
        assertEquals("Review", parsed.title)
        assertEquals(listOf("Finance"), parsed.folderPath)
        assertEquals(TaskPriority.URGENT, parsed.priority)
        assertEquals(now.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli(), parsed.dueDateMillis)
    }

    @Test
    fun `parses Japanese and Korean relative dates cleanly`() {
        val jp = QuickInputParser.parse("明日 15:00 デザイン確認 !2 #UI", now = now, zoneId = zone)
        assertEquals("デザイン確認", jp.title)
        assertEquals(TaskPriority.HIGH, jp.priority)
        assertEquals(listOf("UI"), jp.tags)
        assertEquals(
            LocalDateTime.of(2026, 11, 26, 15, 0),
            Instant.ofEpochMilli(jp.dueDateMillis!!).atZone(zone).toLocalDateTime()
        )

        val ko = QuickInputParser.parse("내일 09:30 스탠드업 미팅 !1", now = now, zoneId = zone)
        assertEquals("스탠드업 미팅", ko.title)
        assertEquals(TaskPriority.URGENT, ko.priority)
        assertEquals(
            LocalDateTime.of(2026, 11, 26, 9, 30),
            Instant.ofEpochMilli(ko.dueDateMillis!!).atZone(zone).toLocalDateTime()
        )
    }

    @Test
    fun `plain capture without tokens preserves full text with zero mandatory fields`() {
        val plain = QuickInputParser.parse("买一箱苏打水", now = now, zoneId = zone)
        assertEquals("买一箱苏打水", plain.title)
        assertNull(plain.dueDateMillis)
        assertNull(plain.folderPath)
        assertEquals(TaskPriority.NONE, plain.priority)
        assertTrue(plain.tags.isEmpty())
        assertFalse(plain.hasStructuredTokens)
        assertNotNull(plain.tokens)
    }
}
