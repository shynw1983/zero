package com.zero.todo.domain.model

import androidx.annotation.StringRes
import com.zero.todo.R

/**
 * Extensible Execution Theme Engine for the ZERO Execute ("NOW / 実行") home screen.
 * Adding a new theme requires declaring an enum entry, registering its UI renderer in
 * `ExecuteHostScreen`, and hooking its preview swatch in `ExecuteThemePickerSheet`.
 */
enum class ExecuteTheme(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int
) {
    MONOLITH(
        titleRes = R.string.execute_theme_monolith_title,
        subtitleRes = R.string.execute_theme_monolith_desc
    ),
    RUNWAY(
        titleRes = R.string.execute_theme_runway_title,
        subtitleRes = R.string.execute_theme_runway_desc
    );

    companion object {
        fun fromStorage(raw: String?): ExecuteTheme =
            entries.firstOrNull { it.name == raw } ?: MONOLITH
    }
}
