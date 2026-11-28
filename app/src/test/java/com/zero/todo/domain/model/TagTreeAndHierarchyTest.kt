package com.zero.todo.domain.model

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zero.todo.data.backup.ZeroBackupManager
import com.zero.todo.data.local.ZeroDatabase
import com.zero.todo.data.repository.TagRepositoryImpl
import com.zero.todo.data.repository.TagWithCount
import com.zero.todo.data.repository.TaskRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class TagTreeAndHierarchyTest {

    private lateinit var database: ZeroDatabase
    private lateinit var tagRepository: TagRepositoryImpl
    private lateinit var taskRepository: TaskRepositoryImpl
    private lateinit var backupManager: ZeroBackupManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ZeroDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tagRepository = TagRepositoryImpl(database.tagDao())
        taskRepository = TaskRepositoryImpl(
            database = database,
            taskDao = database.taskDao(),
            folderDao = database.folderDao()
        )
        backupManager = ZeroBackupManager(
            database = database,
            folderDao = database.folderDao(),
            taskDao = database.taskDao(),
            tagDao = database.tagDao(),
            focusSessionDao = database.focusSessionDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `TagTreeBuilder builds nested paths, aggregates descendant counts, and collapses visibility`() {
        val outing = TagItem(id = "t_out", name = "外出", isExpanded = true)
        val shop = TagItem(id = "t_shop", parentId = "t_out", name = "便利店", isExpanded = true)
        val gov = TagItem(id = "t_gov", parentId = "t_out", name = "政府机关", isExpanded = true)
        val client = TagItem(id = "t_cli", name = "客户", isExpanded = false)
        val zhangsan = TagItem(id = "t_zs", parentId = "t_cli", name = "张三")
        val orphan = TagItem(id = "t_orphan", parentId = "missing_parent", name = "游离")

        val tree = TagTreeBuilder.buildTree(
            listOf(
                TagWithCount(outing, 1),
                TagWithCount(shop, 2),
                TagWithCount(gov, 3),
                TagWithCount(client, 0),
                TagWithCount(zhangsan, 4),
                TagWithCount(orphan, 1)
            )
        )

        val outNode = tree.first { it.tag.id == "t_out" }
        assertEquals(6, outNode.totalTaskCount)
        assertEquals("外出", outNode.fullPath)
        assertEquals(listOf("外出/便利店", "外出/政府机关"), outNode.children.map { it.fullPath })

        val cliNode = tree.first { it.tag.id == "t_cli" }
        assertEquals(4, cliNode.totalTaskCount)
        assertEquals("客户/张三", cliNode.children.single().fullPath)

        // Collapsed `客户` hides `张三`, while expanded `外出` shows its 2 children + orphan promoted to root
        val visible = TagTreeBuilder.flattenVisible(tree)
        val visibleIds = visible.map { it.tag.id }.toSet()
        assertTrue("t_shop" in visibleIds)
        assertTrue("t_gov" in visibleIds)
        assertFalse("t_zs" in visibleIds)
        assertTrue("t_orphan" in visibleIds)

        val paths = TagTreeBuilder.buildPathMap(listOf(outing, shop, gov, client, zhangsan))
        assertEquals("外出/便利店", paths["t_shop"])
        assertEquals("客户/张三", paths["t_zs"])
    }

    @Test
    fun `TagRepositoryImpl creates slash hierarchy, recursively queries tasks, moves parent without cycles, and deletes subtree`() = runBlocking {
        val convTag = tagRepository.getOrCreateTag("#外出/便利店")
        val govTag = tagRepository.getOrCreateTag("外出/政府机关")
        val outingRoot = database.tagDao().getTagByNameInParent("外出", null)!!
        assertEquals(outingRoot.id, convTag.parentId)
        assertEquals(outingRoot.id, govTag.parentId)

        val zhangsanTag = tagRepository.createTag("张三", parentId = tagRepository.createTag("客户").id)
        val taskMilk = taskRepository.createTask("买牛奶")
        val taskTax = taskRepository.createTask("盖章")
        val taskClient = taskRepository.createTask("回访客户")
        tagRepository.attachTagToTask(taskMilk.id, convTag.id)
        // Attach BOTH root and child tag to verify DISTINCT deduplication
        tagRepository.attachTagToTask(taskTax.id, outingRoot.id)
        tagRepository.attachTagToTask(taskTax.id, govTag.id)
        tagRepository.attachTagToTask(taskClient.id, zhangsanTag.id)

        // Filtering by parent `#外出` returns both tasks across descendants without duplicates
        val outingTasks = withTimeout(5_000L) {
            tagRepository.observeTasksByTag(outingRoot.id).first()
        }
        assertEquals(setOf(taskMilk.id, taskTax.id), outingTasks.map { it.id }.toSet())
        assertEquals(2, outingTasks.size)

        // Cycle prevention: moving `外出` under its own child `便利店` must fail
        assertFalse(tagRepository.moveTag(outingRoot.id, convTag.id))
        assertFalse(tagRepository.moveTag(outingRoot.id, outingRoot.id))

        // Move `便利店` under `客户`
        assertTrue(tagRepository.moveTag(convTag.id, zhangsanTag.parentId))
        val movedConv = database.tagDao().getTagById(convTag.id)!!
        assertEquals(zhangsanTag.parentId, movedConv.parentId)

        // Soft-deleting `客户` soft-deletes its descendants (`张三`, `便利店`) without deleting tasks
        tagRepository.softDeleteTag(zhangsanTag.parentId!!)
        val remainingTags = database.tagDao().getActiveTagsSnapshot().map { it.id }.toSet()
        assertFalse(zhangsanTag.id in remainingTags)
        assertFalse(convTag.id in remainingTags)
        assertTrue(govTag.id in remainingTags)
        assertTrue(database.taskDao().getTaskById(taskMilk.id)?.isDeleted == false)
    }

    @Test
    fun `manual order is per parent and survives backup and restore`() = runBlocking {
        val parent = tagRepository.createTag("Parent")
        val other = tagRepository.createTag("Other")
        val alpha = tagRepository.createTag("Alpha", parentId = parent.id)
        val zulu = tagRepository.createTag("Zulu", parentId = parent.id)
        tagRepository.moveTagInOrder(zulu.id, -1)
        tagRepository.moveTagInOrder(zulu.id, -1)
        val backup = backupManager.exportJson()
        backupManager.importJson(backup)
        var tree = TagTreeBuilder.buildTree(tagRepository.observeActiveTagsWithCounts().first())
        assertEquals(listOf(other.id, parent.id), tree.map { it.tag.id })
        assertEquals(listOf(zulu.id, alpha.id), tree.last().children.map { it.tag.id })

        val legacy = JSONObject(backup).apply {
            put("schemaVersion", 4)
            val tags = getJSONArray("tags")
            for (index in 0 until tags.length()) tags.getJSONObject(index).remove("sortOrder")
        }
        backupManager.importJson(legacy.toString())
        tree = TagTreeBuilder.buildTree(tagRepository.observeActiveTagsWithCounts().first())
        assertEquals(listOf(alpha.id, zulu.id), tree.last().children.map { it.tag.id })
    }

    @Test
    fun `v5 database migrates to v6 and keeps tag data and alphabetical fallback`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "zero_tag_sort_migration.db"
        val schema = File("schemas/com.zero.todo.data.local.ZeroDatabase/5.json")
        val old = JSONObject(schema.readText()).getJSONObject("database")
        val tables = old.getJSONArray("entities")
        try {
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { sqlite ->
                for (index in 0 until tables.length()) {
                    val entity = tables.getJSONObject(index)
                    val tableName = entity.getString("tableName")
                    sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
                    val indices = entity.getJSONArray("indices")
                    for (indexId in 0 until indices.length()) {
                        val sql = indices.getJSONObject(indexId).getString("createSql")
                        sqlite.execSQL(sql.replace("\${TABLE_NAME}", tableName))
                    }
                }
                val queries = old.getJSONArray("setupQueries")
                for (index in 0 until queries.length()) sqlite.execSQL(queries.getString(index))
                sqlite.execSQL(
                    "INSERT INTO tags (id, name, isExpanded, isPlaceTag, placeTriggerMode, createdAt, updatedAt, isDeleted) " +
                        "VALUES ('legacy', 'Office', 1, 1, 'EXIT', 1, 1, 0)"
                )
                sqlite.version = 5
            }
            val migrated = Room.databaseBuilder(context, ZeroDatabase::class.java, name)
                .addMigrations(ZeroDatabase.MIGRATION_5_6)
                .allowMainThreadQueries()
                .build()
            try {
                val tag = runBlocking { checkNotNull(migrated.tagDao().getTagById("legacy")) }
                assertEquals("Office", tag.name)
                assertEquals("EXIT", tag.placeTriggerMode)
                assertEquals(null, tag.sortOrder)
            } finally {
                migrated.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun `ZeroBackupManager exports and imports hierarchical parentId and isExpanded losslessly`() = runBlocking {
        val shop = tagRepository.getOrCreateTag("#外出/便利店")
        val outingId = shop.parentId!!
        tagRepository.setTagExpanded(outingId, false)

        val json = backupManager.exportJson()
        database.tagDao().clearAllCrossRefs()
        database.tagDao().clearAllTags()

        backupManager.importJson(json, replaceExisting = true)
        val restoredOuting = database.tagDao().getTagById(outingId)!!
        val restoredShop = database.tagDao().getTagById(shop.id)!!
        assertFalse(restoredOuting.isExpanded)
        assertEquals(outingId, restoredShop.parentId)

        // Legacy v2 backup JSON without parentId / isExpanded defaults cleanly
        val legacyJson = """{"schemaVersion":2,"folders":[],"tasks":[],"tags":[{"id":"t_legacy","name":"旧标签","colorHex":null,"createdAt":1000,"updatedAt":1000,"isDeleted":false}],"taskTagCrossRefs":[],"focusSessions":[]}"""
        backupManager.importJson(legacyJson, replaceExisting = true)
        val legacyRestored = database.tagDao().getTagById("t_legacy")!!
        assertEquals(null, legacyRestored.parentId)
        assertTrue(legacyRestored.isExpanded)
    }
}
