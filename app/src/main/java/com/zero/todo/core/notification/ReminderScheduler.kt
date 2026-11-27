package com.zero.todo.core.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.zero.todo.data.preferences.NotificationPreferences
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.domain.model.TaskItem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

interface ReminderScheduler {
    fun scheduleTaskReminder(taskId: String, title: String, triggerAtMillis: Long)
    fun cancelTaskReminder(taskId: String)
    fun scheduleFocusAlert(mode: FocusMode, taskTitle: String?, triggerAtMillis: Long)
    fun cancelFocusAlert()
    fun syncDailyBriefings(prefs: NotificationPreferences)
}

internal fun ReminderScheduler.syncTaskReminder(task: TaskItem) {
    val triggerAt = task.reminderAt
    if (!task.isCompleted && !task.isDeleted && triggerAt != null && triggerAt > System.currentTimeMillis()) {
        scheduleTaskReminder(task.id, task.title, triggerAt)
    } else {
        cancelTaskReminder(task.id)
    }
}

internal fun nextDailyTriggerMillis(
    now: LocalDateTime,
    hour: Int,
    minute: Int,
    zoneId: ZoneId = ZoneId.systemDefault()
): Long {
    val todayTarget = now.toLocalDate().atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    val target = if (todayTarget.isAfter(now)) todayTarget else todayTarget.plusDays(1)
    return target.atZone(zoneId).toInstant().toEpochMilli()
}

object NoOpReminderScheduler : ReminderScheduler {
    override fun scheduleTaskReminder(taskId: String, title: String, triggerAtMillis: Long) = Unit
    override fun cancelTaskReminder(taskId: String) = Unit
    override fun scheduleFocusAlert(mode: FocusMode, taskTitle: String?, triggerAtMillis: Long) = Unit
    override fun cancelFocusAlert() = Unit
    override fun syncDailyBriefings(prefs: NotificationPreferences) = Unit
}

@Singleton
class AlarmReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : ReminderScheduler {

    private val alarmManager: AlarmManager?
        get() = context.getSystemService(AlarmManager::class.java)

    override fun scheduleTaskReminder(taskId: String, title: String, triggerAtMillis: Long) {
        if (triggerAtMillis <= System.currentTimeMillis()) {
            cancelTaskReminder(taskId)
            return
        }
        val am = alarmManager ?: return
        val pending = taskPendingIntent(taskId, title)
        scheduleSafe(am, triggerAtMillis, pending)
    }

    override fun cancelTaskReminder(taskId: String) {
        val am = alarmManager ?: return
        val pending = taskPendingIntent(taskId, "")
        am.cancel(pending)
        pending.cancel()
        ZeroNotificationHelper.cancelNotification(context, ZeroNotificationHelper.taskNotificationId(taskId))
    }

    override fun scheduleFocusAlert(mode: FocusMode, taskTitle: String?, triggerAtMillis: Long) {
        if (triggerAtMillis <= System.currentTimeMillis()) return
        val am = alarmManager ?: return
        val pending = focusPendingIntent(mode, taskTitle)
        scheduleSafe(am, triggerAtMillis, pending)
    }

    override fun cancelFocusAlert() {
        val am = alarmManager ?: return
        val pending = focusPendingIntent(FocusMode.POMODORO, null)
        am.cancel(pending)
        pending.cancel()
    }

    override fun syncDailyBriefings(prefs: NotificationPreferences) {
        val am = alarmManager ?: return
        val now = LocalDateTime.now()

        val morningPending = dailyBriefingPendingIntent(
            action = ZeroReminderReceiver.ACTION_MORNING_BRIEFING,
            requestCode = MORNING_BRIEFING_REQUEST_CODE
        )
        if (prefs.morningBriefingEnabled) {
            val trigger = nextDailyTriggerMillis(
                now = now,
                hour = prefs.morningBriefingMinutes / 60,
                minute = prefs.morningBriefingMinutes % 60
            )
            scheduleSafe(am, trigger, morningPending)
        } else {
            am.cancel(morningPending)
            morningPending.cancel()
        }

        val eveningPending = dailyBriefingPendingIntent(
            action = ZeroReminderReceiver.ACTION_EVENING_REVIEW,
            requestCode = EVENING_REVIEW_REQUEST_CODE
        )
        if (prefs.eveningReviewEnabled) {
            val trigger = nextDailyTriggerMillis(
                now = now,
                hour = prefs.eveningReviewMinutes / 60,
                minute = prefs.eveningReviewMinutes % 60
            )
            scheduleSafe(am, trigger, eveningPending)
        } else {
            am.cancel(eveningPending)
            eveningPending.cancel()
        }
    }

    private fun scheduleSafe(am: AlarmManager, triggerAtMillis: Long, pending: PendingIntent) {
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        runCatching {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            }
        }.onFailure {
            runCatching {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            }
        }
    }

    private fun taskPendingIntent(taskId: String, title: String): PendingIntent {
        val intent = Intent(context, ZeroReminderReceiver::class.java).apply {
            action = ZeroReminderReceiver.ACTION_TASK_REMINDER
            data = Uri.parse("zero://alarm-task/$taskId")
            putExtra(ZeroReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ZeroReminderReceiver.EXTRA_TASK_TITLE, title)
        }
        val requestCode = 500_000 + (taskId.hashCode() and 0x0FFFFFFF)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun focusPendingIntent(mode: FocusMode, taskTitle: String?): PendingIntent {
        val intent = Intent(context, ZeroReminderReceiver::class.java).apply {
            action = ZeroReminderReceiver.ACTION_FOCUS_COMPLETE
            putExtra(ZeroReminderReceiver.EXTRA_FOCUS_MODE, mode.name)
            putExtra(ZeroReminderReceiver.EXTRA_TASK_TITLE, taskTitle)
        }
        return PendingIntent.getBroadcast(
            context,
            FOCUS_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun dailyBriefingPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, ZeroReminderReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("zero://alarm-briefing/$requestCode")
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val FOCUS_REQUEST_CODE = 800_001
        private const val MORNING_BRIEFING_REQUEST_CODE = 800_101
        private const val EVENING_REVIEW_REQUEST_CODE = 800_102
    }
}
