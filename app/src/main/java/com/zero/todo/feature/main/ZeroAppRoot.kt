package com.zero.todo.feature.main

import android.app.LocaleManager
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.domain.model.AppLanguage
import java.util.Locale

object ZeroRoutes {
    const val MAIN = "main"
}

data class WidgetLaunchArgs(
    val id: Long = 0L,
    val destinationKey: String? = null,
    val taskId: String? = null,
    val launchQuickInput: Boolean = false
)

@Composable
fun ZeroAppRoot(
    widgetLaunchArgs: WidgetLaunchArgs? = null,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val baseContext = LocalContext.current
    val baseConfig = LocalConfiguration.current

    // Sync Android 13+ per-app system locale when user switches language in Settings
    LaunchedEffect(uiState.appLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val localeManager = baseContext.getSystemService(LocaleManager::class.java)
                val tag = uiState.appLanguage.languageTag
                val targetList = if (tag == null) {
                    LocaleList.getEmptyLocaleList()
                } else {
                    LocaleList.forLanguageTags(tag)
                }
                if (localeManager?.applicationLocales != targetList) {
                    localeManager?.applicationLocales = targetList
                }
            }
        }
    }

    LaunchedEffect(widgetLaunchArgs?.id) {
        if (widgetLaunchArgs != null && widgetLaunchArgs.id > 0L) {
            viewModel.handleWidgetLaunch(
                destinationKey = widgetLaunchArgs.destinationKey,
                taskId = widgetLaunchArgs.taskId,
                launchQuickInput = widgetLaunchArgs.launchQuickInput
            )
        }
    }

    val localizedConfig = remember(baseConfig, uiState.appLanguage) {
        Configuration(baseConfig).apply {
            val tag = uiState.appLanguage.languageTag
            if (tag != null) {
                val locale = Locale.forLanguageTag(tag)
                setLocale(locale)
                setLocales(LocaleList(locale))
            }
        }
    }

    val localizedContext = remember(baseContext, localizedConfig, uiState.appLanguage) {
        if (uiState.appLanguage == AppLanguage.SYSTEM) {
            baseContext
        } else {
            baseContext.createConfigurationContext(localizedConfig)
        }
    }

    val callbacks = remember(viewModel) {
        ZeroShellCallbacks(
            onSelectDestination = viewModel::selectDestination,
            onSelectCalendarDate = viewModel::selectCalendarDate,
            onSelectCalendarViewMode = viewModel::selectCalendarViewMode,
            onSelectTask = viewModel::selectTask,
            onToggleIncludeSubfolders = viewModel::toggleIncludeSubfolders,
            onToggleMediumTreeExpanded = viewModel::toggleMediumTreeExpanded,
            onToggleFolderExpanded = viewModel::setFolderExpanded,
            onCreateFolder = viewModel::createFolder,
            onRenameFolder = viewModel::renameFolder,
            onDeleteFolder = viewModel::deleteFolder,
            onToggleTaskComplete = viewModel::toggleTaskCompletion,
            onMoveTaskToFolder = viewModel::moveTaskToFolder,
            onReorderTask = viewModel::reorderTask,
            onUpdateTaskTitleAndDescription = viewModel::updateTaskTitleAndDescription,
            onUpdateTaskPriority = viewModel::updateTaskPriority,
            onUpdateTaskDueDate = viewModel::updateTaskDueDate,
            onUpdateTaskRecurrenceRule = viewModel::updateTaskRecurrenceRule,
            onAddSubtask = viewModel::addSubtask,
            onToggleSubtask = viewModel::toggleSubtaskCompletion,
            onPromoteSubtask = viewModel::promoteSubtask,
            onDeleteSubtask = viewModel::deleteSubtask,
            onAddTag = viewModel::addTagToTask,
            onRemoveTag = viewModel::removeTagFromTask,
            onDeleteTask = viewModel::deleteTask,
            onQuickInputChange = viewModel::updateQuickInputText,
            onQuickInputSubmit = viewModel::submitQuickInput,
            onSelectFolderSuggestion = viewModel::applyFolderSuggestion,
            onToggleContinuousEntry = viewModel::toggleContinuousEntry,
            onVoiceInputRecognized = viewModel::submitVoiceInput,
            onUndo = viewModel::performUndo,
            onUndoTimeout = viewModel::dismissUndoBanner,
            onToggleTheme = viewModel::toggleTheme,
            onSetDarkTheme = viewModel::setDarkTheme,
            onToggleAccent = viewModel::toggleAccent,
            onSetAccent = viewModel::setAccent,
            onSetAntiMisclickMode = viewModel::setAntiMisclickMode,
            onSetHapticsEnabled = viewModel::setHapticsEnabled,
            onSetAppLanguage = viewModel::setAppLanguage,
            onOpenSettings = viewModel::openSettings,
            onCloseSettings = viewModel::closeSettings,
            onGenerateJsonBackup = viewModel::generateJsonBackup,
            onGenerateMarkdownExport = viewModel::generateMarkdownExport,
            onImportJsonBackup = viewModel::importJsonBackup,
            onReportBackupStatus = viewModel::reportBackupStatus,
            onStartFocus = viewModel::openFocusForTask,
            onSelectFocusMode = viewModel::selectFocusMode,
            onStartOrResumeFocus = viewModel::startOrResumeFocus,
            onPauseFocus = viewModel::pauseFocus,
            onResetFocus = viewModel::resetFocus,
            onContinueFocusToPomodoro = viewModel::continueFocusToPomodoro,
            onStopStopwatchFocus = viewModel::stopStopwatchFocus,
            onCompleteFocusedTask = viewModel::completeFocusedTask,
            onExitFocus = viewModel::exitFocus
        )
    }

    CompositionLocalProvider(
        LocalConfiguration provides localizedConfig,
        LocalContext provides localizedContext
    ) {
        ZeroTheme(
            darkTheme = uiState.isDarkTheme,
            accent = uiState.activeAccent,
            hapticsEnabled = uiState.hapticsEnabled
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = ZeroTheme.colors.background
            ) {
                NavHost(
                    navController = navController,
                    startDestination = ZeroRoutes.MAIN
                ) {
                    composable(ZeroRoutes.MAIN) {
                        ZeroAdaptiveShell(
                            uiState = uiState,
                            callbacks = callbacks
                        )
                    }
                }
            }
        }
    }
}
