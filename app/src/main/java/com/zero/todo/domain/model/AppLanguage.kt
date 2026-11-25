package com.zero.todo.domain.model

import androidx.annotation.StringRes
import com.zero.todo.R
import java.util.Locale

enum class AppLanguage(
    val languageTag: String?,
    val nativeLabel: String,
    @StringRes val labelRes: Int
) {
    SYSTEM(null, "System", R.string.lang_system),
    JA("ja", "日本語", R.string.lang_ja),
    ZH("zh", "简体中文", R.string.lang_zh),
    KO("ko", "한국어", R.string.lang_ko),
    EN("en", "English", R.string.lang_en);

    fun toLocaleOrSystem(systemLocale: Locale = Locale.getDefault()): Locale =
        languageTag?.let { Locale.forLanguageTag(it) } ?: systemLocale

    companion object {
        fun fromStorage(raw: String?): AppLanguage =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}
