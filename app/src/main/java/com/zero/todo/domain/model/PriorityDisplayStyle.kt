package com.zero.todo.domain.model

/**
 * Controls how task urgency levels are presented visually across task cards,
 * detail selectors, quick-input preview chips, focus cards, and matrix quadrants.
 */
enum class PriorityDisplayStyle {
    /** Option C (default): clean signal dots (●●● / ●● / ●), silent when NONE. */
    DOTS,

    /** Option A: intuitive localized action words (Urgent / High / Medium / None). */
    WORDS,

    /** Option B: compact exclamation marks (!!! / !! / !), silent when NONE. */
    MARKS;

    companion object {
        fun fromStorage(raw: String?): PriorityDisplayStyle =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DOTS
    }
}
