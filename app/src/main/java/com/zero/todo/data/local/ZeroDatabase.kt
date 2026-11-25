package com.zero.todo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.zero.todo.data.local.dao.FocusSessionDao
import com.zero.todo.data.local.dao.FolderDao
import com.zero.todo.data.local.dao.TagDao
import com.zero.todo.data.local.dao.TaskDao
import com.zero.todo.data.local.entity.FocusSessionEntity
import com.zero.todo.data.local.entity.FolderEntity
import com.zero.todo.data.local.entity.TagEntity
import com.zero.todo.data.local.entity.TaskEntity
import com.zero.todo.data.local.entity.TaskTagCrossRef

@Database(
    entities = [
        FolderEntity::class,
        TaskEntity::class,
        TagEntity::class,
        TaskTagCrossRef::class,
        FocusSessionEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class ZeroDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun taskDao(): TaskDao
    abstract fun tagDao(): TagDao
    abstract fun focusSessionDao(): FocusSessionDao

    companion object {
        const val DATABASE_NAME = "zero_todo.db"
    }
}
