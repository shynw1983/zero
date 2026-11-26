package com.zero.todo.feature.main

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.domain.model.AppLanguage
import com.zero.todo.domain.model.WorkspaceMode
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
    val taskParentPicker by viewModel.taskParentPicker.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val baseContext = LocalContext.current
    val baseConfig = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current

    // Sync system status & navigation bar icon contrast cleanly with Dark / Light theme
    // NOW Execution Runway always enters the matte darkroom stage for deep focus contrast.
    SideEffect {
        val window = baseContext.findActivity()?.window
        if (window != null && !view.isInEditMode) {
            val effectiveDarkSurface = uiState.isDarkTheme || uiState.workspaceMode == WorkspaceMode.NOW
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !effectiveDarkSurface
            controller.isAppearanceLightNavigationBars = !effectiveDarkSurface
        }
    }

    // Sync midnight date & wall-clock focus timer whenever app returns to foreground
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshCurrentDate()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
            } else {
                val systemLocales = Resources.getSystem().configuration.locales
                if (!systemLocales.isEmpty) {
                    setLocale(systemLocales[0])
                    setLocales(systemLocales)
                }
            }
        }
    }

    SideEffect {
        val locales = localizedConfig.locales
        if (!locales.isEmpty) {
            Locale.setDefault(locales[0])
        }
    }

    val localizedContext = remember(baseContext, localizedConfig) {
        baseContext.createConfigurationContext(localizedConfig)
    }

    val callbacks = remember(viewModel, baseContext) {
        ZeroShellCallbacks(
            onSetWorkspaceMode = viewModel::setWorkspaceMode,
            onSetDailyCapacityMinutes = viewModel::setDailyCapacityMinutes,
            onOpenTriage = viewModel::openTriage,
            onCloseTriage = viewModel::closeTriage,
            onSkipRunwayTask = viewModel::skipTaskInRunway,
            onShuffleRunway = viewModel::shuffleRunway,
            onOpenTaskDetailFromNow = viewModel::openTaskDetailFromNow,
            onUpdateTaskFirstStep = viewModel::updateTaskFirstStep,
            onCompleteFirstStepAndStartFlow = viewModel::completeFirstStepAndStartFlow,
            onUpdateTaskEffort = viewModel::updateTaskEffort,
            onTriageScheduleToday = viewModel::triageScheduleToday,
            onTriageDeferTomorrow = viewModel::triageDeferTomorrow,
            onTriageMoveToFolder = viewModel::triageMoveToFolder,
            onRenameTag = viewModel::renameTag,
            onDeleteTag = viewModel::deleteTag,
            onSelectDestination = viewModel::selectDestination,
            onSelectCalendarDate = viewModel::selectCalendarDate,
            onSelectCalendarViewMode = viewModel::selectCalendarViewMode,
            onSelectTask = viewModel::selectTask,
            onToggleIncludeSubfolders = viewModel::toggleIncludeSubfolders,
            onToggleMediumTreeExpanded = viewModel::toggleMediumTreeExpanded,
            onToggleSearch = viewModel::setSearchOpen,
            onSearchQueryChange = viewModel::updateSearchQuery,
            onSetTaskSortMode = viewModel::setTaskSortMode,
            onRescheduleOverdueToToday = viewModel::rescheduleOverdueToToday,
            onPrefillQuickInputPriority = viewModel::prefillQuickInputPriority,
            onToggleFolderExpanded = viewModel::setFolderExpanded,
            onCreateFolder = viewModel::createFolder,
            onRenameFolder = viewModel::renameFolder,
            onMoveFolderOrder = viewModel::moveFolderOrder,
            onMoveFolderToParent = viewModel::moveFolderToParent,
            onDeleteFolder = viewModel::deleteFolder,
            onToggleTaskComplete = viewModel::toggleTaskCompletion,
            onMoveTaskToFolder = viewModel::moveTaskToFolder,
            onReorderTask = viewModel::reorderTask,
            onIndentTask = viewModel::indentTask,
            onOutdentTask = viewModel::outdentTask,
            onUpdateTaskTitleAndDescription = viewModel::updateTaskTitleAndDescription,
            onUpdateTaskPriority = viewModel::updateTaskPriority,
            onUpdateTaskDueDate = viewModel::updateTaskDueDate,
            onUpdateTaskReminder = viewModel::updateTaskReminder,
            onUpdateTaskRecurrenceRule = viewModel::updateTaskRecurrenceRule,
            onAddSubtask = viewModel::addSubtask,
            onPromoteSubtask = viewModel::promoteSubtask,
            onOpenTaskParentPicker = viewModel::openTaskParentPicker,
            onChooseTaskParent = viewModel::chooseTaskParent,
            onCloseTaskParentPicker = viewModel::closeTaskParentPicker,
            onAddTag = viewModel::addTagToTask,
            onRemoveTag = viewModel::removeTagFromTask,
            onDeleteTask = viewModel::deleteTask,
            onRestoreDeletedTask = viewModel::restoreDeletedTask,
            onPermanentlyDeleteTask = viewModel::permanentlyDeleteTask,
            onEmptyTrash = viewModel::emptyTrash,
            onQuickInputEdit = viewModel::editQuickInputText,
            onQuickInputSubmit = viewModel::submitQuickInput,
            onToggleContinuousEntry = viewModel::toggleContinuousEntry,
            onVoiceInputRecognized = viewModel::submitVoiceInput,
            onUndo = viewModel::performUndo,
            onUndoTimeout = viewModel::dismissUndoBanner,
            onToggleTheme = viewModel::toggleTheme,
            onSetDarkTheme = viewModel::setDarkTheme,
            onToggleAccent = viewModel::toggleAccent,
            onSetAccent = viewModel::setAccent,
            onSetAntiMisclickMode = viewModel::setAntiMisclickMode,
            onSetTaskSwipeAction = viewModel::setTaskSwipeAction,
            onSetHapticsEnabled = viewModel::setHapticsEnabled,
            onSetAppLanguage = { lang ->
                viewModel.setAppLanguage(lang)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    runCatching {
                        val localeManager = baseContext.getSystemService(LocaleManager::class.java)
                        val currentLang = localeManager?.applicationLocales
                            ?.takeIf { !it.isEmpty }
                            ?.get(0)
                            ?.language
                        val targetLang = lang.languageTag
                        if (currentLang != targetLang) {
                            localeManager?.applicationLocales = if (targetLang == null) {
                                LocaleList.getEmptyLocaleList()
                            } else {
                                LocaleList.forLanguageTags(targetLang)
                            }
                        }
                    }
                }
            },
            onUpdateWebDavConfig = viewModel::updateWebDavConfig,
            onTestWebDav = viewModel::testWebDavConnection,
            onPushWebDav = viewModel::pushWebDavBackup,
            onPullWebDav = viewModel::pullWebDavBackup,
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
                            callbacks = callbacks,
                            taskParentPicker = taskParentPicker
                        )
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
