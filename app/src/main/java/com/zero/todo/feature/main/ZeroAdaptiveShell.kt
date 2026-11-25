package com.zero.todo.feature.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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
import com.zero.todo.core.designsystem.ZeroMotion
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineDividerBottom
import com.zero.todo.core.designsystem.hairlineDividerEnd
import com.zero.todo.core.designsystem.hairlineDividerStart
import com.zero.todo.core.nlp.QuickInputParser
import com.zero.todo.domain.model.AntiMisclickMode
import com.zero.todo.domain.model.AppLanguage
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.domain.model.Folder
import com.zero.todo.domain.model.FolderNode
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.TaskPriority
import com.zero.todo.feature.calendar.CalendarPane
import com.zero.todo.feature.calendar.CalendarViewMode
import com.zero.todo.feature.focus.SingleTaskFocusOverlay
import com.zero.todo.feature.folder.CreateFolderDialog
import com.zero.todo.feature.folder.FolderTreePanel
import com.zero.todo.feature.folder.NavigationDestination
import com.zero.todo.feature.folder.SmartViewCounts
import com.zero.todo.feature.settings.BackupUiStatus
import com.zero.todo.feature.settings.SettingsOverlay
import com.zero.todo.feature.taskdetail.TaskDetailPane
import com.zero.todo.feature.tasklist.FolderPathSuggestion
import com.zero.todo.feature.tasklist.TaskListPane
import com.zero.todo.feature.tasklist.UndoBannerState
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ZeroShellCallbacks(
    val onSelectDestination: (NavigationDestination) -> Unit = {},
    val onSelectCalendarDate: (LocalDate) -> Unit = {},
    val onSelectCalendarViewMode: (CalendarViewMode) -> Unit = {},
    val onSelectTask: (String?) -> Unit = {},
    val onToggleIncludeSubfolders: () -> Unit = {},
    val onToggleMediumTreeExpanded: () -> Unit = {},
    val onToggleFolderExpanded: (folderId: String, isExpanded: Boolean) -> Unit = { _, _ -> },
    val onCreateFolder: (name: String, parentId: String?) -> Unit = { _, _ -> },
    val onDeleteFolder: (folderId: String) -> Unit = {},
    val onToggleTaskComplete: (TaskItem) -> Unit = {},
    val onReorderTask: (taskId: String, targetIndex: Int) -> Unit = { _, _ -> },
    val onUpdateTaskTitleAndDescription: (task: TaskItem, title: String, description: String) -> Unit = { _, _, _ -> },
    val onUpdateTaskPriority: (task: TaskItem, priority: TaskPriority) -> Unit = { _, _ -> },
    val onUpdateTaskDueDate: (task: TaskItem, dueDateMillis: Long?, isAllDay: Boolean) -> Unit = { _, _, _ -> },
    val onUpdateTaskRecurrenceRule: (task: TaskItem, rrule: String?) -> Unit = { _, _ -> },
    val onAddSubtask: (parentTaskId: String, title: String) -> Unit = { _, _ -> },
    val onToggleSubtask: (TaskItem) -> Unit = {},
    val onPromoteSubtask: (subtaskId: String) -> Unit = {},
    val onDeleteSubtask: (TaskItem) -> Unit = {},
    val onAddTag: (taskId: String, tagName: String) -> Unit = { _, _ -> },
    val onRemoveTag: (taskId: String, tagId: String) -> Unit = { _, _ -> },
    val onDeleteTask: (TaskItem) -> Unit = {},
    val onQuickInputChange: (String) -> Unit = {},
    val onQuickInputSubmit: () -> Unit = {},
    val onSelectFolderSuggestion: (FolderPathSuggestion) -> Unit = {},
    val onToggleContinuousEntry: () -> Unit = {},
    val onVoiceInputRecognized: (String) -> Unit = {},
    val onUndo: (UndoBannerState) -> Unit = {},
    val onUndoTimeout: (Long) -> Unit = {},
    val onToggleTheme: () -> Unit = {},
    val onSetDarkTheme: (Boolean) -> Unit = {},
    val onToggleAccent: () -> Unit = {},
    val onSetAccent: (ZeroAccent) -> Unit = {},
    val onSetAntiMisclickMode: (AntiMisclickMode) -> Unit = {},
    val onSetHapticsEnabled: (Boolean) -> Unit = {},
    val onSetAppLanguage: (AppLanguage) -> Unit = {},
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
    windowWidthSizeClass: WindowWidthSizeClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val focusManager = LocalFocusManager.current
    val quickInputFocusRequester = remember { FocusRequester() }
    var createFolderParent by remember { mutableStateOf<Folder?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.quickInputFocusRequestTick) {
        if (uiState.quickInputFocusRequestTick > 0L) {
            runCatching { quickInputFocusRequester.requestFocus() }
        }
    }

    val openCreateFolderDialog: (Folder?) -> Unit = { parent ->
        createFolderParent = parent
        showCreateFolderDialog = true
    }

    // Tablet & Desktop external keyboard shortcuts (Ctrl/Cmd + N, Esc, Ctrl/Cmd + ',', Ctrl/Cmd + 1..5)
    val rootKeyboardModifier = modifier
        .fillMaxSize()
        .onPreviewKeyEvent { keyEvent ->
            if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val modPressed = keyEvent.isCtrlPressed || keyEvent.isMetaPressed

            when {
                modPressed && keyEvent.key == Key.N -> {
                    if (uiState.isSettingsOpen) callbacks.onCloseSettings()
                    if (uiState.focusState.isActive) callbacks.onExitFocus()
                    runCatching { quickInputFocusRequester.requestFocus() }
                    true
                }
                modPressed && keyEvent.key == Key.Comma -> {
                    if (uiState.isSettingsOpen) callbacks.onCloseSettings() else callbacks.onOpenSettings()
                    true
                }
                modPressed && keyEvent.key == Key.One -> {
                    callbacks.onSelectDestination(NavigationDestination.Inbox)
                    true
                }
                modPressed && keyEvent.key == Key.Two -> {
                    callbacks.onSelectDestination(NavigationDestination.Today)
                    true
                }
                modPressed && keyEvent.key == Key.Three -> {
                    callbacks.onSelectDestination(NavigationDestination.Tomorrow)
                    true
                }
                modPressed && keyEvent.key == Key.Four -> {
                    callbacks.onSelectDestination(NavigationDestination.Next7Days)
                    true
                }
                modPressed && keyEvent.key == Key.Five -> {
                    callbacks.onSelectDestination(NavigationDestination.Calendar)
                    true
                }
                keyEvent.key == Key.Escape -> {
                    when {
                        uiState.isSettingsOpen -> {
                            callbacks.onCloseSettings()
                            true
                        }
                        showCreateFolderDialog -> {
                            showCreateFolderDialog = false
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

    val contentSurfaceModifier = Modifier
        .fillMaxSize()
        .background(colors.background)
        .statusBarsPadding()

    Box(modifier = rootKeyboardModifier) {
        when (windowWidthSizeClass) {
            WindowWidthSizeClass.COMPACT -> {
                CompactSinglePaneLayout(
                    uiState = uiState,
                    callbacks = callbacks,
                    quickInputFocusRequester = quickInputFocusRequester,
                    onRequestCreateFolder = openCreateFolderDialog,
                    modifier = contentSurfaceModifier
                )
            }

            WindowWidthSizeClass.MEDIUM -> {
                MediumTwoPaneLayout(
                    uiState = uiState,
                    callbacks = callbacks,
                    quickInputFocusRequester = quickInputFocusRequester,
                    onRequestCreateFolder = openCreateFolderDialog,
                    modifier = contentSurfaceModifier
                )
            }

            else -> {
                ExpandedThreePaneLayout(
                    uiState = uiState,
                    callbacks = callbacks,
                    quickInputFocusRequester = quickInputFocusRequester,
                    onRequestCreateFolder = openCreateFolderDialog,
                    modifier = contentSurfaceModifier
                )
            }
        }

        // Full-Screen Single-Task Focus Mode Overlay (Spring physical motion)
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

        // Settings Overlay (Theme / Accent / Anti-Misclick / Haptics / JSON & Markdown Backup)
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
                hapticsEnabled = uiState.hapticsEnabled,
                appLanguage = uiState.appLanguage,
                backupStatus = uiState.backupStatus,
                onSetDarkTheme = callbacks.onSetDarkTheme,
                onSetAccent = callbacks.onSetAccent,
                onSetAntiMisclickMode = callbacks.onSetAntiMisclickMode,
                onSetHapticsEnabled = callbacks.onSetHapticsEnabled,
                onSetAppLanguage = callbacks.onSetAppLanguage,
                onGenerateJsonBackup = callbacks.onGenerateJsonBackup,
                onGenerateMarkdownExport = callbacks.onGenerateMarkdownExport,
                onImportJsonBackup = callbacks.onImportJsonBackup,
                onReportBackupStatus = callbacks.onReportBackupStatus,
                onDismiss = callbacks.onCloseSettings
            )
        }
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
    if (uiState.selectedDestination == NavigationDestination.Calendar) {
        CalendarPane(
            selectedDate = uiState.calendarSelectedDate,
            viewMode = uiState.calendarViewMode,
            allActiveTasks = uiState.allActiveTopLevelTasks,
            folderNameById = uiState.folderNameById,
            selectedTaskId = uiState.selectedTaskId,
            antiMisclickMode = uiState.antiMisclickMode,
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
            onSelectTask = { task -> callbacks.onSelectTask(task.id) },
            onQuickInputChange = callbacks.onQuickInputChange,
            onQuickInputSubmit = callbacks.onQuickInputSubmit,
            onSelectFolderSuggestion = callbacks.onSelectFolderSuggestion,
            onToggleContinuousEntry = callbacks.onToggleContinuousEntry,
            onVoiceInputRecognized = callbacks.onVoiceInputRecognized,
            onUndo = callbacks.onUndo,
            onUndoTimeout = callbacks.onUndoTimeout,
            modifier = modifier
        )
    } else {
        val localizedDestinationTitle = when (val dest = uiState.selectedDestination) {
            NavigationDestination.Inbox -> stringResource(R.string.nav_inbox)
            NavigationDestination.Today -> stringResource(R.string.nav_today)
            NavigationDestination.Tomorrow -> stringResource(R.string.nav_tomorrow)
            NavigationDestination.Next7Days -> stringResource(R.string.nav_next_7_days)
            NavigationDestination.Calendar -> stringResource(R.string.nav_calendar)
            is NavigationDestination.FolderView ->
                uiState.folderNameById[dest.folderId] ?: stringResource(R.string.nav_inbox)
        }
        TaskListPane(
            title = localizedDestinationTitle,
            breadcrumbs = uiState.breadcrumbs,
            selectedDestination = uiState.selectedDestination,
            includeSubfolders = uiState.includeSubfolders,
            tasks = uiState.tasks,
            folderNameById = uiState.folderNameById,
            selectedTaskId = uiState.selectedTaskId,
            antiMisclickMode = uiState.antiMisclickMode,
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
            onRequestCreateSubfolder = { onRequestCreateFolder(uiState.breadcrumbs.lastOrNull()) },
            onOpenFocusMode = { callbacks.onStartFocus(null, FocusMode.TWO_MIN_STARTER) },
            onToggleTaskComplete = callbacks.onToggleTaskComplete,
            onSelectTask = { task -> callbacks.onSelectTask(task.id) },
            onReorderTask = callbacks.onReorderTask,
            onQuickInputChange = callbacks.onQuickInputChange,
            onQuickInputSubmit = callbacks.onQuickInputSubmit,
            onSelectFolderSuggestion = callbacks.onSelectFolderSuggestion,
            onToggleContinuousEntry = callbacks.onToggleContinuousEntry,
            onVoiceInputRecognized = callbacks.onVoiceInputRecognized,
            onUndo = callbacks.onUndo,
            onUndoTimeout = callbacks.onUndoTimeout,
            modifier = modifier
        )
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
        folderName = uiState.selectedTask?.folderId?.let { uiState.folderNameById[it] },
        subtasks = uiState.selectedTaskSubtasks,
        showBackButton = showBackButton,
        onNavigateBack = onNavigateBack,
        onToggleComplete = callbacks.onToggleTaskComplete,
        onStartFocus = { task, mode -> callbacks.onStartFocus(task, mode) },
        onUpdateTitleAndDescription = callbacks.onUpdateTaskTitleAndDescription,
        onUpdatePriority = callbacks.onUpdateTaskPriority,
        onUpdateDueDate = callbacks.onUpdateTaskDueDate,
        onUpdateRecurrenceRule = callbacks.onUpdateTaskRecurrenceRule,
        onAddSubtask = callbacks.onAddSubtask,
        onToggleSubtask = callbacks.onToggleSubtask,
        onPromoteSubtask = callbacks.onPromoteSubtask,
        onDeleteSubtask = callbacks.onDeleteSubtask,
        onAddTag = callbacks.onAddTag,
        onRemoveTag = callbacks.onRemoveTag,
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

    BackHandler(enabled = navigator.canNavigateBack() && !uiState.focusState.isActive && !uiState.isSettingsOpen) {
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
                    .width(284.dp)
                    .hairlineDividerEnd()
            ) {
                FolderTreePanel(
                    folderTree = uiState.folderTree,
                    selectedDestination = uiState.selectedDestination,
                    smartViewCounts = uiState.smartViewCounts,
                    includeSubfolders = uiState.includeSubfolders,
                    isDarkTheme = uiState.isDarkTheme,
                    activeAccent = uiState.activeAccent,
                    onSelectDestination = { destination ->
                        callbacks.onSelectDestination(destination)
                        scope.launch { drawerState.close() }
                    },
                    onToggleFolderExpanded = callbacks.onToggleFolderExpanded,
                    onRequestCreateFolder = onRequestCreateFolder,
                    onDeleteFolder = callbacks.onDeleteFolder,
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

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun MediumTwoPaneLayout(
    uiState: MainUiState,
    callbacks: ZeroShellCallbacks,
    quickInputFocusRequester: FocusRequester,
    onRequestCreateFolder: (Folder?) -> Unit,
    modifier: Modifier = Modifier
) {
    val baseDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())
    val twoPaneDirective = baseDirective.copy(
        maxHorizontalPartitions = 2,
        horizontalPartitionSpacerSize = 0.dp
    )
    val navigator = rememberListDetailPaneScaffoldNavigator<String>(
        scaffoldDirective = twoPaneDirective
    )

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        modifier = modifier,
        listPane = {
            AnimatedPane {
                Column(modifier = Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = uiState.isMediumTreeExpanded,
                        enter = expandVertically(animationSpec = ZeroMotion.IntSizeSpring) +
                            fadeIn(animationSpec = ZeroMotion.FloatSpring),
                        exit = shrinkVertically(animationSpec = ZeroMotion.IntSizeSpring) +
                            fadeOut(animationSpec = ZeroMotion.FloatSpring)
                    ) {
                        FolderTreePanel(
                            folderTree = uiState.folderTree,
                            selectedDestination = uiState.selectedDestination,
                            smartViewCounts = uiState.smartViewCounts,
                            includeSubfolders = uiState.includeSubfolders,
                            isDarkTheme = uiState.isDarkTheme,
                            activeAccent = uiState.activeAccent,
                            onSelectDestination = callbacks.onSelectDestination,
                            onToggleFolderExpanded = callbacks.onToggleFolderExpanded,
                            onRequestCreateFolder = onRequestCreateFolder,
                            onDeleteFolder = callbacks.onDeleteFolder,
                            onToggleIncludeSubfolders = callbacks.onToggleIncludeSubfolders,
                            onToggleTheme = callbacks.onToggleTheme,
                            onToggleAccent = callbacks.onToggleAccent,
                            onOpenSettings = callbacks.onOpenSettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp, max = 260.dp)
                                .hairlineDividerBottom()
                        )
                    }

                    CenterWorkspacePane(
                        uiState = uiState,
                        callbacks = callbacks,
                        quickInputFocusRequester = quickInputFocusRequester,
                        showDrawerButton = false,
                        showTreeToggleButton = true,
                        onOpenDrawer = {},
                        onRequestCreateFolder = onRequestCreateFolder,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        detailPane = {
            AnimatedPane {
                BoundTaskDetailPane(
                    uiState = uiState,
                    callbacks = callbacks,
                    showBackButton = false,
                    onNavigateBack = {},
                    modifier = Modifier.hairlineDividerStart()
                )
            }
        }
    )
}

@Composable
private fun ExpandedThreePaneLayout(
    uiState: MainUiState,
    callbacks: ZeroShellCallbacks,
    quickInputFocusRequester: FocusRequester,
    onRequestCreateFolder: (Folder?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxSize()) {
        FolderTreePanel(
            folderTree = uiState.folderTree,
            selectedDestination = uiState.selectedDestination,
            smartViewCounts = uiState.smartViewCounts,
            includeSubfolders = uiState.includeSubfolders,
            isDarkTheme = uiState.isDarkTheme,
            activeAccent = uiState.activeAccent,
            onSelectDestination = callbacks.onSelectDestination,
            onToggleFolderExpanded = callbacks.onToggleFolderExpanded,
            onRequestCreateFolder = onRequestCreateFolder,
            onDeleteFolder = callbacks.onDeleteFolder,
            onToggleIncludeSubfolders = callbacks.onToggleIncludeSubfolders,
            onToggleTheme = callbacks.onToggleTheme,
            onToggleAccent = callbacks.onToggleAccent,
            onOpenSettings = callbacks.onOpenSettings,
            modifier = Modifier
                .width(260.dp)
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
        recurrenceRule = "FREQ=WEEKLY;BYDAY=MO,WE,FR"
    )
    val t2 = TaskItem(
        id = "t2",
        folderId = "f3",
        title = "提交 11 月差旅机票报销单",
        priority = TaskPriority.HIGH
    )
    val sampleInput = "明天下午3点 /工作/财务/报销 提交发票 !1 #财务"
    return MainUiState(
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
        smartViewCounts = SmartViewCounts(inbox = 3, today = 2, tomorrow = 1, next7Days = 5),
        tasks = listOf(t1, t2),
        allActiveTopLevelTasks = listOf(t1, t2),
        selectedTaskId = t1.id,
        selectedTask = t1,
        quickInputText = sampleInput,
        parsedQuickInput = QuickInputParser.parse(sampleInput)
    )
}

@Preview(name = "Compact Phone Dark", widthDp = 390, heightDp = 844)
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

@Preview(name = "Medium Foldable Dark", widthDp = 700, heightDp = 840)
@Composable
private fun PreviewZeroMedium() {
    ZeroTheme(darkTheme = true) {
        ZeroAdaptiveShell(
            uiState = remember { samplePreviewState() },
            callbacks = ZeroShellCallbacks(),
            windowWidthSizeClass = WindowWidthSizeClass.MEDIUM
        )
    }
}

@Preview(name = "Expanded Tablet Dark", widthDp = 1100, heightDp = 760)
@Composable
private fun PreviewZeroExpanded() {
    ZeroTheme(darkTheme = true) {
        ZeroAdaptiveShell(
            uiState = remember { samplePreviewState() },
            callbacks = ZeroShellCallbacks(),
            windowWidthSizeClass = WindowWidthSizeClass.EXPANDED
        )
    }
}
