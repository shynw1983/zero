package com.zero.todo.widget

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.ui.graphics.Color
import com.zero.todo.core.designsystem.ZeroAccent
import com.zero.todo.core.designsystem.ZeroPaletteTokens
import com.zero.todo.data.preferences.SharedPrefsUserPreferencesRepository
import com.zero.todo.domain.model.AppLanguage
import java.util.Locale

enum class WidgetFilterScope {
    TODAY,
    INBOX,
    NEXT_7_DAYS,
    HIGH_PRIORITY,
    ALL_ACTIVE,
    FOLDER,
    TAG;

    companion object {
        fun fromStorage(raw: String?, fallback: WidgetFilterScope = TODAY): WidgetFilterScope =
            entries.firstOrNull { it.name == raw } ?: fallback
    }
}

enum class WidgetViewMode {
    LIST,
    COMPACT,
    FOCUS_NOW;

    companion object {
        fun fromStorage(raw: String?): WidgetViewMode =
            entries.firstOrNull { it.name == raw } ?: LIST
    }
}

enum class WidgetThemeMode {
    FOLLOW_APP,
    DARK_OBSIDIAN,
    LIGHT_PAPER,
    MONOLITH_ORANGE,
    TRANSLUCENT_DARK,
    TRANSLUCENT_LIGHT;

    companion object {
        fun fromStorage(raw: String?): WidgetThemeMode =
            entries.firstOrNull { it.name == raw } ?: FOLLOW_APP
    }
}

enum class WidgetAccentOption {
    FOLLOW_APP,
    SLATE_BLUE,
    MONOCHROME,
    SAGE,
    WARM_CLAY,
    SIGNAL_ORANGE;

    companion object {
        fun fromStorage(raw: String?): WidgetAccentOption =
            entries.firstOrNull { it.name == raw } ?: FOLLOW_APP
    }
}

data class ZeroWidgetConfig(
    val filterScope: WidgetFilterScope = WidgetFilterScope.TODAY,
    val folderId: String? = null,
    val includeSubfolders: Boolean = true,
    val tagId: String? = null,
    val showCompleted: Boolean = false,
    val viewMode: WidgetViewMode = WidgetViewMode.LIST,
    val themeMode: WidgetThemeMode = WidgetThemeMode.FOLLOW_APP,
    val accentOption: WidgetAccentOption = WidgetAccentOption.FOLLOW_APP
)

data class WidgetVisualPalette(
    val isDark: Boolean,
    val background: Color,
    val cardSurface: Color,
    val surfaceElevated: Color,
    val hairline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val priorityUrgent: Color,
    val priorityHigh: Color,
    val priorityMedium: Color
)

object WidgetConfigStore {
    private const val PREFS_NAME = "zero_widget_configs"
    private const val KEY_FILTER_SCOPE = "filter_scope"
    private const val KEY_FOLDER_ID = "folder_id"
    private const val KEY_INCLUDE_SUBFOLDERS = "include_subfolders"
    private const val KEY_TAG_ID = "tag_id"
    private const val KEY_SHOW_COMPLETED = "show_completed"
    private const val KEY_VIEW_MODE = "view_mode"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_ACCENT_OPTION = "accent_option"

    private fun prefix(appWidgetId: Int): String = "w_${appWidgetId}_"

    fun load(
        context: Context,
        appWidgetId: Int,
        defaultScope: WidgetFilterScope = WidgetFilterScope.TODAY
    ): ZeroWidgetConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val p = prefix(appWidgetId)
        return ZeroWidgetConfig(
            filterScope = WidgetFilterScope.fromStorage(
                prefs.getString(p + KEY_FILTER_SCOPE, defaultScope.name),
                fallback = defaultScope
            ),
            folderId = prefs.getString(p + KEY_FOLDER_ID, null),
            includeSubfolders = prefs.getBoolean(p + KEY_INCLUDE_SUBFOLDERS, true),
            tagId = prefs.getString(p + KEY_TAG_ID, null),
            showCompleted = prefs.getBoolean(p + KEY_SHOW_COMPLETED, false),
            viewMode = WidgetViewMode.fromStorage(prefs.getString(p + KEY_VIEW_MODE, WidgetViewMode.LIST.name)),
            themeMode = WidgetThemeMode.fromStorage(prefs.getString(p + KEY_THEME_MODE, WidgetThemeMode.FOLLOW_APP.name)),
            accentOption = WidgetAccentOption.fromStorage(prefs.getString(p + KEY_ACCENT_OPTION, WidgetAccentOption.FOLLOW_APP.name))
        )
    }

    fun save(context: Context, appWidgetId: Int, config: ZeroWidgetConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val p = prefix(appWidgetId)
        prefs.edit()
            .putString(p + KEY_FILTER_SCOPE, config.filterScope.name)
            .putString(p + KEY_FOLDER_ID, config.folderId)
            .putBoolean(p + KEY_INCLUDE_SUBFOLDERS, config.includeSubfolders)
            .putString(p + KEY_TAG_ID, config.tagId)
            .putBoolean(p + KEY_SHOW_COMPLETED, config.showCompleted)
            .putString(p + KEY_VIEW_MODE, config.viewMode.name)
            .putString(p + KEY_THEME_MODE, config.themeMode.name)
            .putString(p + KEY_ACCENT_OPTION, config.accentOption.name)
            .commit()
    }

    fun delete(context: Context, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (id in appWidgetIds) {
            val p = prefix(id)
            editor.remove(p + KEY_FILTER_SCOPE)
                .remove(p + KEY_FOLDER_ID)
                .remove(p + KEY_INCLUDE_SUBFOLDERS)
                .remove(p + KEY_TAG_ID)
                .remove(p + KEY_SHOW_COMPLETED)
                .remove(p + KEY_VIEW_MODE)
                .remove(p + KEY_THEME_MODE)
                .remove(p + KEY_ACCENT_OPTION)
        }
        editor.apply()
    }
}

private val WidgetSignalOrange = Color(0xFFFF4D00)

fun readAppThemePrefs(context: Context): Pair<Boolean, ZeroAccent> {
    val prefs = context.getSharedPreferences(
        SharedPrefsUserPreferencesRepository.PREFS_NAME,
        Context.MODE_PRIVATE
    )
    val isDark = prefs.getBoolean("is_dark_theme", true)
    val accent = ZeroAccent.fromStorage(prefs.getString("accent", ZeroAccent.SlateBlue.name))
    return isDark to accent
}

fun localizedWidgetContext(context: Context): Context {
    val prefs = context.getSharedPreferences(
        SharedPrefsUserPreferencesRepository.PREFS_NAME,
        Context.MODE_PRIVATE
    )
    val language = AppLanguage.fromStorage(
        prefs.getString(SharedPrefsUserPreferencesRepository.KEY_APP_LANGUAGE, AppLanguage.SYSTEM.name)
    )
    val tag = language.languageTag ?: return context
    val locale = Locale.forLanguageTag(tag)
    val config = Configuration(context.resources.configuration).apply {
        setLocale(locale)
        setLocales(LocaleList(locale))
    }
    return context.createConfigurationContext(config)
}

fun resolveWidgetPalette(
    config: ZeroWidgetConfig,
    appIsDark: Boolean,
    appAccent: ZeroAccent
): WidgetVisualPalette {
    val isDark = when (config.themeMode) {
        WidgetThemeMode.FOLLOW_APP -> appIsDark
        WidgetThemeMode.DARK_OBSIDIAN,
        WidgetThemeMode.MONOLITH_ORANGE,
        WidgetThemeMode.TRANSLUCENT_DARK -> true
        WidgetThemeMode.LIGHT_PAPER,
        WidgetThemeMode.TRANSLUCENT_LIGHT -> false
    }

    val resolvedAccent: Color
    val resolvedOnAccent: Color
    when (config.accentOption) {
        WidgetAccentOption.FOLLOW_APP -> {
            if (config.themeMode == WidgetThemeMode.MONOLITH_ORANGE) {
                resolvedAccent = WidgetSignalOrange
                resolvedOnAccent = Color.White
            } else {
                resolvedAccent = appAccent.colorFor(isDark)
                resolvedOnAccent = appAccent.onAccentFor(isDark)
            }
        }
        WidgetAccentOption.SLATE_BLUE -> {
            resolvedAccent = ZeroAccent.SlateBlue.colorFor(isDark)
            resolvedOnAccent = ZeroAccent.SlateBlue.onAccentFor(isDark)
        }
        WidgetAccentOption.MONOCHROME -> {
            resolvedAccent = ZeroAccent.Monochrome.colorFor(isDark)
            resolvedOnAccent = ZeroAccent.Monochrome.onAccentFor(isDark)
        }
        WidgetAccentOption.SAGE -> {
            resolvedAccent = ZeroAccent.Sage.colorFor(isDark)
            resolvedOnAccent = ZeroAccent.Sage.onAccentFor(isDark)
        }
        WidgetAccentOption.WARM_CLAY -> {
            resolvedAccent = ZeroAccent.WarmClay.colorFor(isDark)
            resolvedOnAccent = ZeroAccent.WarmClay.onAccentFor(isDark)
        }
        WidgetAccentOption.SIGNAL_ORANGE -> {
            resolvedAccent = WidgetSignalOrange
            resolvedOnAccent = Color.White
        }
    }

    return when (config.themeMode) {
        WidgetThemeMode.MONOLITH_ORANGE -> WidgetVisualPalette(
            isDark = true,
            background = Color(0xFF09090B),
            cardSurface = Color(0xFF111115),
            surfaceElevated = Color(0xFF1A1A20),
            hairline = Color(0xFF27272A),
            textPrimary = ZeroPaletteTokens.DarkTextPrimary,
            textSecondary = ZeroPaletteTokens.DarkTextSecondary,
            textMuted = Color(0xFF71717A),
            accent = resolvedAccent,
            onAccent = resolvedOnAccent,
            priorityUrgent = ZeroPaletteTokens.PriorityUrgentDark,
            priorityHigh = ZeroPaletteTokens.PriorityHighDark,
            priorityMedium = ZeroPaletteTokens.PriorityMediumDark
        )
        WidgetThemeMode.TRANSLUCENT_DARK -> WidgetVisualPalette(
            isDark = true,
            background = Color(0xCC09090B),
            cardSurface = Color(0x9916161B),
            surfaceElevated = Color(0xB3222229),
            hairline = Color(0x44FFFFFF),
            textPrimary = ZeroPaletteTokens.DarkTextPrimary,
            textSecondary = Color(0xFFD4D4D8),
            textMuted = Color(0xFF8E8E98),
            accent = resolvedAccent,
            onAccent = resolvedOnAccent,
            priorityUrgent = ZeroPaletteTokens.PriorityUrgentDark,
            priorityHigh = ZeroPaletteTokens.PriorityHighDark,
            priorityMedium = ZeroPaletteTokens.PriorityMediumDark
        )
        WidgetThemeMode.TRANSLUCENT_LIGHT -> WidgetVisualPalette(
            isDark = false,
            background = Color(0xDCF7F7F9),
            cardSurface = Color(0xD8FFFFFF),
            surfaceElevated = Color(0xFFE6E6EC),
            hairline = Color(0x2809090B),
            textPrimary = ZeroPaletteTokens.LightTextPrimary,
            textSecondary = Color(0xFF52525B),
            textMuted = Color(0xFF71717A),
            accent = resolvedAccent,
            onAccent = resolvedOnAccent,
            priorityUrgent = ZeroPaletteTokens.PriorityUrgentLight,
            priorityHigh = ZeroPaletteTokens.PriorityHighLight,
            priorityMedium = ZeroPaletteTokens.PriorityMediumLight
        )
        else -> if (isDark) {
            WidgetVisualPalette(
                isDark = true,
                background = ZeroPaletteTokens.DarkBackground,
                cardSurface = ZeroPaletteTokens.DarkSurface,
                surfaceElevated = ZeroPaletteTokens.DarkSurfaceElevated,
                hairline = ZeroPaletteTokens.DarkHairlineBorder,
                textPrimary = ZeroPaletteTokens.DarkTextPrimary,
                textSecondary = ZeroPaletteTokens.DarkTextSecondary,
                textMuted = ZeroPaletteTokens.DarkTextMuted,
                accent = resolvedAccent,
                onAccent = resolvedOnAccent,
                priorityUrgent = ZeroPaletteTokens.PriorityUrgentDark,
                priorityHigh = ZeroPaletteTokens.PriorityHighDark,
                priorityMedium = ZeroPaletteTokens.PriorityMediumDark
            )
        } else {
            WidgetVisualPalette(
                isDark = false,
                background = ZeroPaletteTokens.LightBackground,
                cardSurface = ZeroPaletteTokens.LightSurface,
                surfaceElevated = ZeroPaletteTokens.LightSurfaceElevated,
                hairline = ZeroPaletteTokens.LightHairlineBorder,
                textPrimary = ZeroPaletteTokens.LightTextPrimary,
                textSecondary = ZeroPaletteTokens.LightTextSecondary,
                textMuted = ZeroPaletteTokens.LightTextMuted,
                accent = resolvedAccent,
                onAccent = resolvedOnAccent,
                priorityUrgent = ZeroPaletteTokens.PriorityUrgentLight,
                priorityHigh = ZeroPaletteTokens.PriorityHighLight,
                priorityMedium = ZeroPaletteTokens.PriorityMediumLight
            )
        }
    }
}
