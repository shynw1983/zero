package com.zero.todo.data.preferences

import android.content.Context
import com.zero.todo.core.designsystem.ZeroAccent
import com.zero.todo.data.ai.AiConfig
import com.zero.todo.data.ai.AiProvider
import com.zero.todo.data.backup.WebDavConfig
import com.zero.todo.domain.model.AntiMisclickMode
import com.zero.todo.domain.model.AppLanguage
import com.zero.todo.domain.model.ExecuteTheme
import com.zero.todo.domain.model.FolderPathDisplayMode
import com.zero.todo.domain.model.PriorityDisplayStyle
import com.zero.todo.domain.model.TaskSortMode
import com.zero.todo.domain.model.TaskSwipeAction
import com.zero.todo.domain.model.TaskSwipeActions
import com.zero.todo.domain.model.TaskSwipeDirection
import com.zero.todo.domain.model.WorkspaceMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class NotificationPreferences(
    val defaultAllDayReminderMinutes: Int = 9 * 60,
    val morningBriefingEnabled: Boolean = false,
    val morningBriefingMinutes: Int = 8 * 60 + 30,
    val eveningReviewEnabled: Boolean = false,
    val eveningReviewMinutes: Int = 21 * 60
) {
    companion object {
        fun formatMinutes(totalMinutes: Int): String {
            val clamped = totalMinutes.coerceIn(0, 23 * 60 + 59)
            val h = clamped / 60
            val m = clamped % 60
            return "%02d:%02d".format(h, m)
        }
    }
}

data class UserPreferences(
    val isDarkTheme: Boolean = true,
    val accent: ZeroAccent = ZeroAccent.SlateBlue,
    val antiMisclickMode: AntiMisclickMode = AntiMisclickMode.BOTH,
    val taskSwipeActions: TaskSwipeActions = TaskSwipeActions(),
    val hapticsEnabled: Boolean = true,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val taskSortMode: TaskSortMode = TaskSortMode.MANUAL,
    val workspaceMode: WorkspaceMode = WorkspaceMode.NOW,
    val executeTheme: ExecuteTheme = ExecuteTheme.MONOLITH,
    val dailyCapacityMinutes: Int = 240,
    val folderPathDisplayMode: FolderPathDisplayMode = FolderPathDisplayMode.SMART_DISAMBIGUATE,
    val priorityDisplayStyle: PriorityDisplayStyle = PriorityDisplayStyle.DOTS,
    val notifications: NotificationPreferences = NotificationPreferences(),
    val webDavConfig: WebDavConfig = WebDavConfig(),
    val aiConfig: AiConfig = AiConfig()
)

interface UserPreferencesRepository {
    val preferences: StateFlow<UserPreferences>

    fun setDarkTheme(isDark: Boolean)
    fun setAccent(accent: ZeroAccent)
    fun setAntiMisclickMode(mode: AntiMisclickMode)
    fun setTaskSwipeAction(direction: TaskSwipeDirection, action: TaskSwipeAction)
    fun setHapticsEnabled(enabled: Boolean)
    fun setAppLanguage(language: AppLanguage)
    fun setTaskSortMode(sortMode: TaskSortMode)
    fun setWorkspaceMode(mode: WorkspaceMode)
    fun setExecuteTheme(theme: ExecuteTheme)
    fun setDailyCapacityMinutes(minutes: Int)
    fun setFolderPathDisplayMode(mode: FolderPathDisplayMode)
    fun setPriorityDisplayStyle(style: PriorityDisplayStyle)
    fun setNotificationPreferences(config: NotificationPreferences)
    fun setWebDavConfig(config: WebDavConfig)
    fun setAiConfig(config: AiConfig)
}

@Singleton
class SharedPrefsUserPreferencesRepository @Inject constructor(
    @ApplicationContext context: Context
) : UserPreferencesRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _preferences = MutableStateFlow(loadInitial())
    override val preferences: StateFlow<UserPreferences> = _preferences.asStateFlow()

    override fun setDarkTheme(isDark: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_THEME, isDark).apply()
        _preferences.update { it.copy(isDarkTheme = isDark) }
    }

    override fun setAccent(accent: ZeroAccent) {
        prefs.edit().putString(KEY_ACCENT, accent.name).apply()
        _preferences.update { it.copy(accent = accent) }
    }

    override fun setAntiMisclickMode(mode: AntiMisclickMode) {
        prefs.edit().putString(KEY_ANTI_MISCLICK_MODE, mode.name).apply()
        _preferences.update { it.copy(antiMisclickMode = mode) }
    }

    override fun setTaskSwipeAction(direction: TaskSwipeDirection, action: TaskSwipeAction) {
        val key = when (direction) {
            TaskSwipeDirection.LEFT -> KEY_TASK_SWIPE_LEFT
            TaskSwipeDirection.RIGHT -> KEY_TASK_SWIPE_RIGHT
        }
        prefs.edit().putString(key, action.name).apply()
        _preferences.update { it.copy(taskSwipeActions = it.taskSwipeActions.withAction(direction, action)) }
    }

    override fun setHapticsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTICS_ENABLED, enabled).apply()
        _preferences.update { it.copy(hapticsEnabled = enabled) }
    }

    override fun setAppLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_APP_LANGUAGE, language.name).commit()
        _preferences.update { it.copy(appLanguage = language) }
    }

    override fun setTaskSortMode(sortMode: TaskSortMode) {
        prefs.edit().putString(KEY_TASK_SORT_MODE, sortMode.name).apply()
        _preferences.update { it.copy(taskSortMode = sortMode) }
    }

    override fun setWorkspaceMode(mode: WorkspaceMode) {
        prefs.edit().putString(KEY_WORKSPACE_MODE, mode.name).apply()
        _preferences.update { it.copy(workspaceMode = mode) }
    }

    override fun setExecuteTheme(theme: ExecuteTheme) {
        prefs.edit().putString(KEY_EXECUTE_THEME, theme.name).apply()
        _preferences.update { it.copy(executeTheme = theme) }
    }

    override fun setDailyCapacityMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(60, 720)
        prefs.edit().putInt(KEY_DAILY_CAPACITY_MINUTES, clamped).apply()
        _preferences.update { it.copy(dailyCapacityMinutes = clamped) }
    }

    override fun setFolderPathDisplayMode(mode: FolderPathDisplayMode) {
        prefs.edit().putString(KEY_FOLDER_PATH_DISPLAY_MODE, mode.name).apply()
        _preferences.update { it.copy(folderPathDisplayMode = mode) }
    }

    override fun setPriorityDisplayStyle(style: PriorityDisplayStyle) {
        prefs.edit().putString(KEY_PRIORITY_DISPLAY_STYLE, style.name).apply()
        _preferences.update { it.copy(priorityDisplayStyle = style) }
    }

    override fun setNotificationPreferences(config: NotificationPreferences) {
        val normalized = config.copy(
            defaultAllDayReminderMinutes = config.defaultAllDayReminderMinutes.coerceIn(0, 23 * 60 + 59),
            morningBriefingMinutes = config.morningBriefingMinutes.coerceIn(0, 23 * 60 + 59),
            eveningReviewMinutes = config.eveningReviewMinutes.coerceIn(0, 23 * 60 + 59)
        )
        prefs.edit()
            .putInt(KEY_NOTIF_ALL_DAY_MINUTES, normalized.defaultAllDayReminderMinutes)
            .putBoolean(KEY_NOTIF_MORNING_ENABLED, normalized.morningBriefingEnabled)
            .putInt(KEY_NOTIF_MORNING_MINUTES, normalized.morningBriefingMinutes)
            .putBoolean(KEY_NOTIF_EVENING_ENABLED, normalized.eveningReviewEnabled)
            .putInt(KEY_NOTIF_EVENING_MINUTES, normalized.eveningReviewMinutes)
            .apply()
        _preferences.update { it.copy(notifications = normalized) }
    }

    override fun setWebDavConfig(config: WebDavConfig) {
        prefs.edit()
            .putString(KEY_WEBDAV_URL, config.serverUrl)
            .putString(KEY_WEBDAV_USER, config.username)
            .putString(KEY_WEBDAV_PASS, config.password)
            .putString(KEY_WEBDAV_FILE, config.remoteFileName)
            .apply()
        _preferences.update { it.copy(webDavConfig = config) }
    }

    override fun setAiConfig(config: AiConfig) {
        prefs.edit()
            .putString(KEY_AI_PROVIDER, config.provider.name)
            .putString(KEY_AI_GEMINI_KEY, config.geminiApiKey)
            .putString(KEY_AI_GEMINI_MODEL, config.geminiModel)
            .putString(KEY_AI_OPENAI_KEY, config.openAiApiKey)
            .putString(KEY_AI_OPENAI_MODEL, config.openAiModel)
            .putString(KEY_AI_OPENAI_BASE_URL, config.openAiBaseUrl)
            .apply()
        _preferences.update { it.copy(aiConfig = config) }
    }

    private fun loadInitial(): UserPreferences {
        val isDark = prefs.getBoolean(KEY_DARK_THEME, true)
        val accentName = prefs.getString(KEY_ACCENT, ZeroAccent.SlateBlue.name)
        val accent = ZeroAccent.fromStorage(accentName)
        val antiMisclickMode = AntiMisclickMode.fromStorage(
            prefs.getString(KEY_ANTI_MISCLICK_MODE, AntiMisclickMode.BOTH.name)
        )
        val hapticsEnabled = prefs.getBoolean(KEY_HAPTICS_ENABLED, true)
        val appLanguage = AppLanguage.fromStorage(
            prefs.getString(KEY_APP_LANGUAGE, AppLanguage.SYSTEM.name)
        )
        val sortMode = TaskSortMode.fromStorage(
            prefs.getString(KEY_TASK_SORT_MODE, TaskSortMode.MANUAL.name)
        )
        val workspaceMode = WorkspaceMode.fromStorage(
            prefs.getString(KEY_WORKSPACE_MODE, WorkspaceMode.NOW.name)
        )
        val executeTheme = ExecuteTheme.fromStorage(
            prefs.getString(KEY_EXECUTE_THEME, ExecuteTheme.MONOLITH.name)
        )
        val dailyCapacityMinutes = prefs.getInt(KEY_DAILY_CAPACITY_MINUTES, 240).coerceIn(60, 720)
        val folderPathDisplayMode = FolderPathDisplayMode.fromStorage(
            prefs.getString(KEY_FOLDER_PATH_DISPLAY_MODE, FolderPathDisplayMode.SMART_DISAMBIGUATE.name)
        )
        val priorityDisplayStyle = PriorityDisplayStyle.fromStorage(
            prefs.getString(KEY_PRIORITY_DISPLAY_STYLE, PriorityDisplayStyle.DOTS.name)
        )
        val notifications = NotificationPreferences(
            defaultAllDayReminderMinutes = prefs.getInt(KEY_NOTIF_ALL_DAY_MINUTES, 9 * 60).coerceIn(0, 23 * 60 + 59),
            morningBriefingEnabled = prefs.getBoolean(KEY_NOTIF_MORNING_ENABLED, false),
            morningBriefingMinutes = prefs.getInt(KEY_NOTIF_MORNING_MINUTES, 8 * 60 + 30).coerceIn(0, 23 * 60 + 59),
            eveningReviewEnabled = prefs.getBoolean(KEY_NOTIF_EVENING_ENABLED, false),
            eveningReviewMinutes = prefs.getInt(KEY_NOTIF_EVENING_MINUTES, 21 * 60).coerceIn(0, 23 * 60 + 59)
        )
        val webDavConfig = WebDavConfig(
            serverUrl = prefs.getString(KEY_WEBDAV_URL, "").orEmpty(),
            username = prefs.getString(KEY_WEBDAV_USER, "").orEmpty(),
            password = prefs.getString(KEY_WEBDAV_PASS, "").orEmpty(),
            remoteFileName = prefs.getString(KEY_WEBDAV_FILE, WebDavConfig.DEFAULT_REMOTE_FILE)
                .orEmpty()
                .ifBlank { WebDavConfig.DEFAULT_REMOTE_FILE }
        )
        val aiConfig = AiConfig(
            provider = AiProvider.fromStorage(prefs.getString(KEY_AI_PROVIDER, AiProvider.GEMINI.name)),
            geminiApiKey = prefs.getString(KEY_AI_GEMINI_KEY, "").orEmpty(),
            geminiModel = prefs.getString(KEY_AI_GEMINI_MODEL, AiConfig.DEFAULT_GEMINI_MODEL)
                .orEmpty()
                .ifBlank { AiConfig.DEFAULT_GEMINI_MODEL },
            openAiApiKey = prefs.getString(KEY_AI_OPENAI_KEY, "").orEmpty(),
            openAiModel = prefs.getString(KEY_AI_OPENAI_MODEL, AiConfig.DEFAULT_OPENAI_MODEL)
                .orEmpty()
                .ifBlank { AiConfig.DEFAULT_OPENAI_MODEL },
            openAiBaseUrl = prefs.getString(KEY_AI_OPENAI_BASE_URL, AiConfig.DEFAULT_OPENAI_BASE_URL)
                .orEmpty()
                .ifBlank { AiConfig.DEFAULT_OPENAI_BASE_URL }
        )
        return UserPreferences(
            isDarkTheme = isDark,
            accent = accent,
            antiMisclickMode = antiMisclickMode,
            taskSwipeActions = TaskSwipeActions(
                left = TaskSwipeAction.fromStorage(prefs.getString(KEY_TASK_SWIPE_LEFT, null)),
                right = TaskSwipeAction.fromStorage(prefs.getString(KEY_TASK_SWIPE_RIGHT, null))
            ),
            hapticsEnabled = hapticsEnabled,
            appLanguage = appLanguage,
            taskSortMode = sortMode,
            workspaceMode = workspaceMode,
            executeTheme = executeTheme,
            dailyCapacityMinutes = dailyCapacityMinutes,
            folderPathDisplayMode = folderPathDisplayMode,
            priorityDisplayStyle = priorityDisplayStyle,
            notifications = notifications,
            webDavConfig = webDavConfig,
            aiConfig = aiConfig
        )
    }

    companion object {
        const val PREFS_NAME = "zero_user_preferences"
        private const val KEY_DARK_THEME = "is_dark_theme"
        private const val KEY_ACCENT = "accent"
        private const val KEY_ANTI_MISCLICK_MODE = "anti_misclick_mode"
        private const val KEY_TASK_SWIPE_LEFT = "task_swipe_left"
        private const val KEY_TASK_SWIPE_RIGHT = "task_swipe_right"
        private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
        const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_TASK_SORT_MODE = "task_sort_mode"
        private const val KEY_WORKSPACE_MODE = "workspace_mode"
        private const val KEY_EXECUTE_THEME = "execute_theme"
        private const val KEY_DAILY_CAPACITY_MINUTES = "daily_capacity_minutes"
        private const val KEY_FOLDER_PATH_DISPLAY_MODE = "folder_path_display_mode"
        private const val KEY_PRIORITY_DISPLAY_STYLE = "priority_display_style"
        private const val KEY_NOTIF_ALL_DAY_MINUTES = "notif_all_day_minutes"
        private const val KEY_NOTIF_MORNING_ENABLED = "notif_morning_enabled"
        private const val KEY_NOTIF_MORNING_MINUTES = "notif_morning_minutes"
        private const val KEY_NOTIF_EVENING_ENABLED = "notif_evening_enabled"
        private const val KEY_NOTIF_EVENING_MINUTES = "notif_evening_minutes"
        private const val KEY_WEBDAV_URL = "webdav_url"
        private const val KEY_WEBDAV_USER = "webdav_user"
        private const val KEY_WEBDAV_PASS = "webdav_pass"
        private const val KEY_WEBDAV_FILE = "webdav_file"
        private const val KEY_AI_PROVIDER = "ai_provider"
        private const val KEY_AI_GEMINI_KEY = "ai_gemini_key"
        private const val KEY_AI_GEMINI_MODEL = "ai_gemini_model"
        private const val KEY_AI_OPENAI_KEY = "ai_openai_key"
        private const val KEY_AI_OPENAI_MODEL = "ai_openai_model"
        private const val KEY_AI_OPENAI_BASE_URL = "ai_openai_base_url"
    }
}

class InMemoryUserPreferencesRepository(
    initial: UserPreferences = UserPreferences()
) : UserPreferencesRepository {
    private val _preferences = MutableStateFlow(initial)
    override val preferences: StateFlow<UserPreferences> = _preferences.asStateFlow()

    override fun setDarkTheme(isDark: Boolean) {
        _preferences.update { it.copy(isDarkTheme = isDark) }
    }

    override fun setAccent(accent: ZeroAccent) {
        _preferences.update { it.copy(accent = accent) }
    }

    override fun setAntiMisclickMode(mode: AntiMisclickMode) {
        _preferences.update { it.copy(antiMisclickMode = mode) }
    }

    override fun setTaskSwipeAction(direction: TaskSwipeDirection, action: TaskSwipeAction) {
        _preferences.update { it.copy(taskSwipeActions = it.taskSwipeActions.withAction(direction, action)) }
    }

    override fun setHapticsEnabled(enabled: Boolean) {
        _preferences.update { it.copy(hapticsEnabled = enabled) }
    }

    override fun setAppLanguage(language: AppLanguage) {
        _preferences.update { it.copy(appLanguage = language) }
    }

    override fun setTaskSortMode(sortMode: TaskSortMode) {
        _preferences.update { it.copy(taskSortMode = sortMode) }
    }

    override fun setWorkspaceMode(mode: WorkspaceMode) {
        _preferences.update { it.copy(workspaceMode = mode) }
    }

    override fun setExecuteTheme(theme: ExecuteTheme) {
        _preferences.update { it.copy(executeTheme = theme) }
    }

    override fun setDailyCapacityMinutes(minutes: Int) {
        _preferences.update { it.copy(dailyCapacityMinutes = minutes.coerceIn(60, 720)) }
    }

    override fun setFolderPathDisplayMode(mode: FolderPathDisplayMode) {
        _preferences.update { it.copy(folderPathDisplayMode = mode) }
    }

    override fun setPriorityDisplayStyle(style: PriorityDisplayStyle) {
        _preferences.update { it.copy(priorityDisplayStyle = style) }
    }

    override fun setNotificationPreferences(config: NotificationPreferences) {
        _preferences.update { it.copy(notifications = config) }
    }

    override fun setWebDavConfig(config: WebDavConfig) {
        _preferences.update { it.copy(webDavConfig = config) }
    }

    override fun setAiConfig(config: AiConfig) {
        _preferences.update { it.copy(aiConfig = config) }
    }
}
