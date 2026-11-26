package com.zero.todo.feature.settings

import android.net.Uri
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.hairlineDividerBottom
import com.zero.todo.core.designsystem.rememberZeroHaptics
import com.zero.todo.data.backup.BackupSummary
import com.zero.todo.data.backup.WebDavConfig
import com.zero.todo.domain.model.AntiMisclickMode
import com.zero.todo.domain.model.AppLanguage
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
