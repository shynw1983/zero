package com.zero.todo.domain.model

import androidx.annotation.StringRes
import com.zero.todo.R

enum class TaskSortMode(@StringRes val labelRes: Int) {
    MANUAL(R.string.sort_manual),
    DUE_DATE(R.string.sort_due_date),
    PRIORITY(R.string.sort_priority),
    CREATED_DESC(R.string.sort_created);

    fun next(): TaskSortMode {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }

    companion object {
        fun fromStorage(raw: String?): TaskSortMode =
            entries.firstOrNull { it.name == raw } ?: MANUAL
    }
}
