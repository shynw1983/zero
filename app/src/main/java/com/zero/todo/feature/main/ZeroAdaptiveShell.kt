package com.zero.todo.feature.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowWidthSizeClass
import com.zero.todo.R
import com.zero.todo.core.designsystem.ZeroAccent
import com.zero.todo.core.designsystem.ZeroCapsuleShape
import com.zero.todo.core.designsystem.ZeroMotion
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.hairlineDividerEnd
import com.zero.todo.core.designsystem.hairlineDividerStart
import com.zero.todo.core.designsystem.hairlineDividerTop
import com.zero.todo.core.designsystem.rememberZeroHaptics
import com.zero.todo.core.nlp.QuickInputParser
import com.zero.todo.data.backup.WebDavConfig
import com.zero.todo.domain.model.AntiMisclickMode
import com.zero.todo.domain.model.AppLanguage
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.domain.model.Folder
import com.zero.todo.domain.model.FolderNode
import com.zero.todo.domain.model.TagItem
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.TaskPriority
import com.zero.todo.domain.model.TaskSortMode
import com.zero.todo.domain.model.TaskSwipeAction
import com.zero.todo.domain.model.TaskSwipeDirection
import com.zero.todo.domain.model.WorkspaceMode
import com.zero.todo.feature.calendar.CalendarPane
import com.zero.todo.feature.calendar.CalendarViewMode
import com.zero.todo.feature.focus.SingleTaskFocusOverlay
import com.zero.todo.feature.folder.CreateFolderDialog
import com.zero.todo.feature.folder.DeleteFolderConfirmDialog
import com.zero.todo.feature.folder.DeleteTagConfirmDialog
import com.zero.todo.feature.folder.FolderActionMenuDialog
import com.zero.todo.feature.folder.FolderTreePanel
import com.zero.todo.feature.folder.MoveFolderDialog
import com.zero.todo.feature.folder.NavigationDestination
import com.zero.todo.feature.folder.RenameFolderDialog
import com.zero.todo.feature.folder.RenameTagDialog
import com.zero.todo.feature.folder.SmartViewCounts
import com.zero.todo.feature.folder.TagActionMenuDialog
import com.zero.todo.feature.matrix.MatrixPane
import com.zero.todo.feature.now.NowRunwayScreen
import com.zero.todo.feature.settings.BackupUiStatus
import com.zero.todo.feature.settings.SettingsOverlay
import com.zero.todo.feature.taskdetail.TaskDetailPane
import com.zero.todo.feature.taskdetail.TaskParentPickerDialog
import com.zero.todo.feature.tasklist.TaskListPane
import com.zero.todo.feature.tasklist.UndoBannerState
import com.zero.todo.feature.trash.TrashPane
import com.zero.todo.feature.triage.TriageDeckOverlay
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ZeroShellCallbacks(
    val onSetWorkspaceMode: (WorkspaceMode) -> Unit = {},
    val onSetDailyCapacityMinutes: (Int) -> Unit = {},
    val onOpenTriage: () -> Unit = {},
    val onCloseTriage: () -> Unit = {},
    val onSkipRunwayTask: (TaskItem) -> Unit = {},
    val onOpenTaskDetailFromNow: (TaskItem) -> Unit = {},
    val onUpdateTaskFirstStep: (TaskItem, String?) -> Unit = { _, _ -> },
    val onCompleteFirstStepAndStartFlow: (TaskItem) -> Unit = {},
    val onUpdateTaskEffort: (TaskItem, Int?) -> Unit = { _, _ -> },
    val onTriageScheduleToday: (TaskItem) -> Unit = {},
    val onTriageDeferTomorrow: (TaskItem) -> Unit = {},
    val onTriageMoveToFolder: (TaskItem, String) -> Unit = { _, _ -> },
    val onRenameTag: (tagId: String, newName: String) -> Unit = { _, _ -> },
    val onDeleteTag: (tagId: String) -> Unit = {},
    val onSelectDestination: (NavigationDestination) -> Unit = {},
    val onSelectCalendarDate: (LocalDate) -> Unit = {},
    val onSelectCalendarViewMode: (CalendarViewMode) -> Unit = {},
    val onSelectTask: (String?) -> Unit = {},
    val onToggleIncludeSubfolders: () -> Unit = {},
    val onToggleMediumTreeExpanded: () -> Unit = {},
    val onToggleSearch: (Boolean) -> Unit = {},
    val onSearchQueryChange: (String) -> Unit = {},
    val onSetTaskSortMode: (TaskSortMode) -> Unit = {},
    val onRescheduleOverdueToToday: () -> Unit = {},
    val onPrefillQuickInputPriority: (TaskPriority) -> Unit = {},
    val onToggleFolderExpanded: (folderId: String, isExpanded: Boolean) -> Unit = { _, _ -> },
    val onCreateFolder: (name: String, parentId: String?) -> Unit = { _, _ -> },
    val onRenameFolder: (folderId: String, newName: String) -> Unit = { _, _ -> },
    val onMoveFolderOrder: (folderId: String, direction: Int) -> Unit = { _, _ -> },
    val onMoveFolderToParent: (folderId: String, targetParentId: String?) -> Unit = { _, _ -> },
    val onDeleteFolder: (folderId: String) -> Unit = {},
    val onToggleTaskComplete: (TaskItem) -> Unit = {},
    val onMoveTaskToFolder: (taskId: String, targetFolderId: String?) -> Unit = { _, _ -> },
    val onReorderTask: (task: TaskItem, parentTaskId: String?, beforeTaskId: String?) -> Unit = { _, _, _ -> },
    val onIndentTask: (TaskItem, String) -> Unit = { _, _ -> },
    val onOutdentTask: (TaskItem) -> Unit = {},
    val onUpdateTaskTitleAndDescription: (task: TaskItem, title: String, description: String) -> Unit = { _, _, _ -> },
    val onUpdateTaskPriority: (task: TaskItem, priority: TaskPriority) -> Unit = { _, _ -> },
    val onUpdateTaskDueDate: (task: TaskItem, dueDateMillis: Long?, isAllDay: Boolean) -> Unit = { _, _, _ -> },
    val onUpdateTaskReminder: (task: TaskItem, reminderAtMillis: Long?) -> Unit = { _, _ -> },
    val onUpdateTaskRecurrenceRule: (task: TaskItem, rrule: String?) -> Unit = { _, _ -> },
    val onAddSubtask: (parentTaskId: String, title: String) -> Unit = { _, _ -> },
    val onPromoteSubtask: (subtaskId: String) -> Unit = {},
    val onOpenTaskParentPicker: (TaskItem) -> Unit = {},
    val onChooseTaskParent: (String) -> Unit = {},
    val onCloseTaskParentPicker: () -> Unit = {},
    val onAddTag: (taskId: String, tagName: String) -> Unit = { _, _ -> },
    val onRemoveTag: (taskId: String, tagId: String) -> Unit = { _, _ -> },
    val onDeleteTask: (TaskItem) -> Unit = {},
    val onRestoreDeletedTask: (String) -> Unit = {},
    val onPermanentlyDeleteTask: (String) -> Unit = {},
    val onEmptyTrash: () -> Unit = {},
    val onQuickInputEdit: ((String) -> String) -> Unit = {},
    val onQuickInputSubmit: () -> Unit = {},
    val onToggleContinuousEntry: () -> Unit = {},
    val onVoiceInputRecognized: (String) -> Unit = {},
    val onUndo: (UndoBannerState) -> Unit = {},
    val onUndoTimeout: (Long) -> Unit = {},
    val onToggleTheme: () -> Unit = {},
    val onSetDarkTheme: (Boolean) -> Unit = {},
    val onToggleAccent: () -> Unit = {},
    val onSetAccent: (ZeroAccent) -> Unit = {},
    val onSetAntiMisclickMode: (AntiMisclickMode) -> Unit = {},
    val onSetTaskSwipeAction: (TaskSwipeDirection, TaskSwipeAction) -> Unit = { _, _ -> },
    val onSetHapticsEnabled: (Boolean) -> Unit = {},
    val onSetAppLanguage: (AppLanguage) -> Unit = {},
    val onUpdateWebDavConfig: (WebDavConfig) -> Unit = {},
    val onTestWebDav: () -> Unit = {},
    val onPushWebDav: () -> Unit = {},
    val onPullWebDav: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onCloseSettings: () -> Unit = {},
    val onGenerateJsonBackup: suspend () -> String = { "{}" },
    val onGenerateMarkdownExport: suspend () -> String = { "" },
    val onImportJsonBackup: (String) -> Unit = {},
    val onReportBackupStatus: (BackupUiStatus) -> Unit = {},
    val onStartFocus: (task: TaskItem?, mode: FocusMode) -> Unit = { _, _ -> },
    val onSelectFocusMode: (FocusMode) -> Unit = {},
    val onStartOrResumeFocus: () -> Unit = {},
    val onPauseFocus: () -> Unit = {},
    val onResetFocus: () -> Unit = {},
    val onContinueFocusToPomodoro: () -> Unit = {},
    val onStopStopwatchFocus: () -> Unit = {},
    val onCompleteFocusedTask: (TaskItem) -> Unit = {},
    val onExitFocus: () -> Unit = {}
)

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ZeroAdaptiveShell(
    uiState: MainUiState,
    callbacks: ZeroShellCallbacks,
    taskParentPicker: TaskParentPickerState? = null,
    windowWidthSizeClass: WindowWidthSizeClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val focusManager = LocalFocusManager.current
    val quickInputFocusRequester = remember { FocusRequester() }

    var createFolderParent by remember { mutableStateOf<Folder?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderMenuTarget by remember { mutableStateOf<Folder?>(null) }
    var renameTargetFolder by remember { mutableStateOf<Folder?>(null) }
    var organizeTargetFolder by remember { mutableStateOf<Folder?>(null) }
    var deleteTargetFolder by remember { mutableStateOf<Folder?>(null) }

    var tagMenuTarget by remember { mutableStateOf<TagItem?>(null) }
    var renameTargetTag by remember { mutableStateOf<TagItem?>(null) }
    var deleteTargetTag by remember { mutableStateOf<TagItem?>(null) }

    LaunchedEffect(uiState.quickInputFocusRequestTick) {
        if (uiState.quickInputFocusRequestTick > 0L) {
            runCatching { quickInputFocusRequester.requestFocus() }
        }
    }

    val openCreateFolderDialog: (Folder?) -> Unit = { parent ->
        createFolderParent = parent
        showCreateFolderDialog = true
    }

    // Tablet & Desktop external keyboard shortcuts (Ctrl/Cmd + N, Ctrl/Cmd + F, Esc, Ctrl/Cmd + ',', Ctrl/Cmd + 1..5)
    val rootKeyboardModifier = modifier
        .fillMaxSize()
        .onPreviewKeyEvent { keyEvent ->
            if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val modPressed = keyEvent.isCtrlPressed || keyEvent.isMetaPressed

            when {
                modPressed && keyEvent.key == Key.N -> {
                    if (uiState.isSettingsOpen) callbacks.onCloseSettings()
                    if (uiState.isTriageOpen) callbacks.onCloseTriage()
                    if (uiState.focusState.isActive) callbacks.onExitFocus()
                    runCatching { quickInputFocusRequester.requestFocus() }
                    true
                }
                modPressed && keyEvent.key == Key.F -> {
                    if (uiState.isSettingsOpen) callbacks.onCloseSettings()
                    callbacks.onSetWorkspaceMode(WorkspaceMode.VAULT)
                    callbacks.onToggleSearch(!uiState.isSearchOpen)
                    true
                }
                modPressed && keyEvent.key == Key.Comma -> {
                    if (uiState.isSettingsOpen) callbacks.onCloseSettings() else callbacks.onOpenSettings()
                    true
                }
                modPressed && keyEvent.key == Key.One -> {
                    callbacks.onSetWorkspaceMode(WorkspaceMode.VAULT)
                    callbacks.onSelectDestination(NavigationDestination.Inbox)
                    true
                }
                modPressed && keyEvent.key == Key.Two -> {
                    callbacks.onSetWorkspaceMode(WorkspaceMode.VAULT)
                    callbacks.onSelectDestination(NavigationDestination.Today)
                    true
                }
                modPressed && keyEvent.key == Key.Three -> {
                    callbacks.onSetWorkspaceMode(WorkspaceMode.VAULT)
                    callbacks.onSelectDestination(NavigationDestination.Tomorrow)
                    true
                }
                modPressed && keyEvent.key == Key.Four -> {
                    callbacks.onSetWorkspaceMode(WorkspaceMode.VAULT)
                    callbacks.onSelectDestination(NavigationDestination.Next7Days)
                    true
                }
                modPressed && keyEvent.key == Key.Five -> {
                    callbacks.onSetWorkspaceMode(WorkspaceMode.VAULT)
                    callbacks.onSelectDestination(NavigationDestination.Calendar)
                    true
                }
                keyEvent.key == Key.Escape -> {
                    when {
                        uiState.isSettingsOpen -> {
                            callbacks.onCloseSettings()
                            true
                        }
                        uiState.isTriageOpen -> {
                            callbacks.onCloseTriage()
                            true
                        }
                        folderMenuTarget != null -> {
                            folderMenuTarget = null
                            true
                        }
                        tagMenuTarget != null -> {
                            tagMenuTarget = null
                            true
                        }
                        showCreateFolderDialog -> {
                            showCreateFolderDialog = false
                            true
                        }
                        renameTargetFolder != null -> {
                            renameTargetFolder = null
                            true
                        }
                        organizeTargetFolder != null -> {
                            organizeTargetFolder = null
                            true
                        }
                        deleteTargetFolder != null -> {
                            deleteTargetFolder = null
                            true
                        }
                        renameTargetTag != null -> {
                            renameTargetTag = null
                            true
                        }
                        deleteTargetTag != null -> {
                            deleteTargetTag = null
                            true
                        }
                        uiState.isSearchOpen -> {
                            callbacks.onToggleSearch(false)
                            true
                        }
                        uiState.focusState.isActive -> {
                            callbacks.onExitFocus()
                            true
                        }
                        uiState.selectedTaskId != null -> {
                            callbacks.onSelectTask(null)
                            true
                        }
                        else -> {
                            focusManager.clearFocus()
                            false
                        }
                    }
                }
                else -> false
            }
        }

    Box(
        modifier = rootKeyboardModifier
            .background(colors.background)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Primary Dual-State Content: NOW (Execution Runway) vs VAULT (Organization Studio)
            AnimatedContent(
                targetState = uiState.workspaceMode,
                transitionSpec = {
                    fadeIn(ZeroMotion.FloatSpring) togetherWith fadeOut(ZeroMotion.FloatSpring)
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                label = "workspace_dual_state"
            ) { mode ->
                when (mode) {
                    WorkspaceMode.NOW -> {
                        NowRunwayScreen(
                            currentDate = uiState.currentDate,
                            runwayQueue = uiState.runwayQueue,
                            scheduledTodayMinutes = uiState.scheduledTodayMinutes,
                            dailyCapacityMinutes = uiState.dailyCapacityMinutes,
                            triageCount = uiState.triageQueue.size,
                            folderNameById = uiState.folderNameById,
                            quickInputText = uiState.quickInputText,
                            parsedQuickInput = uiState.parsedQuickInput,
                            folderSuggestions = uiState.folderSuggestions,
                            continuousEntryEnabled = uiState.continuousEntryEnabled,
                            undoBanner = uiState.undoBanner,
                            quickInputFocusRequester = quickInputFocusRequester,
                            onCompleteTask = callbacks.onToggleTaskComplete,
                            onSkipTask = callbacks.onSkipRunwayTask,
                            onOpenTaskDetail = callbacks.onOpenTaskDetailFromNow,
                            onStartFocus = callbacks.onStartFocus,
                            onUpdateFirstStep = callbacks.onUpdateTaskFirstStep,
                            onCompleteFirstStepAndStartFlow = callbacks.onCompleteFirstStepAndStartFlow,
                            onUpdateEffort = callbacks.onUpdateTaskEffort,
                            onOpenTriage = callbacks.onOpenTriage,
                            onSwitchToVault = { callbacks.onSetWorkspaceMode(WorkspaceMode.VAULT) },
                            onOpenSettings = callbacks.onOpenSettings,
                            onQuickInputEdit = callbacks.onQuickInputEdit,
                            onQuickInputSubmit = callbacks.onQuickInputSubmit,
                            onToggleContinuousEntry = callbacks.onToggleContinuousEntry,
                            onVoiceInputRecognized = callbacks.onVoiceInputRecognized,
                            onUndo = callbacks.onUndo,
                            onUndoTimeout = callbacks.onUndoTimeout
                        )
                    }

                    WorkspaceMode.VAULT -> {
                        when (windowWidthSizeClass) {
                            WindowWidthSizeClass.COMPACT -> {
                                CompactSinglePaneLayout(
                                    uiState = uiState,
                                    callbacks = callbacks,
                                    quickInputFocusRequester = quickInputFocusRequester,
                                    onRequestCreateFolder = openCreateFolderDialog,
                                    onRequestFolderMenu = { folderMenuTarget = it },
                                    onRequestTagMenu = { tagMenuTarget = it }
                                )
                            }

                            WindowWidthSizeClass.MEDIUM -> {
                                MediumTwoPaneLayout(
                                    uiState = uiState,
                                    callbacks = callbacks,
                                    quickInputFocusRequester = quickInputFocusRequester,
                                    onRequestCreateFolder = openCreateFolderDialog,
                                    onRequestFolderMenu = { folderMenuTarget = it },
                                    onRequestTagMenu = { tagMenuTarget = it }
                                )
                            }

                            else -> {
                                ExpandedThreePaneLayout(
                                    uiState = uiState,
                                    callbacks = callbacks,
                                    quickInputFocusRequester = quickInputFocusRequester,
                                    onRequestCreateFolder = openCreateFolderDialog,
                                    onRequestFolderMenu = { folderMenuTarget = it },
                                    onRequestTagMenu = { tagMenuTarget = it }
                                )
                            }
                        }
                    }
                }
            }

            // ZERO 2.0 Tactile Dual-State Hardware Switcher Bar (NOW <-> VAULT, 48dp touch height)
            DualStateModeBar(
                activeMode = uiState.workspaceMode,
                runwayCount = uiState.runwayQueue.size,
                vaultBadgeCount = uiState.triageQueue.size,
                onSelectMode = callbacks.onSetWorkspaceMode
            )
        }

        // Full-Screen 30-Second Lightning Triage Deck Overlay
        AnimatedVisibility(
            visible = uiState.isTriageOpen,
            enter = fadeIn(animationSpec = ZeroMotion.FloatSpring) +
                slideInVertically(animationSpec = ZeroMotion.IntOffsetSpring) { it / 10 },
            exit = fadeOut(animationSpec = ZeroMotion.FloatSpring) +
                slideOutVertically(animationSpec = ZeroMotion.IntOffsetSpring) { it / 10 }
        ) {
            TriageDeckOverlay(
                queue = uiState.triageQueue,
                quickFolders = uiState.quickFolders,
                onScheduleToday = callbacks.onTriageScheduleToday,
                onDeferTomorrow = callbacks.onTriageDeferTomorrow,
                onMoveToFolder = callbacks.onTriageMoveToFolder,
                onDiscardTask = callbacks.onDeleteTask,
                onDismiss = callbacks.onCloseTriage
            )
        }

        // Full-Screen Single-Task Focus Mode Overlay
        AnimatedVisibility(
            visible = uiState.focusState.isActive,
            enter = fadeIn(animationSpec = ZeroMotion.FloatSpring) +
                slideInVertically(animationSpec = ZeroMotion.IntOffsetSpring) { it / 10 },
            exit = fadeOut(animationSpec = ZeroMotion.FloatSpring) +
                slideOutVertically(animationSpec = ZeroMotion.IntOffsetSpring) { it / 10 }
        ) {
            SingleTaskFocusOverlay(
                state = uiState.focusState,
                onSelectMode = callbacks.onSelectFocusMode,
                onStartOrResume = callbacks.onStartOrResumeFocus,
                onPause = callbacks.onPauseFocus,
                onReset = callbacks.onResetFocus,
                onContinueToPomodoro = callbacks.onContinueFocusToPomodoro,
                onStopStopwatch = callbacks.onStopStopwatchFocus,
                onCompleteTask = callbacks.onCompleteFocusedTask,
                onExit = callbacks.onExitFocus
            )
        }

        // Settings Overlay
        AnimatedVisibility(
            visible = uiState.isSettingsOpen,
            enter = fadeIn(animationSpec = ZeroMotion.FloatSpring) +
                slideInVertically(animationSpec = ZeroMotion.IntOffsetSpring) { it / 12 },
            exit = fadeOut(animationSpec = ZeroMotion.FloatSpring) +
                slideOutVertically(animationSpec = ZeroMotion.IntOffsetSpring) { it / 12 }
        ) {
            SettingsOverlay(
                isDarkTheme = uiState.isDarkTheme,
                activeAccent = uiState.activeAccent,
                antiMisclickMode = uiState.antiMisclickMode,
                taskSwipeActions = uiState.taskSwipeActions,
                hapticsEnabled = uiState.hapticsEnabled,
                appLanguage = uiState.appLanguage,
                dailyCapacityMinutes = uiState.dailyCapacityMinutes,
                webDavConfig = uiState.webDavConfig,
                webDavStatus = uiState.webDavStatus,
                backupStatus = uiState.backupStatus,
                onSetDarkTheme = callbacks.onSetDarkTheme,
                onSetAccent = callbacks.onSetAccent,
                onSetAntiMisclickMode = callbacks.onSetAntiMisclickMode,
                onSetTaskSwipeAction = callbacks.onSetTaskSwipeAction,
                onSetHapticsEnabled = callbacks.onSetHapticsEnabled,
                onSetAppLanguage = callbacks.onSetAppLanguage,
                onSetDailyCapacityMinutes = callbacks.onSetDailyCapacityMinutes,
                onUpdateWebDavConfig = callbacks.onUpdateWebDavConfig,
                onTestWebDav = callbacks.onTestWebDav,
                onPushWebDav = callbacks.onPushWebDav,
                onPullWebDav = callbacks.onPullWebDav,
                onGenerateJsonBackup = callbacks.onGenerateJsonBackup,
                onGenerateMarkdownExport = callbacks.onGenerateMarkdownExport,
                onImportJsonBackup = callbacks.onImportJsonBackup,
                onReportBackupStatus = callbacks.onReportBackupStatus,
                onDismiss = callbacks.onCloseSettings
            )
        }
    }

    // Folder Management Action Sheet + Dialogs
    taskParentPicker?.let { picker ->
        uiState.allActiveTasks.firstOrNull { it.id == picker.taskId }?.let { task ->
            TaskParentPickerDialog(
                task = task,
                tasks = uiState.allActiveTasks,
                folderNames = uiState.folderNameById,
                saving = picker.saving,
                failed = picker.failed,
                onSelect = callbacks.onChooseTaskParent,
                onDismiss = callbacks.onCloseTaskParentPicker
            )
        }
    }

    val menuFolder = folderMenuTarget
    if (menuFolder != null) {
        FolderActionMenuDialog(
            folder = menuFolder,
            onCreateSubfolder = { openCreateFolderDialog(menuFolder) },
            onRename = { renameTargetFolder = menuFolder },
            onOrganize = { organizeTargetFolder = menuFolder },
            onDelete = { deleteTargetFolder = menuFolder },
            onDismiss = { folderMenuTarget = null }
        )
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            parentFolder = createFolderParent,
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name, parentId ->
                showCreateFolderDialog = false
                callbacks.onCreateFolder(name, parentId)
            }
        )
    }

    val folderToRename = renameTargetFolder
    if (folderToRename != null) {
        RenameFolderDialog(
            folder = folderToRename,
            onDismiss = { renameTargetFolder = null },
            onConfirm = { folderId, newName ->
                renameTargetFolder = null
                callbacks.onRenameFolder(folderId, newName)
            }
        )
    }

    val folderToOrganize = organizeTargetFolder
    if (folderToOrganize != null) {
        MoveFolderDialog(
            folder = folderToOrganize,
            folderTree = uiState.folderTree,
            onMoveOrder = callbacks.onMoveFolderOrder,
            onMoveToParent = callbacks.onMoveFolderToParent,
            onDismiss = { organizeTargetFolder = null }
        )
    }

    val folderToDelete = deleteTargetFolder
    if (folderToDelete != null) {
        DeleteFolderConfirmDialog(
            folder = folderToDelete,
            onDismiss = { deleteTargetFolder = null },
            onConfirmDelete = { id ->
                deleteTargetFolder = null
                callbacks.onDeleteFolder(id)
            }
        )
    }

    // Tag Global Management Action Sheet + Dialogs (Rename & Delete Tag!)
    val menuTag = tagMenuTarget
    if (menuTag != null) {
        TagActionMenuDialog(
            tag = menuTag,
            onRename = { renameTargetTag = menuTag },
            onDelete = { deleteTargetTag = menuTag },
            onDismiss = { tagMenuTarget = null }
        )
    }

    val tagToRename = renameTargetTag
    if (tagToRename != null) {
        RenameTagDialog(
            tag = tagToRename,
            onDismiss = { renameTargetTag = null },
            onConfirm = { tagId, newName ->
                renameTargetTag = null
                callbacks.onRenameTag(tagId, newName)
            }
        )
    }

    val tagToDelete = deleteTargetTag
    if (tagToDelete != null) {
        DeleteTagConfirmDialog(
            tag = tagToDelete,
            onDismiss = { deleteTargetTag = null },
            onConfirmDelete = { tagId ->
                deleteTargetTag = null
                callbacks.onDeleteTag(tagId)
            }
        )
    }
}

/**
 * Tactile Dieter Rams / hardware-inspired bottom Dual-State Switcher Bar (NOW <-> VAULT).
 */
@Composable
private fun DualStateModeBar(
    activeMode: WorkspaceMode,
    runwayCount: Int,
    vaultBadgeCount: Int,
    onSelectMode: (WorkspaceMode) -> Unit
) {
    val colors = ZeroTheme.colors
    val haptics = rememberZeroHaptics()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .hairlineDividerTop()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(ZeroCapsuleShape)
                .background(colors.surface)
                .hairlineBorder(shape = ZeroCapsuleShape)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DualModeSegment(
                icon = Icons.Rounded.Bolt,
                label = stringResource(R.string.mode_now),
                badgeCount = runwayCount,
                selected = activeMode == WorkspaceMode.NOW,
                onClick = {
                    if (activeMode != WorkspaceMode.NOW) {
                        haptics.confirm()
                        onSelectMode(WorkspaceMode.NOW)
                    }
                },
                modifier = Modifier.weight(1f)
            )
            DualModeSegment(
                icon = Icons.Rounded.FolderCopy,
                label = stringResource(R.string.mode_vault),
                badgeCount = vaultBadgeCount,
                selected = activeMode == WorkspaceMode.VAULT,
                onClick = {
                    if (activeMode != WorkspaceMode.VAULT) {
                        haptics.confirm()
                        onSelectMode(WorkspaceMode.VAULT)
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DualModeSegment(
    icon: ImageVector,
    label: String,
    badgeCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography

    Row(
        modifier = modifier
            .fillMaxHeight()
            .clip(ZeroCapsuleShape)
            .background(if (selected) colors.surfaceElevated else colors.surface)
            .then(
                if (selected) Modifier.hairlineBorder(shape = ZeroCapsuleShape, color = colors.accent)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) colors.accent else colors.textMuted,
            modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.textPrimary else colors.textSecondary
        )
        if (badgeCount > 0) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = badgeCount.toString().padStart(2, '0'),
                style = tabular.dateCompact,
                color = if (selected) colors.accent else colors.textMuted
            )
        }
    }
}

@Composable
private fun CenterWorkspacePane(
    uiState: MainUiState,
    callbacks: ZeroShellCallbacks,
    quickInputFocusRequester: FocusRequester,
    showDrawerButton: Boolean,
    showTreeToggleButton: Boolean,
    onOpenDrawer: () -> Unit,
    onRequestCreateFolder: (Folder?) -> Unit,
    modifier: Modifier = Modifier
) {
    when (val dest = uiState.selectedDestination) {
        NavigationDestination.Calendar -> {
            CalendarPane(
                currentDate = uiState.currentDate,
                selectedDate = uiState.calendarSelectedDate,
                viewMode = uiState.calendarViewMode,
                allActiveTasks = uiState.allActiveTasks,
                folderNameById = uiState.folderNameById,
                selectedTaskId = uiState.selectedTaskId,
                antiMisclickMode = uiState.antiMisclickMode,
                taskSwipeActions = uiState.taskSwipeActions,
                quickInputText = uiState.quickInputText,
                parsedQuickInput = uiState.parsedQuickInput,
                folderSuggestions = uiState.folderSuggestions,
                continuousEntryEnabled = uiState.continuousEntryEnabled,
                undoBanner = uiState.undoBanner,
                quickInputFocusRequester = quickInputFocusRequester,
                showDrawerButton = showDrawerButton,
                showTreeToggleButton = showTreeToggleButton,
                onOpenDrawer = onOpenDrawer,
                onToggleTreePanel = callbacks.onToggleMediumTreeExpanded,
                onSelectDate = callbacks.onSelectCalendarDate,
                onSelectViewMode = callbacks.onSelectCalendarViewMode,
                onToggleTaskComplete = callbacks.onToggleTaskComplete,
                onDeleteTask = callbacks.onDeleteTask,
                onSelectTask = { task -> callbacks.onSelectTask(task.id) },
                onSelectTag = { tagId -> callbacks.onSelectDestination(NavigationDestination.TagView(tagId)) },
                onQuickInputEdit = callbacks.onQuickInputEdit,
                onQuickInputSubmit = callbacks.onQuickInputSubmit,
                onToggleContinuousEntry = callbacks.onToggleContinuousEntry,
                onVoiceInputRecognized = callbacks.onVoiceInputRecognized,
                onUndo = callbacks.onUndo,
                onUndoTimeout = callbacks.onUndoTimeout,
                modifier = modifier
            )
        }

        NavigationDestination.Matrix -> {
            MatrixPane(
                allActiveTasks = uiState.allActiveTasks,
                selectedTaskId = uiState.selectedTaskId,
                showDrawerButton = showDrawerButton,
                showTreeToggleButton = showTreeToggleButton,
                onOpenDrawer = onOpenDrawer,
                onToggleTreePanel = callbacks.onToggleMediumTreeExpanded,
                onSelectTask = { task -> callbacks.onSelectTask(task.id) },
                onQuickAddWithPriority = callbacks.onPrefillQuickInputPriority,
                modifier = modifier
            )
        }

        NavigationDestination.Trash -> {
            TrashPane(
                deletedTasks = uiState.deletedTasks,
                showDrawerButton = showDrawerButton,
                showTreeToggleButton = showTreeToggleButton,
                onOpenDrawer = onOpenDrawer,
                onToggleTreePanel = callbacks.onToggleMediumTreeExpanded,
                onRestoreTask = callbacks.onRestoreDeletedTask,
                onPermanentlyDeleteTask = callbacks.onPermanentlyDeleteTask,
                onEmptyTrash = callbacks.onEmptyTrash,
                modifier = modifier
            )
        }

        else -> {
            val localizedDestinationTitle = when (dest) {
                NavigationDestination.Inbox -> stringResource(R.string.nav_inbox)
                NavigationDestination.Today -> stringResource(R.string.nav_today)
                NavigationDestination.Tomorrow -> stringResource(R.string.nav_tomorrow)
                NavigationDestination.Next7Days -> stringResource(R.string.nav_next_7_days)
                NavigationDestination.Calendar -> stringResource(R.string.nav_calendar)
                NavigationDestination.All -> stringResource(R.string.nav_all_tasks)
                NavigationDestination.Matrix -> stringResource(R.string.nav_matrix)
                NavigationDestination.Trash -> stringResource(R.string.nav_trash)
                is NavigationDestination.FolderView ->
                    uiState.folderNameById[dest.folderId] ?: stringResource(R.string.nav_inbox)
                is NavigationDestination.TagView ->
                    uiState.tagNameById[dest.tagId]?.let { "#$it" } ?: stringResource(R.string.nav_inbox)
            }
            TaskListPane(
                currentDate = uiState.currentDate,
                title = localizedDestinationTitle,
                breadcrumbs = uiState.breadcrumbs,
                selectedDestination = uiState.selectedDestination,
                includeSubfolders = uiState.includeSubfolders,
                isSearchOpen = uiState.isSearchOpen,
                searchQuery = uiState.searchQuery,
                taskSortMode = uiState.taskSortMode,
                scheduledTodayMinutes = uiState.scheduledTodayMinutes,
                dailyCapacityMinutes = uiState.dailyCapacityMinutes,
                triageCount = uiState.triageQueue.size,
                tasks = uiState.tasks,
                allActiveTasks = uiState.allActiveTasks,
                folderNameById = uiState.folderNameById,
                selectedTaskId = uiState.selectedTaskId,
                antiMisclickMode = uiState.antiMisclickMode,
                taskSwipeActions = uiState.taskSwipeActions,
                quickInputText = uiState.quickInputText,
                parsedQuickInput = uiState.parsedQuickInput,
                folderSuggestions = uiState.folderSuggestions,
                continuousEntryEnabled = uiState.continuousEntryEnabled,
                undoBanner = uiState.undoBanner,
                quickInputFocusRequester = quickInputFocusRequester,
                showDrawerButton = showDrawerButton,
                showTreeToggleButton = showTreeToggleButton,
                onOpenDrawer = onOpenDrawer,
                onToggleTreePanel = callbacks.onToggleMediumTreeExpanded,
                onSelectBreadcrumb = { id -> callbacks.onSelectDestination(NavigationDestination.FolderView(id)) },
                onToggleIncludeSubfolders = callbacks.onToggleIncludeSubfolders,
                onToggleSearch = callbacks.onToggleSearch,
                onSearchQueryChange = callbacks.onSearchQueryChange,
                onSetSortMode = callbacks.onSetTaskSortMode,
                onRescheduleOverdueToToday = callbacks.onRescheduleOverdueToToday,
                onOpenTriage = callbacks.onOpenTriage,
                onSelectTag = { tagId -> callbacks.onSelectDestination(NavigationDestination.TagView(tagId)) },
                onRequestCreateSubfolder = { onRequestCreateFolder(uiState.breadcrumbs.lastOrNull()) },
                onOpenFocusMode = { callbacks.onStartFocus(null, FocusMode.TWO_MIN_STARTER) },
                onToggleTaskComplete = callbacks.onToggleTaskComplete,
                onDeleteTask = callbacks.onDeleteTask,
                onIndentTask = callbacks.onIndentTask,
                onOutdentTask = callbacks.onOutdentTask,
                onSelectTask = { task -> callbacks.onSelectTask(task.id) },
                onReorderTask = callbacks.onReorderTask,
                onQuickInputEdit = callbacks.onQuickInputEdit,
                onQuickInputSubmit = callbacks.onQuickInputSubmit,
                onToggleContinuousEntry = callbacks.onToggleContinuousEntry,
                onVoiceInputRecognized = callbacks.onVoiceInputRecognized,
                onUndo = callbacks.onUndo,
                onUndoTimeout = callbacks.onUndoTimeout,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun BoundTaskDetailPane(
    uiState: MainUiState,
    callbacks: ZeroShellCallbacks,
    showBackButton: Boolean,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    TaskDetailPane(
        task = uiState.selectedTask,
        currentDate = uiState.currentDate,
        folderName = uiState.selectedTask?.folderId?.let { uiState.folderNameById[it] },
        folderTree = uiState.folderTree,
        subtasks = uiState.selectedTaskSubtasks,
        parentTask = uiState.selectedTask?.parentTaskId?.let { id -> uiState.allActiveTasks.firstOrNull { it.id == id } },
        showBackButton = showBackButton,
        onNavigateBack = onNavigateBack,
        onToggleComplete = callbacks.onToggleTaskComplete,
        onStartFocus = { task, mode -> callbacks.onStartFocus(task, mode) },
        onMoveTaskToFolder = callbacks.onMoveTaskToFolder,
        onUpdateTitleAndDescription = callbacks.onUpdateTaskTitleAndDescription,
        onUpdatePriority = callbacks.onUpdateTaskPriority,
        onUpdateEffort = callbacks.onUpdateTaskEffort,
        onUpdateFirstStep = callbacks.onUpdateTaskFirstStep,
        onUpdateDueDate = callbacks.onUpdateTaskDueDate,
        onUpdateReminder = callbacks.onUpdateTaskReminder,
        onUpdateRecurrenceRule = callbacks.onUpdateTaskRecurrenceRule,
        onAddSubtask = callbacks.onAddSubtask,
        onPromoteSubtask = callbacks.onPromoteSubtask,
        onOpenTaskParentPicker = callbacks.onOpenTaskParentPicker,
        onOpenRelatedTask = callbacks.onSelectTask,
        onAddTag = callbacks.onAddTag,
        onRemoveTag = callbacks.onRemoveTag,
        onSelectTag = { tagId ->
            callbacks.onSelectDestination(NavigationDestination.TagView(tagId))
            onNavigateBack()
        },
        onDeleteTask = callbacks.onDeleteTask,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun CompactSinglePaneLayout(
    uiState: MainUiState,
    callbacks: ZeroShellCallbacks,
    quickInputFocusRequester: FocusRequester,
    onRequestCreateFolder: (Folder?) -> Unit,
    onRequestFolderMenu: (Folder) -> Unit,
    onRequestTagMenu: (TagItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()

    LaunchedEffect(uiState.selectedTaskId) {
        val taskId = uiState.selectedTaskId
        if (taskId != null) {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, taskId)
        } else if (navigator.canNavigateBack()) {
            navigator.navigateBack()
        }
    }

    BackHandler(enabled = navigator.canNavigateBack() && !uiState.focusState.isActive && !uiState.isSettingsOpen && !uiState.isTriageOpen) {
        callbacks.onSelectTask(null)
        navigator.navigateBack()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = modifier,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = colors.background,
                modifier = Modifier
                    .width(292.dp)
                    .hairlineDividerEnd()
            ) {
                FolderTreePanel(
                    folderTree = uiState.folderTree,
                    tagsWithCounts = uiState.tagsWithCounts,
                    selectedDestination = uiState.selectedDestination,
                    smartViewCounts = uiState.smartViewCounts,
                    triageCount = uiState.triageQueue.size,
                    includeSubfolders = uiState.includeSubfolders,
                    isDarkTheme = uiState.isDarkTheme,
                    activeAccent = uiState.activeAccent,
                    onSelectDestination = { destination ->
                        callbacks.onSelectDestination(destination)
                        scope.launch { drawerState.close() }
                    },
                    onOpenTriage = {
                        scope.launch { drawerState.close() }
                        callbacks.onOpenTriage()
                    },
                    onToggleFolderExpanded = callbacks.onToggleFolderExpanded,
                    onRequestCreateFolder = onRequestCreateFolder,
                    onRequestFolderMenu = onRequestFolderMenu,
                    onRequestTagMenu = onRequestTagMenu,
                    onToggleIncludeSubfolders = callbacks.onToggleIncludeSubfolders,
                    onToggleTheme = callbacks.onToggleTheme,
                    onToggleAccent = callbacks.onToggleAccent,
                    onOpenSettings = {
                        scope.launch { drawerState.close() }
                        callbacks.onOpenSettings()
                    }
                )
            }
        }
    ) {
        ListDetailPaneScaffold(
            directive = navigator.scaffoldDirective,
            value = navigator.scaffoldValue,
            listPane = {
                AnimatedPane {
                    CenterWorkspacePane(
                        uiState = uiState,
                        callbacks = callbacks,
                        quickInputFocusRequester = quickInputFocusRequester,
                        showDrawerButton = true,
                        showTreeToggleButton = false,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onRequestCreateFolder = onRequestCreateFolder
                    )
                }
            },
            detailPane = {
                AnimatedPane {
                    BoundTaskDetailPane(
                        uiState = uiState,
                        callbacks = callbacks,
                        showBackButton = true,
                        onNavigateBack = {
                            callbacks.onSelectTask(null)
                            if (navigator.canNavigateBack()) {
                                navigator.navigateBack()
                            }
                        }
                    )
                }
            }
        )
    }
}

/**
 * Smart Dynamic Two-Pane Layout for Unfolded Foldables (Medium 600dp–840dp):
 * - No task selected: `[Full-Height Folder Tree | Full-Height Task List]` — zero blank right pane.
 * - Task selected: smoothly transitions to `[Full-Height Task List | Full-Height Task Detail]`,
 *   while full-height Folder Tree remains 1-tap / edge-swipe away via ModalNavigationDrawer.
 */
@Composable
private fun MediumTwoPaneLayout(
    uiState: MainUiState,
    callbacks: ZeroShellCallbacks,
    quickInputFocusRequester: FocusRequester,
    onRequestCreateFolder: (Folder?) -> Unit,
    onRequestFolderMenu: (Folder) -> Unit,
    onRequestTagMenu: (TagItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val hasSelectedTask = uiState.selectedTask != null

    BackHandler(enabled = hasSelectedTask && !uiState.focusState.isActive && !uiState.isSettingsOpen && !uiState.isTriageOpen) {
        callbacks.onSelectTask(null)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = hasSelectedTask || !uiState.isMediumTreeExpanded,
        modifier = modifier,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = colors.background,
                modifier = Modifier
                    .width(284.dp)
                    .hairlineDividerEnd()
            ) {
                FolderTreePanel(
                    folderTree = uiState.folderTree,
                    tagsWithCounts = uiState.tagsWithCounts,
                    selectedDestination = uiState.selectedDestination,
                    smartViewCounts = uiState.smartViewCounts,
                    triageCount = uiState.triageQueue.size,
                    includeSubfolders = uiState.includeSubfolders,
                    isDarkTheme = uiState.isDarkTheme,
                    activeAccent = uiState.activeAccent,
                    onSelectDestination = { destination ->
                        callbacks.onSelectDestination(destination)
                        scope.launch { drawerState.close() }
                    },
                    onOpenTriage = {
                        scope.launch { drawerState.close() }
                        callbacks.onOpenTriage()
                    },
                    onToggleFolderExpanded = callbacks.onToggleFolderExpanded,
                    onRequestCreateFolder = onRequestCreateFolder,
                    onRequestFolderMenu = onRequestFolderMenu,
                    onRequestTagMenu = onRequestTagMenu,
                    onToggleIncludeSubfolders = callbacks.onToggleIncludeSubfolders,
                    onToggleTheme = callbacks.onToggleTheme,
                    onToggleAccent = callbacks.onToggleAccent,
                    onOpenSettings = {
                        scope.launch { drawerState.close() }
                        callbacks.onOpenSettings()
                    }
                )
            }
        }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val sidebarWidth = (maxWidth * 0.36f).coerceIn(240.dp, 276.dp)
            val detailWidth = (maxWidth * 0.50f).coerceIn(292.dp, 400.dp)

            Row(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = !hasSelectedTask && uiState.isMediumTreeExpanded,
                    enter = expandHorizontally(animationSpec = ZeroMotion.IntSizeSpring) +
                        fadeIn(animationSpec = ZeroMotion.FloatSpring),
                    exit = shrinkHorizontally(animationSpec = ZeroMotion.IntSizeSpring) +
                        fadeOut(animationSpec = ZeroMotion.FloatSpring)
                ) {
                    FolderTreePanel(
                        folderTree = uiState.folderTree,
                        tagsWithCounts = uiState.tagsWithCounts,
                        selectedDestination = uiState.selectedDestination,
                        smartViewCounts = uiState.smartViewCounts,
                        triageCount = uiState.triageQueue.size,
                        includeSubfolders = uiState.includeSubfolders,
                        isDarkTheme = uiState.isDarkTheme,
                        activeAccent = uiState.activeAccent,
                        onSelectDestination = callbacks.onSelectDestination,
                        onOpenTriage = callbacks.onOpenTriage,
                        onToggleFolderExpanded = callbacks.onToggleFolderExpanded,
                        onRequestCreateFolder = onRequestCreateFolder,
                        onRequestFolderMenu = onRequestFolderMenu,
                        onRequestTagMenu = onRequestTagMenu,
                        onToggleIncludeSubfolders = callbacks.onToggleIncludeSubfolders,
                        onToggleTheme = callbacks.onToggleTheme,
                        onToggleAccent = callbacks.onToggleAccent,
                        onOpenSettings = callbacks.onOpenSettings,
                        modifier = Modifier
                            .width(sidebarWidth)
                            .fillMaxHeight()
                            .hairlineDividerEnd()
                    )
                }

                CenterWorkspacePane(
                    uiState = uiState,
                    callbacks = callbacks,
                    quickInputFocusRequester = quickInputFocusRequester,
                    showDrawerButton = hasSelectedTask,
                    showTreeToggleButton = !hasSelectedTask,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onRequestCreateFolder = onRequestCreateFolder,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                AnimatedVisibility(
                    visible = hasSelectedTask,
                    enter = expandHorizontally(animationSpec = ZeroMotion.IntSizeSpring) +
                        fadeIn(animationSpec = ZeroMotion.FloatSpring),
                    exit = shrinkHorizontally(animationSpec = ZeroMotion.IntSizeSpring) +
                        fadeOut(animationSpec = ZeroMotion.FloatSpring)
                ) {
                    BoundTaskDetailPane(
                        uiState = uiState,
                        callbacks = callbacks,
                        showBackButton = true,
                        onNavigateBack = { callbacks.onSelectTask(null) },
                        modifier = Modifier
                            .width(detailWidth)
                            .fillMaxHeight()
                            .hairlineDividerStart()
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandedThreePaneLayout(
    uiState: MainUiState,
    callbacks: ZeroShellCallbacks,
    quickInputFocusRequester: FocusRequester,
    onRequestCreateFolder: (Folder?) -> Unit,
    onRequestFolderMenu: (Folder) -> Unit,
    onRequestTagMenu: (TagItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxSize()) {
        FolderTreePanel(
            folderTree = uiState.folderTree,
            tagsWithCounts = uiState.tagsWithCounts,
            selectedDestination = uiState.selectedDestination,
            smartViewCounts = uiState.smartViewCounts,
            triageCount = uiState.triageQueue.size,
            includeSubfolders = uiState.includeSubfolders,
            isDarkTheme = uiState.isDarkTheme,
            activeAccent = uiState.activeAccent,
            onSelectDestination = callbacks.onSelectDestination,
            onOpenTriage = callbacks.onOpenTriage,
            onToggleFolderExpanded = callbacks.onToggleFolderExpanded,
            onRequestCreateFolder = onRequestCreateFolder,
            onRequestFolderMenu = onRequestFolderMenu,
            onRequestTagMenu = onRequestTagMenu,
            onToggleIncludeSubfolders = callbacks.onToggleIncludeSubfolders,
            onToggleTheme = callbacks.onToggleTheme,
            onToggleAccent = callbacks.onToggleAccent,
            onOpenSettings = callbacks.onOpenSettings,
            modifier = Modifier
                .width(268.dp)
                .fillMaxHeight()
                .hairlineDividerEnd()
        )

        CenterWorkspacePane(
            uiState = uiState,
            callbacks = callbacks,
            quickInputFocusRequester = quickInputFocusRequester,
            showDrawerButton = false,
            showTreeToggleButton = false,
            onOpenDrawer = {},
            onRequestCreateFolder = onRequestCreateFolder,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .hairlineDividerEnd()
        )

        BoundTaskDetailPane(
            uiState = uiState,
            callbacks = callbacks,
            showBackButton = false,
            onNavigateBack = {},
            modifier = Modifier
                .weight(0.85f)
                .fillMaxHeight()
        )
    }
}

private fun samplePreviewState(): MainUiState {
    val fWork = Folder(id = "f1", name = "工作", sortOrder = 1024.0, isExpanded = true)
    val fFinance = Folder(id = "f2", parentId = "f1", name = "财务", sortOrder = 1024.0, isExpanded = true)
    val fReimburse = Folder(id = "f3", parentId = "f2", name = "报销", sortOrder = 1024.0, isExpanded = true)
    val t1 = TaskItem(
        id = "t1",
        folderId = "f1",
        title = "评审 Q4 架构设计与数据库表结构",
        description = "# 核心验收\n- [x] 检查递归 CTE 索引\n- [ ] 验证防误触 30% 阈值",
        priority = TaskPriority.URGENT,
        firstStep = "打开文档写下 3 个核心议题",
        estimatedMinutes = 25,
        recurrenceRule = "FREQ=WEEKLY;BYDAY=MO,WE,FR"
    )
    val t2 = TaskItem(
        id = "t2",
        folderId = "f3",
        title = "提交 11 月差旅机票报销单",
        priority = TaskPriority.HIGH,
        estimatedMinutes = 5
    )
    val sampleInput = "明天下午3点 /工作/财务/报销 提交发票 !1 #财务"
    return MainUiState(
        workspaceMode = WorkspaceMode.NOW,
        runwayQueue = listOf(t1, t2),
        scheduledTodayMinutes = 30,
        dailyCapacityMinutes = 240,
        selectedDestination = NavigationDestination.FolderView("f1"),
        destinationTitle = "工作",
        breadcrumbs = listOf(fWork),
        includeSubfolders = true,
        folderTree = listOf(
            FolderNode(
                folder = fWork,
                depth = 0,
                directTaskCount = 1,
                totalTaskCount = 2,
                children = listOf(
                    FolderNode(
                        folder = fFinance,
                        depth = 1,
                        directTaskCount = 0,
                        totalTaskCount = 1,
                        children = listOf(
                            FolderNode(folder = fReimburse, depth = 2, directTaskCount = 1, totalTaskCount = 1)
                        )
                    )
                )
            )
        ),
        folderNameById = mapOf("f1" to "工作", "f2" to "财务", "f3" to "报销"),
        smartViewCounts = SmartViewCounts(inbox = 3, today = 2, tomorrow = 1, next7Days = 5, all = 6),
        tasks = listOf(t1, t2),
        allActiveTasks = listOf(t1, t2),
        selectedTaskId = t1.id,
        selectedTask = t1,
        quickInputText = sampleInput,
        parsedQuickInput = QuickInputParser.parse(sampleInput)
    )
}

@Preview(name = "Compact Phone NOW Dark", widthDp = 390, heightDp = 844)
@Composable
private fun PreviewZeroCompact() {
    ZeroTheme(darkTheme = true) {
        ZeroAdaptiveShell(
            uiState = remember { samplePreviewState() },
            callbacks = ZeroShellCallbacks(),
            windowWidthSizeClass = WindowWidthSizeClass.COMPACT
        )
    }
}

@Preview(name = "Medium Foldable VAULT Dark", widthDp = 700, heightDp = 840)
@Composable
private fun PreviewZeroMedium() {
    ZeroTheme(darkTheme = true) {
        ZeroAdaptiveShell(
            uiState = remember { samplePreviewState().copy(workspaceMode = WorkspaceMode.VAULT, selectedTask = null, selectedTaskId = null) },
            callbacks = ZeroShellCallbacks(),
            windowWidthSizeClass = WindowWidthSizeClass.MEDIUM
        )
    }
}
