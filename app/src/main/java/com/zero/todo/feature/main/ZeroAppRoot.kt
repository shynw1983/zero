package com.zero.todo.feature.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zero.todo.core.designsystem.ZeroTheme

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

    LaunchedEffect(widgetLaunchArgs?.id) {
        if (widgetLaunchArgs != null && widgetLaunchArgs.id > 0L) {
            viewModel.handleWidgetLaunch(
                destinationKey = widgetLaunchArgs.destinationKey,
                taskId = widgetLaunchArgs.taskId,
                launchQuickInput = widgetLaunchArgs.launchQuickInput
            )
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
            onDeleteFolder = viewModel::deleteFolder,
            onToggleTaskComplete = viewModel::toggleTaskCompletion,
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
