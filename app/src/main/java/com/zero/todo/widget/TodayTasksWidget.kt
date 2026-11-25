package com.zero.todo.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.zero.todo.MainActivity
import com.zero.todo.R
import com.zero.todo.core.common.ZeroDateFormatter
import com.zero.todo.core.notification.syncTaskReminder
import com.zero.todo.data.local.entity.TaskEntity
import com.zero.todo.feature.folder.NavigationDestination
import java.time.LocalDate

private val WidgetObsidian = Color(0xFF09090B)
private val WidgetCarbon = Color(0xFF141417)
private val WidgetSurfaceElevated = Color(0xFF1C1C21)
private val WidgetHairline = Color(0xFF27272A)
private val WidgetTextPrimary = Color(0xFFF4F4F5)
private val WidgetTextSecondary = Color(0xFFA1A1AA)
private val WidgetTextMuted = Color(0xFF63636B)
private val WidgetAccent = Color(0xFF7B9CC2)
private val WidgetPriorityUrgent = Color(0xFFD66A6A)
private val WidgetPriorityHigh = Color(0xFFCFA052)
private val WidgetPriorityMedium = Color(0xFF7B9CC2)

private val PARAM_TASK_ID = ActionParameters.Key<String>("zero_widget_task_id")

/**
 * Minimalist dark "Today Tasks" Android home screen widget built with Jetpack Glance.
 */
class TodayTasksWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val end = ZeroDateFormatter.endOfDayExclusiveMillis(today)

        val todayTasks = runCatching {
            ZeroWidgetUpdater.entryPoint(context)
                .taskDao()
                .getActiveTasksDueBefore(end)
        }.getOrDefault(emptyList())

        provideContent {
            TodayTasksWidgetContent(context = context, tasks = todayTasks)
        }
    }
}

@Composable
private fun TodayTasksWidgetContent(
    context: Context,
    tasks: List<TaskEntity>
) {
    val openTodayIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(ZeroWidgetIntents.EXTRA_DESTINATION_KEY, NavigationDestination.Today.toStorageKey())
    }
    val quickCaptureIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(ZeroWidgetIntents.EXTRA_DESTINATION_KEY, NavigationDestination.Today.toStorageKey())
        putExtra(ZeroWidgetIntents.EXTRA_LAUNCH_QUICK_INPUT, true)
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetObsidian)
            .cornerRadius(16.dp)
            .padding(14.dp)
    ) {
        // Header Row
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity(openTodayIntent)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .size(7.dp)
                    .background(WidgetAccent)
                    .cornerRadius(4.dp)
            ) {}
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = context.getString(R.string.widget_today_header),
                style = TextStyle(
                    color = ColorProvider(WidgetTextPrimary),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = tasks.size.toString().padStart(2, '0'),
                style = TextStyle(
                    color = ColorProvider(WidgetAccent),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(modifier = GlanceModifier.width(10.dp))
            Box(
                modifier = GlanceModifier
                    .size(26.dp)
                    .background(WidgetSurfaceElevated)
                    .cornerRadius(13.dp)
                    .clickable(actionStartActivity(quickCaptureIntent)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    style = TextStyle(
                        color = ColorProvider(WidgetAccent),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(10.dp))
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(WidgetHairline)
        ) {}
        Spacer(modifier = GlanceModifier.height(8.dp))

        if (tasks.isEmpty()) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .clickable(actionStartActivity(quickCaptureIntent)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "00",
                        style = TextStyle(
                            color = ColorProvider(WidgetTextMuted),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = context.getString(R.string.widget_today_empty),
                        style = TextStyle(
                            color = ColorProvider(WidgetTextSecondary),
                            fontSize = 11.sp
                        )
                    )
                }
            }
        } else {
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                tasks.take(6).forEach { task ->
                    WidgetTaskRow(context = context, task = task)
                    Spacer(modifier = GlanceModifier.height(6.dp))
                }
                if (tasks.size > 6) {
                    Text(
                        text = context.getString(R.string.widget_more_tasks, tasks.size - 6),
                        style = TextStyle(
                            color = ColorProvider(WidgetTextMuted),
                            fontSize = 11.sp
                        ),
                        modifier = GlanceModifier
                            .padding(top = 2.dp)
                            .clickable(actionStartActivity(openTodayIntent))
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetTaskRow(
    context: Context,
    task: TaskEntity
) {
    val openTaskIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(ZeroWidgetIntents.EXTRA_DESTINATION_KEY, NavigationDestination.Today.toStorageKey())
        putExtra(ZeroWidgetIntents.EXTRA_TASK_ID, task.id)
    }

    val indicatorColor = when (task.priority) {
        1 -> WidgetPriorityUrgent
        2 -> WidgetPriorityHigh
        3 -> WidgetPriorityMedium
        else -> WidgetTextMuted
    }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(WidgetCarbon)
            .cornerRadius(8.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Complete checkbox button
        Box(
            modifier = GlanceModifier
                .size(18.dp)
                .background(WidgetSurfaceElevated)
                .cornerRadius(9.dp)
                .clickable(
                    actionRunCallback<CompleteTaskWidgetAction>(
                        actionParametersOf(PARAM_TASK_ID to task.id)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = GlanceModifier
                    .size(6.dp)
                    .background(indicatorColor)
                    .cornerRadius(3.dp)
            ) {}
        }

        Spacer(modifier = GlanceModifier.width(10.dp))

        Text(
            text = task.title,
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(WidgetTextPrimary),
                fontSize = 13.sp
            ),
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(actionStartActivity(openTaskIntent))
        )

        if (task.dueDate != null && !task.isAllDay) {
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = ZeroDateFormatter.formatDueDate(task.dueDate, isAllDay = false)
                    .substringAfter(' '),
                style = TextStyle(
                    color = ColorProvider(WidgetTextSecondary),
                    fontSize = 11.sp
                )
            )
        }
    }
}

class CompleteTaskWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val taskId = parameters[PARAM_TASK_ID] ?: return
        val entryPoint = ZeroWidgetUpdater.entryPoint(context)
        val scheduler = entryPoint.reminderScheduler()
        val copies = entryPoint.taskRepository().completeTask(taskId)
        scheduler.cancelTaskReminder(taskId)
        copies?.forEach(scheduler::syncTaskReminder)
        ZeroWidgetUpdater.refreshAll(context)
    }
}

class TodayTasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayTasksWidget()
}
