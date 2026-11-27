package com.zero.todo.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
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
import com.zero.todo.data.repository.toDomain
import com.zero.todo.domain.model.FolderPathFormatter
import java.time.LocalDate

private val PARAM_TASK_ID = ActionParameters.Key<String>("zero_widget_task_id")

/**
 * Configurable minimalist ZERO task list home screen widget built with Jetpack Glance.
 * Supports per-instance Filter Scope, View Mode (List / Compact / Focus Spotlight), and Theme + Accent.
 */
class TodayTasksWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val manager = GlanceAppWidgetManager(context)
        val appWidgetId = runCatching { manager.getAppWidgetId(id) }
            .getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)

        val config = WidgetConfigStore.load(context, appWidgetId, defaultScope = WidgetFilterScope.TODAY)
        val locContext = localizedWidgetContext(context)
        val (appDark, appAccent) = readAppThemePrefs(context)
        val palette = resolveWidgetPalette(config, appDark, appAccent)

        val entryPoint = ZeroWidgetUpdater.entryPoint(context)
        val allTasks = runCatching { entryPoint.taskDao().getActiveTasksWithTagsSnapshot() }.getOrDefault(emptyList())
        val allFolders = runCatching { entryPoint.folderDao().getActiveFolders() }.getOrDefault(emptyList())
        val allTags = runCatching { entryPoint.tagDao().getActiveTagsSnapshot() }.getOrDefault(emptyList())

        val filteredTasks = WidgetTaskQueryEngine.filterTasks(
            allTasks = allTasks,
            allFolders = allFolders,
            config = config,
            today = LocalDate.now(),
            allTags = allTags
        )
        val tagPaths = WidgetTaskQueryEngine.buildTagPathMap(allTags)
        val pathMode = entryPoint.userPreferencesRepository().preferences.value.folderPathDisplayMode
        val folderIndex = FolderPathFormatter.buildIndex(allFolders.map { it.toDomain() })

        val headerTitle = resolveWidgetHeaderTitle(
            locContext = locContext,
            config = config,
            folderName = config.folderId?.let { fid ->
                folderIndex.badgeFor(fid, pathMode)?.fullText
                    ?: allFolders.firstOrNull { it.id == fid }?.name
            },
            tagName = config.tagId?.let { tid -> tagPaths[tid] ?: allTags.firstOrNull { it.id == tid }?.name }
        )

        provideContent {
            ZeroTasksWidgetContent(
                context = locContext,
                appWidgetId = appWidgetId,
                config = config,
                palette = palette,
                headerTitle = headerTitle,
                tasks = filteredTasks
            )
        }
    }
}

internal fun resolveWidgetHeaderTitle(
    locContext: Context,
    config: ZeroWidgetConfig,
    folderName: String?,
    tagName: String?
): String = when (config.filterScope) {
    WidgetFilterScope.TODAY -> locContext.getString(R.string.widget_header_today)
    WidgetFilterScope.INBOX -> locContext.getString(R.string.widget_header_inbox)
    WidgetFilterScope.NEXT_7_DAYS -> locContext.getString(R.string.widget_header_next7)
    WidgetFilterScope.HIGH_PRIORITY -> locContext.getString(R.string.widget_header_priority)
    WidgetFilterScope.ALL_ACTIVE -> locContext.getString(R.string.widget_header_all)
    WidgetFilterScope.FOLDER -> folderName?.takeIf { it.isNotBlank() }
        ?: locContext.getString(R.string.widget_header_folder_fallback)
    WidgetFilterScope.TAG -> tagName?.takeIf { it.isNotBlank() }?.let { "#$it" }
        ?: locContext.getString(R.string.widget_header_tag_fallback)
}

@Composable
private fun ZeroTasksWidgetContent(
    context: Context,
    appWidgetId: Int,
    config: ZeroWidgetConfig,
    palette: WidgetVisualPalette,
    headerTitle: String,
    tasks: List<TaskEntity>
) {
    val destinationKey = WidgetTaskQueryEngine.destinationStorageKey(config)
    val openListIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        data = Uri.parse("zero://widget-open/$appWidgetId")
        putExtra(ZeroWidgetIntents.EXTRA_DESTINATION_KEY, destinationKey)
    }
    val quickCaptureIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        data = Uri.parse("zero://widget-capture/$appWidgetId")
        putExtra(ZeroWidgetIntents.EXTRA_DESTINATION_KEY, destinationKey)
        putExtra(ZeroWidgetIntents.EXTRA_LAUNCH_QUICK_INPUT, true)
    }
    val configureIntent = Intent(context, WidgetConfigActivity::class.java).apply {
        action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        data = Uri.parse("zero://widget-config/$appWidgetId")
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        putExtra(WidgetConfigActivity.EXTRA_IS_QUICK_CAPTURE, false)
    }

    val activeIncompleteCount = tasks.count { !it.isCompleted }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.background)
            .cornerRadius(16.dp)
            .padding(14.dp)
    ) {
        // Header Row
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(openListIntent)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(7.dp)
                        .background(palette.accent)
                        .cornerRadius(4.dp)
                ) {}
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = headerTitle,
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(palette.textPrimary),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = activeIncompleteCount.toString().padStart(2, '0'),
                    style = TextStyle(
                        color = ColorProvider(palette.accent),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                Box(
                    modifier = GlanceModifier
                        .size(26.dp)
                        .background(palette.surfaceElevated)
                        .cornerRadius(13.dp)
                        .clickable(actionStartActivity(configureIntent)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_tune),
                        contentDescription = context.getString(R.string.widget_cd_configure),
                        colorFilter = ColorFilter.tint(ColorProvider(palette.textSecondary)),
                        modifier = GlanceModifier.size(14.dp)
                    )
                }
                Spacer(modifier = GlanceModifier.width(6.dp))
            }

            Box(
                modifier = GlanceModifier
                    .size(26.dp)
                    .background(palette.surfaceElevated)
                    .cornerRadius(13.dp)
                    .clickable(actionStartActivity(quickCaptureIntent)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    style = TextStyle(
                        color = ColorProvider(palette.accent),
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
                .background(palette.hairline)
        ) {}
        Spacer(modifier = GlanceModifier.height(8.dp))

        if (tasks.isEmpty()) {
            val emptyText = if (config.filterScope == WidgetFilterScope.TODAY) {
                context.getString(R.string.widget_today_empty)
            } else {
                context.getString(R.string.widget_empty_filtered)
            }
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
                            color = ColorProvider(palette.textMuted),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = emptyText,
                        style = TextStyle(
                            color = ColorProvider(palette.textSecondary),
                            fontSize = 11.sp
                        )
                    )
                }
            }
        } else {
            when (config.viewMode) {
                WidgetViewMode.LIST -> WidgetListBody(
                    context = context,
                    destinationKey = destinationKey,
                    openListIntent = openListIntent,
                    palette = palette,
                    tasks = tasks
                )
                WidgetViewMode.COMPACT -> WidgetCompactBody(
                    context = context,
                    destinationKey = destinationKey,
                    openListIntent = openListIntent,
                    palette = palette,
                    tasks = tasks
                )
                WidgetViewMode.FOCUS_NOW -> WidgetFocusNowBody(
                    context = context,
                    destinationKey = destinationKey,
                    openListIntent = openListIntent,
                    palette = palette,
                    tasks = tasks
                )
            }
        }
    }
}

@Composable
private fun WidgetListBody(
    context: Context,
    destinationKey: String,
    openListIntent: Intent,
    palette: WidgetVisualPalette,
    tasks: List<TaskEntity>
) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        tasks.take(6).forEach { task ->
            WidgetTaskCardRow(
                context = context,
                destinationKey = destinationKey,
                palette = palette,
                task = task
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
        }
        if (tasks.size > 6) {
            Text(
                text = context.getString(R.string.widget_more_tasks, tasks.size - 6),
                style = TextStyle(
                    color = ColorProvider(palette.textMuted),
                    fontSize = 11.sp
                ),
                modifier = GlanceModifier
                    .padding(top = 2.dp)
                    .clickable(actionStartActivity(openListIntent))
            )
        }
    }
}

@Composable
private fun WidgetCompactBody(
    context: Context,
    destinationKey: String,
    openListIntent: Intent,
    palette: WidgetVisualPalette,
    tasks: List<TaskEntity>
) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        val visible = tasks.take(10)
        visible.forEachIndexed { index, task ->
            val openTaskIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data = Uri.parse("zero://widget-task/${task.id}")
                putExtra(ZeroWidgetIntents.EXTRA_DESTINATION_KEY, destinationKey)
                putExtra(ZeroWidgetIntents.EXTRA_TASK_ID, task.id)
            }
            val indicatorColor = priorityColorFor(task, palette)
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WidgetCheckDot(
                    task = task,
                    indicatorColor = indicatorColor,
                    palette = palette,
                    boxSizeDp = 15
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = task.title,
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(if (task.isCompleted) palette.textMuted else palette.textPrimary),
                        fontSize = 12.sp
                    ),
                    modifier = GlanceModifier
                        .defaultWeight()
                        .clickable(actionStartActivity(openTaskIntent))
                )
                val badgeText = formatTaskMeta(task)
                if (badgeText != null) {
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = badgeText,
                        style = TextStyle(
                            color = ColorProvider(palette.textMuted),
                            fontSize = 10.sp
                        )
                    )
                }
            }
            if (index < visible.lastIndex) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(palette.hairline)
                ) {}
            }
        }
        if (tasks.size > 10) {
            Text(
                text = context.getString(R.string.widget_more_tasks, tasks.size - 10),
                style = TextStyle(
                    color = ColorProvider(palette.textMuted),
                    fontSize = 10.sp
                ),
                modifier = GlanceModifier
                    .padding(top = 4.dp)
                    .clickable(actionStartActivity(openListIntent))
            )
        }
    }
}

@Composable
private fun WidgetFocusNowBody(
    context: Context,
    destinationKey: String,
    openListIntent: Intent,
    palette: WidgetVisualPalette,
    tasks: List<TaskEntity>
) {
    val hero = tasks.first()
    val heroIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        data = Uri.parse("zero://widget-task/${hero.id}")
        putExtra(ZeroWidgetIntents.EXTRA_DESTINATION_KEY, destinationKey)
        putExtra(ZeroWidgetIntents.EXTRA_TASK_ID, hero.id)
    }

    Column(modifier = GlanceModifier.fillMaxWidth()) {
        // Hero Spotlight Card
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(palette.cardSurface)
                .cornerRadius(12.dp)
                .padding(12.dp)
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity(heroIntent)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(8.dp)
                        .background(priorityColorFor(hero, palette))
                        .cornerRadius(4.dp)
                ) {}
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = hero.title,
                    maxLines = 2,
                    style = TextStyle(
                        color = ColorProvider(if (hero.isCompleted) palette.textMuted else palette.textPrimary),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
            }

            val subMeta = hero.firstStep?.takeIf { it.isNotBlank() } ?: formatTaskMeta(hero)
            if (subMeta != null) {
                Spacer(modifier = GlanceModifier.height(6.dp))
                Text(
                    text = subMeta,
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(palette.textSecondary),
                        fontSize = 11.sp
                    )
                )
            }

            if (!hero.isCompleted) {
                Spacer(modifier = GlanceModifier.height(10.dp))
                Box(
                    modifier = GlanceModifier
                        .background(palette.accent)
                        .cornerRadius(8.dp)
                        .clickable(
                            actionRunCallback<CompleteTaskWidgetAction>(
                                actionParametersOf(PARAM_TASK_ID to hero.id)
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = context.getString(R.string.widget_focus_done),
                        style = TextStyle(
                            color = ColorProvider(palette.onAccent),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }

        val upNext = tasks.drop(1).take(2)
        if (upNext.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(8.dp))
            Text(
                text = context.getString(R.string.widget_up_next),
                style = TextStyle(
                    color = ColorProvider(palette.textMuted),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = GlanceModifier.clickable(actionStartActivity(openListIntent))
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            upNext.forEach { task ->
                val taskIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    data = Uri.parse("zero://widget-task/${task.id}")
                    putExtra(ZeroWidgetIntents.EXTRA_DESTINATION_KEY, destinationKey)
                    putExtra(ZeroWidgetIntents.EXTRA_TASK_ID, task.id)
                }
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable(actionStartActivity(taskIntent)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = GlanceModifier
                            .size(5.dp)
                            .background(priorityColorFor(task, palette))
                            .cornerRadius(3.dp)
                    ) {}
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        text = task.title,
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(if (task.isCompleted) palette.textMuted else palette.textSecondary),
                            fontSize = 12.sp
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetTaskCardRow(
    context: Context,
    destinationKey: String,
    palette: WidgetVisualPalette,
    task: TaskEntity
) {
    val openTaskIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        data = Uri.parse("zero://widget-task/${task.id}")
        putExtra(ZeroWidgetIntents.EXTRA_DESTINATION_KEY, destinationKey)
        putExtra(ZeroWidgetIntents.EXTRA_TASK_ID, task.id)
    }
    val indicatorColor = priorityColorFor(task, palette)

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(palette.cardSurface)
            .cornerRadius(8.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WidgetCheckDot(
            task = task,
            indicatorColor = indicatorColor,
            palette = palette,
            boxSizeDp = 18
        )

        Spacer(modifier = GlanceModifier.width(10.dp))

        Text(
            text = task.title,
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(if (task.isCompleted) palette.textMuted else palette.textPrimary),
                fontSize = 13.sp
            ),
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(actionStartActivity(openTaskIntent))
        )

        val meta = formatTaskMeta(task)
        if (meta != null) {
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = meta,
                style = TextStyle(
                    color = ColorProvider(palette.textSecondary),
                    fontSize = 11.sp
                )
            )
        }
    }
}

@Composable
private fun WidgetCheckDot(
    task: TaskEntity,
    indicatorColor: androidx.compose.ui.graphics.Color,
    palette: WidgetVisualPalette,
    boxSizeDp: Int
) {
    val radius = boxSizeDp / 2
    val modifier = if (task.isCompleted) {
        GlanceModifier
            .size(boxSizeDp.dp)
            .background(palette.accent)
            .cornerRadius(radius.dp)
    } else {
        GlanceModifier
            .size(boxSizeDp.dp)
            .background(palette.surfaceElevated)
            .cornerRadius(radius.dp)
            .clickable(
                actionRunCallback<CompleteTaskWidgetAction>(
                    actionParametersOf(PARAM_TASK_ID to task.id)
                )
            )
    }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (!task.isCompleted) {
            Box(
                modifier = GlanceModifier
                    .size(6.dp)
                    .background(indicatorColor)
                    .cornerRadius(3.dp)
            ) {}
        }
    }
}

private fun priorityColorFor(task: TaskEntity, palette: WidgetVisualPalette): androidx.compose.ui.graphics.Color =
    when (task.priority) {
        1 -> palette.priorityUrgent
        2 -> palette.priorityHigh
        3 -> palette.priorityMedium
        else -> palette.textMuted
    }

private fun formatTaskMeta(task: TaskEntity): String? {
    if (task.dueDate != null && !task.isAllDay) {
        return ZeroDateFormatter.formatDueDate(task.dueDate, isAllDay = false).substringAfter(' ')
    }
    val est = task.estimatedMinutes
    if (est != null && est > 0) {
        return if (est >= 60 && est % 60 == 0) "${est / 60}h" else "${est}m"
    }
    return null
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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        WidgetConfigStore.delete(context, appWidgetIds)
    }
}
