package com.zero.todo.domain.model

import androidx.annotation.StringRes
import com.zero.todo.R

/**
 * ZERO 2.0 Dual-State Architecture:
 * - [NOW]: Execution Runway — single-card focus deck, zero list overwhelm, first-step momentum.
 * - [VAULT]: Organization Studio — infinite folder tree, calendar, matrix, tags, trash, triage.
 */
enum class WorkspaceMode(@StringRes val labelRes: Int) {
    NOW(R.string.mode_now),
    VAULT(R.string.mode_vault);

    companion object {
        fun fromStorage(raw: String?): WorkspaceMode =
            entries.firstOrNull { it.name == raw } ?: NOW
    }
}

/**
 * Lightweight tactile effort/energy tiers for the Day Capacity Ruler (Day Budget).
 */
enum class EffortTier(
    val minutes: Int,
    @StringRes val durationRes: Int,
    @StringRes val labelRes: Int
) {
    MICRO(5, R.string.effort_duration_5, R.string.effort_micro),
    MEDIUM(25, R.string.effort_duration_25, R.string.effort_medium),
    HEAVY(60, R.string.effort_duration_60, R.string.effort_heavy),
    EXTENDED(120, R.string.effort_duration_120, R.string.effort_extended);

    companion object {
        const val DEFAULT_UNSET_MINUTES = 15

        fun fromMinutes(minutes: Int?): EffortTier? =
            entries.firstOrNull { it.minutes == minutes }
    }
}
