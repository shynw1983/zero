package com.zero.todo.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.zero.todo.core.notification.ReminderScheduler
import com.zero.todo.data.local.dao.TaskDao
import com.zero.todo.data.repository.TaskRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ZeroWidgetEntryPoint {
    fun taskDao(): TaskDao
    fun taskRepository(): TaskRepository
    fun reminderScheduler(): ReminderScheduler
}

object ZeroWidgetIntents {
    const val EXTRA_LAUNCH_QUICK_INPUT = "com.zero.todo.extra.LAUNCH_QUICK_INPUT"
    const val EXTRA_DESTINATION_KEY = "com.zero.todo.extra.DESTINATION_KEY"
    const val EXTRA_TASK_ID = "com.zero.todo.extra.TASK_ID"
}

object ZeroWidgetUpdater {
    fun entryPoint(context: Context): ZeroWidgetEntryPoint =
        EntryPointAccessors.fromApplication(context.applicationContext, ZeroWidgetEntryPoint::class.java)

    suspend fun refreshAll(context: Context) {
        runCatching { TodayTasksWidget().updateAll(context) }
        runCatching { QuickCaptureWidget().updateAll(context) }
    }
}
