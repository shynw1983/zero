package com.zero.todo.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.zero.todo.R
import com.zero.todo.core.designsystem.ZeroAccent
import com.zero.todo.core.designsystem.ZeroCapsuleShape
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.hairlineDividerBottom
import com.zero.todo.core.designsystem.rememberZeroHaptics
import com.zero.todo.data.local.dao.FolderDao
import com.zero.todo.data.local.dao.TagDao
import com.zero.todo.data.local.entity.FolderEntity
import com.zero.todo.data.local.entity.TagEntity
import com.zero.todo.data.preferences.SharedPrefsUserPreferencesRepository
import com.zero.todo.data.preferences.UserPreferencesRepository
import com.zero.todo.domain.model.AppLanguage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    @Inject lateinit var folderDao: FolderDao
    @Inject lateinit var tagDao: TagDao
    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences(
            SharedPrefsUserPreferencesRepository.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val savedLanguage = AppLanguage.fromStorage(
            prefs.getString(SharedPrefsUserPreferencesRepository.KEY_APP_LANGUAGE, AppLanguage.SYSTEM.name)
        )
        val tag = savedLanguage.languageTag
        val wrapped = if (tag != null) {
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            val config = Configuration(newBase.resources.configuration).apply {
                setLocale(locale)
                setLocales(LocaleList(locale))
            }
            newBase.createConfigurationContext(config)
        } else {
            newBase
        }
        super.attachBaseContext(wrapped)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setResult(RESULT_CANCELED)

        val appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val providerClassName = AppWidgetManager.getInstance(this)
            .getAppWidgetInfo(appWidgetId)
            ?.provider
            ?.className
        val isQuickCapture = if (intent.hasExtra(EXTRA_IS_QUICK_CAPTURE)) {
            intent.getBooleanExtra(EXTRA_IS_QUICK_CAPTURE, false)
        } else {
            providerClassName == QuickCaptureWidgetReceiver::class.java.name
        }

        val defaultScope = if (isQuickCapture) WidgetFilterScope.INBOX else WidgetFilterScope.TODAY
        val initialConfig = WidgetConfigStore.load(this, appWidgetId, defaultScope = defaultScope)
        val prefs = userPreferencesRepository.preferences.value

        setContent {
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !prefs.isDarkTheme
                    controller.isAppearanceLightNavigationBars = !prefs.isDarkTheme
                }
            }

            ZeroTheme(
                darkTheme = prefs.isDarkTheme,
                accent = prefs.accent,
                hapticsEnabled = prefs.hapticsEnabled
            ) {
                WidgetConfigScreen(
                    isQuickCapture = isQuickCapture,
                    appIsDark = prefs.isDarkTheme,
                    appAccent = prefs.accent,
                    initialConfig = initialConfig,
                    loadFolders = { folderDao.getActiveFolders() },
                    loadTags = { tagDao.getActiveTagsSnapshot() },
                    onCancel = { finish() },
                    onSave = { finalConfig ->
                        WidgetConfigStore.save(this@WidgetConfigActivity, appWidgetId, finalConfig)
                        val glanceManager = GlanceAppWidgetManager(this@WidgetConfigActivity)
                        runCatching {
                            val glanceId = glanceManager.getGlanceIdBy(appWidgetId)
                            if (isQuickCapture) {
                                QuickCaptureWidget().update(this@WidgetConfigActivity, glanceId)
                            } else {
                                TodayTasksWidget().update(this@WidgetConfigActivity, glanceId)
                            }
                        }
                        ZeroWidgetUpdater.refreshAll(this@WidgetConfigActivity)
                        setResult(
                            RESULT_OK,
                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        )
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_IS_QUICK_CAPTURE = "com.zero.todo.extra.IS_QUICK_CAPTURE"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetConfigScreen(
    isQuickCapture: Boolean,
    appIsDark: Boolean,
    appAccent: ZeroAccent,
    initialConfig: ZeroWidgetConfig,
    loadFolders: suspend () -> List<FolderEntity>,
    loadTags: suspend () -> List<TagEntity>,
    onCancel: () -> Unit,
    onSave: suspend (ZeroWidgetConfig) -> Unit
) {
    val colors = ZeroTheme.colors
    val haptics = rememberZeroHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var draft by remember { mutableStateOf(initialConfig) }
    var folders by remember { mutableStateOf<List<FolderEntity>>(emptyList()) }
    var tags by remember { mutableStateOf<List<TagEntity>>(emptyList()) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        folders = runCatching { loadFolders() }.getOrDefault(emptyList())
        tags = runCatching { loadTags() }.getOrDefault(emptyList())
    }

    val folderDisplayNames = remember(folders) {
        val byId = folders.associateBy { it.id }
        folders.associate { folder ->
            val parts = mutableListOf(folder.name)
            var currentParent = folder.parentId
            var guard = 0
            while (currentParent != null && guard < 12) {
                val p = byId[currentParent] ?: break
                parts.add(0, p.name)
                currentParent = p.parentId
                guard++
            }
            folder.id to parts.joinToString(" / ")
        }
    }

    val tagDisplayNames = remember(tags) {
        WidgetTaskQueryEngine.buildTagPathMap(tags)
    }

    val previewPalette = remember(draft, appIsDark, appAccent) {
        resolveWidgetPalette(draft, appIsDark, appAccent)
    }

    val headerTitlePreview = remember(draft, folders, tags, tagDisplayNames, context) {
        resolveWidgetHeaderTitle(
            locContext = context,
            config = draft,
            folderName = draft.folderId?.let { fid -> folders.firstOrNull { it.id == fid }?.name },
            tagName = draft.tagId?.let { tid -> tagDisplayNames[tid] ?: tags.firstOrNull { it.id == tid }?.name }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .hairlineDividerBottom()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        haptics.reorderTick()
                        onCancel()
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                        tint = colors.textSecondary
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.widget_config_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .clip(ZeroCapsuleShape)
                        .background(colors.accent)
                        .clickable(enabled = !saving) {
                            haptics.confirm()
                            saving = true
                            scope.launch { onSave(draft) }
                        }
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.widget_config_save),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onAccent
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Live Widget Preview Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.widget_config_preview_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted
                    )
                    WidgetLivePreviewCard(
                        isQuickCapture = isQuickCapture,
                        config = draft,
                        palette = previewPalette,
                        headerTitle = headerTitlePreview
                    )
                }

                // Section 1: Filter Scope
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(
                            if (isQuickCapture) R.string.widget_config_capture_target
                            else R.string.widget_config_section_filter
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted
                    )

                    val scopeOptions = if (isQuickCapture) {
                        listOf(WidgetFilterScope.INBOX, WidgetFilterScope.TODAY, WidgetFilterScope.FOLDER)
                    } else {
                        WidgetFilterScope.entries.toList()
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        scopeOptions.forEach { scopeOption ->
                            val selected = draft.filterScope == scopeOption
                            SelectableChip(
                                label = stringResource(filterScopeLabelRes(scopeOption)),
                                selected = selected,
                                onClick = {
                                    haptics.reorderTick()
                                    draft = draft.copy(
                                        filterScope = scopeOption,
                                        folderId = if (scopeOption == WidgetFilterScope.FOLDER && draft.folderId == null) {
                                            folders.firstOrNull()?.id
                                        } else {
                                            draft.folderId
                                        },
                                        tagId = if (scopeOption == WidgetFilterScope.TAG && draft.tagId == null) {
                                            tags.firstOrNull()?.id
                                        } else {
                                            draft.tagId
                                        }
                                    )
                                }
                            )
                        }
                    }

                    if (draft.filterScope == WidgetFilterScope.FOLDER) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.surface)
                                .hairlineBorder(shape = RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.widget_config_pick_folder),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted
                            )
                            if (folders.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.widget_config_no_folders),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    folders.forEach { folder ->
                                        val selected = draft.folderId == folder.id
                                        SelectableChip(
                                            label = folderDisplayNames[folder.id] ?: folder.name,
                                            selected = selected,
                                            leadingIcon = Icons.Rounded.Folder,
                                            onClick = {
                                                haptics.reorderTick()
                                                draft = draft.copy(folderId = folder.id)
                                            }
                                        )
                                    }
                                }
                            }
                            if (!isQuickCapture) {
                                HorizontalDivider(thickness = 0.5.dp, color = colors.hairlineBorder)
                                ConfigSwitchRow(
                                    label = stringResource(R.string.widget_config_include_subfolders),
                                    checked = draft.includeSubfolders,
                                    onCheckedChange = {
                                        haptics.reorderTick()
                                        draft = draft.copy(includeSubfolders = it)
                                    }
                                )
                            }
                        }
                    }

                    if (!isQuickCapture && draft.filterScope == WidgetFilterScope.TAG) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.surface)
                                .hairlineBorder(shape = RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.widget_config_pick_tag),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted
                            )
                            if (tags.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.widget_config_no_tags),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    tags.sortedBy { (tagDisplayNames[it.id] ?: it.name).lowercase() }.forEach { tag ->
                                        val labelPath = tagDisplayNames[tag.id] ?: tag.name
                                        SelectableChip(
                                            label = "#$labelPath",
                                            selected = draft.tagId == tag.id,
                                            leadingIcon = Icons.Rounded.Tag,
                                            onClick = {
                                                haptics.reorderTick()
                                                draft = draft.copy(tagId = tag.id)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!isQuickCapture) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.surface)
                                .hairlineBorder(shape = RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            ConfigSwitchRow(
                                label = stringResource(R.string.widget_config_show_completed),
                                checked = draft.showCompleted,
                                onCheckedChange = {
                                    haptics.reorderTick()
                                    draft = draft.copy(showCompleted = it)
                                }
                            )
                        }
                    }
                }

                // Section 2: View Mode (for Task List Widget)
                if (!isQuickCapture) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.widget_config_section_view),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textMuted
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            WidgetViewMode.entries.forEach { viewMode ->
                                val selected = draft.viewMode == viewMode
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (selected) colors.surfaceElevated else colors.surface)
                                        .hairlineBorder(
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (selected) colors.accent else colors.hairlineBorder
                                        )
                                        .clickable {
                                            haptics.reorderTick()
                                            draft = draft.copy(viewMode = viewMode)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(viewModeTitleRes(viewMode)),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colors.textPrimary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = stringResource(viewModeDescRes(viewMode)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textSecondary
                                        )
                                    }
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = colors.accent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 3: Theme & Accent
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.widget_config_section_theme),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WidgetThemeMode.entries.forEach { themeMode ->
                            val selected = draft.themeMode == themeMode
                            val swatchBg = when (themeMode) {
                                WidgetThemeMode.FOLLOW_APP -> if (appIsDark) Color(0xFF09090B) else Color(0xFFF7F7F9)
                                WidgetThemeMode.DARK_OBSIDIAN -> Color(0xFF09090B)
                                WidgetThemeMode.LIGHT_PAPER -> Color(0xFFF7F7F9)
                                WidgetThemeMode.MONOLITH_ORANGE -> Color(0xFF09090B)
                                WidgetThemeMode.TRANSLUCENT_DARK -> Color(0xAA18181D)
                                WidgetThemeMode.TRANSLUCENT_LIGHT -> Color(0xDDDADAE0)
                            }
                            val swatchBorder = if (themeMode == WidgetThemeMode.MONOLITH_ORANGE) {
                                Color(0xFFFF4D00)
                            } else {
                                colors.hairlineBorder
                            }
                            Row(
                                modifier = Modifier
                                    .height(40.dp)
                                    .clip(ZeroSmallShape)
                                    .background(if (selected) colors.accentSubtle else colors.surface)
                                    .hairlineBorder(
                                        shape = ZeroSmallShape,
                                        color = if (selected) colors.accent else colors.hairlineBorder
                                    )
                                    .clickable {
                                        haptics.reorderTick()
                                        draft = draft.copy(themeMode = themeMode)
                                    }
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(swatchBg)
                                        .hairlineBorder(shape = CircleShape, color = swatchBorder)
                                )
                                Text(
                                    text = stringResource(themeModeLabelRes(themeMode)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) colors.accent else colors.textPrimary,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WidgetAccentOption.entries.forEach { accentOpt ->
                            val selected = draft.accentOption == accentOpt
                            val dotColor = when (accentOpt) {
                                WidgetAccentOption.FOLLOW_APP -> appAccent.colorFor(colors.isDark)
                                WidgetAccentOption.SLATE_BLUE -> ZeroAccent.SlateBlue.colorFor(colors.isDark)
                                WidgetAccentOption.MONOCHROME -> ZeroAccent.Monochrome.colorFor(colors.isDark)
                                WidgetAccentOption.SAGE -> ZeroAccent.Sage.colorFor(colors.isDark)
                                WidgetAccentOption.WARM_CLAY -> ZeroAccent.WarmClay.colorFor(colors.isDark)
                                WidgetAccentOption.SIGNAL_ORANGE -> Color(0xFFFF4D00)
                            }
                            Row(
                                modifier = Modifier
                                    .height(36.dp)
                                    .clip(ZeroCapsuleShape)
                                    .background(if (selected) colors.accentSubtle else colors.surface)
                                    .hairlineBorder(
                                        shape = ZeroCapsuleShape,
                                        color = if (selected) colors.accent else colors.hairlineBorder
                                    )
                                    .clickable {
                                        haptics.reorderTick()
                                        draft = draft.copy(accentOption = accentOpt)
                                    }
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                                Text(
                                    text = stringResource(accentOptionLabelRes(accentOpt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) colors.accent else colors.textSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun WidgetLivePreviewCard(
    isQuickCapture: Boolean,
    config: ZeroWidgetConfig,
    palette: WidgetVisualPalette,
    headerTitle: String
) {
    if (isQuickCapture) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ZeroTheme.colors.surface)
                .hairlineBorder(shape = RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.background)
                    .hairlineBorder(shape = RoundedCornerShape(20.dp), color = palette.hairline)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.cardSurface),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(palette.accent)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "+",
                        color = palette.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = ZeroTheme.colors.textPrimary
                )
                Text(
                    text = stringResource(themeModeLabelRes(config.themeMode)),
                    style = MaterialTheme.typography.bodySmall,
                    color = ZeroTheme.colors.textSecondary
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.background)
            .hairlineBorder(shape = RoundedCornerShape(16.dp), color = palette.hairline)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(palette.accent)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = headerTitle,
                color = palette.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "02",
                color = palette.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(palette.surfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "+", color = palette.accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(thickness = 0.5.dp, color = palette.hairline)
        Spacer(modifier = Modifier.height(8.dp))

        when (config.viewMode) {
            WidgetViewMode.LIST -> {
                listOf("25m" to palette.priorityUrgent, "15m" to palette.priorityMedium).forEachIndexed { idx, (meta, dot) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.cardSurface)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(palette.surfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(dot)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (idx == 0) stringResource(R.string.widget_filter_priority) else stringResource(R.string.widget_header_today),
                            color = palette.textPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(text = meta, color = palette.textSecondary, fontSize = 11.sp)
                    }
                    if (idx == 0) Spacer(modifier = Modifier.height(6.dp))
                }
            }

            WidgetViewMode.COMPACT -> {
                repeat(3) { idx ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (idx == 0) palette.priorityUrgent else palette.textMuted)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$headerTitle #${idx + 1}",
                            color = palette.textPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(text = "${(idx + 1) * 5}m", color = palette.textMuted, fontSize = 10.sp)
                    }
                    if (idx < 2) HorizontalDivider(thickness = 0.5.dp, color = palette.hairline)
                }
            }

            WidgetViewMode.FOCUS_NOW -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.cardSurface)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(palette.priorityUrgent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = headerTitle,
                            color = palette.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.accent)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.widget_focus_done),
                            color = palette.onAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val colors = ZeroTheme.colors
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(ZeroSmallShape)
            .background(if (selected) colors.accentSubtle else colors.surface)
            .hairlineBorder(
                shape = ZeroSmallShape,
                color = if (selected) colors.accent else colors.hairlineBorder
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (selected) colors.accent else colors.textSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.accent else colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ConfigSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = ZeroTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accent
            )
        )
    }
}

private fun filterScopeLabelRes(scope: WidgetFilterScope): Int = when (scope) {
    WidgetFilterScope.TODAY -> R.string.widget_filter_today
    WidgetFilterScope.INBOX -> R.string.widget_filter_inbox
    WidgetFilterScope.NEXT_7_DAYS -> R.string.widget_filter_next7
    WidgetFilterScope.HIGH_PRIORITY -> R.string.widget_filter_priority
    WidgetFilterScope.ALL_ACTIVE -> R.string.widget_filter_all
    WidgetFilterScope.FOLDER -> R.string.widget_filter_folder
    WidgetFilterScope.TAG -> R.string.widget_filter_tag
}

private fun viewModeTitleRes(viewMode: WidgetViewMode): Int = when (viewMode) {
    WidgetViewMode.LIST -> R.string.widget_view_list
    WidgetViewMode.COMPACT -> R.string.widget_view_compact
    WidgetViewMode.FOCUS_NOW -> R.string.widget_view_focus
}

private fun viewModeDescRes(viewMode: WidgetViewMode): Int = when (viewMode) {
    WidgetViewMode.LIST -> R.string.widget_view_list_desc
    WidgetViewMode.COMPACT -> R.string.widget_view_compact_desc
    WidgetViewMode.FOCUS_NOW -> R.string.widget_view_focus_desc
}

private fun themeModeLabelRes(themeMode: WidgetThemeMode): Int = when (themeMode) {
    WidgetThemeMode.FOLLOW_APP -> R.string.widget_theme_follow_app
    WidgetThemeMode.DARK_OBSIDIAN -> R.string.widget_theme_dark
    WidgetThemeMode.LIGHT_PAPER -> R.string.widget_theme_light
    WidgetThemeMode.MONOLITH_ORANGE -> R.string.widget_theme_monolith
    WidgetThemeMode.TRANSLUCENT_DARK -> R.string.widget_theme_translucent_dark
    WidgetThemeMode.TRANSLUCENT_LIGHT -> R.string.widget_theme_translucent_light
}

private fun accentOptionLabelRes(accent: WidgetAccentOption): Int = when (accent) {
    WidgetAccentOption.FOLLOW_APP -> R.string.widget_accent_follow_app
    WidgetAccentOption.SLATE_BLUE -> R.string.settings_accent_slate
    WidgetAccentOption.MONOCHROME -> R.string.settings_accent_monochrome
    WidgetAccentOption.SAGE -> R.string.settings_accent_sage
    WidgetAccentOption.WARM_CLAY -> R.string.settings_accent_clay
    WidgetAccentOption.SIGNAL_ORANGE -> R.string.widget_accent_signal_orange
}
