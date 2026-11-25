package com.zero.todo.feature.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zero.todo.core.common.RecurrenceRule
import com.zero.todo.core.common.ZeroDateFormatter
import com.zero.todo.core.designsystem.ZeroAccent
import com.zero.todo.core.nlp.ParsedQuickInput
import com.zero.todo.core.nlp.QuickInputParser
import com.zero.todo.data.backup.ZeroBackupManager
import com.zero.todo.data.preferences.UserPreferences
import com.zero.todo.data.preferences.UserPreferencesRepository
import com.zero.todo.data.repository.FocusRepository
import com.zero.todo.data.repository.FolderRepository
import com.zero.todo.data.repository.TagRepository
import com.zero.todo.data.repository.TaskRepository
import com.zero.todo.domain.model.AntiMisclickMode
import com.zero.todo.domain.model.AppLanguage
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.domain.model.Folder
import com.zero.todo.domain.model.FolderNode
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.TaskPriority
import com.zero.todo.domain.usecase.GetFolderTreeUseCase
import com.zero.todo.feature.calendar.CalendarViewMode
import com.zero.todo.feature.focus.FocusTimerController
import com.zero.todo.feature.focus.FocusUiState
import com.zero.todo.feature.folder.NavigationDestination
import com.zero.todo.feature.folder.SmartViewCounts
import com.zero.todo.feature.settings.BackupUiStatus
import com.zero.todo.feature.tasklist.FolderPathSuggestion
import com.zero.todo.feature.tasklist.UndoBannerState
import com.zero.todo.feature.tasklist.UndoType
import com.zero.todo.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class MainUiState(
    val selectedDestination: NavigationDestination = NavigationDestination.Inbox,
    val destinationTitle: String = "收集箱",
    val breadcrumbs: List<Folder> = emptyList(),
    val includeSubfolders: Boolean = false,
    val folderTree: List<FolderNode> = emptyList(),
    val folderNameById: Map<String, String> = emptyMap(),
    val smartViewCounts: SmartViewCounts = SmartViewCounts(),
    val tasks: List<TaskItem> = emptyList(),
    val allActiveTopLevelTasks: List<TaskItem> = emptyList(),
    val calendarSelectedDate: LocalDate = LocalDate.now(),
    val calendarViewMode: CalendarViewMode = CalendarViewMode.MONTH,
    val selectedTaskId: String? = null,
    val selectedTask: TaskItem? = null,
    val selectedTaskSubtasks: List<TaskItem> = emptyList(),
    val quickInputText: String = "",
    val parsedQuickInput: ParsedQuickInput = ParsedQuickInput(rawInput = "", title = ""),
    val folderSuggestions: List<FolderPathSuggestion> = emptyList(),
    val continuousEntryEnabled: Boolean = true,
    val undoBanner: UndoBannerState? = null,
    val isDarkTheme: Boolean = true,
    val activeAccent: ZeroAccent = ZeroAccent.SlateBlue,
    val antiMisclickMode: AntiMisclickMode = AntiMisclickMode.BOTH,
    val hapticsEnabled: Boolean = true,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val isMediumTreeExpanded: Boolean = true,
    val isSettingsOpen: Boolean = false,
    val backupStatus: BackupUiStatus? = null,
    val focusState: FocusUiState = FocusUiState(),
    val quickInputFocusRequestTick: Long = 0L
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val folderRepository: FolderRepository,
    private val taskRepository: TaskRepository,
    private val tagRepository: TagRepository,
    private val focusRepository: FocusRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val backupManager: ZeroBackupManager,
    private val widgetRefresher: WidgetRefresher,
    getFolderTreeUseCase: GetFolderTreeUseCase
) : ViewModel() {

    private val focusController = FocusTimerController(
        scope = viewModelScope,
        focusRepository = focusRepository
    )

    private val destinationKeyFlow: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_DESTINATION, NavigationDestination.Inbox.toStorageKey())

    private val selectedDestinationFlow: Flow<NavigationDestination> =
        destinationKeyFlow.map { NavigationDestination.fromStorageKey(it) }

    private val selectedTaskIdFlow: StateFlow<String?> =
        savedStateHandle.getStateFlow(KEY_SELECTED_TASK_ID, null)

    private val includeSubfoldersFlow: StateFlow<Boolean> =
        savedStateHandle.getStateFlow(KEY_INCLUDE_SUBFOLDERS, false)

    private val quickInputTextFlow: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_QUICK_INPUT, "")

    private val continuousEntryFlow: StateFlow<Boolean> =
        savedStateHandle.getStateFlow(KEY_CONTINUOUS_ENTRY, true)

    private val mediumTreeExpandedFlow: StateFlow<Boolean> =
        savedStateHandle.getStateFlow(KEY_MEDIUM_TREE_EXPANDED, true)

    private val calendarDateIsoFlow: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_CALENDAR_DATE, LocalDate.now().toString())

    private val calendarViewModeNameFlow: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_CALENDAR_VIEW_MODE, CalendarViewMode.MONTH.name)

    private val settingsOpenFlow: StateFlow<Boolean> =
        savedStateHandle.getStateFlow(KEY_SETTINGS_OPEN, false)

    private val undoBannerFlow = MutableStateFlow<UndoBannerState?>(null)
    private val backupStatusFlow = MutableStateFlow<BackupUiStatus?>(null)
    private val quickInputFocusRequestTickFlow = MutableStateFlow(0L)

    private val folderTreeFlow = getFolderTreeUseCase()
    private val activeFoldersFlow = folderRepository.observeActiveFolders()
    private val allTopLevelTasksFlow = taskRepository.observeAllActiveTopLevelTasks()

    init {
        viewModelScope.launch {
            val today = LocalDate.now()
            focusRepository.observeTotalFocusSecondsBetween(
                startMillis = ZeroDateFormatter.startOfDayMillis(today),
                endMillis = ZeroDateFormatter.endOfDayExclusiveMillis(today)
            ).collect { totalSec ->
                focusController.updateTodayFocusSeconds(totalSec)
            }
        }
    }

    private val currentTasksFlow: Flow<List<TaskItem>> =
        combine(selectedDestinationFlow, includeSubfoldersFlow) { destination, includeSubs ->
            destination to includeSubs
        }.flatMapLatest { (destination, includeSubs) ->
            val today = LocalDate.now()
            val todayStart = ZeroDateFormatter.startOfDayMillis(today)
            val tomorrowStart = ZeroDateFormatter.startOfDayMillis(today.plusDays(1))
            val dayAfterStart = ZeroDateFormatter.startOfDayMillis(today.plusDays(2))
            val next7End = ZeroDateFormatter.endOfDayExclusiveMillis(today.plusDays(6))

            when (destination) {
                NavigationDestination.Inbox -> taskRepository.observeInboxTasks()
                NavigationDestination.Today -> taskRepository.observeTasksDueBetween(todayStart, tomorrowStart)
                NavigationDestination.Tomorrow -> taskRepository.observeTasksDueBetween(tomorrowStart, dayAfterStart)
                NavigationDestination.Next7Days -> taskRepository.observeTasksDueBetween(todayStart, next7End)
                NavigationDestination.Calendar -> allTopLevelTasksFlow
                is NavigationDestination.FolderView -> taskRepository.observeTasksByFolder(
                    folderId = destination.folderId,
                    includeSubfolders = includeSubs
                )
            }
        }

    private val selectedTaskFlow: Flow<TaskItem?> = selectedTaskIdFlow.flatMapLatest { taskId ->
        if (taskId == null) flowOf(null) else taskRepository.observeTaskWithTags(taskId)
    }

    private val selectedSubtasksFlow: Flow<List<TaskItem>> = selectedTaskIdFlow.flatMapLatest { taskId ->
        if (taskId == null) flowOf(emptyList()) else taskRepository.observeDescendantSubtasks(taskId)
    }

    private val smartViewCountsFlow: Flow<SmartViewCounts> = allTopLevelTasksFlow.map { tasks ->
        val today = LocalDate.now()
        val todayStart = ZeroDateFormatter.startOfDayMillis(today)
        val tomorrowStart = ZeroDateFormatter.startOfDayMillis(today.plusDays(1))
        val dayAfterStart = ZeroDateFormatter.startOfDayMillis(today.plusDays(2))
        val next7End = ZeroDateFormatter.endOfDayExclusiveMillis(today.plusDays(6))

        var inbox = 0
        var todayCount = 0
        var tomorrowCount = 0
        var next7Count = 0
        for (task in tasks) {
            if (task.isCompleted) continue
            if (task.folderId == null) inbox++
            val due = task.dueDate ?: continue
            if (due in todayStart until tomorrowStart) todayCount++
            if (due in tomorrowStart until dayAfterStart) tomorrowCount++
            if (due in todayStart until next7End) next7Count++
        }
        SmartViewCounts(
            inbox = inbox,
            today = todayCount,
            tomorrow = tomorrowCount,
            next7Days = next7Count
        )
    }

    private data class NavigationBundle(
        val destination: NavigationDestination,
        val includeSubfolders: Boolean,
        val folderTree: List<FolderNode>,
        val activeFolders: List<Folder>,
        val smartViewCounts: SmartViewCounts,
        val calendarSelectedDate: LocalDate,
        val calendarViewMode: CalendarViewMode
    )

    private data class InputAndUiBundle(
        val quickInputText: String,
        val continuousEntryEnabled: Boolean,
        val undoBanner: UndoBannerState?,
        val preferences: UserPreferences,
        val isMediumTreeExpanded: Boolean,
        val isSettingsOpen: Boolean,
        val backupStatus: BackupUiStatus?,
        val focusState: FocusUiState,
        val quickInputFocusRequestTick: Long
    )

    private val navigationBundleFlow: Flow<NavigationBundle> = combine(
        selectedDestinationFlow,
        includeSubfoldersFlow,
        folderTreeFlow,
        activeFoldersFlow,
        smartViewCountsFlow,
        calendarDateIsoFlow,
        calendarViewModeNameFlow
    ) { values ->
        val destination = values[0] as NavigationDestination
        val includeSubfolders = values[1] as Boolean
        @Suppress("UNCHECKED_CAST")
        val tree = values[2] as List<FolderNode>
        @Suppress("UNCHECKED_CAST")
        val folders = values[3] as List<Folder>
        val counts = values[4] as SmartViewCounts
        val dateIso = values[5] as String
        val modeName = values[6] as String
        val parsedDate = runCatching { LocalDate.parse(dateIso) }.getOrDefault(LocalDate.now())
        val viewMode = CalendarViewMode.entries.firstOrNull { it.name == modeName } ?: CalendarViewMode.MONTH

        NavigationBundle(destination, includeSubfolders, tree, folders, counts, parsedDate, viewMode)
    }

    private val inputAndUiBundleFlow: Flow<InputAndUiBundle> = combine(
        quickInputTextFlow,
        continuousEntryFlow,
        undoBannerFlow,
        userPreferencesRepository.preferences,
        mediumTreeExpandedFlow,
        settingsOpenFlow,
        backupStatusFlow,
        focusController.state,
        quickInputFocusRequestTickFlow
    ) { values ->
        val input = values[0] as String
        val continuous = values[1] as Boolean
        val banner = values[2] as UndoBannerState?
        val prefs = values[3] as UserPreferences
        val mediumExpanded = values[4] as Boolean
        val settingsOpen = values[5] as Boolean
        val backupStatus = values[6] as BackupUiStatus?
        val focusState = values[7] as FocusUiState
        val focusTick = values[8] as Long
        InputAndUiBundle(
            quickInputText = input,
            continuousEntryEnabled = continuous,
            undoBanner = banner,
            preferences = prefs,
            isMediumTreeExpanded = mediumExpanded,
            isSettingsOpen = settingsOpen,
            backupStatus = backupStatus,
            focusState = focusState,
            quickInputFocusRequestTick = focusTick
        )
    }

    val uiState: StateFlow<MainUiState> = combine(
        navigationBundleFlow,
        currentTasksFlow,
        allTopLevelTasksFlow,
        selectedTaskIdFlow,
        selectedTaskFlow,
        selectedSubtasksFlow,
        inputAndUiBundleFlow
    ) { values ->
        val nav = values[0] as NavigationBundle
        @Suppress("UNCHECKED_CAST")
        val tasks = values[1] as List<TaskItem>
        @Suppress("UNCHECKED_CAST")
        val allTasks = values[2] as List<TaskItem>
        val selectedTaskId = values[3] as String?
        val selectedTask = values[4] as TaskItem?
        @Suppress("UNCHECKED_CAST")
        val subtasks = values[5] as List<TaskItem>
        val ui = values[6] as InputAndUiBundle

        val folderById = nav.activeFolders.associateBy { it.id }
        val folderNameById = nav.activeFolders.associate { it.id to it.name }

        val breadcrumbs = when (val dest = nav.destination) {
            is NavigationDestination.FolderView -> buildBreadcrumbs(dest.folderId, folderById)
            else -> emptyList()
        }

        val destinationTitle = when (val dest = nav.destination) {
            NavigationDestination.Inbox -> "收集箱"
            NavigationDestination.Today -> "今天"
            NavigationDestination.Tomorrow -> "明天"
            NavigationDestination.Next7Days -> "最近 7 天"
            NavigationDestination.Calendar -> "日历"
            is NavigationDestination.FolderView -> folderById[dest.folderId]?.name ?: "收集箱"
        }

        val parsed = QuickInputParser.parse(ui.quickInputText)
        val suggestions = buildFolderSuggestions(nav.folderTree, parsed.activeFolderQuery)

        MainUiState(
            selectedDestination = nav.destination,
            destinationTitle = destinationTitle,
            breadcrumbs = breadcrumbs,
            includeSubfolders = nav.includeSubfolders,
            folderTree = nav.folderTree,
            folderNameById = folderNameById,
            smartViewCounts = nav.smartViewCounts,
            tasks = tasks,
            allActiveTopLevelTasks = allTasks,
            calendarSelectedDate = nav.calendarSelectedDate,
            calendarViewMode = nav.calendarViewMode,
            selectedTaskId = selectedTaskId,
            selectedTask = selectedTask,
            selectedTaskSubtasks = subtasks,
            quickInputText = ui.quickInputText,
            parsedQuickInput = parsed,
            folderSuggestions = suggestions,
            continuousEntryEnabled = ui.continuousEntryEnabled,
            undoBanner = ui.undoBanner,
            isDarkTheme = ui.preferences.isDarkTheme,
            activeAccent = ui.preferences.accent,
            antiMisclickMode = ui.preferences.antiMisclickMode,
            hapticsEnabled = ui.preferences.hapticsEnabled,
            appLanguage = ui.preferences.appLanguage,
            isMediumTreeExpanded = ui.isMediumTreeExpanded,
            isSettingsOpen = ui.isSettingsOpen,
            backupStatus = ui.backupStatus,
            focusState = ui.focusState,
            quickInputFocusRequestTick = ui.quickInputFocusRequestTick
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = MainUiState()
    )

    fun handleWidgetLaunch(destinationKey: String?, taskId: String?, launchQuickInput: Boolean) {
        if (!destinationKey.isNullOrBlank()) {
            selectDestination(NavigationDestination.fromStorageKey(destinationKey))
        }
        if (!taskId.isNullOrBlank()) {
            selectTask(taskId)
        }
        if (launchQuickInput) {
            quickInputFocusRequestTickFlow.value = System.currentTimeMillis()
        }
    }

    fun selectDestination(destination: NavigationDestination) {
        savedStateHandle[KEY_DESTINATION] = destination.toStorageKey()
    }

    fun selectCalendarDate(date: LocalDate) {
        savedStateHandle[KEY_CALENDAR_DATE] = date.toString()
    }

    fun selectCalendarViewMode(mode: CalendarViewMode) {
        savedStateHandle[KEY_CALENDAR_VIEW_MODE] = mode.name
    }

    fun selectTask(taskId: String?) {
        savedStateHandle[KEY_SELECTED_TASK_ID] = taskId
    }

    fun toggleIncludeSubfolders() {
        savedStateHandle[KEY_INCLUDE_SUBFOLDERS] = !includeSubfoldersFlow.value
    }

    fun toggleMediumTreeExpanded() {
        savedStateHandle[KEY_MEDIUM_TREE_EXPANDED] = !mediumTreeExpandedFlow.value
    }

    fun openSettings() {
        backupStatusFlow.value = null
        savedStateHandle[KEY_SETTINGS_OPEN] = true
    }

    fun closeSettings() {
        savedStateHandle[KEY_SETTINGS_OPEN] = false
    }

    fun setDarkTheme(isDark: Boolean) {
        userPreferencesRepository.setDarkTheme(isDark)
    }

    fun toggleTheme() {
        userPreferencesRepository.setDarkTheme(!userPreferencesRepository.preferences.value.isDarkTheme)
    }

    fun setAccent(accent: ZeroAccent) {
        userPreferencesRepository.setAccent(accent)
    }

    fun toggleAccent() {
        val current = userPreferencesRepository.preferences.value.accent
        userPreferencesRepository.setAccent(current.next())
    }

    fun setAntiMisclickMode(mode: AntiMisclickMode) {
        userPreferencesRepository.setAntiMisclickMode(mode)
    }

    fun setHapticsEnabled(enabled: Boolean) {
        userPreferencesRepository.setHapticsEnabled(enabled)
    }

    fun setAppLanguage(language: AppLanguage) {
        userPreferencesRepository.setAppLanguage(language)
        viewModelScope.launch {
            widgetRefresher.refreshAll()
        }
    }

    suspend fun generateJsonBackup(): String = backupManager.exportJson()

    suspend fun generateMarkdownExport(): String = backupManager.exportMarkdown()

    fun reportBackupStatus(status: BackupUiStatus) {
        backupStatusFlow.value = status
    }

    fun importJsonBackup(jsonPayload: String) {
        viewModelScope.launch {
            runCatching {
                backupManager.importJson(jsonPayload, replaceExisting = true)
            }.onSuccess { summary ->
                selectTask(null)
                selectDestination(NavigationDestination.Inbox)
                backupStatusFlow.value = BackupUiStatus.ImportSuccess(summary)
                widgetRefresher.refreshAll()
            }.onFailure { err ->
                backupStatusFlow.value = BackupUiStatus.Error(err.message ?: "Invalid JSON")
            }
        }
    }

    fun updateQuickInputText(text: String) {
        savedStateHandle[KEY_QUICK_INPUT] = text
    }

    fun toggleContinuousEntry() {
        savedStateHandle[KEY_CONTINUOUS_ENTRY] = !continuousEntryFlow.value
    }

    fun applyFolderSuggestion(suggestion: FolderPathSuggestion) {
        val current = quickInputTextFlow.value
        val replaced = current.replace(Regex("""(^|\s)/[^\s!！#]*$""")) { match ->
            "${match.groupValues[1]}/${suggestion.fullPath} "
        }
        savedStateHandle[KEY_QUICK_INPUT] = replaced
    }

    fun submitQuickInput() {
        val raw = quickInputTextFlow.value.trim()
        if (raw.isEmpty()) return
        savedStateHandle[KEY_QUICK_INPUT] = ""
        viewModelScope.launch {
            createStructuredTaskFromText(raw)
            widgetRefresher.refreshAll()
        }
    }

    fun submitVoiceInput(spokenText: String) {
        val trimmed = spokenText.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            createStructuredTaskFromText(trimmed)
            widgetRefresher.refreshAll()
        }
    }

    fun createFolder(name: String, parentId: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val created = folderRepository.createFolder(name = trimmed, parentId = parentId)
            if (parentId != null) {
                folderRepository.setFolderExpanded(parentId, true)
            }
            selectDestination(NavigationDestination.FolderView(created.id))
        }
    }

    fun setFolderExpanded(folderId: String, isExpanded: Boolean) {
        viewModelScope.launch {
            folderRepository.setFolderExpanded(folderId, isExpanded)
        }
    }

    fun renameFolder(folderId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            folderRepository.renameFolder(folderId, trimmed)
        }
    }

    fun moveTaskToFolder(taskId: String, targetFolderId: String?) {
        viewModelScope.launch {
            taskRepository.moveTaskToFolder(taskId, targetFolderId)
            widgetRefresher.refreshAll()
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            folderRepository.softDeleteFolder(folderId, deleteContainedTasks = false)
            val currentDest = NavigationDestination.fromStorageKey(destinationKeyFlow.value)
            if (currentDest is NavigationDestination.FolderView && currentDest.folderId == folderId) {
                selectDestination(NavigationDestination.Inbox)
            }
            widgetRefresher.refreshAll()
        }
    }

    fun toggleTaskCompletion(task: TaskItem) {
        viewModelScope.launch {
            val newCompleted = !task.isCompleted
            taskRepository.setTaskCompleted(task.id, newCompleted)

            if (newCompleted && task.recurrenceRule != null) {
                val parsedRule = RecurrenceRule.parse(task.recurrenceRule)
                if (parsedRule != null) {
                    val anchorMillis = task.dueDate
                        ?: ZeroDateFormatter.startOfDayMillis(LocalDate.now())
                    val nextDueMillis = parsedRule.nextOccurrenceMillis(anchorMillis)
                    taskRepository.createTask(
                        title = task.title,
                        folderId = task.folderId,
                        description = task.description,
                        priority = task.priority,
                        dueDate = nextDueMillis,
                        isAllDay = task.isAllDay,
                        recurrenceRule = task.recurrenceRule
                    )
                }
            }

            focusController.syncFocusedTask(task.copy(isCompleted = newCompleted))
            undoBannerFlow.value = UndoBannerState(
                taskId = task.id,
                taskTitle = task.title,
                undoType = if (newCompleted) UndoType.REVERT_COMPLETE else UndoType.REVERT_UNCOMPLETE
            )
            widgetRefresher.refreshAll()
        }
    }

    fun reorderTask(taskId: String, targetIndex: Int) {
        val dest = NavigationDestination.fromStorageKey(destinationKeyFlow.value)
        val targetFolderId = when (dest) {
            NavigationDestination.Inbox -> null
            is NavigationDestination.FolderView -> if (!includeSubfoldersFlow.value) dest.folderId else return
            else -> return
        }
        viewModelScope.launch {
            taskRepository.moveTaskToIndex(
                taskId = taskId,
                targetFolderId = targetFolderId,
                targetParentTaskId = null,
                targetIndex = targetIndex
            )
        }
    }

    fun updateTaskTitleAndDescription(task: TaskItem, title: String, description: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            taskRepository.updateTask(task.copy(title = title, description = description))
            widgetRefresher.refreshAll()
        }
    }

    fun updateTaskPriority(task: TaskItem, priority: TaskPriority) {
        viewModelScope.launch {
            taskRepository.updateTask(task.copy(priority = priority))
            widgetRefresher.refreshAll()
        }
    }

    fun updateTaskDueDate(task: TaskItem, dueDateMillis: Long?, isAllDay: Boolean) {
        viewModelScope.launch {
            taskRepository.updateTask(task.copy(dueDate = dueDateMillis, isAllDay = isAllDay))
            widgetRefresher.refreshAll()
        }
    }

    fun updateTaskRecurrenceRule(task: TaskItem, rrule: String?) {
        viewModelScope.launch {
            taskRepository.updateTask(task.copy(recurrenceRule = rrule))
        }
    }

    fun addSubtask(parentTaskId: String, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            taskRepository.createTask(
                title = title,
                folderId = null,
                parentTaskId = parentTaskId
            )
        }
    }

    fun toggleSubtaskCompletion(subtask: TaskItem) {
        viewModelScope.launch {
            taskRepository.setTaskCompleted(subtask.id, !subtask.isCompleted)
        }
    }

    fun promoteSubtask(subtaskId: String) {
        viewModelScope.launch {
            val promoted = taskRepository.promoteSubtaskToTopLevel(subtaskId)
            if (promoted != null) {
                selectTask(promoted.id)
                widgetRefresher.refreshAll()
            }
        }
    }

    fun deleteSubtask(subtask: TaskItem) {
        viewModelScope.launch {
            taskRepository.softDeleteTask(subtask.id)
        }
    }

    fun addTagToTask(taskId: String, tagName: String) {
        val clean = tagName.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val tag = tagRepository.getOrCreateTag(clean)
            tagRepository.attachTagToTask(taskId, tag.id)
        }
    }

    fun removeTagFromTask(taskId: String, tagId: String) {
        viewModelScope.launch {
            tagRepository.detachTagFromTask(taskId, tagId)
        }
    }

    fun deleteTask(task: TaskItem) {
        viewModelScope.launch {
            taskRepository.softDeleteTask(task.id)
            if (selectedTaskIdFlow.value == task.id) {
                selectTask(null)
            }
            undoBannerFlow.value = UndoBannerState(
                taskId = task.id,
                taskTitle = task.title,
                undoType = UndoType.RESTORE_DELETED_TASK
            )
            widgetRefresher.refreshAll()
        }
    }

    fun performUndo(banner: UndoBannerState) {
        undoBannerFlow.value = null
        viewModelScope.launch {
            when (banner.undoType) {
                UndoType.REVERT_COMPLETE -> taskRepository.setTaskCompleted(banner.taskId, false)
                UndoType.REVERT_UNCOMPLETE -> taskRepository.setTaskCompleted(banner.taskId, true)
                UndoType.RESTORE_DELETED_TASK -> taskRepository.restoreTask(banner.taskId)
            }
            widgetRefresher.refreshAll()
        }
    }

    fun dismissUndoBanner(bannerId: Long) {
        if (undoBannerFlow.value?.id == bannerId) {
            undoBannerFlow.value = null
        }
    }

    // Focus System intents
    fun openFocusForTask(task: TaskItem?, initialMode: FocusMode = FocusMode.TWO_MIN_STARTER) {
        val target = task
            ?: uiState.value.selectedTask
            ?: uiState.value.tasks.firstOrNull { !it.isCompleted }
        focusController.openFocus(task = target, initialMode = initialMode, autoStart = true)
    }

    fun selectFocusMode(mode: FocusMode) = focusController.selectMode(mode)
    fun startOrResumeFocus() = focusController.startOrResume()
    fun pauseFocus() = focusController.pause()
    fun resetFocus() = focusController.reset()
    fun continueFocusToPomodoro() = focusController.continueToPomodoro()
    fun stopStopwatchFocus() = focusController.stopAndRecordStopwatchIfElapsed()
    fun exitFocus() = focusController.exitFocus()

    fun completeFocusedTask(task: TaskItem) {
        toggleTaskCompletion(task)
        focusController.exitFocus()
    }

    private suspend fun createStructuredTaskFromText(raw: String) {
        val parsed = QuickInputParser.parse(raw)
        val currentDest = NavigationDestination.fromStorageKey(destinationKeyFlow.value)

        val resolvedFolderId = if (parsed.folderPath != null) {
            resolveOrCreateFolderPath(parsed.folderPath)
        } else if (currentDest is NavigationDestination.FolderView) {
            currentDest.folderId
        } else {
            null
        }

        val resolvedDueDate = parsed.dueDateMillis ?: when (currentDest) {
            NavigationDestination.Today -> ZeroDateFormatter.startOfDayMillis(LocalDate.now())
            NavigationDestination.Tomorrow -> ZeroDateFormatter.startOfDayMillis(LocalDate.now().plusDays(1))
            NavigationDestination.Calendar -> ZeroDateFormatter.startOfDayMillis(uiState.value.calendarSelectedDate)
            else -> null
        }
        val resolvedAllDay = if (parsed.dueDateMillis != null) parsed.isAllDay else true
        val resolvedTitle = parsed.title.ifBlank { raw }

        val created = taskRepository.createTask(
            title = resolvedTitle,
            folderId = resolvedFolderId,
            priority = parsed.priority,
            dueDate = resolvedDueDate,
            isAllDay = resolvedAllDay
        )

        for (tagName in parsed.tags) {
            val tag = tagRepository.getOrCreateTag(tagName)
            tagRepository.attachTagToTask(created.id, tag.id)
        }
    }

    private suspend fun resolveOrCreateFolderPath(segments: List<String>): String? {
        var parentId: String? = null
        var allFolders = uiState.value.folderTree.flattenToFolderList()
        for (segment in segments) {
            val existing = allFolders.firstOrNull {
                it.parentId == parentId && it.name.equals(segment, ignoreCase = true)
            }
            parentId = if (existing != null) {
                existing.id
            } else {
                val created = folderRepository.createFolder(name = segment, parentId = parentId)
                allFolders = allFolders + created
                created.id
            }
        }
        return parentId
    }

    private fun buildBreadcrumbs(folderId: String, folderById: Map<String, Folder>): List<Folder> {
        val chain = mutableListOf<Folder>()
        val visited = HashSet<String>()
        var current = folderById[folderId]
        while (current != null && visited.add(current.id)) {
            chain.add(current)
            current = current.parentId?.let { folderById[it] }
        }
        return chain.reversed()
    }

    private fun buildFolderSuggestions(
        roots: List<FolderNode>,
        activeQuery: String?
    ): List<FolderPathSuggestion> {
        if (activeQuery == null) return emptyList()
        val allPaths = mutableListOf<FolderPathSuggestion>()
        fun walk(nodes: List<FolderNode>, prefix: String) {
            for (node in nodes) {
                val full = if (prefix.isEmpty()) node.folder.name else "$prefix/${node.folder.name}"
                allPaths.add(FolderPathSuggestion(folderId = node.folder.id, fullPath = full))
                if (node.children.isNotEmpty()) {
                    walk(node.children, full)
                }
            }
        }
        walk(roots, "")
        val query = activeQuery.trim()
        return if (query.isEmpty()) {
            allPaths.take(8)
        } else {
            allPaths.filter { it.fullPath.contains(query, ignoreCase = true) }.take(8)
        }
    }

    private fun List<FolderNode>.flattenToFolderList(): List<Folder> {
        val out = mutableListOf<Folder>()
        fun visit(nodes: List<FolderNode>) {
            for (n in nodes) {
                out.add(n.folder)
                visit(n.children)
            }
        }
        visit(this)
        return out
    }

    companion object {
        internal const val KEY_DESTINATION = "selected_destination"
        internal const val KEY_SELECTED_TASK_ID = "selected_task_id"
        internal const val KEY_INCLUDE_SUBFOLDERS = "include_subfolders"
        internal const val KEY_QUICK_INPUT = "quick_input_text"
        internal const val KEY_CONTINUOUS_ENTRY = "continuous_entry"
        internal const val KEY_MEDIUM_TREE_EXPANDED = "medium_tree_expanded"
        internal const val KEY_CALENDAR_DATE = "calendar_selected_date"
        internal const val KEY_CALENDAR_VIEW_MODE = "calendar_view_mode"
        internal const val KEY_SETTINGS_OPEN = "settings_open"
    }
}
