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
import com.zero.todo.data.local.entity.TagPlaceEntity
import com.zero.todo.data.local.entity.TaskEntity
import com.zero.todo.data.local.entity.TaskTagCrossRef

@Database(
    entities = [
        FolderEntity::class,
        TaskEntity::class,
        TagEntity::class,
        TagPlaceEntity::class,
        TaskTagCrossRef::class,
        FocusSessionEntity::class
    ],
    version = 6,
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tags ADD COLUMN parentId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE tags ADD COLUMN isExpanded INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tags_parentId ON tags(parentId)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tags ADD COLUMN isPlaceTag INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tags ADD COLUMN placeTriggerMode TEXT NOT NULL DEFAULT 'ENTER'")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tag_places (
                        id TEXT NOT NULL PRIMARY KEY,
                        tagId TEXT NOT NULL,
                        label TEXT NOT NULL,
                        address TEXT NOT NULL DEFAULT '',
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        radiusMeters INTEGER NOT NULL DEFAULT 150,
                        isEnabled INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(tagId) REFERENCES tags(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tag_places_tagId ON tag_places(tagId)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN placeTriggerOverride TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tags ADD COLUMN sortOrder INTEGER")
            }
        }
    }
}
