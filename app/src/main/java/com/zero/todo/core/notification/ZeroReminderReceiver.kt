package com.zero.todo.core.notification

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zero.todo.core.common.ZeroDateFormatter
import com.zero.todo.data.repository.toDomain
import com.zero.todo.domain.model.EffortTier
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.domain.model.FolderPathFormatter
import com.zero.todo.widget.ZeroWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
                        val ep = ZeroWidgetUpdater.entryPoint(context)
                        val task = ep.taskRepository().getTaskById(taskId)
                        if (task != null && !task.isCompleted && !task.isDeleted) {
                            val folderLabel = task.folderId?.let { fid ->
                                val folders = runCatching {
                                    ep.folderDao().getActiveFolders().map { it.toDomain() }
                                }.getOrDefault(emptyList())
                                val mode = ep.userPreferencesRepository().preferences.value.folderPathDisplayMode
                                val index = FolderPathFormatter.buildIndex(folders)
                                index.badgeFor(fid, mode)?.fullText
                                    ?: ep.folderDao().getFolderById(fid)?.name
                            }
                            val timeText = if (task.dueDate != null && !task.isAllDay) {
                                Instant.ofEpochMilli(task.dueDate)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalTime()
                                    .format(DateTimeFormatter.ofPattern("HH:mm"))
                            } else {
                                null
                            }
                            val subtitle = listOfNotNull(
                                folderLabel?.let { "/$it" },
                                timeText
                            ).joinToString(" · ").takeIf { it.isNotBlank() }
                            ZeroNotificationHelper.showTaskReminder(
                                context = context,
                                taskId = task.id,
                                title = task.title,
                                subtitle = subtitle,
                                firstStep = task.firstStep
                            )
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
                        val scheduler = ep.reminderScheduler()
                        val copies = ep.taskRepository().completeTask(taskId)
                        scheduler.cancelTaskReminder(taskId)
                        copies?.forEach(scheduler::syncTaskReminder)
                        ZeroWidgetUpdater.refreshAll(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_NOTIFICATION_SNOOZE_15M,
            ACTION_NOTIFICATION_SNOOZE -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                val title = intent.getStringExtra(EXTRA_TASK_TITLE).orEmpty()
                val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
                if (notifId != -1) ZeroNotificationHelper.cancelNotification(context, notifId)
                val delayMs = if (action == ACTION_NOTIFICATION_SNOOZE_15M) SNOOZE_15M_MS else SNOOZE_ONE_HOUR_MS
                val snoozeAt = System.currentTimeMillis() + delayMs
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val ep = ZeroWidgetUpdater.entryPoint(context)
                        val task = ep.taskRepository().getTaskById(taskId)
                        if (task != null && !task.isCompleted) {
                            ep.taskRepository().updateTask(task.id) { current ->
                                if (current.isCompleted) current else current.copy(reminderAt = snoozeAt)
                            }?.let(ep.reminderScheduler()::syncTaskReminder)
                        } else if (title.isNotBlank()) {
                            ep.reminderScheduler().scheduleTaskReminder(taskId, title, snoozeAt)
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_MORNING_BRIEFING -> {
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val ep = ZeroWidgetUpdater.entryPoint(context)
                        val today = LocalDate.now()
                        val startOfToday = ZeroDateFormatter.startOfDayMillis(today)
                        val endOfToday = ZeroDateFormatter.endOfDayExclusiveMillis(today)
                        val active = ep.taskDao().getActiveTasksDueBefore(endOfToday)
                        val overdueCount = active.count { (it.dueDate ?: Long.MAX_VALUE) < startOfToday }
                        val todayCount = active.size - overdueCount
                        val totalMinutes = active.sumOf { it.estimatedMinutes ?: EffortTier.DEFAULT_UNSET_MINUTES }
                        val previewTitles = active.take(5).map { it.title }

                        ZeroNotificationHelper.showDailyBriefing(
                            context = context,
                            todayCount = todayCount,
                            overdueCount = overdueCount,
                            totalMinutes = totalMinutes,
                            previewTitles = previewTitles
                        )
                        val prefs = ep.userPreferencesRepository().preferences.value.notifications
                        ep.reminderScheduler().syncDailyBriefings(prefs)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_EVENING_REVIEW -> {
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val ep = ZeroWidgetUpdater.entryPoint(context)
                        val today = LocalDate.now()
                        val endOfToday = ZeroDateFormatter.endOfDayExclusiveMillis(today)
                        val active = ep.taskDao().getActiveTasksDueBefore(endOfToday)
                        ZeroNotificationHelper.showEveningReview(
                            context = context,
                            remainingCount = active.size,
                            previewTitles = active.take(5).map { it.title }
                        )
                        val prefs = ep.userPreferencesRepository().preferences.value.notifications
                        ep.reminderScheduler().syncDailyBriefings(prefs)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
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
                        scheduler.syncDailyBriefings(ep.userPreferencesRepository().preferences.value.notifications)
                        ep.geofenceController().syncAllGeofences()
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
        const val ACTION_NOTIFICATION_SNOOZE_15M = "com.zero.todo.action.NOTIFICATION_SNOOZE_15M"
        const val ACTION_MORNING_BRIEFING = "com.zero.todo.action.MORNING_BRIEFING"
        const val ACTION_EVENING_REVIEW = "com.zero.todo.action.EVENING_REVIEW"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_FOCUS_MODE = "extra_focus_mode"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        private const val SNOOZE_15M_MS = 15L * 60L * 1000L
        private const val SNOOZE_ONE_HOUR_MS = 60L * 60L * 1000L
    }
}
