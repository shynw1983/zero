package com.zero.todo.core.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zero.todo.MainActivity
import com.zero.todo.R
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.feature.folder.NavigationDestination
import com.zero.todo.widget.ZeroWidgetIntents
import com.zero.todo.widget.localizedWidgetContext

object ZeroNotificationHelper {

    const val CHANNEL_TASK_REMINDERS = "zero_task_reminders"
    const val CHANNEL_FOCUS_ALERTS = "zero_focus_alerts"
    const val CHANNEL_DAILY_BRIEFING = "zero_daily_briefing"

    private const val FOCUS_NOTIFICATION_ID = 900_001
    private const val MORNING_BRIEFING_NOTIFICATION_ID = 900_101
    private const val EVENING_REVIEW_NOTIFICATION_ID = 900_102
    private const val TEST_NOTIFICATION_ID = 900_999
    private const val ACCENT_ARGB = 0xFFFF4D00.toInt()

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val loc = localizedWidgetContext(context)

        val reminderChannel = NotificationChannel(
            CHANNEL_TASK_REMINDERS,
            loc.getString(R.string.notif_channel_reminders),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = loc.getString(R.string.notif_channel_reminders_desc)
            enableVibration(true)
        }
        val focusChannel = NotificationChannel(
            CHANNEL_FOCUS_ALERTS,
            loc.getString(R.string.notif_channel_focus),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = loc.getString(R.string.notif_channel_focus_desc)
            enableVibration(true)
        }
        val briefingChannel = NotificationChannel(
            CHANNEL_DAILY_BRIEFING,
            loc.getString(R.string.notif_channel_briefing),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = loc.getString(R.string.notif_channel_briefing_desc)
            enableVibration(true)
        }
        manager.createNotificationChannel(reminderChannel)
        manager.createNotificationChannel(focusChannel)
        manager.createNotificationChannel(briefingChannel)
    }

    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun taskNotificationId(taskId: String): Int = 100_000 + (taskId.hashCode() and 0x0FFFFFFF)

    fun showTaskReminder(
        context: Context,
        taskId: String,
        title: String,
        subtitle: String? = null,
        firstStep: String? = null
    ) {
        ensureChannels(context)
        if (!canPostNotifications(context)) return

        val loc = localizedWidgetContext(context)
        val notificationId = taskNotificationId(taskId)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("zero://notif-task/$taskId")
            putExtra(ZeroWidgetIntents.EXTRA_TASK_ID, taskId)
        }
        val openPending = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeIntent = Intent(context, ZeroReminderReceiver::class.java).apply {
            action = ZeroReminderReceiver.ACTION_NOTIFICATION_COMPLETE
            data = Uri.parse("zero://notif-complete/$taskId")
            putExtra(ZeroReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ZeroReminderReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val completePending = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snooze15mIntent = Intent(context, ZeroReminderReceiver::class.java).apply {
            action = ZeroReminderReceiver.ACTION_NOTIFICATION_SNOOZE_15M
            data = Uri.parse("zero://notif-snooze15/$taskId")
            putExtra(ZeroReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ZeroReminderReceiver.EXTRA_TASK_TITLE, title)
            putExtra(ZeroReminderReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val snooze15mPending = PendingIntent.getBroadcast(
            context,
            notificationId + 2,
            snooze15mIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snooze1hIntent = Intent(context, ZeroReminderReceiver::class.java).apply {
            action = ZeroReminderReceiver.ACTION_NOTIFICATION_SNOOZE
            data = Uri.parse("zero://notif-snooze60/$taskId")
            putExtra(ZeroReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ZeroReminderReceiver.EXTRA_TASK_TITLE, title)
            putExtra(ZeroReminderReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val snooze1hPending = PendingIntent.getBroadcast(
            context,
            notificationId + 3,
            snooze1hIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bodyText = buildString {
            if (!firstStep.isNullOrBlank()) {
                append(firstStep.trim())
            }
            if (!subtitle.isNullOrBlank()) {
                if (isNotEmpty()) append(" · ")
                append(subtitle)
            }
            if (isEmpty()) {
                append(loc.getString(R.string.notif_task_due_body))
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_TASK_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_zero)
            .setColor(ACCENT_ARGB)
            .setContentTitle(title)
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .addAction(0, loc.getString(R.string.action_done), completePending)
            .addAction(0, loc.getString(R.string.notif_action_snooze_15m), snooze15mPending)
            .addAction(0, loc.getString(R.string.notif_action_snooze_1h), snooze1hPending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    fun showFocusCompletion(context: Context, mode: FocusMode, taskTitle: String?) {
        ensureChannels(context)
        if (!canPostNotifications(context)) return

        val loc = localizedWidgetContext(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            FOCUS_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val titleRes = when (mode) {
            FocusMode.TWO_MIN_STARTER -> R.string.notif_focus_two_min_title
            FocusMode.POMODORO -> R.string.notif_focus_pomodoro_title
            FocusMode.STOPWATCH -> R.string.focus_header_label
        }
        val body = if (!taskTitle.isNullOrBlank()) {
            taskTitle
        } else {
            loc.getString(R.string.notif_focus_body_default)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_FOCUS_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_zero)
            .setColor(ACCENT_ARGB)
            .setContentTitle(loc.getString(titleRes))
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(FOCUS_NOTIFICATION_ID, notification)
        }
    }

    fun showDailyBriefing(
        context: Context,
        todayCount: Int,
        overdueCount: Int,
        totalMinutes: Int,
        previewTitles: List<String>
    ) {
        ensureChannels(context)
        if (!canPostNotifications(context)) return

        val loc = localizedWidgetContext(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("zero://notif-briefing/morning")
            putExtra(ZeroWidgetIntents.EXTRA_DESTINATION_KEY, NavigationDestination.Today.toStorageKey())
        }
        val openPending = PendingIntent.getActivity(
            context,
            MORNING_BRIEFING_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val totalCount = todayCount + overdueCount
        val durationLabel = if (totalMinutes >= 60) {
            val h = totalMinutes / 60
            val m = totalMinutes % 60
            if (m > 0) "${h}h${m}m" else "${h}h"
        } else {
            "${totalMinutes}m"
        }

        val summary = if (totalCount == 0) {
            loc.getString(R.string.notif_morning_empty)
        } else if (overdueCount > 0) {
            loc.getString(R.string.notif_morning_summary_overdue, todayCount, overdueCount, durationLabel)
        } else {
            loc.getString(R.string.notif_morning_summary, todayCount, durationLabel)
        }

        val inboxStyle = NotificationCompat.InboxStyle().setSummaryText(summary)
        for (title in previewTitles.take(5)) {
            inboxStyle.addLine("• $title")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_BRIEFING)
            .setSmallIcon(R.drawable.ic_stat_zero)
            .setColor(ACCENT_ARGB)
            .setContentTitle(loc.getString(R.string.notif_morning_title))
            .setContentText(summary)
            .setStyle(if (previewTitles.isNotEmpty()) inboxStyle else NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(MORNING_BRIEFING_NOTIFICATION_ID, notification)
        }
    }

    fun showEveningReview(
        context: Context,
        remainingCount: Int,
        previewTitles: List<String>
    ) {
        if (remainingCount <= 0) return
        ensureChannels(context)
        if (!canPostNotifications(context)) return

        val loc = localizedWidgetContext(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("zero://notif-briefing/evening")
            putExtra(ZeroWidgetIntents.EXTRA_DESTINATION_KEY, NavigationDestination.Today.toStorageKey())
        }
        val openPending = PendingIntent.getActivity(
            context,
            EVENING_REVIEW_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summary = loc.getString(R.string.notif_evening_summary, remainingCount)
        val inboxStyle = NotificationCompat.InboxStyle().setSummaryText(summary)
        for (title in previewTitles.take(5)) {
            inboxStyle.addLine("• $title")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_BRIEFING)
            .setSmallIcon(R.drawable.ic_stat_zero)
            .setColor(ACCENT_ARGB)
            .setContentTitle(loc.getString(R.string.notif_evening_title))
            .setContentText(summary)
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(EVENING_REVIEW_NOTIFICATION_ID, notification)
        }
    }

    fun showTestNotification(context: Context): Boolean {
        ensureChannels(context)
        if (!canPostNotifications(context)) return false
        val loc = localizedWidgetContext(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            TEST_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_TASK_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_zero)
            .setColor(ACCENT_ARGB)
            .setContentTitle(loc.getString(R.string.notif_test_title))
            .setContentText(loc.getString(R.string.notif_test_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
    }
}
