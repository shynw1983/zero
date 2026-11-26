package com.zero.todo.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.zero.todo.core.common.ZeroDateFormatter
import com.zero.todo.core.designsystem.ZeroAccent
import com.zero.todo.core.designsystem.ZeroPaletteTokens
import com.zero.todo.data.local.entity.FolderEntity
import com.zero.todo.data.local.entity.TagEntity
import com.zero.todo.data.local.entity.TaskEntity
import com.zero.todo.data.local.entity.TaskWithTags
import com.zero.todo.feature.folder.NavigationDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class WidgetConfigAndFilterTest {

    private fun task(
        id: String,
        title: String = id,
        folderId: String? = null,
        parentTaskId: String? = null,
        priority: Int = 4,
        isCompleted: Boolean = false,
        dueDate: Long? = null,
        sortOrder: Double = 0.0
    ): TaskEntity = TaskEntity(
        id = id,
        folderId = folderId,
        parentTaskId = parentTaskId,
        title = title,
        priority = priority,
        isCompleted = isCompleted,
        dueDate = dueDate,
        sortOrder = sortOrder
    )

    @Test
    fun `WidgetTaskQueryEngine filters today, inbox, next 7 days, high priority, folders with subfolders, tags, and completed toggle`() {
        val today = LocalDate.of(2026, 11, 26)
        val todayNoon = ZeroDateFormatter.startOfDayMillis(today) + 12 * 3600_000L
        val yesterdayNoon = ZeroDateFormatter.startOfDayMillis(today.minusDays(1)) + 12 * 3600_000L
        val plus3DaysNoon = ZeroDateFormatter.startOfDayMillis(today.plusDays(3)) + 12 * 3600_000L
        val plus10DaysNoon = ZeroDateFormatter.startOfDayMillis(today.plusDays(10)) + 12 * 3600_000L

        val rootFolder = FolderEntity(id = "f_work", name = "Work")
        val childFolder = FolderEntity(id = "f_sub", parentId = "f_work", name = "Q4")
        val folders = listOf(rootFolder, childFolder)

        val focusTag = TagEntity(id = "tag_focus", name = "deep")

        val allTasks = listOf(
            TaskWithTags(task("overdue", dueDate = yesterdayNoon, priority = 2), emptyList()),
            TaskWithTags(task("today_done", dueDate = todayNoon, isCompleted = true), emptyList()),
            TaskWithTags(task("today_open", dueDate = todayNoon, priority = 1, folderId = "f_work"), listOf(focusTag)),
            TaskWithTags(task("inbox_open", folderId = null, priority = 3), emptyList()),
            TaskWithTags(task("next3_child", dueDate = plus3DaysNoon, folderId = "f_sub", priority = 4), listOf(focusTag)),
            TaskWithTags(task("next10_far", dueDate = plus10DaysNoon, priority = 1), emptyList()),
            TaskWithTags(task("subtask", folderId = "f_work", parentTaskId = "today_open", priority = 1), emptyList())
        )

        // TODAY without completed
        val todayOpen = WidgetTaskQueryEngine.filterTasks(
            allTasks,
            folders,
            ZeroWidgetConfig(filterScope = WidgetFilterScope.TODAY, showCompleted = false),
            today
        )
        assertEquals(listOf("overdue", "today_open"), todayOpen.map { it.id })

        // TODAY with completed
        val todayWithDone = WidgetTaskQueryEngine.filterTasks(
            allTasks,
            folders,
            ZeroWidgetConfig(filterScope = WidgetFilterScope.TODAY, showCompleted = true),
            today
        )
        assertEquals(listOf("overdue", "today_open", "today_done"), todayWithDone.map { it.id })

        // INBOX
        val inbox = WidgetTaskQueryEngine.filterTasks(
            allTasks,
            folders,
            ZeroWidgetConfig(filterScope = WidgetFilterScope.INBOX),
            today
        )
        assertEquals(listOf("overdue", "inbox_open", "next10_far"), inbox.map { it.id })

        // NEXT_7_DAYS
        val next7 = WidgetTaskQueryEngine.filterTasks(
            allTasks,
            folders,
            ZeroWidgetConfig(filterScope = WidgetFilterScope.NEXT_7_DAYS),
            today
        )
        assertEquals(listOf("overdue", "today_open", "next3_child"), next7.map { it.id })

        // HIGH_PRIORITY
        val highPriority = WidgetTaskQueryEngine.filterTasks(
            allTasks,
            folders,
            ZeroWidgetConfig(filterScope = WidgetFilterScope.HIGH_PRIORITY),
            today
        )
        assertEquals(listOf("today_open", "next10_far", "subtask", "overdue"), highPriority.map { it.id })

        // FOLDER with includeSubfolders = true vs false
        val folderWithSub = WidgetTaskQueryEngine.filterTasks(
            allTasks,
            folders,
            ZeroWidgetConfig(filterScope = WidgetFilterScope.FOLDER, folderId = "f_work", includeSubfolders = true),
            today
        )
        assertEquals(listOf("today_open", "next3_child"), folderWithSub.map { it.id })

        val folderOnly = WidgetTaskQueryEngine.filterTasks(
            allTasks,
            folders,
            ZeroWidgetConfig(filterScope = WidgetFilterScope.FOLDER, folderId = "f_work", includeSubfolders = false),
            today
        )
        assertEquals(listOf("today_open"), folderOnly.map { it.id })

        // TAG (single tag + hierarchical parent tag matching child tag)
        val childTag = TagEntity(id = "tag_convenience", parentId = "tag_outing", name = "便利店")
        val parentOutingTag = TagEntity(id = "tag_outing", name = "外出")
        val subTagTask = TaskWithTags(
            task("subtag_task", folderId = "f_work", priority = 2),
            listOf(childTag)
        )
        val tagHierarchyFiltered = WidgetTaskQueryEngine.filterTasks(
            allTasks = allTasks + subTagTask,
            allFolders = folders,
            config = ZeroWidgetConfig(filterScope = WidgetFilterScope.TAG, tagId = parentOutingTag.id),
            today = today,
            allTags = listOf(parentOutingTag, childTag, focusTag)
        )
        assertEquals(listOf("subtag_task"), tagHierarchyFiltered.map { it.id })

        val tagged = WidgetTaskQueryEngine.filterTasks(
            allTasks,
            folders,
            ZeroWidgetConfig(filterScope = WidgetFilterScope.TAG, tagId = "tag_focus"),
            today
        )
        assertEquals(listOf("today_open", "next3_child"), tagged.map { it.id })

        // Destination keys match
        assertEquals(
            NavigationDestination.FolderView("f_work").toStorageKey(),
            WidgetTaskQueryEngine.destinationStorageKey(
                ZeroWidgetConfig(filterScope = WidgetFilterScope.FOLDER, folderId = "f_work")
            )
        )
        assertEquals(
            NavigationDestination.TagView("tag_focus").toStorageKey(),
            WidgetTaskQueryEngine.destinationStorageKey(
                ZeroWidgetConfig(filterScope = WidgetFilterScope.TAG, tagId = "tag_focus")
            )
        )
    }

    @Test
    fun `WidgetConfigStore saves, isolates per widget id, and deletes configs cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cfg1 = ZeroWidgetConfig(
            filterScope = WidgetFilterScope.FOLDER,
            folderId = "f_work",
            includeSubfolders = false,
            showCompleted = true,
            viewMode = WidgetViewMode.FOCUS_NOW,
            themeMode = WidgetThemeMode.MONOLITH_ORANGE,
            accentOption = WidgetAccentOption.SIGNAL_ORANGE
        )
        val cfg2 = ZeroWidgetConfig(
            filterScope = WidgetFilterScope.TAG,
            tagId = "t_deep",
            viewMode = WidgetViewMode.COMPACT,
            themeMode = WidgetThemeMode.LIGHT_PAPER,
            accentOption = WidgetAccentOption.SAGE
        )

        WidgetConfigStore.save(context, 101, cfg1)
        WidgetConfigStore.save(context, 202, cfg2)

        assertEquals(cfg1, WidgetConfigStore.load(context, 101))
        assertEquals(cfg2, WidgetConfigStore.load(context, 202))

        WidgetConfigStore.delete(context, intArrayOf(101))
        val reset = WidgetConfigStore.load(context, 101, defaultScope = WidgetFilterScope.INBOX)
        assertEquals(WidgetFilterScope.INBOX, reset.filterScope)
        assertEquals(cfg2, WidgetConfigStore.load(context, 202))
    }

    @Test
    fun `resolveWidgetPalette maps all 6 themes and accent overrides accurately`() {
        val followDark = resolveWidgetPalette(
            ZeroWidgetConfig(themeMode = WidgetThemeMode.FOLLOW_APP, accentOption = WidgetAccentOption.FOLLOW_APP),
            appIsDark = true,
            appAccent = ZeroAccent.Sage
        )
        assertTrue(followDark.isDark)
        assertEquals(ZeroPaletteTokens.DarkBackground, followDark.background)
        assertEquals(ZeroAccent.Sage.darkColor, followDark.accent)

        val lightPaper = resolveWidgetPalette(
            ZeroWidgetConfig(themeMode = WidgetThemeMode.LIGHT_PAPER, accentOption = WidgetAccentOption.WARM_CLAY),
            appIsDark = true,
            appAccent = ZeroAccent.SlateBlue
        )
        assertFalse(lightPaper.isDark)
        assertEquals(ZeroPaletteTokens.LightBackground, lightPaper.background)
        assertEquals(ZeroAccent.WarmClay.lightColor, lightPaper.accent)

        val monolith = resolveWidgetPalette(
            ZeroWidgetConfig(themeMode = WidgetThemeMode.MONOLITH_ORANGE, accentOption = WidgetAccentOption.FOLLOW_APP),
            appIsDark = false,
            appAccent = ZeroAccent.Sage
        )
        assertTrue(monolith.isDark)
        assertEquals(Color(0xFF09090B), monolith.background)
        assertEquals(Color(0xFFFF4D00), monolith.accent)

        val translucentDark = resolveWidgetPalette(
            ZeroWidgetConfig(themeMode = WidgetThemeMode.TRANSLUCENT_DARK, accentOption = WidgetAccentOption.SLATE_BLUE),
            appIsDark = false,
            appAccent = ZeroAccent.Sage
        )
        assertTrue(translucentDark.isDark)
        assertEquals(Color(0xCC09090B), translucentDark.background)
        assertEquals(ZeroAccent.SlateBlue.darkColor, translucentDark.accent)

        val translucentLight = resolveWidgetPalette(
            ZeroWidgetConfig(themeMode = WidgetThemeMode.TRANSLUCENT_LIGHT, accentOption = WidgetAccentOption.FOLLOW_APP),
            appIsDark = true,
            appAccent = ZeroAccent.SlateBlue
        )
        assertFalse(translucentLight.isDark)
        assertEquals(Color(0xDCF7F7F9), translucentLight.background)
    }
}
