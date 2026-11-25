package com.zero.todo.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zero.todo.MainActivity
import com.zero.todo.R
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.widget.ZeroWidgetIntents

object ZeroNotificationHelper {

    const val CHANNEL_TASK_REMINDERS = "zero_task_reminders"
    const val CHANNEL_FOCUS_ALERTS = "zero_focus_alerts"
    private const val FOCUS_NOTIFICATION_ID = 900_001

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val reminderChannel = NotificationChannel(
            CHANNEL_TASK_REMINDERS,
            context.getString(R.string.notif_channel_reminders),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_reminders_desc)
            enableVibration(true)
        }
        val focusChannel = NotificationChannel(
            CHANNEL_FOCUS_ALERTS,
            context.getString(R.string.notif_channel_focus),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_focus_desc)
            enableVibration(true)
        }
        manager.createNotificationChannel(reminderChannel)
        manager.createNotificationChannel(focusChannel)
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

    fun taskNotificationId(taskId: String): Int = 100_000 + (taskId.hashCode() and 0x0FFFFFFF)

    fun showTaskReminder(context: Context, taskId: String, title: String) {
        ensureChannels(context)
        if (!canPostNotifications(context)) return

        val notificationId = taskNotificationId(taskId)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
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
            putExtra(ZeroReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ZeroReminderReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val completePending = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, ZeroReminderReceiver::class.java).apply {
            action = ZeroReminderReceiver.ACTION_NOTIFICATION_SNOOZE
            putExtra(ZeroReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ZeroReminderReceiver.EXTRA_TASK_TITLE, title)
            putExtra(ZeroReminderReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val snoozePending = PendingIntent.getBroadcast(
            context,
            notificationId + 2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_TASK_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notif_task_due_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .addAction(0, context.getString(R.string.action_done), completePending)
            .addAction(0, context.getString(R.string.notif_action_snooze_1h), snoozePending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    fun showFocusCompletion(context: Context, mode: FocusMode, taskTitle: String?) {
        ensureChannels(context)
        if (!canPostNotifications(context)) return

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
            context.getString(R.string.notif_focus_body_default)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_FOCUS_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(titleRes))
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

    fun cancelNotification(context: Context, notificationId: Int) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
    }
}
