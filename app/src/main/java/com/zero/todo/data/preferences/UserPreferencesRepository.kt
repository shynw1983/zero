package com.zero.todo.data.preferences

import android.content.Context
import com.zero.todo.core.designsystem.ZeroAccent
import com.zero.todo.data.backup.WebDavConfig
import com.zero.todo.domain.model.AntiMisclickMode
import com.zero.todo.domain.model.AppLanguage
import com.zero.todo.domain.model.TaskSortMode
import com.zero.todo.domain.model.WorkspaceMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class UserPreferences(
    val isDarkTheme: Boolean = true,
    val accent: ZeroAccent = ZeroAccent.SlateBlue,
    val antiMisclickMode: AntiMisclickMode = AntiMisclickMode.BOTH,
    val hapticsEnabled: Boolean = true,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val taskSortMode: TaskSortMode = TaskSortMode.MANUAL,
    val workspaceMode: WorkspaceMode = WorkspaceMode.NOW,
    val dailyCapacityMinutes: Int = 240,
    val webDavConfig: WebDavConfig = WebDavConfig()
)

interface UserPreferencesRepository {
    val preferences: StateFlow<UserPreferences>

    fun setDarkTheme(isDark: Boolean)
    fun setAccent(accent: ZeroAccent)
    fun setAntiMisclickMode(mode: AntiMisclickMode)
    fun setHapticsEnabled(enabled: Boolean)
    fun setAppLanguage(language: AppLanguage)
    fun setTaskSortMode(sortMode: TaskSortMode)
    fun setWorkspaceMode(mode: WorkspaceMode)
    fun setDailyCapacityMinutes(minutes: Int)
    fun setWebDavConfig(config: WebDavConfig)
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

    override fun setHapticsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTICS_ENABLED, enabled).apply()
        _preferences.update { it.copy(hapticsEnabled = enabled) }
    }

    override fun setAppLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_APP_LANGUAGE, language.name).apply()
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

    override fun setDailyCapacityMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(60, 720)
        prefs.edit().putInt(KEY_DAILY_CAPACITY_MINUTES, clamped).apply()
        _preferences.update { it.copy(dailyCapacityMinutes = clamped) }
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
        val dailyCapacityMinutes = prefs.getInt(KEY_DAILY_CAPACITY_MINUTES, 240).coerceIn(60, 720)
        val webDavConfig = WebDavConfig(
            serverUrl = prefs.getString(KEY_WEBDAV_URL, "").orEmpty(),
            username = prefs.getString(KEY_WEBDAV_USER, "").orEmpty(),
            password = prefs.getString(KEY_WEBDAV_PASS, "").orEmpty(),
            remoteFileName = prefs.getString(KEY_WEBDAV_FILE, WebDavConfig.DEFAULT_REMOTE_FILE)
                .orEmpty()
                .ifBlank { WebDavConfig.DEFAULT_REMOTE_FILE }
        )
        return UserPreferences(
            isDarkTheme = isDark,
            accent = accent,
            antiMisclickMode = antiMisclickMode,
            hapticsEnabled = hapticsEnabled,
            appLanguage = appLanguage,
            taskSortMode = sortMode,
            workspaceMode = workspaceMode,
            dailyCapacityMinutes = dailyCapacityMinutes,
            webDavConfig = webDavConfig
        )
    }

    companion object {
        const val PREFS_NAME = "zero_user_preferences"
        private const val KEY_DARK_THEME = "is_dark_theme"
        private const val KEY_ACCENT = "accent"
        private const val KEY_ANTI_MISCLICK_MODE = "anti_misclick_mode"
        private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
        const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_TASK_SORT_MODE = "task_sort_mode"
        private const val KEY_WORKSPACE_MODE = "workspace_mode"
        private const val KEY_DAILY_CAPACITY_MINUTES = "daily_capacity_minutes"
        private const val KEY_WEBDAV_URL = "webdav_url"
        private const val KEY_WEBDAV_USER = "webdav_user"
        private const val KEY_WEBDAV_PASS = "webdav_pass"
        private const val KEY_WEBDAV_FILE = "webdav_file"
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

    override fun setDailyCapacityMinutes(minutes: Int) {
        _preferences.update { it.copy(dailyCapacityMinutes = minutes.coerceIn(60, 720)) }
    }

    override fun setWebDavConfig(config: WebDavConfig) {
        _preferences.update { it.copy(webDavConfig = config) }
    }
}
