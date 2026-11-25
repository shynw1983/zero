package com.zero.todo.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.widget.ZeroWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ZeroReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            ACTION_TASK_REMINDER -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                val fallbackTitle = intent.getStringExtra(EXTRA_TASK_TITLE).orEmpty()
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val task = ZeroWidgetUpdater.entryPoint(context).taskRepository().getTaskById(taskId)
                        if (task != null && !task.isCompleted) {
                            ZeroNotificationHelper.showTaskReminder(context, task.id, task.title)
                        } else if (task == null && fallbackTitle.isNotBlank()) {
                            ZeroNotificationHelper.showTaskReminder(context, taskId, fallbackTitle)
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_FOCUS_COMPLETE -> {
                val modeName = intent.getStringExtra(EXTRA_FOCUS_MODE)
                val mode = FocusMode.entries.firstOrNull { it.name == modeName } ?: FocusMode.POMODORO
                val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE)
                ZeroNotificationHelper.showFocusCompletion(context, mode, taskTitle)
            }

            ACTION_NOTIFICATION_COMPLETE -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
                if (notifId != -1) ZeroNotificationHelper.cancelNotification(context, notifId)
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val ep = ZeroWidgetUpdater.entryPoint(context)
                        ep.taskRepository().setTaskCompleted(taskId, true)
                        ep.reminderScheduler().cancelTaskReminder(taskId)
                        ZeroWidgetUpdater.refreshAll(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_NOTIFICATION_SNOOZE -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                val title = intent.getStringExtra(EXTRA_TASK_TITLE).orEmpty()
                val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
                if (notifId != -1) ZeroNotificationHelper.cancelNotification(context, notifId)
                val snoozeAt = System.currentTimeMillis() + SNOOZE_ONE_HOUR_MS
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val ep = ZeroWidgetUpdater.entryPoint(context)
                        val task = ep.taskRepository().getTaskById(taskId)
                        if (task != null && !task.isCompleted) {
                            ep.taskRepository().updateTask(task.copy(reminderAt = snoozeAt))
                            ep.reminderScheduler().scheduleTaskReminder(task.id, task.title, snoozeAt)
                        } else if (title.isNotBlank()) {
                            ep.reminderScheduler().scheduleTaskReminder(taskId, title, snoozeAt)
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val ep = ZeroWidgetUpdater.entryPoint(context)
                        val upcoming = ep.taskRepository().getUpcomingReminderTasks(System.currentTimeMillis())
                        val scheduler = ep.reminderScheduler()
                        for (task in upcoming) {
                            val triggerAt = task.reminderAt ?: continue
                            scheduler.scheduleTaskReminder(task.id, task.title, triggerAt)
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_TASK_REMINDER = "com.zero.todo.action.TASK_REMINDER"
        const val ACTION_FOCUS_COMPLETE = "com.zero.todo.action.FOCUS_COMPLETE"
        const val ACTION_NOTIFICATION_COMPLETE = "com.zero.todo.action.NOTIFICATION_COMPLETE"
        const val ACTION_NOTIFICATION_SNOOZE = "com.zero.todo.action.NOTIFICATION_SNOOZE"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_FOCUS_MODE = "extra_focus_mode"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        private const val SNOOZE_ONE_HOUR_MS = 60L * 60L * 1000L
    }
}
