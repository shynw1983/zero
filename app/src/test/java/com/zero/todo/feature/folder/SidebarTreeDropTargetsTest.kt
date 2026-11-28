package com.zero.todo.feature.folder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SidebarTreeDropTargetsTest {
    private val tree = SidebarTree.FOLDERS
    private val rows = listOf(
        SidebarTreeRow("a", null, 0),
        SidebarTreeRow("child", "a", 1),
        SidebarTreeRow("b", null, 0),
        SidebarTreeRow("c", null, 0)
    )
    private val bounds = listOf(
        SidebarRowBounds(tree.headerKey, 0, 50),
        SidebarRowBounds(tree.rowKey("a"), 50, 50),
        SidebarRowBounds(tree.rowKey("child"), 100, 50),
        SidebarRowBounds(tree.rowKey("b"), 150, 50),
        SidebarRowBounds(tree.rowKey("c"), 200, 50),
        SidebarRowBounds(SidebarTree.TAGS.headerKey, 250, 50)
    )

    @Test
    fun `row edges insert at the target level and after an expanded branch`() {
        assertEquals(
            SidebarDropTarget(tree.rowKey("a"), SidebarDropZone.BEFORE, null, followingId = "a"),
            target("c", 55f)
        )
        assertEquals(
            SidebarDropTarget(tree.rowKey("child"), SidebarDropZone.AFTER, null, precedingId = "a"),
            target("c", 95f)
        )
        assertEquals(
            SidebarDropTarget(tree.rowKey("child"), SidebarDropZone.BEFORE, "a", followingId = "child", markerDepth = 1),
            target("c", 105f)
        )
    }

    @Test
    fun `row center nests and the section heading promotes to the start of the root`() {
        assertEquals(SidebarDropTarget(tree.rowKey("b"), SidebarDropZone.INSIDE, "b"), target("a", 175f))
        assertEquals(
            SidebarDropTarget(tree.headerKey, SidebarDropZone.TOP_LEVEL, null, followingId = "a"),
            target("child", 25f)
        )
    }

    @Test
    fun `source descendants other sections and unchanged orders are invalid drop targets`() {
        assertNull(target("a", 75f))
        assertNull(target("a", 105f))
        assertNull(target("a", 125f))
        assertNull(target("c", 275f))
        assertNull(target("c", 195f))
        assertNull(target("a", 25f))
        assertNull(target("c", 310f))
    }

    private fun target(sourceId: String, y: Float) = sidebarDropTarget(tree, sourceId, rows, bounds, y)
}
