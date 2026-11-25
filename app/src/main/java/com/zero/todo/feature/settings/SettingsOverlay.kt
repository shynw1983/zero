package com.zero.todo.feature.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zero.todo.core.designsystem.ZeroAccent
import com.zero.todo.core.designsystem.ZeroCardShape
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.hairlineDividerBottom
import com.zero.todo.core.designsystem.rememberZeroHaptics
import com.zero.todo.domain.model.AntiMisclickMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SettingsOverlay(
    isDarkTheme: Boolean,
    activeAccent: ZeroAccent,
    antiMisclickMode: AntiMisclickMode,
    hapticsEnabled: Boolean,
    backupStatusMessage: String?,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetAccent: (ZeroAccent) -> Unit,
    onSetAntiMisclickMode: (AntiMisclickMode) -> Unit,
    onSetHapticsEnabled: (Boolean) -> Unit,
    onGenerateJsonBackup: suspend () -> String,
    onGenerateMarkdownExport: suspend () -> String,
    onImportJsonBackup: (String) -> Unit,
    onReportBackupStatus: (String) -> Unit,
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
                    } ?: error("无法打开目标写入流")
                }
            }.onSuccess {
                haptics.confirm()
                onReportBackupStatus("JSON 完整备份已导出")
            }.onFailure { err ->
                haptics.reject()
                onReportBackupStatus("导出失败：${err.message ?: "未知错误"}")
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
                    } ?: error("无法打开目标写入流")
                }
            }.onSuccess {
                haptics.confirm()
                onReportBackupStatus("层级 Markdown 文件已导出")
            }.onFailure { err ->
                haptics.reject()
                onReportBackupStatus("Markdown 导出失败：${err.message ?: "未知错误"}")
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
                    } ?: error("无法读取备份文件")
                }
            }.onSuccess { content ->
                onImportJsonBackup(content)
            }.onFailure { err ->
                haptics.reject()
                onReportBackupStatus("读取失败：${err.message ?: "未知错误"}")
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
                        text = "设置 · ZERO",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭设置",
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
                // 1. Theme Appearance
                SettingsSectionCard(title = "外观主题") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SettingsChoiceTile(
                            title = "深色 · 黑曜石",
                            subtitle = "#09090B",
                            selected = isDarkTheme,
                            onClick = {
                                haptics.reorderTick()
                                onSetDarkTheme(true)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        SettingsChoiceTile(
                            title = "浅色 · 冷纸白",
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

                // 2. Functional Accent Color
                SettingsSectionCard(title = "功能强调色") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AccentChoiceTile(
                            label = "信号橙",
                            hex = "#FF4D00",
                            accent = ZeroAccent.SignalOrange,
                            selected = activeAccent == ZeroAccent.SignalOrange,
                            onClick = {
                                haptics.reorderTick()
                                onSetAccent(ZeroAccent.SignalOrange)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        AccentChoiceTile(
                            label = "电子青",
                            hex = "#00E5FF",
                            accent = ZeroAccent.CyberCyan,
                            selected = activeAccent == ZeroAccent.CyberCyan,
                            onClick = {
                                haptics.reorderTick()
                                onSetAccent(ZeroAccent.CyberCyan)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 3. Anti-Misclick Mode Switcher
                SettingsSectionCard(title = "防误触完成模式") {
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
                                        text = mode.titleZh,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (selected) colors.accent else colors.textPrimary
                                    )
                                    Text(
                                        text = mode.descriptionZh,
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
                    }
                }

                // 4. Tactile Haptics Toggle
                SettingsSectionCard(title = "触觉与物理反馈") {
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
                                text = "线性马达震动反馈",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "任务完成、拖拽刻度、2 分钟起步柔和提示",
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
                            ToggleChip(label = "开启", selected = hapticsEnabled) {
                                onSetHapticsEnabled(true)
                                haptics.confirm()
                            }
                            ToggleChip(label = "静音", selected = !hapticsEnabled) {
                                onSetHapticsEnabled(false)
                            }
                        }
                    }
                }

                // 5. Local Data Backup & Export (JSON + Hierarchical Markdown)
                SettingsSectionCard(title = "本地数据导入 / 导出") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BackupActionButton(
                            icon = Icons.Rounded.Download,
                            title = "导出完整 JSON 备份",
                            subtitle = "完整保存多级目录、任务、子任务、标签与专注记录",
                            onClick = { exportJsonLauncher.launch("zero_backup_$dateStamp.json") }
                        )
                        BackupActionButton(
                            icon = Icons.Rounded.Upload,
                            title = "从 JSON 备份恢复",
                            subtitle = "原子事务全量恢复并即时刷新目录树与桌面小组件",
                            onClick = { importJsonLauncher.launch(arrayOf("application/json", "text/plain")) }
                        )
                        BackupActionButton(
                            icon = Icons.Rounded.Description,
                            title = "导出层级 Markdown (.md)",
                            subtitle = "按无限嵌套文件夹结构整理为可读 Markdown 文档",
                            onClick = { exportMarkdownLauncher.launch("zero_tasks_$dateStamp.md") }
                        )

                        if (!backupStatusMessage.isNullOrBlank()) {
                            Text(
                                text = backupStatusMessage,
                                style = tabular.dateCompact,
                                color = colors.accent,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // 6. Hardware Keyboard Shortcuts (Tablets)
                SettingsSectionCard(title = "外接键盘快捷键（平板 / 桌面模式）") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShortcutRow("Ctrl / Cmd + N", "聚焦底部极速输入胶囊")
                        ShortcutRow("Esc", "关闭弹窗 / 退出专注 / 取消选中")
                        ShortcutRow("Ctrl / Cmd + ,", "打开设置面板")
                        ShortcutRow("Ctrl / Cmd + 1..5", "快速切换收集箱 / 今天 / 明天 / 7天 / 日历")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
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
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    Row(
        modifier = modifier
            .clip(ZeroSmallShape)
            .background(if (selected) accent.color.copy(alpha = 0.14f) else colors.background)
            .hairlineBorder(
                shape = ZeroSmallShape,
                color = if (selected) accent.color else colors.hairlineBorder
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
                .background(accent.color)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) accent.color else colors.textPrimary
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
