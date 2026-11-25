package com.zero.todo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

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
    indices = [Index("parentId"), Index("isDeleted")]
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
