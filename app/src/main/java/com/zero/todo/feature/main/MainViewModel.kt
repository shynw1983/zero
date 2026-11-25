package com.zero.todo.feature.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zero.todo.core.common.RecurrenceRule
import com.zero.todo.core.common.ZeroDateFormatter
import com.zero.todo.core.designsystem.ZeroAccent
import com.zero.todo.core.nlp.ParsedQuickInput
import com.zero.todo.core.nlp.QuickInputParser
import com.zero.todo.core.notification.ReminderScheduler
import com.zero.todo.data.backup.WebDavConfig
import com.zero.todo.data.backup.WebDavSyncManager
import com.zero.todo.data.backup.ZeroBackupManager
import com.zero.todo.data.preferences.UserPreferences
import com.zero.todo.data.preferences.UserPreferencesRepository
import com.zero.todo.data.repository.FocusRepository
import com.zero.todo.data.repository.FolderRepository
import com.zero.todo.data.repository.TagRepository
import com.zero.todo.data.repository.TagWithCount
import com.zero.todo.data.repository.TaskRepository
import com.zero.todo.domain.model.AntiMisclickMode
import com.zero.todo.domain.model.AppLanguage
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.domain.model.Folder
import com.zero.todo.domain.model.FolderNode
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.TaskPriority
import com.zero.todo.domain.model.TaskSortMode
import com.zero.todo.domain.usecase.GetFolderTreeUseCase
import com.zero.todo.feature.calendar.CalendarViewMode
import com.zero.todo.feature.focus.FocusTimerController
import com.zero.todo.feature.focus.FocusUiState
import com.zero.todo.feature.folder.NavigationDestination
import com.zero.todo.feature.folder.SmartViewCounts
import com.zero.todo.feature.settings.BackupUiStatus
import com.zero.todo.feature.settings.WebDavUiStatus
import com.zero.todo.feature.tasklist.FolderPathSuggestion
import com.zero.todo.feature.tasklist.UndoBannerState
import com.zero.todo.feature.tasklist.UndoType
import com.zero.todo.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
import java.time.ZoneId
import javax.inject.Inject

data class MainUiState(
    val currentDate: LocalDate = LocalDate.now(),
    val selectedDestination: NavigationDestination = NavigationDestination.Inbox,
    val destinationTitle: String = "收集箱",
    val breadcrumbs: List<Folder> = emptyList(),
    val includeSubfolders: Boolean = false,
    val folderTree: List<FolderNode> = emptyList(),
    val folderNameById: Map<String, String> = emptyMap(),
    val tagsWithCounts: List<TagWithCount> = emptyList(),
    val tagNameById: Map<String, String> = emptyMap(),
    val smartViewCounts: SmartViewCounts = SmartViewCounts(),
    val tasks: List<TaskItem> = emptyList(),
    val allActiveTopLevelTasks: List<TaskItem> = emptyList(),
    val deletedTasks: List<TaskItem> = emptyList(),
    val searchQuery: String = "",
    val isSearchOpen: Boolean = false,
    val taskSortMode: TaskSortMode = TaskSortMode.MANUAL,
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
    val webDavConfig: WebDavConfig = WebDavConfig(),
    val webDavStatus: WebDavUiStatus? = null,
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
    private val webDavSyncManager: WebDavSyncManager,
    private val reminderScheduler: ReminderScheduler,
    private val widgetRefresher: WidgetRefresher,
    getFolderTreeUseCase: GetFolderTreeUseCase
) : ViewModel() {

    private val focusController = FocusTimerController(
        scope = viewModelScope,
        focusRepository = focusRepository,
        reminderScheduler = reminderScheduler
    )

    private val currentDateFlow = MutableStateFlow(LocalDate.now())

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

    private val searchQueryFlow: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_SEARCH_QUERY, "")

    private val searchOpenFlow: StateFlow<Boolean> =
        savedStateHandle.getStateFlow(KEY_SEARCH_OPEN, false)

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
    private val webDavStatusFlow = MutableStateFlow<WebDavUiStatus?>(null)
    private val quickInputFocusRequestTickFlow = MutableStateFlow(0L)

    private val folderTreeFlow = getFolderTreeUseCase()
    private val activeFoldersFlow = folderRepository.observeActiveFolders()
    private val tagsWithCountsFlow = tagRepository.observeActiveTagsWithCounts()
    private val allTopLevelTasksFlow = taskRepository.observeAllActiveTopLevelTasks()
    private val deletedTasksFlow = taskRepository.observeDeletedTasks()

    init {
        // Keep today's focus total synced even when crossing midnight
        viewModelScope.launch {
            currentDateFlow.flatMapLatest { today ->
                focusRepository.observeTotalFocusSecondsBetween(
                    startMillis = ZeroDateFormatter.startOfDayMillis(today),
                    endMillis = ZeroDateFormatter.endOfDayExclusiveMillis(today)
                )
            }.collect { totalSec ->
                focusController.updateTodayFocusSeconds(totalSec)
            }
        }

        // Midnight auto-refresh loop so "Today / Tomorrow" chips & smart views never go stale
        viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val nextMidnight = ZeroDateFormatter.endOfDayExclusiveMillis(LocalDate.now())
                val waitMs = (nextMidnight - now + 1_000L).coerceAtLeast(60_000L)
                delay(waitMs)
                refreshCurrentDate()
            }
        }
    }

    fun refreshCurrentDate() {
        val actualToday = LocalDate.now()
        if (currentDateFlow.value != actualToday) {
            currentDateFlow.value = actualToday
        }
        focusController.syncWithWallClock()
    }

    private val currentTasksFlow: Flow<List<TaskItem>> =
        combine(selectedDestinationFlow, includeSubfoldersFlow, currentDateFlow) { destination, includeSubs, today ->
            Triple(destination, includeSubs, today)
        }.flatMapLatest { (destination, includeSubs, today) ->
            val todayStart = ZeroDateFormatter.startOfDayMillis(today)
            val tomorrowStart = ZeroDateFormatter.startOfDayMillis(today.plusDays(1))
            val dayAfterStart = ZeroDateFormatter.startOfDayMillis(today.plusDays(2))
            val next7End = ZeroDateFormatter.endOfDayExclusiveMillis(today.plusDays(6))

            when (destination) {
                NavigationDestination.Inbox -> taskRepository.observeInboxTasks()
                NavigationDestination.Today -> taskRepository.observeTodayAndOverdueTasks(todayStart, tomorrowStart)
                NavigationDestination.Tomorrow -> taskRepository.observeTasksDueBetween(tomorrowStart, dayAfterStart)
                NavigationDestination.Next7Days -> taskRepository.observeTasksDueBetween(todayStart, next7End)
                NavigationDestination.Calendar,
                NavigationDestination.All,
                NavigationDestination.Matrix -> allTopLevelTasksFlow
                NavigationDestination.Trash -> deletedTasksFlow
                is NavigationDestination.FolderView -> taskRepository.observeTasksByFolder(
                    folderId = destination.folderId,
                    includeSubfolders = includeSubs
                )
                is NavigationDestination.TagView -> tagRepository.observeTasksByTag(destination.tagId)
            }
        }

    private val selectedTaskFlow: Flow<TaskItem?> = selectedTaskIdFlow.flatMapLatest { taskId ->
        if (taskId == null) flowOf(null) else taskRepository.observeTaskWithTags(taskId)
    }

    private val selectedSubtasksFlow: Flow<List<TaskItem>> = selectedTaskIdFlow.flatMapLatest { taskId ->
        if (taskId == null) flowOf(emptyList()) else taskRepository.observeDescendantSubtasks(taskId)
    }

    private val smartViewCountsFlow: Flow<SmartViewCounts> = combine(
        allTopLevelTasksFlow,
        deletedTasksFlow,
        currentDateFlow
    ) { tasks, deleted, today ->
        val todayStart = ZeroDateFormatter.startOfDayMillis(today)
        val tomorrowStart = ZeroDateFormatter.startOfDayMillis(today.plusDays(1))
        val dayAfterStart = ZeroDateFormatter.startOfDayMillis(today.plusDays(2))
        val next7End = ZeroDateFormatter.endOfDayExclusiveMillis(today.plusDays(6))

        var inbox = 0
        var todayCount = 0
        var overdueCount = 0
        var tomorrowCount = 0
        var next7Count = 0
        var allCount = 0
        for (task in tasks) {
            if (task.isCompleted) continue
            allCount++
            if (task.folderId == null) inbox++
            val due = task.dueDate ?: continue
            if (due < todayStart) {
                overdueCount++
                todayCount++
            } else if (due in todayStart until tomorrowStart) {
                todayCount++
            }
            if (due in tomorrowStart until dayAfterStart) tomorrowCount++
            if (due in todayStart until next7End) next7Count++
        }
        SmartViewCounts(
            inbox = inbox,
            today = todayCount,
            overdue = overdueCount,
            tomorrow = tomorrowCount,
            next7Days = next7Count,
            all = allCount,
            trash = deleted.size
        )
    }

    private data class NavigationBundle(
        val currentDate: LocalDate,
        val destination: NavigationDestination,
        val includeSubfolders: Boolean,
        val folderTree: List<FolderNode>,
        val activeFolders: List<Folder>,
        val tagsWithCounts: List<TagWithCount>,
        val smartViewCounts: SmartViewCounts,
        val calendarSelectedDate: LocalDate,
        val calendarViewMode: CalendarViewMode
    )

    private data class SearchAndTrashBundle(
        val searchQuery: String,
        val isSearchOpen: Boolean,
        val deletedTasks: List<TaskItem>,
        val webDavStatus: WebDavUiStatus?
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
        currentDateFlow,
        selectedDestinationFlow,
        includeSubfoldersFlow,
        folderTreeFlow,
        activeFoldersFlow,
        tagsWithCountsFlow,
        smartViewCountsFlow,
        calendarDateIsoFlow,
        calendarViewModeNameFlow
    ) { values ->
        val today = values[0] as LocalDate
        val destination = values[1] as NavigationDestination
        val includeSubfolders = values[2] as Boolean
        @Suppress("UNCHECKED_CAST")
        val tree = values[3] as List<FolderNode>
        @Suppress("UNCHECKED_CAST")
        val folders = values[4] as List<Folder>
        @Suppress("UNCHECKED_CAST")
        val tagsWithCounts = values[5] as List<TagWithCount>
        val counts = values[6] as SmartViewCounts
        val dateIso = values[7] as String
        val modeName = values[8] as String
        val parsedDate = runCatching { LocalDate.parse(dateIso) }.getOrDefault(today)
        val viewMode = CalendarViewMode.entries.firstOrNull { it.name == modeName } ?: CalendarViewMode.MONTH

        NavigationBundle(
            currentDate = today,
            destination = destination,
            includeSubfolders = includeSubfolders,
            folderTree = tree,
            activeFolders = folders,
            tagsWithCounts = tagsWithCounts,
            smartViewCounts = counts,
            calendarSelectedDate = parsedDate,
            calendarViewMode = viewMode
        )
    }

    private val searchAndTrashBundleFlow: Flow<SearchAndTrashBundle> = combine(
        searchQueryFlow,
        searchOpenFlow,
        deletedTasksFlow,
        webDavStatusFlow
    ) { query, isOpen, deleted, webDavStatus ->
        SearchAndTrashBundle(query, isOpen, deleted, webDavStatus)
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
        inputAndUiBundleFlow,
        searchAndTrashBundleFlow
    ) { values ->
        val nav = values[0] as NavigationBundle
        @Suppress("UNCHECKED_CAST")
        val rawTasks = values[1] as List<TaskItem>
        @Suppress("UNCHECKED_CAST")
        val allTasks = values[2] as List<TaskItem>
        val selectedTaskId = values[3] as String?
        val selectedTask = values[4] as TaskItem?
        @Suppress("UNCHECKED_CAST")
        val subtasks = values[5] as List<TaskItem>
        val ui = values[6] as InputAndUiBundle
        val searchTrash = values[7] as SearchAndTrashBundle

        val folderById = nav.activeFolders.associateBy { it.id }
        val folderNameById = nav.activeFolders.associate { it.id to it.name }
        val tagNameById = nav.tagsWithCounts.associate { it.tag.id to it.tag.name }

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
            NavigationDestination.All -> "全部"
            NavigationDestination.Matrix -> "四象限"
            NavigationDestination.Trash -> "回收站"
            is NavigationDestination.FolderView -> folderById[dest.folderId]?.name ?: "收集箱"
            is NavigationDestination.TagView -> tagNameById[dest.tagId]?.let { "#$it" } ?: "收集箱"
        }

        // Global search filters across ALL active top-level tasks when query is non-blank
        val trimmedQuery = searchTrash.searchQuery.trim()
        val effectiveTasks = if (searchTrash.isSearchOpen && trimmedQuery.isNotEmpty()) {
            val q = trimmedQuery.lowercase()
            allTasks.filter { task ->
                task.title.lowercase().contains(q) ||
                    task.description.lowercase().contains(q) ||
                    task.tags.any { it.name.lowercase().contains(q) } ||
                    (task.folderId != null && folderNameById[task.folderId]?.lowercase()?.contains(q) == true)
            }
        } else {
            rawTasks
        }

        val sortedTasks = applyTaskSort(effectiveTasks, ui.preferences.taskSortMode)
        val parsed = QuickInputParser.parse(ui.quickInputText)
        val suggestions = buildFolderSuggestions(nav.folderTree, parsed.activeFolderQuery)

        MainUiState(
            currentDate = nav.currentDate,
            selectedDestination = nav.destination,
            destinationTitle = destinationTitle,
            breadcrumbs = breadcrumbs,
            includeSubfolders = nav.includeSubfolders,
            folderTree = nav.folderTree,
            folderNameById = folderNameById,
            tagsWithCounts = nav.tagsWithCounts,
            tagNameById = tagNameById,
            smartViewCounts = nav.smartViewCounts,
            tasks = sortedTasks,
            allActiveTopLevelTasks = allTasks,
            deletedTasks = searchTrash.deletedTasks,
            searchQuery = searchTrash.searchQuery,
            isSearchOpen = searchTrash.isSearchOpen,
            taskSortMode = ui.preferences.taskSortMode,
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
            webDavConfig = ui.preferences.webDavConfig,
            webDavStatus = searchTrash.webDavStatus,
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

    private fun applyTaskSort(tasks: List<TaskItem>, sortMode: TaskSortMode): List<TaskItem> {
        if (sortMode == TaskSortMode.MANUAL) return tasks
        return when (sortMode) {
            TaskSortMode.MANUAL -> tasks
            TaskSortMode.DUE_DATE -> tasks.sortedWith(
                compareBy<TaskItem>(
                    { it.isCompleted },
                    { it.dueDate == null },
                    { it.dueDate ?: Long.MAX_VALUE },
                    { it.priority.value },
                    { it.sortOrder }
                )
            )
            TaskSortMode.PRIORITY -> tasks.sortedWith(
                compareBy<TaskItem>(
                    { it.isCompleted },
                    { it.priority.value },
                    { it.dueDate == null },
                    { it.dueDate ?: Long.MAX_VALUE },
                    { it.sortOrder }
                )
            )
            TaskSortMode.CREATED_DESC -> tasks.sortedWith(
                compareBy<TaskItem>({ it.isCompleted })
                    .thenByDescending { it.createdAt }
            )
        }
    }

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

    fun setSearchOpen(open: Boolean) {
        savedStateHandle[KEY_SEARCH_OPEN] = open
        if (!open) {
            savedStateHandle[KEY_SEARCH_QUERY] = ""
        }
    }

    fun updateSearchQuery(query: String) {
        savedStateHandle[KEY_SEARCH_QUERY] = query
    }

    fun cycleTaskSortMode() {
        val current = userPreferencesRepository.preferences.value.taskSortMode
        userPreferencesRepository.setTaskSortMode(current.next())
    }

    fun setTaskSortMode(mode: TaskSortMode) {
        userPreferencesRepository.setTaskSortMode(mode)
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
        webDavStatusFlow.value = null
        savedStateHandle[KEY_SETTINGS_OPEN] = true
    }

    fun closeSettings() {
        savedStateHandle[KEY_SETTINGS_OPEN] = false
    }

    fun setDarkTheme(isDark: Boolean) = userPreferencesRepository.setDarkTheme(isDark)

    fun toggleTheme() {
        userPreferencesRepository.setDarkTheme(!userPreferencesRepository.preferences.value.isDarkTheme)
    }

    fun setAccent(accent: ZeroAccent) = userPreferencesRepository.setAccent(accent)

    fun toggleAccent() {
        val current = userPreferencesRepository.preferences.value.accent
        userPreferencesRepository.setAccent(current.next())
    }

    fun setAntiMisclickMode(mode: AntiMisclickMode) = userPreferencesRepository.setAntiMisclickMode(mode)

    fun setHapticsEnabled(enabled: Boolean) = userPreferencesRepository.setHapticsEnabled(enabled)

    fun setAppLanguage(language: AppLanguage) {
        userPreferencesRepository.setAppLanguage(language)
        viewModelScope.launch { widgetRefresher.refreshAll() }
    }

    fun updateWebDavConfig(config: WebDavConfig) {
        userPreferencesRepository.setWebDavConfig(config)
        webDavStatusFlow.value = null
    }

    fun testWebDavConnection() {
        val config = userPreferencesRepository.preferences.value.webDavConfig
        webDavStatusFlow.value = WebDavUiStatus.Working
        viewModelScope.launch {
            webDavSyncManager.testConnection(config)
                .onSuccess { webDavStatusFlow.value = WebDavUiStatus.TestSuccess }
                .onFailure { err -> webDavStatusFlow.value = WebDavUiStatus.Error(err.message ?: "Error") }
        }
    }

    fun pushWebDavBackup() {
        val config = userPreferencesRepository.preferences.value.webDavConfig
        webDavStatusFlow.value = WebDavUiStatus.Working
        viewModelScope.launch {
            runCatching {
                val payload = backupManager.exportJson()
                webDavSyncManager.pushBackup(config, payload).getOrThrow()
            }.onSuccess {
                webDavStatusFlow.value = WebDavUiStatus.PushSuccess
            }.onFailure { err ->
                webDavStatusFlow.value = WebDavUiStatus.Error(err.message ?: "Push failed")
            }
        }
    }

    fun pullWebDavBackup() {
        val config = userPreferencesRepository.preferences.value.webDavConfig
        webDavStatusFlow.value = WebDavUiStatus.Working
        viewModelScope.launch {
            runCatching {
                val payload = webDavSyncManager.pullBackup(config).getOrThrow()
                backupManager.importJson(payload, replaceExisting = true)
            }.onSuccess { summary ->
                selectTask(null)
                selectDestination(NavigationDestination.Inbox)
                rescheduleAllUpcomingReminders()
                widgetRefresher.refreshAll()
                webDavStatusFlow.value = WebDavUiStatus.PullSuccess(summary)
            }.onFailure { err ->
                webDavStatusFlow.value = WebDavUiStatus.Error(err.message ?: "Pull failed")
            }
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
                rescheduleAllUpcomingReminders()
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

    fun prefillQuickInputPriority(priority: TaskPriority) {
        val prefix = if (priority == TaskPriority.NONE) "" else "!${priority.value} "
        savedStateHandle[KEY_QUICK_INPUT] = prefix
        selectDestination(NavigationDestination.Inbox)
        quickInputFocusRequestTickFlow.value = System.currentTimeMillis()
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

    fun moveFolderToParent(folderId: String, targetParentId: String?) {
        viewModelScope.launch {
            runCatching {
                val targetSiblings = findChildrenInTree(uiState.value.folderTree, targetParentId)
                folderRepository.moveFolderToIndex(
                    folderId = folderId,
                    targetParentId = targetParentId,
                    targetIndex = targetSiblings.size
                )
                if (targetParentId != null) {
                    folderRepository.setFolderExpanded(targetParentId, true)
                }
            }
        }
    }

    fun moveFolderOrder(folderId: String, direction: Int) {
        viewModelScope.launch {
            runCatching {
                val folder = folderRepository.getFolderById(folderId) ?: return@launch
                val siblings = findChildrenInTree(uiState.value.folderTree, folder.parentId)
                val currentIdx = siblings.indexOfFirst { it.folder.id == folderId }
                if (currentIdx == -1) return@launch
                val targetIdx = (currentIdx + direction).coerceIn(0, siblings.lastIndex)
                if (targetIdx != currentIdx) {
                    folderRepository.moveFolderToIndex(
                        folderId = folderId,
                        targetParentId = folder.parentId,
                        targetIndex = targetIdx
                    )
                }
            }
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

    fun rescheduleOverdueToToday() {
        val today = currentDateFlow.value
        val todayStart = ZeroDateFormatter.startOfDayMillis(today)
        val overdueIds = uiState.value.tasks
            .filter { !it.isCompleted && it.dueDate != null && it.dueDate < todayStart }
            .map { it.id }
        if (overdueIds.isEmpty()) return
        viewModelScope.launch {
            taskRepository.rescheduleTasksToDate(overdueIds, today)
            for (id in overdueIds) {
                val updated = taskRepository.getTaskById(id) ?: continue
                syncTaskReminder(updated)
            }
            widgetRefresher.refreshAll()
        }
    }

    fun toggleTaskCompletion(task: TaskItem) {
        viewModelScope.launch {
            val newCompleted = !task.isCompleted
            taskRepository.setTaskCompleted(task.id, newCompleted)
            if (newCompleted) {
                reminderScheduler.cancelTaskReminder(task.id)
            } else {
                syncTaskReminder(task.copy(isCompleted = false))
            }

            var spawnedTaskId: String? = null
            if (newCompleted && task.recurrenceRule != null) {
                val parsedRule = RecurrenceRule.parse(task.recurrenceRule)
                if (parsedRule != null) {
                    val anchorMillis = task.dueDate
                        ?: ZeroDateFormatter.startOfDayMillis(currentDateFlow.value)
                    val nextDueMillis = parsedRule.nextOccurrenceMillis(anchorMillis)
                    val nextReminderMillis = if (task.reminderAt != null && task.dueDate != null) {
                        nextDueMillis - (task.dueDate - task.reminderAt)
                    } else {
                        null
                    }
                    val spawned = taskRepository.createTask(
                        title = task.title,
                        folderId = task.folderId,
                        description = task.description,
                        priority = task.priority,
                        dueDate = nextDueMillis,
                        isAllDay = task.isAllDay,
                        reminderAt = nextReminderMillis,
                        recurrenceRule = task.recurrenceRule
                    )
                    spawnedTaskId = spawned.id
                    syncTaskReminder(spawned)

                    // Copy all tags to the spawned recurrence
                    val fullSource = taskRepository.getTaskById(task.id)
                    val tagsToCopy = fullSource?.tags?.ifEmpty { task.tags } ?: task.tags
                    for (tag in tagsToCopy) {
                        tagRepository.attachTagToTask(spawned.id, tag.id)
                    }

                    // Clone nested subtasks cleanly (reset to uncompleted)
                    val sourceSubtasks = taskRepository.getDescendantSubtasksSnapshot(task.id)
                    if (sourceSubtasks.isNotEmpty()) {
                        val idMap = HashMap<String, String>(sourceSubtasks.size + 1)
                        idMap[task.id] = spawned.id
                        for (sub in sourceSubtasks) {
                            val mappedParent = idMap[sub.parentTaskId] ?: spawned.id
                            val cloned = taskRepository.createTask(
                                title = sub.title,
                                folderId = null,
                                parentTaskId = mappedParent,
                                description = sub.description,
                                priority = sub.priority
                            )
                            idMap[sub.id] = cloned.id
                        }
                    }
                }
            }

            focusController.syncFocusedTask(task.copy(isCompleted = newCompleted))
            undoBannerFlow.value = UndoBannerState(
                taskId = task.id,
                taskTitle = task.title,
                undoType = if (newCompleted) UndoType.REVERT_COMPLETE else UndoType.REVERT_UNCOMPLETE,
                spawnedTaskId = spawnedTaskId
            )
            widgetRefresher.refreshAll()
        }
    }

    fun reorderTask(taskId: String, targetIndex: Int) {
        if (userPreferencesRepository.preferences.value.taskSortMode != TaskSortMode.MANUAL) return
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
            val updated = task.copy(title = title, description = description)
            taskRepository.updateTask(updated)
            syncTaskReminder(updated)
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
            // Auto-sync reminder when a specific time is set or cleared
            val updatedReminder = when {
                dueDateMillis == null -> null
                !isAllDay && task.reminderAt != null && task.dueDate != null -> {
                    val offset = (task.dueDate - task.reminderAt).coerceAtLeast(0L)
                    dueDateMillis - offset
                }
                !isAllDay && task.reminderAt == null -> dueDateMillis
                else -> task.reminderAt
            }
            val updated = task.copy(
                dueDate = dueDateMillis,
                isAllDay = isAllDay,
                reminderAt = updatedReminder
            )
            taskRepository.updateTask(updated)
            syncTaskReminder(updated)
            widgetRefresher.refreshAll()
        }
    }

    fun updateTaskReminder(task: TaskItem, reminderAtMillis: Long?) {
        viewModelScope.launch {
            val updated = task.copy(reminderAt = reminderAtMillis)
            taskRepository.updateTask(updated)
            syncTaskReminder(updated)
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
            reminderScheduler.cancelTaskReminder(task.id)
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

    fun restoreDeletedTask(taskId: String) {
        viewModelScope.launch {
            taskRepository.restoreTask(taskId)
            taskRepository.getTaskById(taskId)?.let { syncTaskReminder(it) }
            widgetRefresher.refreshAll()
        }
    }

    fun permanentlyDeleteTask(taskId: String) {
        viewModelScope.launch {
            reminderScheduler.cancelTaskReminder(taskId)
            taskRepository.permanentlyDeleteTask(taskId)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            taskRepository.emptyTrash()
        }
    }

    fun performUndo(banner: UndoBannerState) {
        undoBannerFlow.value = null
        viewModelScope.launch {
            when (banner.undoType) {
                UndoType.REVERT_COMPLETE -> {
                    taskRepository.setTaskCompleted(banner.taskId, false)
                    // Cleanly roll back any auto-spawned recurring task copy
                    banner.spawnedTaskId?.let { spawnedId ->
                        reminderScheduler.cancelTaskReminder(spawnedId)
                        taskRepository.permanentlyDeleteTask(spawnedId)
                    }
                    taskRepository.getTaskById(banner.taskId)?.let { syncTaskReminder(it) }
                }
                UndoType.REVERT_UNCOMPLETE -> {
                    taskRepository.setTaskCompleted(banner.taskId, true)
                    reminderScheduler.cancelTaskReminder(banner.taskId)
                }
                UndoType.RESTORE_DELETED_TASK -> {
                    taskRepository.restoreTask(banner.taskId)
                    taskRepository.getTaskById(banner.taskId)?.let { syncTaskReminder(it) }
                }
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

    private fun syncTaskReminder(task: TaskItem) {
        val triggerAt = task.reminderAt
        if (!task.isCompleted && !task.isDeleted && triggerAt != null && triggerAt > System.currentTimeMillis()) {
            reminderScheduler.scheduleTaskReminder(task.id, task.title, triggerAt)
        } else {
            reminderScheduler.cancelTaskReminder(task.id)
        }
    }

    private suspend fun rescheduleAllUpcomingReminders() {
        val upcoming = taskRepository.getUpcomingReminderTasks(System.currentTimeMillis())
        for (task in upcoming) {
            val triggerAt = task.reminderAt ?: continue
            reminderScheduler.scheduleTaskReminder(task.id, task.title, triggerAt)
        }
    }

    private suspend fun createStructuredTaskFromText(raw: String) {
        val parsed = QuickInputParser.parse(raw)
        val currentDest = NavigationDestination.fromStorageKey(destinationKeyFlow.value)
        val today = currentDateFlow.value

        val resolvedFolderId = if (parsed.folderPath != null) {
            resolveOrCreateFolderPath(parsed.folderPath)
        } else if (currentDest is NavigationDestination.FolderView) {
            currentDest.folderId
        } else {
            null
        }

        val resolvedDueDate = parsed.dueDateMillis ?: when (currentDest) {
            NavigationDestination.Today -> ZeroDateFormatter.startOfDayMillis(today)
            NavigationDestination.Tomorrow -> ZeroDateFormatter.startOfDayMillis(today.plusDays(1))
            NavigationDestination.Calendar -> ZeroDateFormatter.startOfDayMillis(uiState.value.calendarSelectedDate)
            else -> null
        }
        val resolvedAllDay = if (parsed.dueDateMillis != null) parsed.isAllDay else true
        // Auto-schedule reminder when user explicitly enters a specific clock time
        val resolvedReminder = if (resolvedDueDate != null && !resolvedAllDay) resolvedDueDate else null
        val resolvedTitle = parsed.title.ifBlank { raw }

        val created = taskRepository.createTask(
            title = resolvedTitle,
            folderId = resolvedFolderId,
            priority = parsed.priority,
            dueDate = resolvedDueDate,
            isAllDay = resolvedAllDay,
            reminderAt = resolvedReminder
        )
        syncTaskReminder(created)

        for (tagName in parsed.tags) {
            val tag = tagRepository.getOrCreateTag(tagName)
            tagRepository.attachTagToTask(created.id, tag.id)
        }
        // If user is currently inside a TagView, auto-attach that tag as well
        if (currentDest is NavigationDestination.TagView) {
            tagRepository.attachTagToTask(created.id, currentDest.tagId)
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

    private fun findChildrenInTree(tree: List<FolderNode>, parentId: String?): List<FolderNode> {
        if (parentId == null) return tree
        fun search(nodes: List<FolderNode>): List<FolderNode>? {
            for (node in nodes) {
                if (node.folder.id == parentId) return node.children
                val found = search(node.children)
                if (found != null) return found
            }
            return null
        }
        return search(tree).orEmpty()
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
        val normalized = activeQuery.trim().lowercase()
        return if (normalized.isEmpty()) {
            allPaths.take(6)
        } else {
            allPaths.filter { it.fullPath.lowercase().contains(normalized) }.take(6)
        }
    }

    private fun List<FolderNode>.flattenToFolderList(): List<Folder> {
        val out = mutableListOf<Folder>()
        fun walk(nodes: List<FolderNode>) {
            for (node in nodes) {
                out.add(node.folder)
                walk(node.children)
            }
        }
        walk(this)
        return out
    }

    companion object {
        private const val KEY_DESTINATION = "zero_selected_destination"
        private const val KEY_SELECTED_TASK_ID = "zero_selected_task_id"
        private const val KEY_INCLUDE_SUBFOLDERS = "zero_include_subfolders"
        private const val KEY_QUICK_INPUT = "zero_quick_input"
        private const val KEY_SEARCH_QUERY = "zero_search_query"
        private const val KEY_SEARCH_OPEN = "zero_search_open"
        private const val KEY_CONTINUOUS_ENTRY = "zero_continuous_entry"
        private const val KEY_MEDIUM_TREE_EXPANDED = "zero_medium_tree_expanded"
        private const val KEY_CALENDAR_DATE = "zero_calendar_date"
        private const val KEY_CALENDAR_VIEW_MODE = "zero_calendar_view_mode"
        private const val KEY_SETTINGS_OPEN = "zero_settings_open"
    }
}
