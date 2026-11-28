package com.zero.todo.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zero.todo.data.local.ZeroDatabase
import com.zero.todo.domain.usecase.GetFolderTreeUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FolderRepositoryTest {

    private lateinit var database: ZeroDatabase
    private lateinit var folderRepository: FolderRepositoryImpl
    private lateinit var taskRepository: TaskRepositoryImpl
    private lateinit var getFolderTreeUseCase: GetFolderTreeUseCase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ZeroDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        folderRepository = FolderRepositoryImpl(
            database = database,
            folderDao = database.folderDao(),
            taskDao = database.taskDao()
        )
        taskRepository = TaskRepositoryImpl(
            database = database,
            taskDao = database.taskDao(),
            folderDao = database.folderDao()
        )
        getFolderTreeUseCase = GetFolderTreeUseCase(folderRepository, taskRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `nested folder CRUD, ancestors breadcrumb, and recursive descendants work across 5 levels`() = runTest {
        val work = folderRepository.createFolder("Work")
        val finance = folderRepository.createFolder("Finance", parentId = work.id)
        val q4 = folderRepository.createFolder("Q4", parentId = finance.id)
        val reimbursement = folderRepository.createFolder("Reimbursement", parentId = q4.id)
        val travel = folderRepository.createFolder("Travel", parentId = reimbursement.id)

        // Update operations
        folderRepository.renameFolder(travel.id, "International Travel")
        folderRepository.setFolderExpanded(finance.id, isExpanded = false)
        folderRepository.updateFolderAppearance(work.id, icon = "briefcase", colorHex = "#FF4D00")

        val updatedTravel = folderRepository.getFolderById(travel.id)
        assertNotNull(updatedTravel)
        assertEquals("International Travel", updatedTravel!!.name)

        val updatedFinance = folderRepository.getFolderById(finance.id)
        assertFalse(updatedFinance!!.isExpanded)

        // Breadcrumb ancestors root -> leaf
        val ancestors = folderRepository.getAncestors(travel.id)
        assertEquals(
            listOf("Work", "Finance", "Q4", "Reimbursement", "International Travel"),
            ancestors.map { it.name }
        )

        // Recursive descendant IDs
        val descendants = folderRepository.getDescendantIds(work.id)
        assertEquals(
            setOf(work.id, finance.id, q4.id, reimbursement.id, travel.id),
            descendants.toSet()
        )

        // Full tree construction
        val tree = folderRepository.observeFolderTree().first()
        assertEquals(1, tree.size)
        val leaf = tree.single().children.single().children.single().children.single().children.single()
        assertEquals(4, leaf.depth)
        assertEquals("International Travel", leaf.folder.name)
    }

    @Test
    fun `sibling reordering and cross-level reparenting maintain exact sortOrder`() = runTest {
        val f1 = folderRepository.createFolder("Alpha")
        val f2 = folderRepository.createFolder("Beta")
        val f3 = folderRepository.createFolder("Gamma")
        val f4 = folderRepository.createFolder("Delta")

        // Initial order: Alpha, Beta, Gamma, Delta
        assertEquals(
            listOf("Alpha", "Beta", "Gamma", "Delta"),
            folderRepository.observeChildFolders(null).first().map { it.name }
        )

        // Drag Delta between Alpha and Beta
        folderRepository.reorderFolderBetween(
            folderId = f4.id,
            targetParentId = null,
            beforeFolderId = f1.id,
            afterFolderId = f2.id
        )
        assertEquals(
            listOf("Alpha", "Delta", "Beta", "Gamma"),
            folderRepository.observeChildFolders(null).first().map { it.name }
        )

        // Drag Gamma to index 0 (top of root)
        folderRepository.moveFolderToIndex(
            folderId = f3.id,
            targetParentId = null,
            targetIndex = 0
        )
        assertEquals(
            listOf("Gamma", "Alpha", "Delta", "Beta"),
            folderRepository.observeChildFolders(null).first().map { it.name }
        )

        // Re-parent Beta inside Alpha, then Delta inside Alpha before Beta
        folderRepository.moveFolderToIndex(folderId = f2.id, targetParentId = f1.id, targetIndex = 0)
        folderRepository.reorderFolderBetween(
            folderId = f4.id,
            targetParentId = f1.id,
            beforeFolderId = null,
            afterFolderId = f2.id
        )

        assertEquals(
            listOf("Gamma", "Alpha"),
            folderRepository.observeChildFolders(null).first().map { it.name }
        )
        assertEquals(
            listOf("Delta", "Beta"),
            folderRepository.observeChildFolders(f1.id).first().map { it.name }
        )
    }

    @Test
    fun `dropping a folder into a collapsed parent opens it and preserves the branch`() = runBlocking {
        val source = folderRepository.createFolder("Source")
        val leaf = folderRepository.createFolder("Leaf", parentId = source.id)
        val destination = folderRepository.createFolder("Destination")
        val sibling = folderRepository.createFolder("Sibling", parentId = destination.id)
        folderRepository.setFolderExpanded(destination.id, false)

        folderRepository.reorderFolderBetween(source.id, destination.id, null, leaf.id)
        assertNull(folderRepository.getFolderById(source.id)?.parentId)
        assertFalse(checkNotNull(folderRepository.getFolderById(destination.id)).isExpanded)

        folderRepository.reorderFolderBetween(source.id, destination.id, null, sibling.id)

        assertTrue(checkNotNull(folderRepository.getFolderById(destination.id)).isExpanded)
        assertEquals(listOf(source.id, sibling.id), folderRepository.observeChildFolders(destination.id).first().map { it.id })
        assertEquals(source.id, folderRepository.getFolderById(leaf.id)?.parentId)
    }

    @Test
    fun `repeated insertions between same two folders automatically rebalance without order drift`() = runTest {
        val anchorStart = folderRepository.createFolder("Start")
        val anchorEnd = folderRepository.createFolder("End")

        val expectedBetween = mutableListOf<String>()
        for (i in 1..45) {
            val created = folderRepository.createFolder("Item-$i")
            // Always insert right after anchorStart
            folderRepository.moveFolderToIndex(
                folderId = created.id,
                targetParentId = null,
                targetIndex = 1
            )
            expectedBetween.add(0, created.name)
        }

        val actual = folderRepository.observeChildFolders(null).first()
        assertEquals(listOf("Start") + expectedBetween + listOf("End"), actual.map { it.name })
        for (i in 0 until actual.lastIndex) {
            assertTrue(actual[i].sortOrder < actual[i + 1].sortOrder)
        }
    }

    @Test
    fun `cycle prevention rejects moving a folder into itself or any nested descendant`() = runTest {
        val root = folderRepository.createFolder("Root")
        val child = folderRepository.createFolder("Child", parentId = root.id)
        val grandChild = folderRepository.createFolder("GrandChild", parentId = child.id)

        try {
            folderRepository.moveFolderToIndex(folderId = root.id, targetParentId = root.id, targetIndex = 0)
            fail("Expected IllegalArgumentException when moving folder into itself")
        } catch (_: IllegalArgumentException) {
            // Expected
        }

        try {
            folderRepository.reorderFolderBetween(
                folderId = root.id,
                targetParentId = grandChild.id,
                beforeFolderId = null,
                afterFolderId = null
            )
            fail("Expected IllegalArgumentException when moving folder into descendant")
        } catch (_: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun `soft deleting a folder cascades to descendants, hides from active queries, and detaches tasks to Inbox`() = runTest {
        val work = folderRepository.createFolder("Work")
        val sub = folderRepository.createFolder("Sub", parentId = work.id)
        val deep = folderRepository.createFolder("Deep", parentId = sub.id)
        val personal = folderRepository.createFolder("Personal")

        val t1 = taskRepository.createTask("Work root task", folderId = work.id)
        val t2 = taskRepository.createTask("Deep nested task", folderId = deep.id)

        // Verify includeSubfolders toggle before deletion
        val directOnly = taskRepository.observeTasksByFolder(work.id, includeSubfolders = false).first()
        val withSubfolders = taskRepository.observeTasksByFolder(work.id, includeSubfolders = true).first()
        assertEquals(listOf(t1.id), directOnly.map { it.id })
        assertEquals(setOf(t1.id, t2.id), withSubfolders.map { it.id }.toSet())

        // Verify GetFolderTreeUseCase aggregates incomplete tasks across hierarchy
        val treeWithCounts = getFolderTreeUseCase().first()
        val workNode = treeWithCounts.first { it.folder.id == work.id }
        assertEquals(1, workNode.directTaskCount)
        assertEquals(2, workNode.totalTaskCount)

        // Soft delete Work (keep tasks by moving to Inbox)
        folderRepository.softDeleteFolder(work.id, deleteContainedTasks = false)

        assertNull(folderRepository.getFolderById(work.id))
        assertNull(folderRepository.getFolderById(sub.id))
        assertNull(folderRepository.getFolderById(deep.id))

        val remainingFolders = folderRepository.observeActiveFolders().first()
        assertEquals(listOf(personal.id), remainingFolders.map { it.id })

        val inboxTasks = taskRepository.observeInboxTasks().first()
        assertEquals(setOf(t1.id, t2.id), inboxTasks.map { it.id }.toSet())

        // Restore Work subtree
        folderRepository.restoreFolder(work.id)
        val restoredIds = folderRepository.observeActiveFolders().first().map { it.id }.toSet()
        assertEquals(setOf(work.id, sub.id, deep.id, personal.id), restoredIds)
    }

    @Test
    fun `soft deleting a folder with deleteContainedTasks removes tasks inside subtree`() = runTest {
        val project = folderRepository.createFolder("Project")
        val sub = folderRepository.createFolder("Phase 1", parentId = project.id)
        val t1 = taskRepository.createTask("Spec", folderId = sub.id)

        folderRepository.softDeleteFolder(project.id, deleteContainedTasks = true)

        assertNull(taskRepository.getTaskById(t1.id))
        assertTrue(taskRepository.observeInboxTasks().first().isEmpty())
    }
}
