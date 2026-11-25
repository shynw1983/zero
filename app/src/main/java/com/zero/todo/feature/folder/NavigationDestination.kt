package com.zero.todo.feature.folder

sealed interface NavigationDestination {
    data object Inbox : NavigationDestination
    data object Today : NavigationDestination
    data object Tomorrow : NavigationDestination
    data object Next7Days : NavigationDestination
    data object Calendar : NavigationDestination
    data class FolderView(val folderId: String) : NavigationDestination

    fun toStorageKey(): String = when (this) {
        Inbox -> KEY_INBOX
        Today -> KEY_TODAY
        Tomorrow -> KEY_TOMORROW
        Next7Days -> KEY_NEXT_7_DAYS
        Calendar -> KEY_CALENDAR
        is FolderView -> "$KEY_FOLDER_PREFIX$folderId"
    }

    companion object {
        private const val KEY_INBOX = "inbox"
        private const val KEY_TODAY = "today"
        private const val KEY_TOMORROW = "tomorrow"
        private const val KEY_NEXT_7_DAYS = "next7"
        private const val KEY_CALENDAR = "calendar"
        private const val KEY_FOLDER_PREFIX = "folder:"

        fun fromStorageKey(key: String?): NavigationDestination = when {
            key == null || key == KEY_INBOX -> Inbox
            key == KEY_TODAY -> Today
            key == KEY_TOMORROW -> Tomorrow
            key == KEY_NEXT_7_DAYS -> Next7Days
            key == KEY_CALENDAR -> Calendar
            key.startsWith(KEY_FOLDER_PREFIX) -> {
                val id = key.removePrefix(KEY_FOLDER_PREFIX)
                if (id.isNotBlank()) FolderView(id) else Inbox
            }
            else -> Inbox
        }
    }
}

data class SmartViewCounts(
    val inbox: Int = 0,
    val today: Int = 0,
    val tomorrow: Int = 0,
    val next7Days: Int = 0
)
