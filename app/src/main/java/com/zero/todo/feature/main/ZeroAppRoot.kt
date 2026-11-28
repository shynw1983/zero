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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
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
import com.zero.todo.domain.model.ExecuteTheme
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
    sharedCapturePayload: com.zero.todo.core.common.SharedCapturePayload? = null,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val executeUiState by viewModel.executeUiState.collectAsStateWithLifecycle()
    val taskParentPicker by viewModel.taskParentPicker.collectAsStateWithLifecycle()
    val aiTestStatus by viewModel.aiTestStatus.collectAsStateWithLifecycle()
    val aiCaptureState by viewModel.aiCaptureState.collectAsStateWithLifecycle()
    val aiBreakdownState by viewModel.aiBreakdownState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val baseContext = LocalContext.current
    val baseConfig = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    var isAtlasMapDark by remember(uiState.isDarkTheme) { mutableStateOf(uiState.isDarkTheme) }

    // Sync system status & navigation bar icon contrast cleanly with Dark / Light theme
    // NOW Execution Runway enters darkroom contrast, except ATLAS follows the active Light/Dark map style.
    SideEffect {
        val window = baseContext.findActivity()?.window
        if (window != null && !view.isInEditMode) {
            val isNowAtlas = uiState.workspaceMode == WorkspaceMode.NOW && uiState.executeTheme == ExecuteTheme.ATLAS
            val effectiveDarkSurface = when {
                isNowAtlas -> isAtlasMapDark
                uiState.workspaceMode == WorkspaceMode.NOW -> true
                else -> uiState.isDarkTheme
            }
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

    var promptedNotifPermission by remember { mutableStateOf(false) }
    val startupNotifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(
        uiState.allActiveTasks,
        uiState.notificationPreferences.morningBriefingEnabled,
        uiState.notificationPreferences.eveningReviewEnabled
    ) {
        if (promptedNotifPermission || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val now = System.currentTimeMillis()
        val hasScheduledWork = uiState.notificationPreferences.morningBriefingEnabled ||
            uiState.notificationPreferences.eveningReviewEnabled ||
            uiState.allActiveTasks.any { !it.isCompleted && (it.reminderAt ?: 0L) > now }
        if (hasScheduledWork && !com.zero.todo.core.notification.ZeroNotificationHelper.canPostNotifications(baseContext)) {
            promptedNotifPermission = true
            startupNotifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
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

    LaunchedEffect(sharedCapturePayload?.id) {
        if (sharedCapturePayload != null && sharedCapturePayload.id > 0L) {
            viewModel.openAiCaptureFromShare(
                sharedText = sharedCapturePayload.text,
                sharedImageUri = sharedCapturePayload.imageUri
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

    val windowWidthSizeClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass

    val localizedContext = remember(baseContext, localizedConfig) {
        val localizedResources = baseContext.createConfigurationContext(localizedConfig).resources
        LocalizedContextWrapper(baseContext, localizedResources)
    }

    val activeLanguageTag = if (!localizedConfig.locales.isEmpty) {
        localizedConfig.locales[0].toLanguageTag()
    } else {
        "en"
    }

    val callbacks = remember(viewModel, baseContext, activeLanguageTag) {
        ZeroShellCallbacks(
            onSetWorkspaceMode = viewModel::setWorkspaceMode,
            onSetExecuteTheme = viewModel::setExecuteTheme,
            onSelectFocusedRunwayTask = viewModel::selectFocusedRunwayTask,
            onFocusPreviousRunwayTask = viewModel::focusPreviousRunwayTask,
            onFocusNextRunwayTask = viewModel::focusNextRunwayTask,
            onSetDailyCapacityMinutes = viewModel::setDailyCapacityMinutes,
            onOpenTriage = viewModel::openTriage,
            onCloseTriage = viewModel::closeTriage,
            onSkipRunwayTask = viewModel::skipTaskInRunway,
            onOpenTaskDetailFromNow = viewModel::openTaskDetailFromNow,
            onUpdateTaskFirstStep = viewModel::updateTaskFirstStep,
            onCompleteFirstStepAndStartFlow = viewModel::completeFirstStepAndStartFlow,
            onUpdateTaskEffort = viewModel::updateTaskEffort,
            onTriageScheduleToday = viewModel::triageScheduleToday,
            onTriageDeferTomorrow = viewModel::triageDeferTomorrow,
            onTriageMoveToFolder = viewModel::triageMoveToFolder,
            onCreateTag = viewModel::createTag,
            onToggleTagExpanded = viewModel::toggleTagExpanded,
            onMoveTag = viewModel::moveTag,
            onRenameTag = viewModel::renameTag,
            onDeleteTag = viewModel::deleteTag,
            onUpdateTagPlaceConfig = viewModel::updateTagPlaceConfig,
            onAddTagPlace = viewModel::addTagPlace,
            onUpdateTagPlace = viewModel::updateTagPlace,
            onDeleteTagPlace = viewModel::deleteTagPlace,
            onResyncGeofences = viewModel::resyncGeofences,
            onTestPlaceReminder = viewModel::testPlaceReminder,
            geofencePermissionStateProvider = viewModel::geofencePermissionState,
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
            onUpdateFolderColor = viewModel::updateFolderColor,
            onMoveFolderOrder = viewModel::moveFolderOrder,
            onMoveFolderToParent = viewModel::moveFolderToParent,
            onDeleteFolder = viewModel::deleteFolder,
            onToggleTaskComplete = viewModel::toggleTaskCompletion,
            onMoveTaskToFolder = viewModel::moveTaskToFolder,
            onReorderTask = viewModel::reorderTask,
            onIndentTask = viewModel::indentTask,
            onOutdentTask = viewModel::outdentTask,
            onOpenTaskContext = viewModel::openTaskContext,
            onSkipTaskOccurrence = viewModel::skipTaskOccurrence,
            onMoveTagInOrder = viewModel::moveTagInOrder,
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
            onUpdateTaskPlaceTriggerOverride = viewModel::updateTaskPlaceTriggerOverride,
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
            onSetFolderPathDisplayMode = viewModel::setFolderPathDisplayMode,
            onSetPriorityDisplayStyle = viewModel::setPriorityDisplayStyle,
            onUpdateNotificationPreferences = viewModel::updateNotificationPreferences,
            onUpdateAiConfig = viewModel::updateAiConfig,
            onTestAiConnection = viewModel::testAiConnection,
            onOpenAiCapture = viewModel::openAiCapture,
            onCloseAiCapture = viewModel::closeAiCapture,
            onAnalyzeAiCapture = { sessionId, text, image, audio ->
                viewModel.analyzeAiCapture(sessionId, text, image, audio, activeLanguageTag)
            },
            onApplyAiCapturedTasks = viewModel::applyAiCapturedTasks,
            onOpenAiBreakdown = { task ->
                viewModel.openAiBreakdown(task, activeLanguageTag)
            },
            onCloseAiBreakdown = viewModel::closeAiBreakdown,
            onRetryAiBreakdown = { sessionId ->
                viewModel.retryAiBreakdown(sessionId, activeLanguageTag)
            },
            onApplyAiBreakdown = viewModel::applyAiBreakdown,
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
            hapticsEnabled = uiState.hapticsEnabled,
            priorityDisplayStyle = uiState.priorityDisplayStyle
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
                            executeUiState = executeUiState,
                            taskParentPicker = taskParentPicker,
                            aiTestStatus = aiTestStatus,
                            aiCaptureState = aiCaptureState,
                            aiBreakdownState = aiBreakdownState,
                            isAtlasMapDark = isAtlasMapDark,
                            onToggleAtlasMapDark = { isAtlasMapDark = !isAtlasMapDark },
                            windowWidthSizeClass = windowWidthSizeClass
                        )
                    }
                }
            }
        }
    }
}

internal class LocalizedContextWrapper(
    base: Context,
    private val localizedResources: android.content.res.Resources
) : ContextWrapper(base) {
    override fun getResources(): android.content.res.Resources = localizedResources
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
