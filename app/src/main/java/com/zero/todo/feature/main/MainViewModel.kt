package com.zero.todo.feature.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zero.todo.core.common.ZeroDateFormatter
import com.zero.todo.core.designsystem.ZeroAccent
import com.zero.todo.core.nlp.ParsedQuickInput
import com.zero.todo.core.nlp.QuickInputParser
import com.zero.todo.core.notification.ReminderScheduler
import com.zero.todo.core.notification.syncTaskReminder
import com.zero.todo.data.ai.AiBreakdownDialogState
import com.zero.todo.data.ai.AiCaptureDialogState
import com.zero.todo.data.ai.AiCapturedTaskDraft
import com.zero.todo.data.ai.AiConfig
import com.zero.todo.data.ai.AiImagePayload
import com.zero.todo.data.ai.AiSubtaskDraft
import com.zero.todo.data.ai.AiTestStatus
import com.zero.todo.data.ai.AiWorkspaceContext
import com.zero.todo.data.ai.ZeroAiEngine
import com.zero.todo.data.ai.ZeroAiJsonParser
import com.zero.todo.data.backup.WebDavConfig
import com.zero.todo.data.backup.WebDavSyncManager
import com.zero.todo.data.backup.ZeroBackupManager
import com.zero.todo.data.preferences.UserPreferences
import com.zero.todo.data.preferences.NotificationPreferences
import com.zero.todo.data.preferences.UserPreferencesRepository
import com.zero.todo.data.repository.FocusRepository
import com.zero.todo.data.repository.FolderRepository
import com.zero.todo.data.repository.TagRepository
import com.zero.todo.data.repository.TagWithCount
import com.zero.todo.data.repository.TaskRepository
import com.zero.todo.domain.model.AntiMisclickMode
import com.zero.todo.domain.model.AppLanguage
import com.zero.todo.domain.model.EffortTier
import com.zero.todo.domain.model.ExecuteTheme
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.domain.model.Folder
import com.zero.todo.domain.model.FolderBadgeDisplay
import com.zero.todo.domain.model.FolderNode
import com.zero.todo.domain.model.FolderPathDisplayMode
import com.zero.todo.domain.model.FolderPathFormatter
import com.zero.todo.domain.model.PriorityDisplayStyle
import com.zero.todo.domain.model.RunwayQueueBuilder
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.TaskPlacementChange
import com.zero.todo.domain.model.TaskPriority
import com.zero.todo.domain.model.TaskSortMode
import com.zero.todo.domain.model.TaskSwipeAction
import com.zero.todo.domain.model.TaskSwipeActions
import com.zero.todo.domain.model.TaskSwipeDirection
import com.zero.todo.domain.model.WorkspaceMode
import com.zero.todo.domain.usecase.GetFolderTreeUseCase
import com.zero.todo.feature.calendar.CalendarViewMode
import com.zero.todo.feature.execute.ExecuteUiState
import com.zero.todo.feature.execute.ExecuteViewModel
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
import com.zero.todo.core.designsystem.AppIconSwitcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import com.zero.todo.domain.model.TagTreeBuilder
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class MainUiState(
    val currentDate: LocalDate = LocalDate.now(),
    val workspaceMode: WorkspaceMode = WorkspaceMode.NOW,
    val executeTheme: ExecuteTheme = ExecuteTheme.MONOLITH,
    val dailyCapacityMinutes: Int = 240,
    val scheduledTodayMinutes: Int = 0,
    val todayCompletedCount: Int = 0,
    val runwayQueue: List<TaskItem> = emptyList(),
    val focusedRunwayTaskId: String? = null,
    val triageQueue: List<TaskItem> = emptyList(),
    val quickFolders: List<Folder> = emptyList(),
    val isTriageOpen: Boolean = false,
    val selectedDestination: NavigationDestination = NavigationDestination.Inbox,
    val destinationTitle: String = "收集箱",
    val breadcrumbs: List<Folder> = emptyList(),
    val includeSubfolders: Boolean = false,
    val folderTree: List<FolderNode> = emptyList(),
    val folderNameById: Map<String, String> = emptyMap(),
    val folderPathDisplayMode: FolderPathDisplayMode = FolderPathDisplayMode.SMART_DISAMBIGUATE,
    val priorityDisplayStyle: PriorityDisplayStyle = PriorityDisplayStyle.DOTS,
    val folderBadgeById: Map<String, FolderBadgeDisplay> = emptyMap(),
    val relativeFolderBadgeById: Map<String, FolderBadgeDisplay> = emptyMap(),
    val monolithFolderBadgeById: Map<String, FolderBadgeDisplay> = emptyMap(),
    val folderFullPathById: Map<String, String> = emptyMap(),
    val folderBreadcrumbsById: Map<String, List<String>> = emptyMap(),
    val folderColorHexById: Map<String, String> = emptyMap(),
    val folderRootNameById: Map<String, String> = emptyMap(),
    val parentTaskTitleById: Map<String, String> = emptyMap(),
    val tagsWithCounts: List<TagWithCount> = emptyList(),
    val tagNameById: Map<String, String> = emptyMap(),
    val tagPathById: Map<String, String> = emptyMap(),
    val smartViewCounts: SmartViewCounts = SmartViewCounts(),
    val tasks: List<TaskItem> = emptyList(),
    val allActiveTasks: List<TaskItem> = emptyList(),
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
    val taskSwipeActions: TaskSwipeActions = TaskSwipeActions(),
    val hapticsEnabled: Boolean = true,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val notificationPreferences: NotificationPreferences = NotificationPreferences(),
    val webDavConfig: WebDavConfig = WebDavConfig(),
    val aiConfig: AiConfig = AiConfig(),
    val webDavStatus: WebDavUiStatus? = null,
    val isMediumTreeExpanded: Boolean = true,
    val isSettingsOpen: Boolean = false,
    val backupStatus: BackupUiStatus? = null,
    val focusState: FocusUiState = FocusUiState(),
    val quickInputFocusRequestTick: Long = 0L
)

data class TaskParentPickerState(
    val taskId: String,
    val saving: Boolean = false,
    val failed: Boolean = false
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
    private val aiEngine: ZeroAiEngine,
    private val reminderScheduler: ReminderScheduler,
    private val widgetRefresher: WidgetRefresher,
    getFolderTreeUseCase: GetFolderTreeUseCase,
    private val appIconSwitcher: AppIconSwitcher
) : ViewModel(), ExecuteViewModel {

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
    private val triageOpenFlow = MutableStateFlow(false)
    private val skippedRunwayIdsFlow = MutableStateFlow<List<String>>(emptyList())
    private val pinnedRunwayTaskIdFlow = MutableStateFlow<String?>(null)
    private val focusedRunwayTaskIdFlow = MutableStateFlow<String?>(null)

    private val folderTreeFlow = getFolderTreeUseCase()
    private val activeFoldersFlow = folderRepository.observeActiveFolders()
    private val tagsWithCountsFlow = tagRepository.observeActiveTagsWithCounts()
    private val allActiveTasksFlow = taskRepository.observeAllActiveTasks()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), replay = 1)
    private val allTopLevelTasksFlow = allActiveTasksFlow.map { tasks -> tasks.filter { it.parentTaskId == null } }
    private val deletedTasksFlow = taskRepository.observeDeletedTasks()
    private val _taskParentPicker = MutableStateFlow<TaskParentPickerState?>(null)
    val taskParentPicker = _taskParentPicker.asStateFlow()

    private val _aiTestStatus = MutableStateFlow<AiTestStatus?>(null)
    val aiTestStatus = _aiTestStatus.asStateFlow()

    private val _aiCaptureState = MutableStateFlow(AiCaptureDialogState())
    val aiCaptureState = _aiCaptureState.asStateFlow()

    private val _aiBreakdownState = MutableStateFlow(AiBreakdownDialogState())
    val aiBreakdownState = _aiBreakdownState.asStateFlow()

    init {
        // Re-sync upcoming task alarms & daily briefings on startup
        viewModelScope.launch {
            rescheduleAllUpcomingReminders()
            reminderScheduler.syncDailyBriefings(userPreferencesRepository.preferences.value.notifications)
        }

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
                NavigationDestination.Calendar, NavigationDestination.Matrix -> allActiveTasksFlow
                NavigationDestination.All -> allTopLevelTasksFlow
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
        allActiveTasksFlow,
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
            if (task.parentTaskId == null) {
                allCount++
                if (task.folderId == null) inbox++
            }
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
        val webDavStatus: WebDavUiStatus?,
        val isTriageOpen: Boolean,
        val skippedRunwayIds: List<String>,
        val pinnedRunwayTaskId: String?,
        val focusedRunwayTaskId: String?
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
        webDavStatusFlow,
        triageOpenFlow,
        skippedRunwayIdsFlow,
        pinnedRunwayTaskIdFlow,
        focusedRunwayTaskIdFlow
    ) { values ->
        val query = values[0] as String
        val isOpen = values[1] as Boolean
        @Suppress("UNCHECKED_CAST")
        val deleted = values[2] as List<TaskItem>
        val webDavStatus = values[3] as WebDavUiStatus?
        val isTriageOpen = values[4] as Boolean
        @Suppress("UNCHECKED_CAST")
        val skippedIds = values[5] as List<String>
        val pinnedId = values[6] as String?
        val focusedId = values[7] as String?
        SearchAndTrashBundle(query, isOpen, deleted, webDavStatus, isTriageOpen, skippedIds, pinnedId, focusedId)
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
        allActiveTasksFlow,
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
        val selectedTask = (values[4] as TaskItem?)?.takeIf { it.id == selectedTaskId }
        @Suppress("UNCHECKED_CAST")
        val subtasks = values[5] as List<TaskItem>
        val ui = values[6] as InputAndUiBundle
        val searchTrash = values[7] as SearchAndTrashBundle

        val folderById = nav.activeFolders.associateBy { it.id }
        val folderNameById = nav.activeFolders.associate { it.id to it.name }
        val folderIndex = FolderPathFormatter.buildIndex(nav.activeFolders)
        val pathMode = ui.preferences.folderPathDisplayMode
        val relativeAnchorFolderId = (nav.destination as? NavigationDestination.FolderView)?.folderId
        val folderBadgeById = buildMap {
            nav.activeFolders.forEach { folder ->
                folderIndex.badgeFor(folder.id, pathMode, relativeToFolderId = null)?.let { put(folder.id, it) }
            }
        }
        val relativeFolderBadgeById = if (relativeAnchorFolderId != null) {
            buildMap {
                nav.activeFolders.forEach { folder ->
                    folderIndex.badgeFor(folder.id, pathMode, relativeToFolderId = relativeAnchorFolderId)?.let { put(folder.id, it) }
                }
            }
        } else {
            folderBadgeById
        }
        val monolithFolderBadgeById = buildMap {
            nav.activeFolders.forEach { folder ->
                folderIndex.monolithBadgeFor(folder.id, pathMode)?.let { put(folder.id, it) }
            }
        }
        val folderBreadcrumbsById = buildMap {
            nav.activeFolders.forEach { folder ->
                put(folder.id, folderIndex.breadcrumbSegments(folder.id))
            }
        }
        val tagNameById = nav.tagsWithCounts.associate { it.tag.id to it.tag.name }
        val tagPathById = TagTreeBuilder.buildPathMap(nav.tagsWithCounts.map { it.tag })

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
            is NavigationDestination.TagView -> tagPathById[dest.tagId]?.let { "#$it" } ?: "收集箱"
        }

        // Search includes nested tasks so they can be opened independently, plus full folder path.
        val trimmedQuery = searchTrash.searchQuery.trim()
        val effectiveTasks = if (searchTrash.isSearchOpen && trimmedQuery.isNotEmpty()) {
            val q = trimmedQuery.lowercase()
            allTasks.filter { task ->
                task.title.lowercase().contains(q) ||
                    task.description.lowercase().contains(q) ||
                    task.tags.any {
                        it.name.lowercase().contains(q) ||
                            (tagPathById[it.id]?.lowercase()?.contains(q) == true)
                    } ||
                    (task.folderId != null && (
                        folderNameById[task.folderId]?.lowercase()?.contains(q) == true ||
                            folderIndex.fullPathById[task.folderId]?.lowercase()?.contains(q) == true
                        ))
            }
        } else {
            rawTasks
        }

        val sortedTasks = applyTaskSort(effectiveTasks, ui.preferences.taskSortMode)
        val parsed = QuickInputParser.parse(ui.quickInputText)
        val suggestions = buildFolderSuggestions(nav.folderTree, parsed.activeFolderQuery)

        // ZERO 2.0 + v2.8.0: Leaf-Only Parallel NOW Runway Queue, Day Budget Scheduled Minutes, Today Rhythm & Triage Queue
        val todayStart = ZeroDateFormatter.startOfDayMillis(nav.currentDate)
        val tomorrowStart = ZeroDateFormatter.startOfDayMillis(nav.currentDate.plusDays(1))
        val todayCompletedCount = allTasks.count { task ->
            task.isCompleted && (task.completedAt?.let { it in todayStart until tomorrowStart } == true)
        }
        val activeIncomplete = allTasks.filterNot { it.isCompleted }
        val runwayResult = RunwayQueueBuilder.build(
            allActiveTasks = allTasks,
            tomorrowStartMillis = tomorrowStart,
            pinnedTaskId = searchTrash.pinnedRunwayTaskId,
            skippedIds = searchTrash.skippedRunwayIds
        )
        val scheduledTodayMinutes = runwayResult.scheduledTodayMinutes
        val runwayQueue = runwayResult.runwayQueue
        val triageQueue = activeIncomplete.filter { task ->
            val isOverdue = task.dueDate != null && task.dueDate < todayStart
            val isUnfiledInbox = task.parentTaskId == null && task.folderId == null && task.dueDate == null
            isOverdue || isUnfiledInbox
        }.sortedWith(compareBy<TaskItem>({ it.dueDate ?: Long.MAX_VALUE }, { it.createdAt }))

        val resolvedFocusedId = searchTrash.focusedRunwayTaskId
            ?.takeIf { id -> runwayQueue.any { it.id == id } }
            ?: runwayQueue.firstOrNull()?.id

        MainUiState(
            currentDate = nav.currentDate,
            workspaceMode = ui.preferences.workspaceMode,
            executeTheme = ui.preferences.executeTheme,
            dailyCapacityMinutes = ui.preferences.dailyCapacityMinutes,
            scheduledTodayMinutes = scheduledTodayMinutes,
            todayCompletedCount = todayCompletedCount,
            runwayQueue = runwayQueue,
            focusedRunwayTaskId = resolvedFocusedId,
            triageQueue = triageQueue,
            quickFolders = nav.activeFolders.take(10),
            isTriageOpen = searchTrash.isTriageOpen,
            selectedDestination = nav.destination,
            destinationTitle = destinationTitle,
            breadcrumbs = breadcrumbs,
            includeSubfolders = nav.includeSubfolders,
            folderTree = nav.folderTree,
            folderNameById = folderNameById,
            folderPathDisplayMode = pathMode,
            priorityDisplayStyle = ui.preferences.priorityDisplayStyle,
            folderBadgeById = folderBadgeById,
            relativeFolderBadgeById = relativeFolderBadgeById,
            monolithFolderBadgeById = monolithFolderBadgeById,
            folderFullPathById = folderIndex.fullPathById,
            folderBreadcrumbsById = folderBreadcrumbsById,
            folderColorHexById = folderIndex.colorHexById,
            folderRootNameById = folderIndex.rootNameById,
            parentTaskTitleById = runwayResult.parentTitleByTaskId,
            tagsWithCounts = nav.tagsWithCounts,
            tagNameById = tagNameById,
            tagPathById = tagPathById,
            smartViewCounts = nav.smartViewCounts,
            tasks = sortedTasks,
            allActiveTasks = allTasks,
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
            taskSwipeActions = ui.preferences.taskSwipeActions,
            hapticsEnabled = ui.preferences.hapticsEnabled,
            appLanguage = ui.preferences.appLanguage,
            notificationPreferences = ui.preferences.notifications,
            webDavConfig = ui.preferences.webDavConfig,
            aiConfig = ui.preferences.aiConfig,
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
        initialValue = initialUiStateFromPreferences(userPreferencesRepository.preferences.value)
    )

    private fun initialUiStateFromPreferences(prefs: UserPreferences): MainUiState = MainUiState(
        workspaceMode = prefs.workspaceMode,
        executeTheme = prefs.executeTheme,
        dailyCapacityMinutes = prefs.dailyCapacityMinutes,
        isDarkTheme = prefs.isDarkTheme,
        activeAccent = prefs.accent,
        antiMisclickMode = prefs.antiMisclickMode,
        taskSwipeActions = prefs.taskSwipeActions,
        hapticsEnabled = prefs.hapticsEnabled,
        appLanguage = prefs.appLanguage,
        taskSortMode = prefs.taskSortMode,
        folderPathDisplayMode = prefs.folderPathDisplayMode,
        priorityDisplayStyle = prefs.priorityDisplayStyle,
        notificationPreferences = prefs.notifications,
        webDavConfig = prefs.webDavConfig,
        aiConfig = prefs.aiConfig
    )

    override val executeUiState: StateFlow<ExecuteUiState> = uiState
        .map { it.toExecuteUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = uiState.value.toExecuteUiState()
        )

    private fun MainUiState.toExecuteUiState(): ExecuteUiState {
        val activeIndex = focusedRunwayTaskId
            ?.let { id -> runwayQueue.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: 0
        val focusedTask = runwayQueue.getOrNull(activeIndex)
        val inlineFlow = workspaceMode == WorkspaceMode.NOW &&
            focusState.isActive &&
            focusedTask != null &&
            focusState.task?.id == focusedTask.id
        return ExecuteUiState(
            executeTheme = executeTheme,
            currentDate = currentDate,
            runwayQueue = runwayQueue,
            focusedTaskId = focusedTask?.id,
            focusedTaskIndex = activeIndex,
            focusedTask = focusedTask,
            scheduledTodayMinutes = scheduledTodayMinutes,
            dailyCapacityMinutes = dailyCapacityMinutes,
            todayCompletedCount = todayCompletedCount,
            triageCount = triageQueue.size,
            folderNameById = folderNameById,
            folderBadgeById = folderBadgeById,
            monolithFolderBadgeById = monolithFolderBadgeById,
            parentTaskTitleById = parentTaskTitleById,
            focusState = focusState,
            isInlineFlowActive = inlineFlow,
            quickInputText = quickInputText,
            parsedQuickInput = parsedQuickInput,
            folderSuggestions = folderSuggestions,
            continuousEntryEnabled = continuousEntryEnabled,
            undoBanner = undoBanner,
            aiConfigured = aiConfig.isConfigured
        )
    }

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

    override fun openSettings() {
        backupStatusFlow.value = null
        webDavStatusFlow.value = null
        savedStateHandle[KEY_SETTINGS_OPEN] = true
    }

    fun closeSettings() {
        savedStateHandle[KEY_SETTINGS_OPEN] = false
    }

    fun setDarkTheme(isDark: Boolean) {
        userPreferencesRepository.setDarkTheme(isDark)
        viewModelScope.launch { widgetRefresher.refreshAll() }
    }

    fun toggleTheme() {
        userPreferencesRepository.setDarkTheme(!userPreferencesRepository.preferences.value.isDarkTheme)
        viewModelScope.launch { widgetRefresher.refreshAll() }
    }

    fun setAccent(accent: ZeroAccent) {
        userPreferencesRepository.setAccent(accent)
        appIconSwitcher.syncAccentIcon(accent)
        viewModelScope.launch { widgetRefresher.refreshAll() }
    }

    fun toggleAccent() {
        val next = userPreferencesRepository.preferences.value.accent.next()
        userPreferencesRepository.setAccent(next)
        appIconSwitcher.syncAccentIcon(next)
        viewModelScope.launch { widgetRefresher.refreshAll() }
    }

    fun setAntiMisclickMode(mode: AntiMisclickMode) = userPreferencesRepository.setAntiMisclickMode(mode)

    fun setTaskSwipeAction(direction: TaskSwipeDirection, action: TaskSwipeAction) =
        userPreferencesRepository.setTaskSwipeAction(direction, action)

    fun setHapticsEnabled(enabled: Boolean) = userPreferencesRepository.setHapticsEnabled(enabled)

    fun setAppLanguage(language: AppLanguage) {
        userPreferencesRepository.setAppLanguage(language)
        viewModelScope.launch { widgetRefresher.refreshAll() }
    }

    override fun setWorkspaceMode(mode: WorkspaceMode) {
        userPreferencesRepository.setWorkspaceMode(mode)
    }

    override fun setExecuteTheme(theme: ExecuteTheme) {
        userPreferencesRepository.setExecuteTheme(theme)
    }

    fun setDailyCapacityMinutes(minutes: Int) {
        userPreferencesRepository.setDailyCapacityMinutes(minutes)
    }

    fun setFolderPathDisplayMode(mode: FolderPathDisplayMode) {
        userPreferencesRepository.setFolderPathDisplayMode(mode)
        viewModelScope.launch { widgetRefresher.refreshAll() }
    }

    fun setPriorityDisplayStyle(style: PriorityDisplayStyle) {
        userPreferencesRepository.setPriorityDisplayStyle(style)
    }

    fun updateNotificationPreferences(config: NotificationPreferences) {
        userPreferencesRepository.setNotificationPreferences(config)
        reminderScheduler.syncDailyBriefings(config)
    }

    override fun openTriage() {
        triageOpenFlow.value = true
    }

    fun closeTriage() {
        triageOpenFlow.value = false
    }

    override fun selectFocusedRunwayTask(taskId: String) {
        focusedRunwayTaskIdFlow.value = taskId
    }

    override fun focusNextRunwayTask() {
        val queue = uiState.value.runwayQueue
        if (queue.size <= 1) return
        val currentId = uiState.value.focusedRunwayTaskId ?: queue.first().id
        val idx = queue.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        focusedRunwayTaskIdFlow.value = queue[(idx + 1) % queue.size].id
    }

    override fun focusPreviousRunwayTask() {
        val queue = uiState.value.runwayQueue
        if (queue.size <= 1) return
        val currentId = uiState.value.focusedRunwayTaskId ?: queue.first().id
        val idx = queue.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        focusedRunwayTaskIdFlow.value = queue[(idx - 1 + queue.size) % queue.size].id
    }

    override fun skipTaskInRunway(task: TaskItem) {
        if (pinnedRunwayTaskIdFlow.value == task.id) {
            pinnedRunwayTaskIdFlow.value = null
        }
        if (focusedRunwayTaskIdFlow.value == task.id) {
            val queue = uiState.value.runwayQueue
            val nextCandidate = queue.firstOrNull { it.id != task.id }?.id
            focusedRunwayTaskIdFlow.value = nextCandidate
        }
        skippedRunwayIdsFlow.value = skippedRunwayIdsFlow.value.filterNot { it == task.id } + task.id
    }

    /**
     * ADHD Blind Draw ("盲抽") — breaks choice paralysis by drawing a different runway task
     * to the top spotlight card when multiple tasks are queued.
     */
    fun shuffleRunway() {
        val queue = uiState.value.runwayQueue
        if (queue.size <= 1) return
        val currentTopId = queue.first().id
        val others = queue.drop(1)
        val picked = others.random()
        skippedRunwayIdsFlow.value = skippedRunwayIdsFlow.value.filterNot { it == picked.id }
        if (picked.id != currentTopId) {
            pinnedRunwayTaskIdFlow.value = picked.id
        }
    }

    override fun openTaskDetailFromNow(task: TaskItem) {
        selectTask(task.id)
        userPreferencesRepository.setWorkspaceMode(WorkspaceMode.VAULT)
    }

    override fun updateTaskFirstStep(task: TaskItem, firstStep: String?) {
        val clean = firstStep?.trim()?.ifBlank { null }
        viewModelScope.launch {
            taskRepository.updateTask(task.id) { current ->
                current.copy(
                    firstStep = clean,
                    firstStepCompleted = clean == current.firstStep && current.firstStepCompleted
                )
            }
        }
    }

    /**
     * Marks the lightweight First Tiny Step done and smoothly launches Forward Momentum (Stopwatch)
     * flow mode so the user rides dopamine straight into execution without countdown stress.
     */
    override fun completeFirstStepAndStartFlow(task: TaskItem) {
        viewModelScope.launch {
            val updated = taskRepository.updateTask(task.id) { it.copy(firstStepCompleted = true) } ?: return@launch
            focusController.openFocus(
                task = updated,
                initialMode = FocusMode.STOPWATCH,
                autoStart = true
            )
        }
    }

    override fun updateTaskEffort(task: TaskItem, estimatedMinutes: Int?) {
        viewModelScope.launch {
            taskRepository.updateTask(task.id) { it.copy(estimatedMinutes = estimatedMinutes) }
        }
    }

    fun triageScheduleToday(task: TaskItem) {
        val today = currentDateFlow.value
        viewModelScope.launch {
            taskRepository.rescheduleTasksToDate(listOf(task.id), today)
            taskRepository.getTaskById(task.id)?.let { syncTaskReminder(it) }
            widgetRefresher.refreshAll()
        }
    }

    fun triageDeferTomorrow(task: TaskItem) {
        val tomorrow = currentDateFlow.value.plusDays(1)
        viewModelScope.launch {
            taskRepository.rescheduleTasksToDate(listOf(task.id), tomorrow)
            taskRepository.getTaskById(task.id)?.let { syncTaskReminder(it) }
            widgetRefresher.refreshAll()
        }
    }

    fun triageMoveToFolder(task: TaskItem, folderId: String) {
        viewModelScope.launch {
            taskRepository.moveTaskToFolder(task.id, folderId)
            widgetRefresher.refreshAll()
        }
    }

    fun createTag(name: String, parentId: String? = null) {
        viewModelScope.launch {
            if (TagTreeBuilder.parseTagPathSegments(name).isEmpty()) return@launch
            tagRepository.createTag(name = name, parentId = parentId)
            widgetRefresher.refreshAll()
        }
    }

    fun toggleTagExpanded(tagId: String, isExpanded: Boolean) {
        viewModelScope.launch {
            tagRepository.setTagExpanded(tagId, isExpanded)
        }
    }

    fun moveTag(tagId: String, newParentId: String?) {
        viewModelScope.launch {
            if (tagRepository.moveTag(tagId, newParentId)) {
                widgetRefresher.refreshAll()
            }
        }
    }

    fun renameTag(tagId: String, newName: String) {
        viewModelScope.launch {
            tagRepository.renameTag(tagId, newName)
            widgetRefresher.refreshAll()
        }
    }

    fun deleteTag(tagId: String) {
        viewModelScope.launch {
            val subtreeIds = TagTreeBuilder.collectDescendantIds(
                rootTagId = tagId,
                tags = uiState.value.tagsWithCounts.map { it.tag }
            )
            tagRepository.softDeleteTag(tagId)
            val currentDest = NavigationDestination.fromStorageKey(destinationKeyFlow.value)
            if (currentDest is NavigationDestination.TagView &&
                (currentDest.tagId == tagId || currentDest.tagId in subtreeIds)
            ) {
                selectDestination(NavigationDestination.Inbox)
            }
            widgetRefresher.refreshAll()
        }
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

    override fun editQuickInputText(edit: (String) -> String) {
        updateQuickInputText(edit(quickInputTextFlow.value))
    }

    fun prefillQuickInputPriority(priority: TaskPriority) {
        val prefix = if (priority == TaskPriority.NONE) "" else "!${priority.value} "
        savedStateHandle[KEY_QUICK_INPUT] = prefix
        selectDestination(NavigationDestination.Inbox)
        quickInputFocusRequestTickFlow.value = System.currentTimeMillis()
    }

    override fun toggleContinuousEntry() {
        savedStateHandle[KEY_CONTINUOUS_ENTRY] = !continuousEntryFlow.value
    }

    override fun submitQuickInput() {
        val raw = quickInputTextFlow.value.trim()
        if (raw.isEmpty()) return
        savedStateHandle[KEY_QUICK_INPUT] = ""
        viewModelScope.launch {
            createStructuredTaskFromText(raw)
            widgetRefresher.refreshAll()
        }
    }

    override fun submitVoiceInput(spokenText: String) {
        val trimmed = spokenText.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            createStructuredTaskFromText(trimmed)
            widgetRefresher.refreshAll()
        }
    }

    fun createFolder(name: String, parentId: String?, colorHex: String? = null) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val created = folderRepository.createFolder(
                name = trimmed,
                parentId = parentId,
                colorHex = if (parentId == null) colorHex else null
            )
            if (parentId != null) {
                folderRepository.setFolderExpanded(parentId, true)
            }
            selectDestination(NavigationDestination.FolderView(created.id))
        }
    }

    fun updateFolderColor(folderId: String, colorHex: String?) {
        viewModelScope.launch {
            val existingIcon = folderRepository.getFolderById(folderId)?.icon
            folderRepository.updateFolderAppearance(folderId, existingIcon, colorHex)
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

    override fun toggleTaskCompletion(task: TaskItem) {
        viewModelScope.launch {
            val newCompleted = !task.isCompleted
            val spawned = if (newCompleted) {
                val copies = taskRepository.completeTask(task.id, currentDateFlow.value) ?: return@launch
                reminderScheduler.cancelTaskReminder(task.id)
                copies.forEach(::syncTaskReminder)
                copies
            } else {
                taskRepository.setTaskCompleted(task.id, false)
                taskRepository.getTaskById(task.id)?.let(::syncTaskReminder)
                emptyList()
            }

            focusController.syncFocusedTask(task.copy(isCompleted = newCompleted))
            if (newCompleted && pinnedRunwayTaskIdFlow.value == task.id) {
                pinnedRunwayTaskIdFlow.value = null
            }
            if (newCompleted && focusedRunwayTaskIdFlow.value == task.id) {
                focusedRunwayTaskIdFlow.value = null
            }
            undoBannerFlow.value = UndoBannerState(
                taskId = task.id,
                taskTitle = task.title,
                undoType = if (newCompleted) UndoType.REVERT_COMPLETE else UndoType.REVERT_UNCOMPLETE,
                spawnedTaskId = spawned.firstOrNull()?.id
            )
            widgetRefresher.refreshAll()
        }
    }

    fun indentTask(task: TaskItem, parentTaskId: String) {
        viewModelScope.launch {
            val change = taskRepository.placeTaskInList(task.id, parentTaskId) ?: return@launch
            showTaskPlacementUndo(task, change)
        }
    }

    fun outdentTask(task: TaskItem) {
        viewModelScope.launch {
            val change = taskRepository.outdentTask(task.id) ?: return@launch
            showTaskPlacementUndo(task, change)
        }
    }

    fun reorderTask(task: TaskItem, parentTaskId: String?, beforeTaskId: String?) {
        if (userPreferencesRepository.preferences.value.taskSortMode != TaskSortMode.MANUAL) return
        if (searchOpenFlow.value && searchQueryFlow.value.isNotBlank()) return
        val dest = NavigationDestination.fromStorageKey(destinationKeyFlow.value)
        if (!dest.showsTaskHierarchy) return
        val acrossFolders = dest == NavigationDestination.All ||
            (dest is NavigationDestination.FolderView && includeSubfoldersFlow.value)
        val targetFolderId = (dest as? NavigationDestination.FolderView)?.folderId
        viewModelScope.launch {
            val change = taskRepository.placeTaskInList(task.id, parentTaskId, beforeTaskId, targetFolderId, acrossFolders)
                ?: return@launch
            showTaskPlacementUndo(task, change)
        }
    }

    private suspend fun showTaskPlacementUndo(task: TaskItem, change: TaskPlacementChange) {
        undoBannerFlow.value = UndoBannerState(
            taskId = task.id,
            taskTitle = task.title,
            undoType = UndoType.RESTORE_TASK_PLACEMENT,
            placementChange = change
        )
        widgetRefresher.refreshAll()
    }

    fun updateTaskTitleAndDescription(task: TaskItem, title: String, description: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            taskRepository.updateTask(task.id) { it.copy(title = title, description = description) }
                ?.let(::syncTaskReminder)
            widgetRefresher.refreshAll()
        }
    }

    fun updateTaskPriority(task: TaskItem, priority: TaskPriority) {
        viewModelScope.launch {
            taskRepository.updateTask(task.id) { it.copy(priority = priority) }
            widgetRefresher.refreshAll()
        }
    }

    fun updateTaskDueDate(task: TaskItem, dueDateMillis: Long?, isAllDay: Boolean) {
        viewModelScope.launch {
            taskRepository.updateTask(task.id) { current ->
                val updatedReminder = when {
                    dueDateMillis == null -> null
                    !isAllDay && current.reminderAt != null && current.dueDate != null -> {
                        val offset = (current.dueDate - current.reminderAt).coerceAtLeast(0L)
                        dueDateMillis - offset
                    }
                    !isAllDay && current.reminderAt == null -> dueDateMillis
                    else -> current.reminderAt
                }
                current.copy(dueDate = dueDateMillis, isAllDay = isAllDay, reminderAt = updatedReminder)
            }?.let(::syncTaskReminder)
            widgetRefresher.refreshAll()
        }
    }

    fun updateTaskReminder(task: TaskItem, reminderAtMillis: Long?) {
        viewModelScope.launch {
            taskRepository.updateTask(task.id) { it.copy(reminderAt = reminderAtMillis) }?.let(::syncTaskReminder)
        }
    }

    fun updateTaskRecurrenceRule(task: TaskItem, rrule: String?) {
        viewModelScope.launch {
            taskRepository.updateTask(task.id) { it.copy(recurrenceRule = rrule) }
        }
    }

    fun addSubtask(parentTaskId: String, title: String) {
        val raw = title.trim()
        if (raw.isEmpty()) return
        val parsed = QuickInputParser.parse(raw)
        val resolvedTitle = parsed.title.ifBlank { raw }
        if (resolvedTitle.isBlank()) return
        viewModelScope.launch {
            val parent = taskRepository.getTaskById(parentTaskId) ?: return@launch
            val inheritedPriority = if (parsed.priority != TaskPriority.NONE) parsed.priority else parent.priority
            val resolvedDueDate = parsed.dueDateMillis
            val resolvedIsAllDay = if (resolvedDueDate != null) parsed.isAllDay else true
            val resolvedReminderAt = if (resolvedDueDate != null && !resolvedIsAllDay) resolvedDueDate else null
            val created = taskRepository.createTask(
                title = resolvedTitle,
                folderId = parent.folderId,
                parentTaskId = parent.id,
                priority = inheritedPriority,
                dueDate = resolvedDueDate,
                isAllDay = resolvedIsAllDay,
                reminderAt = resolvedReminderAt
            )
            parsed.tags.forEach { tagName ->
                val tag = tagRepository.getOrCreateTag(tagName)
                tagRepository.attachTagToTask(created.id, tag.id)
            }
            syncTaskReminder(created)
            widgetRefresher.refreshAll()
        }
    }

    fun openTaskParentPicker(task: TaskItem) {
        _taskParentPicker.value = TaskParentPickerState(task.id)
    }

    fun closeTaskParentPicker() {
        if (_taskParentPicker.value?.saving != true) _taskParentPicker.value = null
    }

    fun chooseTaskParent(parentTaskId: String) {
        val picker = _taskParentPicker.value?.takeUnless { it.saving } ?: return
        val saving = picker.copy(saving = true, failed = false)
        _taskParentPicker.value = saving
        viewModelScope.launch {
            val moved = try {
                taskRepository.moveTaskUnderParent(picker.taskId, parentTaskId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            }
            if (_taskParentPicker.value == saving) {
                _taskParentPicker.value = if (moved) null else picker.copy(failed = true)
            }
            if (moved) widgetRefresher.refreshAll()
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
            val deletedIds = taskRepository.softDeleteTask(task.id)
            if (deletedIds.isEmpty()) return@launch
            deletedIds.forEach(reminderScheduler::cancelTaskReminder)
            if (deletedIds.any { it == selectedTaskIdFlow.value }) {
                selectTask(null)
            }
            if (deletedIds.any { it == pinnedRunwayTaskIdFlow.value }) {
                pinnedRunwayTaskIdFlow.value = null
            }
            if (deletedIds.any { it == focusedRunwayTaskIdFlow.value }) {
                focusedRunwayTaskIdFlow.value = null
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
            restoreTaskAndReminders(taskId)
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

    override fun performUndo(banner: UndoBannerState) {
        if (!undoBannerFlow.compareAndSet(banner, null)) return
        viewModelScope.launch {
            when (banner.undoType) {
                UndoType.REVERT_COMPLETE -> {
                    taskRepository.setTaskCompleted(banner.taskId, false)
                    // Cleanly roll back any auto-spawned recurring task copy
                    banner.spawnedTaskId?.let { spawnedId ->
                        reminderScheduler.cancelTaskReminder(spawnedId)
                        taskRepository.getDescendantSubtasksSnapshot(spawnedId).forEach {
                            reminderScheduler.cancelTaskReminder(it.id)
                        }
                        taskRepository.permanentlyDeleteTask(spawnedId)
                    }
                    taskRepository.getTaskById(banner.taskId)?.let { syncTaskReminder(it) }
                }
                UndoType.REVERT_UNCOMPLETE -> {
                    taskRepository.setTaskCompleted(banner.taskId, true)
                    reminderScheduler.cancelTaskReminder(banner.taskId)
                }
                UndoType.RESTORE_DELETED_TASK -> {
                    restoreTaskAndReminders(banner.taskId)
                }
                UndoType.RESTORE_TASK_PLACEMENT -> {
                    banner.placementChange?.let { taskRepository.undoTaskPlacement(it) }
                }
            }
            widgetRefresher.refreshAll()
        }
    }

    override fun dismissUndoBanner(bannerId: Long) {
        if (undoBannerFlow.value?.id == bannerId) {
            undoBannerFlow.value = null
        }
    }

    // Focus System intents
    override fun openFocusForTask(task: TaskItem?, initialMode: FocusMode) {
        val target = task
            ?: uiState.value.selectedTask
            ?: uiState.value.tasks.firstOrNull { !it.isCompleted }
        focusController.openFocus(task = target, initialMode = initialMode, autoStart = true)
    }

    override fun selectFocusMode(mode: FocusMode) = focusController.selectMode(mode)
    override fun startOrResumeFocus() = focusController.startOrResume()
    override fun pauseFocus() = focusController.pause()
    override fun resetFocus() = focusController.reset()
    override fun continueFocusToPomodoro() = focusController.continueToPomodoro()
    override fun stopStopwatchFocus() = focusController.stopAndRecordStopwatchIfElapsed()
    override fun exitFocus() = focusController.exitFocus()

    override fun completeFocusedTask(task: TaskItem) {
        toggleTaskCompletion(task)
        focusController.exitFocus()
    }

    private fun syncTaskReminder(task: TaskItem) = reminderScheduler.syncTaskReminder(task)

    private suspend fun restoreTaskAndReminders(taskId: String) {
        taskRepository.restoreTask(taskId)
        taskRepository.getTaskById(taskId)?.let(::syncTaskReminder)
        taskRepository.getDescendantSubtasksSnapshot(taskId).forEach(::syncTaskReminder)
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

    // =========================================================================
    // Built-in BYOK AI Engine Workflows (Capture / Image OCR & Breakdown)
    // =========================================================================

    fun updateAiConfig(config: AiConfig) {
        userPreferencesRepository.setAiConfig(config)
        if (_aiTestStatus.value != null) {
            _aiTestStatus.value = null
        }
    }

    fun testAiConnection() {
        val cfg = userPreferencesRepository.preferences.value.aiConfig
        _aiTestStatus.value = AiTestStatus.Working
        viewModelScope.launch {
            val result = aiEngine.testConnection(cfg)
            _aiTestStatus.value = result.fold(
                onSuccess = { model -> AiTestStatus.Success(model) },
                onFailure = { err -> AiTestStatus.Error(err.message ?: "Request failed") }
            )
        }
    }

    override fun openAiCapture(initialText: String, autoLaunchCamera: Boolean) {
        _aiCaptureState.value = AiCaptureDialogState(
            isOpen = true,
            initialText = initialText,
            autoLaunchCamera = autoLaunchCamera
        )
    }

    fun closeAiCapture() {
        _aiCaptureState.value = AiCaptureDialogState()
    }

    fun analyzeAiCapture(text: String, image: AiImagePayload?, languageTag: String) {
        val cfg = userPreferencesRepository.preferences.value.aiConfig
        _aiCaptureState.value = _aiCaptureState.value.copy(
            isAnalyzing = true,
            autoLaunchCamera = false,
            errorMessage = null
        )
        viewModelScope.launch {
            val ctx = buildAiWorkspaceContext(languageTag)
            val result = aiEngine.captureTasks(
                config = cfg,
                rawText = text,
                image = image,
                context = ctx
            )
            _aiCaptureState.value = result.fold(
                onSuccess = { drafts ->
                    _aiCaptureState.value.copy(
                        isAnalyzing = false,
                        results = drafts,
                        errorMessage = null
                    )
                },
                onFailure = { err ->
                    _aiCaptureState.value.copy(
                        isAnalyzing = false,
                        errorMessage = err.message ?: "AI analysis failed"
                    )
                }
            )
        }
    }

    fun applyAiCapturedTasks(drafts: List<AiCapturedTaskDraft>) {
        if (drafts.isEmpty()) return
        val sourceInitialText = _aiCaptureState.value.initialText
        viewModelScope.launch {
            val zoneId = ZoneId.systemDefault()
            val fallbackDest = NavigationDestination.fromStorageKey(destinationKeyFlow.value)
            val fallbackFolderId = (fallbackDest as? NavigationDestination.FolderView)?.folderId
            val todayStart = ZeroDateFormatter.startOfDayMillis(currentDateFlow.value)

            for (draft in drafts) {
                val cleanTitle = draft.title.trim()
                if (cleanTitle.isEmpty()) continue
                val folderSegments = draft.folderPath
                    ?.split('/')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()
                val resolvedFolderId = if (folderSegments.isNotEmpty()) {
                    resolveOrCreateFolderPath(folderSegments)
                } else {
                    null
                }

                val parsedDue = ZeroAiJsonParser.parseIsoDueDate(draft.dueDateIso, zoneId)
                val resolvedDueMillis = parsedDue?.first ?: if (fallbackDest == NavigationDestination.Today) todayStart else null
                val resolvedAllDay = parsedDue?.second ?: true
                val resolvedReminder = if (resolvedDueMillis != null && !resolvedAllDay) resolvedDueMillis else null

                val createdParent = taskRepository.createTask(
                    title = cleanTitle,
                    folderId = resolvedFolderId,
                    description = draft.description,
                    priority = draft.priority,
                    dueDate = resolvedDueMillis,
                    isAllDay = resolvedAllDay,
                    reminderAt = resolvedReminder,
                    firstStep = draft.firstStep,
                    estimatedMinutes = draft.estimatedMinutes
                )
                syncTaskReminder(createdParent)

                for (tagName in draft.tags) {
                    val cleanTag = tagName.trim().removePrefix("#").trim()
                    if (cleanTag.isNotEmpty()) {
                        val tag = tagRepository.getOrCreateTag(cleanTag)
                        tagRepository.attachTagToTask(createdParent.id, tag.id)
                    }
                }
                if (fallbackDest is NavigationDestination.TagView) {
                    tagRepository.attachTagToTask(createdParent.id, fallbackDest.tagId)
                }

                for (sub in draft.subtasks) {
                    val subTitle = sub.title.trim()
                    if (subTitle.isEmpty()) continue
                    val subDue = ZeroAiJsonParser.parseIsoDueDate(sub.dueDateIso, zoneId)
                    val subPriority = if (sub.priority != TaskPriority.NONE) sub.priority else draft.priority
                    val createdSub = taskRepository.createTask(
                        title = subTitle,
                        folderId = resolvedFolderId,
                        parentTaskId = createdParent.id,
                        priority = subPriority,
                        dueDate = subDue?.first,
                        isAllDay = subDue?.second ?: true,
                        reminderAt = if (subDue != null && !subDue.second) subDue.first else null,
                        estimatedMinutes = sub.estimatedMinutes
                    )
                    syncTaskReminder(createdSub)
                    for (tagName in sub.tags) {
                        val cleanTag = tagName.trim().removePrefix("#").trim()
                        if (cleanTag.isNotEmpty()) {
                            val tag = tagRepository.getOrCreateTag(cleanTag)
                            tagRepository.attachTagToTask(createdSub.id, tag.id)
                        }
                    }
                }
            }

            if (sourceInitialText.isNotBlank() && quickInputTextFlow.value == sourceInitialText) {
                savedStateHandle[KEY_QUICK_INPUT] = ""
            }
            _aiCaptureState.value = AiCaptureDialogState()
            widgetRefresher.refreshAll()
        }
    }

    override fun openAiBreakdown(task: TaskItem, languageTag: String) {
        _aiBreakdownState.value = AiBreakdownDialogState(
            task = task,
            isAnalyzing = userPreferencesRepository.preferences.value.aiConfig.isConfigured
        )
        if (!userPreferencesRepository.preferences.value.aiConfig.isConfigured) return
        launchAiBreakdownAnalysis(task, languageTag)
    }

    fun retryAiBreakdown(languageTag: String) {
        val target = _aiBreakdownState.value.task ?: return
        _aiBreakdownState.value = AiBreakdownDialogState(task = target, isAnalyzing = true)
        launchAiBreakdownAnalysis(target, languageTag)
    }

    fun closeAiBreakdown() {
        _aiBreakdownState.value = AiBreakdownDialogState()
    }

    private fun launchAiBreakdownAnalysis(task: TaskItem, languageTag: String) {
        val cfg = userPreferencesRepository.preferences.value.aiConfig
        viewModelScope.launch {
            val state = uiState.value
            val folderPath = task.folderId?.let { state.folderFullPathById[it] ?: state.folderNameById[it] }
            val existingTags = task.tags.map { state.tagPathById[it.id] ?: it.name }
            val ctx = buildAiWorkspaceContext(languageTag)
            val result = aiEngine.breakdownTask(
                config = cfg,
                taskTitle = task.title,
                taskDescription = task.description,
                existingFolderPath = folderPath,
                existingTags = existingTags,
                context = ctx
            )
            _aiBreakdownState.value = result.fold(
                onSuccess = { draft ->
                    _aiBreakdownState.value.copy(
                        isAnalyzing = false,
                        draft = draft,
                        errorMessage = null
                    )
                },
                onFailure = { err ->
                    _aiBreakdownState.value.copy(
                        isAnalyzing = false,
                        errorMessage = err.message ?: "AI breakdown failed"
                    )
                }
            )
        }
    }

    fun applyAiBreakdown(
        task: TaskItem,
        firstStep: String?,
        estimatedMinutes: Int?,
        subtasks: List<AiSubtaskDraft>,
        checklistItems: List<String>
    ) {
        viewModelScope.launch {
            val latestParent = taskRepository.getTaskById(task.id) ?: task
            val cleanFirstStep = firstStep?.trim()?.take(36)?.ifBlank { null }
            val checklistBlock = checklistItems
                .map { it.trim().removePrefix("- [ ]").trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n") { "- [ ] $it" }
            val mergedDesc = when {
                checklistBlock.isEmpty() -> latestParent.description
                latestParent.description.isBlank() -> checklistBlock
                else -> "${latestParent.description.trimEnd()}\n$checklistBlock"
            }

            if (cleanFirstStep != null || estimatedMinutes != null || checklistBlock.isNotEmpty()) {
                taskRepository.updateTask(latestParent.id) { cur ->
                    cur.copy(
                        firstStep = cleanFirstStep ?: cur.firstStep,
                        firstStepCompleted = if (cleanFirstStep != null && cleanFirstStep != cur.firstStep) false else cur.firstStepCompleted,
                        estimatedMinutes = estimatedMinutes ?: cur.estimatedMinutes,
                        description = mergedDesc
                    )
                }
            }

            val zoneId = ZoneId.systemDefault()
            for (sub in subtasks) {
                val subTitle = sub.title.trim()
                if (subTitle.isEmpty()) continue
                val subDue = ZeroAiJsonParser.parseIsoDueDate(sub.dueDateIso, zoneId)
                val subPriority = if (sub.priority != TaskPriority.NONE) sub.priority else latestParent.priority
                val createdSub = taskRepository.createTask(
                    title = subTitle,
                    folderId = latestParent.folderId,
                    parentTaskId = latestParent.id,
                    priority = subPriority,
                    dueDate = subDue?.first,
                    isAllDay = subDue?.second ?: true,
                    reminderAt = if (subDue != null && !subDue.second) subDue.first else null,
                    estimatedMinutes = sub.estimatedMinutes
                )
                syncTaskReminder(createdSub)
                for (tagName in sub.tags) {
                    val cleanTag = tagName.trim().removePrefix("#").trim()
                    if (cleanTag.isNotEmpty()) {
                        val tag = tagRepository.getOrCreateTag(cleanTag)
                        tagRepository.attachTagToTask(createdSub.id, tag.id)
                    }
                }
            }

            _aiBreakdownState.value = AiBreakdownDialogState()
            widgetRefresher.refreshAll()
        }
    }

    private fun buildAiWorkspaceContext(languageTag: String): AiWorkspaceContext {
        val today = currentDateFlow.value
        val state = uiState.value
        return AiWorkspaceContext(
            currentDateIso = today.toString(),
            currentWeekday = today.dayOfWeek.name,
            languageTag = languageTag,
            folderPaths = state.folderFullPathById.values.sorted(),
            tagPaths = state.tagPathById.values.sorted()
        )
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
