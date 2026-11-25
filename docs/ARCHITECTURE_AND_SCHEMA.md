# ZERO — Technical Architecture & Database Schema

## 1. Tech Stack (Modern Native Android)
- Language: Kotlin 2.x
- UI: Jetpack Compose (Material 3 + `androidx.compose.material3.adaptive`)
- Architecture: Clean Architecture + Unidirectional Data Flow (MVI)
- DI: Hilt
- Local DB: Room (SQLite) with KSP
- Async: Kotlin Coroutines + StateFlow
- Widgets (Phase 3): Jetpack Glance

## 2. Package Structure
```text
com.zero.todo
├── core
│   ├── designsystem   // Theme, Color, Type (Tabular Mono), Hairline modifiers, Haptics
│   ├── nlp            // Local Chinese/English Natural Language Parser
│   └── common         // FractionalIndexing helper, Date/Time utils
├── data
│   ├── local          // Room Database, Entities, DAOs
│   └── repository     // Repository implementations
├── domain
│   ├── model          // Pure Kotlin domain models (FolderNode tree, TaskItem)
│   └── usecase        // GetFolderTreeUseCase, ParseQuickInputUseCase, etc.
└── feature
    ├── main           // Adaptive 1/2/3-pane scaffold & navigation
    ├── folder         // Nested tree UI & drag-drop reordering
    ├── tasklist       // Anti-misclick TaskCard, QuickInputCapsule
    ├── taskdetail     // Markdown editor, subtasks, recurrence
    ├── calendar       // Month/Week/Day views
    └── focus          // Pomodoro & 2-Minute Starter mode
```

## 3. Room Database Schema (Local-First, Sync-Ready)
All entities include `id: String` (UUID), `createdAt: Long`, `updatedAt: Long`, and `isDeleted: Boolean` (soft delete) to support future cloud sync (Supabase/WebDAV) without schema migration pain.

```kotlin
@Entity(
    tableName = "folders",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("parentId")]
)
data class FolderEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val parentId: String? = null, // null = root level
    val name: String,
    val icon: String? = null,
    val colorHex: String? = null,
    val sortOrder: Double = 0.0, // Fractional indexing for O(1) drag reorder
    val isExpanded: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentTaskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("folderId"), Index("parentTaskId"), Index("dueDate")]
)
data class TaskEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val folderId: String? = null, // null = Inbox
    val parentTaskId: String? = null, // non-null = Subtask
    val title: String,
    val description: String = "", // Markdown supported
    val priority: Int = 4, // 1=Urgent, 2=High, 3=Medium, 4=None
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val dueDate: Long? = null, // Epoch millis
    val isAllDay: Boolean = true, // Lightweight "date only" mode
    val reminderAt: Long? = null,
    val recurrenceRule: String? = null, // RFC 5545 RRULE string
    val sortOrder: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "task_tag_cross_ref",
    primaryKeys = ["taskId", "tagId"],
    indices = [Index("tagId")]
)
data class TaskTagCrossRef(
    val taskId: String,
    val tagId: String
)

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val taskId: String?,
    val durationSeconds: Int,
    val mode: String, // "POMODORO", "TWO_MIN_STARTER", "STOPWATCH"
    val completedAt: Long = System.currentTimeMillis()
)