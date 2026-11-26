package com.zero.todo.feature.folder

sealed interface NavigationDestination {
    data object Inbox : NavigationDestination
    data object Today : NavigationDestination
    data object Tomorrow : NavigationDestination
    data object Next7Days : NavigationDestination
    data object Calendar : NavigationDestination
    data object All : NavigationDestination
    data object Matrix : NavigationDestination
    data object Trash : NavigationDestination
    data class FolderView(val folderId: String) : NavigationDestination
    data class TagView(val tagId: String) : NavigationDestination

    val showsTaskHierarchy: Boolean get() = this == Inbox || this == All || this is FolderView

    fun toStorageKey(): String = when (this) {
        Inbox -> KEY_INBOX
        Today -> KEY_TODAY
        Tomorrow -> KEY_TOMORROW
        Next7Days -> KEY_NEXT_7_DAYS
        Calendar -> KEY_CALENDAR
        All -> KEY_ALL
        Matrix -> KEY_MATRIX
        Trash -> KEY_TRASH
        is FolderView -> "$KEY_FOLDER_PREFIX$folderId"
        is TagView -> "$KEY_TAG_PREFIX$tagId"
    }

    companion object {
        private const val KEY_INBOX = "inbox"
        private const val KEY_TODAY = "today"
        private const val KEY_TOMORROW = "tomorrow"
        private const val KEY_NEXT_7_DAYS = "next7"
        private const val KEY_CALENDAR = "calendar"
        private const val KEY_ALL = "all"
        private const val KEY_MATRIX = "matrix"
        private const val KEY_TRASH = "trash"
        private const val KEY_FOLDER_PREFIX = "folder:"
        private const val KEY_TAG_PREFIX = "tag:"

        fun fromStorageKey(key: String?): NavigationDestination = when {
            key == null || key == KEY_INBOX -> Inbox
            key == KEY_TODAY -> Today
            key == KEY_TOMORROW -> Tomorrow
            key == KEY_NEXT_7_DAYS -> Next7Days
            key == KEY_CALENDAR -> Calendar
            key == KEY_ALL -> All
            key == KEY_MATRIX -> Matrix
            key == KEY_TRASH -> Trash
            key.startsWith(KEY_FOLDER_PREFIX) -> {
                val id = key.removePrefix(KEY_FOLDER_PREFIX)
                if (id.isNotBlank()) FolderView(id) else Inbox
            }
            key.startsWith(KEY_TAG_PREFIX) -> {
                val id = key.removePrefix(KEY_TAG_PREFIX)
                if (id.isNotBlank()) TagView(id) else Inbox
            }
            else -> Inbox
        }
    }
}

data class SmartViewCounts(
    val inbox: Int = 0,
    val today: Int = 0,
    val overdue: Int = 0,
    val tomorrow: Int = 0,
    val next7Days: Int = 0,
    val all: Int = 0,
    val trash: Int = 0
)
