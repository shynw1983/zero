package com.zero.todo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
    exportSchema = true
)
abstract class ZeroDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun taskDao(): TaskDao
    abstract fun tagDao(): TagDao
    abstract fun focusSessionDao(): FocusSessionDao

    companion object {
        const val DATABASE_NAME = "zero_todo.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN firstStep TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE tasks ADD COLUMN firstStepCompleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN estimatedMinutes INTEGER DEFAULT NULL")
            }
        }
    }
}
