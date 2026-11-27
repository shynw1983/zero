package com.zero.todo.feature.settings

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.zero.todo.R
import com.zero.todo.core.designsystem.ZeroAccent
import com.zero.todo.core.designsystem.ZeroCardShape
import com.zero.todo.core.designsystem.ZeroPriorityBadge
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.hairlineDividerBottom
import com.zero.todo.core.designsystem.rememberZeroHaptics
import com.zero.todo.core.notification.ZeroNotificationHelper
import com.zero.todo.data.ai.AiConfig
import com.zero.todo.data.ai.AiProvider
import com.zero.todo.data.ai.AiTestStatus
import com.zero.todo.data.backup.BackupSummary
import com.zero.todo.data.backup.WebDavConfig
import com.zero.todo.data.preferences.NotificationPreferences
import com.zero.todo.domain.model.AntiMisclickMode
import com.zero.todo.domain.model.AppLanguage
import com.zero.todo.domain.model.FolderPathDisplayMode
import com.zero.todo.domain.model.PriorityDisplayStyle
import com.zero.todo.domain.model.TaskPriority
import com.zero.todo.domain.model.TaskSwipeAction
import com.zero.todo.domain.model.TaskSwipeActions
import com.zero.todo.domain.model.TaskSwipeDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

sealed interface BackupUiStatus {
    data object ExportJsonSuccess : BackupUiStatus
    data object ExportMarkdownSuccess : BackupUiStatus
    data class ImportSuccess(val summary: BackupSummary) : BackupUiStatus
    data class Error(val detail: String) : BackupUiStatus
}

sealed interface WebDavUiStatus {
    data object Working : WebDavUiStatus
    data object TestSuccess : WebDavUiStatus
    data object PushSuccess : WebDavUiStatus
    data class PullSuccess(val summary: BackupSummary) : WebDavUiStatus
    data class Error(val detail: String) : WebDavUiStatus
}

@Composable
fun SettingsOverlay(
    isDarkTheme: Boolean,
    activeAccent: ZeroAccent,
    antiMisclickMode: AntiMisclickMode,
    taskSwipeActions: TaskSwipeActions,
    hapticsEnabled: Boolean,
    appLanguage: AppLanguage,
    dailyCapacityMinutes: Int,
    folderPathDisplayMode: FolderPathDisplayMode = FolderPathDisplayMode.SMART_DISAMBIGUATE,
    priorityDisplayStyle: PriorityDisplayStyle = PriorityDisplayStyle.DOTS,
    notificationPreferences: NotificationPreferences = NotificationPreferences(),
    aiConfig: AiConfig = AiConfig(),
    aiTestStatus: AiTestStatus? = null,
    webDavConfig: WebDavConfig,
    webDavStatus: WebDavUiStatus?,
    backupStatus: BackupUiStatus?,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetAccent: (ZeroAccent) -> Unit,
    onSetAntiMisclickMode: (AntiMisclickMode) -> Unit,
    onSetTaskSwipeAction: (TaskSwipeDirection, TaskSwipeAction) -> Unit,
    onSetHapticsEnabled: (Boolean) -> Unit,
    onSetAppLanguage: (AppLanguage) -> Unit,
    onSetDailyCapacityMinutes: (Int) -> Unit,
    onSetFolderPathDisplayMode: (FolderPathDisplayMode) -> Unit = {},
    onSetPriorityDisplayStyle: (PriorityDisplayStyle) -> Unit = {},
    onUpdateNotificationPreferences: (NotificationPreferences) -> Unit = {},
    onUpdateAiConfig: (AiConfig) -> Unit = {},
    onTestAiConnection: () -> Unit = {},
    onUpdateWebDavConfig: (WebDavConfig) -> Unit,
    onTestWebDav: () -> Unit,
    onPushWebDav: () -> Unit,
    onPullWebDav: () -> Unit,
    onGenerateJsonBackup: suspend () -> String,
    onGenerateMarkdownExport: suspend () -> String,
    onImportJsonBackup: (String) -> Unit,
    onReportBackupStatus: (BackupUiStatus) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val haptics = rememberZeroHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onDismiss)

    val dateStamp = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val payload = onGenerateJsonBackup()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(payload.toByteArray(Charsets.UTF_8))
                    } ?: error("OutputStream unavailable")
                }
            }.onSuccess {
                haptics.confirm()
                onReportBackupStatus(BackupUiStatus.ExportJsonSuccess)
            }.onFailure { err ->
                haptics.reject()
                onReportBackupStatus(BackupUiStatus.Error(err.message ?: "IO error"))
            }
        }
    }

    val exportMarkdownLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val payload = onGenerateMarkdownExport()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(payload.toByteArray(Charsets.UTF_8))
                    } ?: error("OutputStream unavailable")
                }
            }.onSuccess {
                haptics.confirm()
                onReportBackupStatus(BackupUiStatus.ExportMarkdownSuccess)
            }.onFailure { err ->
                haptics.reject()
                onReportBackupStatus(BackupUiStatus.Error(err.message ?: "IO error"))
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: error("InputStream unavailable")
                }
            }.onSuccess { content ->
                onImportJsonBackup(content)
            }.onFailure { err ->
                haptics.reject()
                onReportBackupStatus(BackupUiStatus.Error(err.message ?: "IO error"))
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = colors.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .hairlineDividerBottom()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(colors.accent)
                    )
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_close_settings),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 0. Language Selector (JA / ZH / KO / EN / System)
                SettingsSectionCard(title = stringResource(R.string.settings_section_language)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppLanguage.entries.forEach { lang ->
                            val selected = appLanguage == lang
                            Box(
                                modifier = Modifier
                                    .clip(ZeroSmallShape)
                                    .background(if (selected) colors.accentSubtle else colors.background)
                                    .hairlineBorder(
                                        shape = ZeroSmallShape,
                                        color = if (selected) colors.accent else colors.hairlineBorder
                                    )
                                    .clickable {
                                        haptics.reorderTick()
                                        onSetAppLanguage(lang)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = stringResource(lang.labelRes),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) colors.accent else colors.textPrimary
                                )
                            }
                        }
                    }
                }

                // 1. Theme Appearance
                SettingsSectionCard(title = stringResource(R.string.settings_section_theme)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SettingsChoiceTile(
                            title = stringResource(R.string.settings_theme_dark),
                            subtitle = "#09090B",
                            selected = isDarkTheme,
                            onClick = {
                                haptics.reorderTick()
                                onSetDarkTheme(true)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        SettingsChoiceTile(
                            title = stringResource(R.string.settings_theme_light),
                            subtitle = "#F7F7F9",
                            selected = !isDarkTheme,
                            onClick = {
                                haptics.reorderTick()
                                onSetDarkTheme(false)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 2. Functional Accent Color (Calm, non-sci-fi 2x2 minimalist palette)
                SettingsSectionCard(title = stringResource(R.string.settings_section_accent)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ZeroAccent.entries.chunked(2).forEach { rowAccents ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowAccents.forEach { accentOption ->
                                    AccentChoiceTile(
                                        label = stringResource(accentOption.labelRes),
                                        hex = accentOption.darkHexLabel,
                                        accent = accentOption,
                                        isDarkTheme = isDarkTheme,
                                        selected = activeAccent == accentOption,
                                        onClick = {
                                            haptics.reorderTick()
                                            onSetAccent(accentOption)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2.5 Folder Path Display Mode
                SettingsSectionCard(title = stringResource(R.string.settings_folder_path_title)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FolderPathDisplayMode.entries.forEach { mode ->
                                val selected = folderPathDisplayMode == mode
                                val labelRes = when (mode) {
                                    FolderPathDisplayMode.SMART_DISAMBIGUATE -> R.string.settings_folder_path_smart
                                    FolderPathDisplayMode.ROOT_AND_LEAF -> R.string.settings_folder_path_root_leaf
                                    FolderPathDisplayMode.FULL_PATH -> R.string.settings_folder_path_full
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(ZeroSmallShape)
                                        .background(if (selected) colors.accentSubtle else colors.background)
                                        .hairlineBorder(
                                            shape = ZeroSmallShape,
                                            color = if (selected) colors.accent else colors.hairlineBorder
                                        )
                                        .clickable {
                                            haptics.reorderTick()
                                            onSetFolderPathDisplayMode(mode)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(labelRes),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) colors.accent else colors.textPrimary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        val descRes = when (folderPathDisplayMode) {
                            FolderPathDisplayMode.SMART_DISAMBIGUATE -> R.string.settings_folder_path_desc_smart
                            FolderPathDisplayMode.ROOT_AND_LEAF -> R.string.settings_folder_path_desc_root_leaf
                            FolderPathDisplayMode.FULL_PATH -> R.string.settings_folder_path_desc_full
                        }
                        Text(
                            text = stringResource(descRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                }

                // 2.6 Priority Display Style (C: Dots [default] / A: Words / B: Marks)
                SettingsSectionCard(title = stringResource(R.string.settings_priority_style_title)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PriorityDisplayStyle.entries.forEach { option ->
                                val selected = priorityDisplayStyle == option
                                val labelRes = when (option) {
                                    PriorityDisplayStyle.DOTS -> R.string.settings_priority_style_dots
                                    PriorityDisplayStyle.WORDS -> R.string.settings_priority_style_words
                                    PriorityDisplayStyle.MARKS -> R.string.settings_priority_style_marks
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 54.dp)
                                        .clip(ZeroSmallShape)
                                        .background(if (selected) colors.accentSubtle else colors.background)
                                        .hairlineBorder(
                                            shape = ZeroSmallShape,
                                            color = if (selected) colors.accent else colors.hairlineBorder
                                        )
                                        .clickable {
                                            haptics.reorderTick()
                                            onSetPriorityDisplayStyle(option)
                                        }
                                        .padding(horizontal = 6.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
                                ) {
                                    ZeroPriorityBadge(
                                        priority = TaskPriority.URGENT,
                                        color = if (selected) colors.accent else colors.priorityUrgent,
                                        style = option
                                    )
                                    Text(
                                        text = stringResource(labelRes),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) colors.accent else colors.textPrimary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        val styleDescRes = when (priorityDisplayStyle) {
                            PriorityDisplayStyle.DOTS -> R.string.settings_priority_style_desc_dots
                            PriorityDisplayStyle.WORDS -> R.string.settings_priority_style_desc_words
                            PriorityDisplayStyle.MARKS -> R.string.settings_priority_style_desc_marks
                        }
                        Text(
                            text = stringResource(styleDescRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                }

                // 3. Anti-Misclick Mode Switcher
                SettingsSectionCard(title = stringResource(R.string.settings_section_antimisclick)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AntiMisclickMode.entries.forEach { mode ->
                            val selected = antiMisclickMode == mode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(ZeroSmallShape)
                                    .background(if (selected) colors.accentSubtle else colors.background)
                                    .hairlineBorder(
                                        shape = ZeroSmallShape,
                                        color = if (selected) colors.accent else colors.hairlineBorder
                                    )
                                    .clickable {
                                        haptics.reorderTick()
                                        onSetAntiMisclickMode(mode)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = stringResource(mode.titleRes),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (selected) colors.accent else colors.textPrimary
                                    )
                                    Text(
                                        text = stringResource(mode.descriptionRes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (selected) colors.accent else colors.hairlineBorder)
                                )
                            }
                        }
                        if (antiMisclickMode.allowSwipe) {
                            Text(
                                text = stringResource(R.string.settings_swipe_indent_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                            TaskSwipeDirection.entries.forEach { direction ->
                                SwipeActionSelector(
                                    label = stringResource(direction.labelRes),
                                    action = taskSwipeActions.actionFor(direction),
                                    onSelect = { onSetTaskSwipeAction(direction, it) }
                                )
                            }
                        }
                    }
                }

                // 4. Tactile Haptics Toggle
                SettingsSectionCard(title = stringResource(R.string.settings_section_haptics)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_haptics_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary
                            )
                            Text(
                                text = stringResource(R.string.settings_haptics_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                        Row(
                            modifier = Modifier
                                .clip(ZeroSmallShape)
                                .background(colors.background)
                                .hairlineBorder(shape = ZeroSmallShape)
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            ToggleChip(
                                label = stringResource(R.string.settings_toggle_on),
                                selected = hapticsEnabled
                            ) {
                                onSetHapticsEnabled(true)
                                haptics.confirm()
                            }
                            ToggleChip(
                                label = stringResource(R.string.settings_toggle_off),
                                selected = !hapticsEnabled
                            ) {
                                onSetHapticsEnabled(false)
                            }
                        }
                    }
                }

                // 4b. Daily Energy Capacity Budget (for Day Capacity Ruler)
                SettingsSectionCard(title = stringResource(R.string.settings_section_capacity)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.settings_capacity_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(120, 180, 240, 360, 480).forEach { mins ->
                                val selected = dailyCapacityMinutes == mins
                                Box(
                                    modifier = Modifier
                                        .clip(ZeroSmallShape)
                                        .background(if (selected) colors.accentSubtle else colors.background)
                                        .hairlineBorder(
                                            shape = ZeroSmallShape,
                                            color = if (selected) colors.accent else colors.hairlineBorder
                                        )
                                        .clickable {
                                            haptics.reorderTick()
                                            onSetDailyCapacityMinutes(mins)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "${mins / 60}h",
                                        style = tabular.counter,
                                        color = if (selected) colors.accent else colors.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                // 4c. Reminders & Push Notifications Center
                NotificationsSettingsCard(
                    preferences = notificationPreferences,
                    onUpdate = onUpdateNotificationPreferences
                )

                // 4d. Built-in Multimodal AI Engine (Gemini & OpenAI BYOK)
                AiEngineSettingsCard(
                    aiConfig = aiConfig,
                    testStatus = aiTestStatus,
                    onUpdateConfig = onUpdateAiConfig,
                    onTestConnection = onTestAiConnection
                )

                // 5. Local Data Backup & Export (JSON + Hierarchical Markdown)
                SettingsSectionCard(title = stringResource(R.string.settings_section_backup)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BackupActionButton(
                            icon = Icons.Rounded.Download,
                            title = stringResource(R.string.settings_export_json_title),
                            subtitle = stringResource(R.string.settings_export_json_desc),
                            onClick = { exportJsonLauncher.launch("zero_backup_$dateStamp.json") }
                        )
                        BackupActionButton(
                            icon = Icons.Rounded.Upload,
                            title = stringResource(R.string.settings_import_json_title),
                            subtitle = stringResource(R.string.settings_import_json_desc),
                            onClick = { importJsonLauncher.launch(arrayOf("application/json", "text/plain")) }
                        )
                        BackupActionButton(
                            icon = Icons.Rounded.Description,
                            title = stringResource(R.string.settings_export_md_title),
                            subtitle = stringResource(R.string.settings_export_md_desc),
                            onClick = { exportMarkdownLauncher.launch("zero_tasks_$dateStamp.md") }
                        )

                        if (backupStatus != null) {
                            val statusText = when (backupStatus) {
                                BackupUiStatus.ExportJsonSuccess -> stringResource(R.string.backup_export_json_ok)
                                BackupUiStatus.ExportMarkdownSuccess -> stringResource(R.string.backup_export_md_ok)
                                is BackupUiStatus.ImportSuccess -> stringResource(
                                    R.string.backup_import_ok,
                                    backupStatus.summary.foldersCount,
                                    backupStatus.summary.tasksCount,
                                    backupStatus.summary.tagsCount
                                )
                                is BackupUiStatus.Error -> stringResource(R.string.backup_error, backupStatus.detail)
                            }
                            Text(
                                text = statusText,
                                style = tabular.dateCompact,
                                color = colors.accent,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // 6. Lightweight WebDAV Cross-Device Sync (Phone / Foldable / Tablet)
                SettingsSectionCard(title = stringResource(R.string.settings_section_webdav)) {
                    var urlDraft by remember(webDavConfig.serverUrl) { mutableStateOf(webDavConfig.serverUrl) }
                    var userDraft by remember(webDavConfig.username) { mutableStateOf(webDavConfig.username) }
                    var passDraft by remember(webDavConfig.password) { mutableStateOf(webDavConfig.password) }
                    val isBusy = webDavStatus == WebDavUiStatus.Working

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.settings_webdav_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                        WebDavInputField(
                            value = urlDraft,
                            placeholder = stringResource(R.string.settings_webdav_url_hint),
                            onValueChange = {
                                urlDraft = it
                                onUpdateWebDavConfig(webDavConfig.copy(serverUrl = it))
                            }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WebDavInputField(
                                value = userDraft,
                                placeholder = stringResource(R.string.settings_webdav_user_hint),
                                onValueChange = {
                                    userDraft = it
                                    onUpdateWebDavConfig(webDavConfig.copy(username = it))
                                },
                                modifier = Modifier.weight(1f)
                            )
                            WebDavInputField(
                                value = passDraft,
                                placeholder = stringResource(R.string.settings_webdav_pass_hint),
                                isPassword = true,
                                onValueChange = {
                                    passDraft = it
                                    onUpdateWebDavConfig(webDavConfig.copy(password = it))
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            WebDavActionChip(
                                icon = Icons.Rounded.Link,
                                label = stringResource(R.string.settings_webdav_test),
                                enabled = !isBusy && urlDraft.isNotBlank(),
                                onClick = {
                                    haptics.reorderTick()
                                    onTestWebDav()
                                },
                                modifier = Modifier.weight(1f)
                            )
                            WebDavActionChip(
                                icon = Icons.Rounded.CloudUpload,
                                label = stringResource(R.string.settings_webdav_push),
                                enabled = !isBusy && urlDraft.isNotBlank(),
                                onClick = {
                                    haptics.confirm()
                                    onPushWebDav()
                                },
                                modifier = Modifier.weight(1f)
                            )
                            WebDavActionChip(
                                icon = Icons.Rounded.CloudDownload,
                                label = stringResource(R.string.settings_webdav_pull),
                                enabled = !isBusy && urlDraft.isNotBlank(),
                                onClick = {
                                    haptics.confirm()
                                    onPullWebDav()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (webDavStatus != null) {
                            val davText = when (webDavStatus) {
                                WebDavUiStatus.Working -> stringResource(R.string.webdav_status_working)
                                WebDavUiStatus.TestSuccess -> stringResource(R.string.webdav_status_test_ok)
                                WebDavUiStatus.PushSuccess -> stringResource(R.string.webdav_status_push_ok)
                                is WebDavUiStatus.PullSuccess -> stringResource(
                                    R.string.backup_import_ok,
                                    webDavStatus.summary.foldersCount,
                                    webDavStatus.summary.tasksCount,
                                    webDavStatus.summary.tagsCount
                                )
                                is WebDavUiStatus.Error -> stringResource(R.string.backup_error, webDavStatus.detail)
                            }
                            Text(
                                text = davText,
                                style = tabular.dateCompact,
                                color = if (webDavStatus is WebDavUiStatus.Error) colors.priorityUrgent else colors.accent
                            )
                        }
                    }
                }

                // 7. Hardware Keyboard Shortcuts
                SettingsSectionCard(title = stringResource(R.string.settings_section_shortcuts)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShortcutRow("Ctrl / Cmd + N", stringResource(R.string.shortcut_quick_input))
                        ShortcutRow("Ctrl / Cmd + F", stringResource(R.string.shortcut_search))
                        ShortcutRow("Esc", stringResource(R.string.shortcut_escape))
                        ShortcutRow("Ctrl / Cmd + ,", stringResource(R.string.shortcut_settings))
                        ShortcutRow("Ctrl / Cmd + 1..5", stringResource(R.string.shortcut_smart_views))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SwipeActionSelector(
    label: String,
    action: TaskSwipeAction,
    onSelect: (TaskSwipeAction) -> Unit
) {
    val colors = ZeroTheme.colors
    val haptics = rememberZeroHaptics()
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(ZeroSmallShape)
                .background(colors.background)
                .hairlineBorder(shape = ZeroSmallShape)
                .clickable(role = Role.Button) { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(action.labelRes),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TaskSwipeAction.entries.forEach { option ->
                val isSelected = action == option
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    onClick = {
                        expanded = false
                        if (option != action) {
                            haptics.reorderTick()
                            onSelect(option)
                        }
                    },
                    modifier = Modifier.semantics { selected = isSelected },
                    trailingIcon = {
                        if (isSelected) Icon(Icons.Rounded.Check, contentDescription = null)
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    val colors = ZeroTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ZeroCardShape)
            .background(colors.surface)
            .hairlineBorder(shape = ZeroCardShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textMuted
        )
        content()
    }
}

@Composable
private fun SettingsChoiceTile(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    Column(
        modifier = modifier
            .clip(ZeroSmallShape)
            .background(if (selected) colors.accentSubtle else colors.background)
            .hairlineBorder(
                shape = ZeroSmallShape,
                color = if (selected) colors.accent else colors.hairlineBorder
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) colors.accent else colors.textPrimary
        )
        Text(
            text = subtitle,
            style = tabular.dateCompact,
            color = colors.textMuted
        )
    }
}

@Composable
private fun AccentChoiceTile(
    label: String,
    hex: String,
    accent: ZeroAccent,
    isDarkTheme: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val swatchColor = accent.colorFor(isDarkTheme)
    Row(
        modifier = modifier
            .clip(ZeroSmallShape)
            .background(if (selected) swatchColor.copy(alpha = 0.14f) else colors.background)
            .hairlineBorder(
                shape = ZeroSmallShape,
                color = if (selected) swatchColor else colors.hairlineBorder
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(swatchColor)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) swatchColor else colors.textPrimary
            )
            Text(
                text = hex,
                style = tabular.dateCompact,
                color = colors.textMuted
            )
        }
    }
}

@Composable
private fun ToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = ZeroTheme.colors
    Box(
        modifier = Modifier
            .clip(ZeroSmallShape)
            .background(if (selected) colors.surfaceElevated else colors.background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.accent else colors.textMuted
        )
    }
}

@Composable
private fun BackupActionButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = ZeroTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ZeroSmallShape)
            .background(colors.background)
            .hairlineBorder(shape = ZeroSmallShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(18.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun WebDavInputField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(ZeroSmallShape)
            .background(colors.background)
            .hairlineBorder(shape = ZeroSmallShape)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun WebDavActionChip(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    Row(
        modifier = modifier
            .clip(ZeroSmallShape)
            .background(colors.background)
            .hairlineBorder(
                shape = ZeroSmallShape,
                color = if (enabled) colors.hairlineBorder else colors.hairlineBorder.copy(alpha = 0.5f)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) colors.accent else colors.textMuted,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) colors.textPrimary else colors.textMuted
        )
    }
}

@Composable
private fun ShortcutRow(keys: String, description: String) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = keys,
            style = tabular.dateCompact,
            color = colors.textPrimary,
            modifier = Modifier
                .clip(ZeroSmallShape)
                .background(colors.background)
                .hairlineBorder(shape = ZeroSmallShape)
                .padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun NotificationsSettingsCard(
    preferences: NotificationPreferences,
    onUpdate: (NotificationPreferences) -> Unit
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val haptics = rememberZeroHaptics()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var notificationsGranted by remember { mutableStateOf(ZeroNotificationHelper.canPostNotifications(context)) }
    var exactAlarmsGranted by remember { mutableStateOf(ZeroNotificationHelper.canScheduleExactAlarms(context)) }
    var batteryUnrestricted by remember { mutableStateOf(ZeroNotificationHelper.isIgnoringBatteryOptimizations(context)) }
    var testFeedbackRes by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsGranted = ZeroNotificationHelper.canPostNotifications(context)
                exactAlarmsGranted = ZeroNotificationHelper.canScheduleExactAlarms(context)
                batteryUnrestricted = ZeroNotificationHelper.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = ZeroNotificationHelper.canPostNotifications(context)
        if (granted && testFeedbackRes == R.string.settings_notif_test_blocked) {
            testFeedbackRes = null
        }
    }

    fun requestOrOpenNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            runCatching { context.startActivity(intent) }
        }
    }

    SettingsSectionCard(title = stringResource(R.string.settings_section_notifications)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Permission & Health Indicators
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ZeroSmallShape)
                    .background(colors.background)
                    .hairlineBorder(shape = ZeroSmallShape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PermissionStatusRow(
                    title = stringResource(R.string.settings_notif_perm_title),
                    statusText = stringResource(
                        if (notificationsGranted) R.string.settings_notif_status_granted
                        else R.string.settings_notif_status_denied
                    ),
                    isHealthy = notificationsGranted,
                    onAction = ::requestOrOpenNotificationSettings
                )
                PermissionStatusRow(
                    title = stringResource(R.string.settings_notif_exact_title),
                    statusText = stringResource(
                        if (exactAlarmsGranted) R.string.settings_notif_status_granted
                        else R.string.settings_notif_status_denied
                    ),
                    isHealthy = exactAlarmsGranted,
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            runCatching { context.startActivity(intent) }
                        }
                    }
                )
                PermissionStatusRow(
                    title = stringResource(R.string.settings_notif_battery_title),
                    statusText = stringResource(
                        if (batteryUnrestricted) R.string.settings_notif_status_battery_ok
                        else R.string.settings_notif_status_battery_limited
                    ),
                    isHealthy = batteryUnrestricted,
                    onAction = {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        runCatching { context.startActivity(intent) }
                    }
                )
            }

            // Default All-Day Reminder Time
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.settings_notif_all_day_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary
                )
                val presets = listOf(8 * 60, 9 * 60, 10 * 60, 18 * 60)
                val isCustomDefault = preferences.defaultAllDayReminderMinutes !in presets
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { mins ->
                        val selected = preferences.defaultAllDayReminderMinutes == mins
                        Box(
                            modifier = Modifier
                                .clip(ZeroSmallShape)
                                .background(if (selected) colors.accentSubtle else colors.background)
                                .hairlineBorder(
                                    shape = ZeroSmallShape,
                                    color = if (selected) colors.accent else colors.hairlineBorder
                                )
                                .clickable {
                                    haptics.reorderTick()
                                    onUpdate(preferences.copy(defaultAllDayReminderMinutes = mins))
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = NotificationPreferences.formatMinutes(mins),
                                style = tabular.dateCompact,
                                color = if (selected) colors.accent else colors.textPrimary
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(ZeroSmallShape)
                            .background(if (isCustomDefault) colors.accentSubtle else colors.background)
                            .hairlineBorder(
                                shape = ZeroSmallShape,
                                color = if (isCustomDefault) colors.accent else colors.hairlineBorder
                            )
                            .clickable {
                                TimePickerDialog(
                                    context,
                                    { _, h, m ->
                                        haptics.confirm()
                                        onUpdate(preferences.copy(defaultAllDayReminderMinutes = h * 60 + m))
                                    },
                                    preferences.defaultAllDayReminderMinutes / 60,
                                    preferences.defaultAllDayReminderMinutes % 60,
                                    true
                                ).show()
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = if (isCustomDefault) {
                                NotificationPreferences.formatMinutes(preferences.defaultAllDayReminderMinutes)
                            } else {
                                stringResource(R.string.reminder_custom_time)
                            },
                            style = if (isCustomDefault) tabular.dateCompact else MaterialTheme.typography.labelSmall,
                            color = if (isCustomDefault) colors.accent else colors.textSecondary
                        )
                    }
                }
            }

            // Morning Briefing Push Row
            DailyBriefingRow(
                title = stringResource(R.string.settings_notif_morning_title),
                description = stringResource(R.string.settings_notif_morning_desc),
                enabled = preferences.morningBriefingEnabled,
                minutesOfDay = preferences.morningBriefingMinutes,
                onToggle = { enable ->
                    if (enable && !notificationsGranted) {
                        requestOrOpenNotificationSettings()
                    }
                    onUpdate(preferences.copy(morningBriefingEnabled = enable))
                },
                onPickTime = {
                    TimePickerDialog(
                        context,
                        { _, h, m ->
                            haptics.confirm()
                            onUpdate(
                                preferences.copy(
                                    morningBriefingEnabled = true,
                                    morningBriefingMinutes = h * 60 + m
                                )
                            )
                        },
                        preferences.morningBriefingMinutes / 60,
                        preferences.morningBriefingMinutes % 60,
                        true
                    ).show()
                }
            )

            // Evening Review Push Row
            DailyBriefingRow(
                title = stringResource(R.string.settings_notif_evening_title),
                description = stringResource(R.string.settings_notif_evening_desc),
                enabled = preferences.eveningReviewEnabled,
                minutesOfDay = preferences.eveningReviewMinutes,
                onToggle = { enable ->
                    if (enable && !notificationsGranted) {
                        requestOrOpenNotificationSettings()
                    }
                    onUpdate(preferences.copy(eveningReviewEnabled = enable))
                },
                onPickTime = {
                    TimePickerDialog(
                        context,
                        { _, h, m ->
                            haptics.confirm()
                            onUpdate(
                                preferences.copy(
                                    eveningReviewEnabled = true,
                                    eveningReviewMinutes = h * 60 + m
                                )
                            )
                        },
                        preferences.eveningReviewMinutes / 60,
                        preferences.eveningReviewMinutes % 60,
                        true
                    ).show()
                }
            )

            // One-tap Test Notification
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ZeroSmallShape)
                        .background(colors.background)
                        .hairlineBorder(shape = ZeroSmallShape, color = colors.accent.copy(alpha = 0.55f))
                        .clickable {
                            val posted = ZeroNotificationHelper.showTestNotification(context)
                            if (posted) {
                                haptics.confirm()
                                testFeedbackRes = R.string.settings_notif_test_sent
                            } else {
                                haptics.reject()
                                testFeedbackRes = R.string.settings_notif_test_blocked
                                requestOrOpenNotificationSettings()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_notif_test_btn),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.accent
                    )
                }
                testFeedbackRes?.let { resId ->
                    Text(
                        text = stringResource(resId),
                        style = tabular.dateCompact,
                        color = if (resId == R.string.settings_notif_test_sent) colors.accent else colors.priorityUrgent
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    statusText: String,
    isHealthy: Boolean,
    onAction: () -> Unit
) {
    val colors = ZeroTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = if (isHealthy) colors.accent else colors.priorityHigh
            )
            if (!isHealthy) {
                Box(
                    modifier = Modifier
                        .clip(ZeroSmallShape)
                        .background(colors.accentSubtle)
                        .hairlineBorder(shape = ZeroSmallShape, color = colors.accent)
                        .clickable(onClick = onAction)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_notif_action_enable),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.accent
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyBriefingRow(
    title: String,
    description: String,
    enabled: Boolean,
    minutesOfDay: Int,
    onToggle: (Boolean) -> Unit,
    onPickTime: () -> Unit
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val haptics = rememberZeroHaptics()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(ZeroSmallShape)
                    .background(if (enabled) colors.accentSubtle else colors.background)
                    .hairlineBorder(
                        shape = ZeroSmallShape,
                        color = if (enabled) colors.accent else colors.hairlineBorder
                    )
                    .clickable(onClick = onPickTime)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = NotificationPreferences.formatMinutes(minutesOfDay),
                    style = tabular.dateCompact,
                    color = if (enabled) colors.accent else colors.textSecondary
                )
            }
            Row(
                modifier = Modifier
                    .clip(ZeroSmallShape)
                    .background(colors.background)
                    .hairlineBorder(shape = ZeroSmallShape)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ToggleChip(
                    label = stringResource(R.string.settings_toggle_on),
                    selected = enabled
                ) {
                    onToggle(true)
                    haptics.confirm()
                }
                ToggleChip(
                    label = stringResource(R.string.settings_toggle_off),
                    selected = !enabled
                ) {
                    onToggle(false)
                }
            }
        }
    }
}

@Composable
private fun AiEngineSettingsCard(
    aiConfig: AiConfig,
    testStatus: AiTestStatus?,
    onUpdateConfig: (AiConfig) -> Unit,
    onTestConnection: () -> Unit
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val haptics = rememberZeroHaptics()

    var geminiKeyDraft by remember(aiConfig.geminiApiKey) { mutableStateOf(aiConfig.geminiApiKey) }
    var geminiModelDraft by remember(aiConfig.geminiModel) { mutableStateOf(aiConfig.geminiModel) }
    var openAiKeyDraft by remember(aiConfig.openAiApiKey) { mutableStateOf(aiConfig.openAiApiKey) }
    var openAiModelDraft by remember(aiConfig.openAiModel) { mutableStateOf(aiConfig.openAiModel) }
    var openAiBaseUrlDraft by remember(aiConfig.openAiBaseUrl) { mutableStateOf(aiConfig.openAiBaseUrl) }
    var customInstructionsDraft by remember(aiConfig.customInstructions) { mutableStateOf(aiConfig.customInstructions) }
    var showKey by remember { mutableStateOf(false) }

    fun pushCurrentDraft(providerOverride: AiProvider = aiConfig.provider) {
        onUpdateConfig(
            AiConfig(
                provider = providerOverride,
                geminiApiKey = geminiKeyDraft,
                geminiModel = geminiModelDraft.trim().ifBlank { AiConfig.DEFAULT_GEMINI_MODEL },
                openAiApiKey = openAiKeyDraft,
                openAiModel = openAiModelDraft.trim().ifBlank { AiConfig.DEFAULT_OPENAI_MODEL },
                openAiBaseUrl = openAiBaseUrlDraft.trim().ifBlank { AiConfig.DEFAULT_OPENAI_BASE_URL },
                customInstructions = customInstructionsDraft.trim()
            )
        )
    }

    SettingsSectionCard(title = stringResource(R.string.settings_section_ai)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.settings_ai_desc),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )

            // Provider Selector (Gemini | OpenAI)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiProvider.entries.forEach { provider ->
                    val selected = aiConfig.provider == provider
                    val titleRes = when (provider) {
                        AiProvider.GEMINI -> R.string.ai_provider_gemini
                        AiProvider.OPENAI -> R.string.ai_provider_openai
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(ZeroSmallShape)
                            .background(if (selected) colors.accentSubtle else colors.background)
                            .hairlineBorder(
                                shape = ZeroSmallShape,
                                color = if (selected) colors.accent else colors.hairlineBorder
                            )
                            .clickable {
                                haptics.reorderTick()
                                pushCurrentDraft(providerOverride = provider)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(titleRes),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) colors.accent else colors.textPrimary
                        )
                    }
                }
            }

            if (aiConfig.provider == AiProvider.GEMINI) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WebDavInputField(
                        value = geminiKeyDraft,
                        placeholder = stringResource(R.string.settings_ai_gemini_key_placeholder),
                        isPassword = !showKey,
                        onValueChange = {
                            geminiKeyDraft = it
                            pushCurrentDraft()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showKey = !showKey }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                WebDavInputField(
                    value = geminiModelDraft,
                    placeholder = AiConfig.DEFAULT_GEMINI_MODEL,
                    onValueChange = {
                        geminiModelDraft = it
                        pushCurrentDraft()
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("gemini-2.5-flash", "gemini-2.5-pro").forEach { preset ->
                        val active = geminiModelDraft.trim() == preset
                        Box(
                            modifier = Modifier
                                .clip(ZeroSmallShape)
                                .background(if (active) colors.accentSubtle else colors.background)
                                .hairlineBorder(
                                    shape = ZeroSmallShape,
                                    color = if (active) colors.accent else colors.hairlineBorder
                                )
                                .clickable {
                                    geminiModelDraft = preset
                                    pushCurrentDraft()
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = preset,
                                style = tabular.dateCompact,
                                color = if (active) colors.accent else colors.textSecondary
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WebDavInputField(
                        value = openAiKeyDraft,
                        placeholder = stringResource(R.string.settings_ai_openai_key_placeholder),
                        isPassword = !showKey,
                        onValueChange = {
                            openAiKeyDraft = it
                            pushCurrentDraft()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showKey = !showKey }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                WebDavInputField(
                    value = openAiModelDraft,
                    placeholder = AiConfig.DEFAULT_OPENAI_MODEL,
                    onValueChange = {
                        openAiModelDraft = it
                        pushCurrentDraft()
                    }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini", "gpt-4.1").forEach { preset ->
                        val active = openAiModelDraft.trim() == preset
                        Box(
                            modifier = Modifier
                                .clip(ZeroSmallShape)
                                .background(if (active) colors.accentSubtle else colors.background)
                                .hairlineBorder(
                                    shape = ZeroSmallShape,
                                    color = if (active) colors.accent else colors.hairlineBorder
                                )
                                .clickable {
                                    openAiModelDraft = preset
                                    pushCurrentDraft()
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = preset,
                                style = tabular.dateCompact,
                                color = if (active) colors.accent else colors.textSecondary
                            )
                        }
                    }
                }
                WebDavInputField(
                    value = openAiBaseUrlDraft,
                    placeholder = AiConfig.DEFAULT_OPENAI_BASE_URL,
                    onValueChange = {
                        openAiBaseUrlDraft = it
                        pushCurrentDraft()
                    }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.settings_ai_custom_instructions_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 58.dp, max = 110.dp)
                        .clip(ZeroSmallShape)
                        .background(colors.background)
                        .hairlineBorder(shape = ZeroSmallShape)
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                ) {
                    if (customInstructionsDraft.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_ai_custom_instructions_placeholder),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                    BasicTextField(
                        value = customInstructionsDraft,
                        onValueChange = {
                            customInstructionsDraft = it
                            pushCurrentDraft()
                        },
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            val isBusy = testStatus == AiTestStatus.Working
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ZeroSmallShape)
                    .background(colors.background)
                    .hairlineBorder(shape = ZeroSmallShape, color = colors.accent.copy(alpha = 0.55f))
                    .clickable(enabled = !isBusy && aiConfig.isConfigured) {
                        haptics.reorderTick()
                        pushCurrentDraft()
                        onTestConnection()
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = if (aiConfig.isConfigured) colors.accent else colors.textMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_ai_test_btn),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (aiConfig.isConfigured) colors.accent else colors.textMuted
                )
            }

            if (testStatus != null) {
                val (msg, isError) = when (testStatus) {
                    AiTestStatus.Working -> stringResource(R.string.ai_analyzing) to false
                    is AiTestStatus.Success -> stringResource(R.string.settings_ai_test_ok, testStatus.model) to false
                    is AiTestStatus.Error -> stringResource(R.string.settings_ai_test_err, testStatus.detail) to true
                }
                Text(
                    text = msg,
                    style = tabular.dateCompact,
                    color = if (isError) colors.priorityUrgent else colors.accent
                )
            }
        }
    }
}
