package com.zero.todo.data.preferences

import android.content.Context
import com.zero.todo.core.designsystem.ZeroAccent
import com.zero.todo.domain.model.AntiMisclickMode
import com.zero.todo.domain.model.AppLanguage
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
    val appLanguage: AppLanguage = AppLanguage.SYSTEM
)

interface UserPreferencesRepository {
    val preferences: StateFlow<UserPreferences>

    fun setDarkTheme(isDark: Boolean)
    fun setAccent(accent: ZeroAccent)
    fun setAntiMisclickMode(mode: AntiMisclickMode)
    fun setHapticsEnabled(enabled: Boolean)
    fun setAppLanguage(language: AppLanguage)
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
        return UserPreferences(
            isDarkTheme = isDark,
            accent = accent,
            antiMisclickMode = antiMisclickMode,
            hapticsEnabled = hapticsEnabled,
            appLanguage = appLanguage
        )
    }

    companion object {
        const val PREFS_NAME = "zero_user_preferences"
        private const val KEY_DARK_THEME = "is_dark_theme"
        private const val KEY_ACCENT = "accent"
        private const val KEY_ANTI_MISCLICK_MODE = "anti_misclick_mode"
        private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
        const val KEY_APP_LANGUAGE = "app_language"
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
}
