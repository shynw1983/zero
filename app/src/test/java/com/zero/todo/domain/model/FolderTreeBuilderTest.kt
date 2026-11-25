package com.zero.todo.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderTreeBuilderTest {

    @Test
    fun `builds deeply nested 5-level tree sorted by sortOrder and aggregates task counts`() {
        val folders = listOf(
            Folder(id = "root_b", parentId = null, name = "Personal", sortOrder = 2048.0),
            Folder(id = "root_a", parentId = null, name = "Work", sortOrder = 1024.0),
            Folder(id = "l2_b", parentId = "root_a", name = "Engineering", sortOrder = 2048.0),
            Folder(id = "l2_a", parentId = "root_a", name = "Finance", sortOrder = 1024.0),
            Folder(id = "l3", parentId = "l2_a", name = "Q4", sortOrder = 1024.0),
            Folder(id = "l4", parentId = "l3", name = "Reimbursements", sortOrder = 1024.0),
            Folder(id = "l5", parentId = "l4", name = "Travel", sortOrder = 1024.0),
            Folder(id = "deleted_root", parentId = null, name = "Archived", sortOrder = 512.0, isDeleted = true)
        )
        val directTaskCounts = mapOf(
            "root_a" to 1,
            "l2_a" to 2,
            "l5" to 3,
            "root_b" to 4
        )

        val roots = FolderTreeBuilder.build(folders, directTaskCounts)

        assertEquals(listOf("root_a", "root_b"), roots.map { it.folder.id })
        val work = roots[0]
        assertEquals(0, work.depth)
        assertEquals(1, work.directTaskCount)
        assertEquals(6, work.totalTaskCount) // 1 + 2 + 3

        assertEquals(listOf("l2_a", "l2_b"), work.children.map { it.folder.id })
        val finance = work.children[0]
        assertEquals(1, finance.depth)
        assertEquals(2, finance.directTaskCount)
        assertEquals(5, finance.totalTaskCount)

        val travel = finance.children.single().children.single().children.single()
        assertEquals("l5", travel.folder.id)
        assertEquals(4, travel.depth)
        assertEquals(3, travel.directTaskCount)
        assertEquals(3, travel.totalTaskCount)
    }

    @Test
    fun `flattenVisible skips descendants of collapsed folders`() {
        val folders = listOf(
            Folder(id = "a", parentId = null, name = "A", sortOrder = 1024.0, isExpanded = false),
            Folder(id = "a1", parentId = "a", name = "A1", sortOrder = 1024.0, isExpanded = true),
            Folder(id = "a1_1", parentId = "a1", name = "A1.1", sortOrder = 1024.0, isExpanded = true),
            Folder(id = "b", parentId = null, name = "B", sortOrder = 2048.0, isExpanded = true),
            Folder(id = "b1", parentId = "b", name = "B1", sortOrder = 1024.0, isExpanded = true)
        )

        val visible = FolderTreeBuilder.flattenVisible(FolderTreeBuilder.build(folders))
        assertEquals(listOf("a", "b", "b1"), visible.map { it.folder.id })
    }

    @Test
    fun `handles cyclic or orphaned folders without crashing`() {
        val malformed = listOf(
            Folder(id = "x", parentId = "y", name = "Cycle X", sortOrder = 1024.0),
            Folder(id = "y", parentId = "x", name = "Cycle Y", sortOrder = 2048.0),
            Folder(id = "orphan", parentId = "missing_parent", name = "Orphan", sortOrder = 3072.0)
        )

        val defaultRoots = FolderTreeBuilder.build(malformed, promoteOrphansToRoot = false)
        assertTrue(defaultRoots.isEmpty())

        val promotedRoots = FolderTreeBuilder.build(malformed, promoteOrphansToRoot = true)
        assertEquals(listOf("orphan"), promotedRoots.map { it.folder.id })
    }
}
