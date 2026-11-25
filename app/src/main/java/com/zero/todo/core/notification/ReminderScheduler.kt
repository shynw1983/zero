package com.zero.todo.core.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.domain.model.TaskItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface ReminderScheduler {
    fun scheduleTaskReminder(taskId: String, title: String, triggerAtMillis: Long)
    fun cancelTaskReminder(taskId: String)
    fun scheduleFocusAlert(mode: FocusMode, taskTitle: String?, triggerAtMillis: Long)
    fun cancelFocusAlert()
}

internal fun ReminderScheduler.syncTaskReminder(task: TaskItem) {
    val triggerAt = task.reminderAt
    if (!task.isCompleted && !task.isDeleted && triggerAt != null && triggerAt > System.currentTimeMillis()) {
        scheduleTaskReminder(task.id, task.title, triggerAt)
    } else {
        cancelTaskReminder(task.id)
    }
}

object NoOpReminderScheduler : ReminderScheduler {
    override fun scheduleTaskReminder(taskId: String, title: String, triggerAtMillis: Long) = Unit
    override fun cancelTaskReminder(taskId: String) = Unit
    override fun scheduleFocusAlert(mode: FocusMode, taskTitle: String?, triggerAtMillis: Long) = Unit
    override fun cancelFocusAlert() = Unit
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

    companion object {
        private const val FOCUS_REQUEST_CODE = 800_001
    }
}
