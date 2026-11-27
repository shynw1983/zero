package com.zero.todo.core.notification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zero.todo.data.preferences.NotificationPreferences
import com.zero.todo.data.preferences.SharedPrefsUserPreferencesRepository
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.domain.model.TaskItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class ReminderScheduleLogicTest {

    private class RecordingReminderScheduler : ReminderScheduler {
        val scheduled = mutableMapOf<String, Long>()
        val cancelled = mutableSetOf<String>()
        var lastSyncedDailyPrefs: NotificationPreferences? = null

        override fun scheduleTaskReminder(taskId: String, title: String, triggerAtMillis: Long) {
            scheduled[taskId] = triggerAtMillis
            cancelled.remove(taskId)
        }

        override fun cancelTaskReminder(taskId: String) {
            cancelled.add(taskId)
            scheduled.remove(taskId)
        }

        override fun scheduleFocusAlert(mode: FocusMode, taskTitle: String?, triggerAtMillis: Long) = Unit
        override fun cancelFocusAlert() = Unit
        override fun syncDailyBriefings(prefs: NotificationPreferences) {
            lastSyncedDailyPrefs = prefs
        }
    }

    @Test
    fun `nextDailyTriggerMillis schedules today when target is strictly in future, else rolls to tomorrow`() {
        val zone = ZoneId.of("Asia/Tokyo")
        val morningNow = LocalDateTime.of(2026, 11, 27, 7, 15)

        val sameDayTrigger = nextDailyTriggerMillis(morningNow, hour = 8, minute = 30, zoneId = zone)
        assertEquals(
            LocalDateTime.of(2026, 11, 27, 8, 30).atZone(zone).toInstant().toEpochMilli(),
            sameDayTrigger
        )

        val exactMinuteTrigger = nextDailyTriggerMillis(
            LocalDateTime.of(2026, 11, 27, 8, 30),
            hour = 8,
            minute = 30,
            zoneId = zone
        )
        assertEquals(
            LocalDateTime.of(2026, 11, 28, 8, 30).atZone(zone).toInstant().toEpochMilli(),
            exactMinuteTrigger
        )

        val eveningPast = LocalDateTime.of(2026, 11, 27, 22, 0)
        val rolledTrigger = nextDailyTriggerMillis(eveningPast, hour = 21, minute = 0, zoneId = zone)
        assertEquals(
            LocalDateTime.of(2026, 11, 28, 21, 0),
            Instant.ofEpochMilli(rolledTrigger).atZone(zone).toLocalDateTime()
        )
    }

    @Test
    fun `SharedPrefsUserPreferencesRepository persists NotificationPreferences round-trip`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SharedPrefsUserPreferencesRepository(context)

        val custom = NotificationPreferences(
            defaultAllDayReminderMinutes = 8 * 60 + 15,
            morningBriefingEnabled = true,
            morningBriefingMinutes = 7 * 60 + 45,
            eveningReviewEnabled = true,
            eveningReviewMinutes = 20 * 60 + 30
        )
        repo.setNotificationPreferences(custom)

        val reloaded = SharedPrefsUserPreferencesRepository(context)
        assertEquals(custom, reloaded.preferences.value.notifications)
        assertEquals("08:15", NotificationPreferences.formatMinutes(custom.defaultAllDayReminderMinutes))
        assertEquals("20:30", NotificationPreferences.formatMinutes(custom.eveningReviewMinutes))
    }

    @Test
    fun `syncTaskReminder schedules active future reminders and cancels completed, deleted, or past tasks`() {
        val scheduler = RecordingReminderScheduler()
        val now = System.currentTimeMillis()
        val future = now + 3_600_000L
        val past = now - 60_000L

        val active = TaskItem(id = "t_active", title = "Call bank", reminderAt = future)
        val completed = TaskItem(id = "t_done", title = "Done", isCompleted = true, reminderAt = future)
        val deleted = TaskItem(id = "t_del", title = "Trash", isDeleted = true, reminderAt = future)
        val expired = TaskItem(id = "t_past", title = "Old", reminderAt = past)

        scheduler.syncTaskReminder(active)
        scheduler.syncTaskReminder(completed)
        scheduler.syncTaskReminder(deleted)
        scheduler.syncTaskReminder(expired)

        assertEquals(future, scheduler.scheduled["t_active"])
        assertEquals(setOf("t_done", "t_del", "t_past"), scheduler.cancelled)
    }

    @Test
    fun `taskNotificationId generates stable distinct notification IDs`() {
        val idA1 = ZeroNotificationHelper.taskNotificationId("task-alpha-1")
        val idA2 = ZeroNotificationHelper.taskNotificationId("task-alpha-1")
        val idB = ZeroNotificationHelper.taskNotificationId("task-beta-2")

        assertEquals(idA1, idA2)
        assertNotEquals(idA1, idB)
    }
}
