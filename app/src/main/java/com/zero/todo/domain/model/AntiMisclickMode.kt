package com.zero.todo.domain.model

import androidx.annotation.StringRes
import com.zero.todo.R

/**
 * Configurable anti-misclick completion mode for [com.zero.todo.feature.tasklist.AntiMisclickTaskCard].
 */
enum class AntiMisclickMode(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int
) {
    HOLD_CONFIRM(
        titleRes = R.string.antimisclick_hold_title,
        descriptionRes = R.string.antimisclick_hold_desc
    ),
    SWIPE_DAMPED(
        titleRes = R.string.antimisclick_swipe_title,
        descriptionRes = R.string.antimisclick_swipe_desc
    ),
    BOTH(
        titleRes = R.string.antimisclick_both_title,
        descriptionRes = R.string.antimisclick_both_desc
    );

    val allowHoldConfirm: Boolean
        get() = this == HOLD_CONFIRM || this == BOTH

    val allowSwipeComplete: Boolean
        get() = this == SWIPE_DAMPED || this == BOTH

    companion object {
        fun fromStorage(raw: String?): AntiMisclickMode =
            entries.firstOrNull { it.name == raw } ?: BOTH
    }
}
