package com.zero.todo.core.geofence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zero.todo.data.backup.ZeroBackupManager
import com.zero.todo.data.local.ZeroDatabase
import com.zero.todo.data.repository.FocusRepositoryImpl
import com.zero.todo.data.repository.FolderRepositoryImpl
import com.zero.todo.data.repository.TagRepositoryImpl
import com.zero.todo.data.repository.TaskRepositoryImpl
import com.zero.todo.domain.model.PlaceTriggerMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaceGeofenceTest {

    private lateinit var database: ZeroDatabase
    private lateinit var folderRepository: FolderRepositoryImpl
    private lateinit var taskRepository: TaskRepositoryImpl
    private lateinit var tagRepository: TagRepositoryImpl
    private lateinit var focusRepository: FocusRepositoryImpl
    private lateinit var backupManager: ZeroBackupManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ZeroDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        folderRepository = FolderRepositoryImpl(database, database.folderDao(), database.taskDao())
        taskRepository = TaskRepositoryImpl(database, database.taskDao(), database.folderDao())
        tagRepository = TagRepositoryImpl(database.tagDao())
        focusRepository = FocusRepositoryImpl(database.focusSessionDao())
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
    fun `coordinate and map URL parser validates coordinates and computes distances`() {
        val plain = PlaceSearchEngine.parseCoordinateOrMapUrl("35.6595, 139.7004")
        assertNotNull(plain)
        assertEquals(35.6595, plain!!.first, 0.0001)
        assertEquals(139.7004, plain.second, 0.0001)

        val gmaps = PlaceSearchEngine.parseCoordinateOrMapUrl(
            "https://www.google.com/maps/place/Tokyo/@35.6812362,139.7649361,17z"
        )
        assertNotNull(gmaps)
        assertEquals(35.6812, gmaps!!.first, 0.001)
        assertEquals(139.7649, gmaps.second, 0.001)

        val geoUri = PlaceSearchEngine.parseCoordinateOrMapUrl("geo:35.6580,-139.7016?q=Lawson")
        assertNotNull(geoUri)
        assertEquals(35.6580, geoUri!!.first, 0.0001)

        assertNull(PlaceSearchEngine.parseCoordinateOrMapUrl("95.0, 200.0"))
        assertNull(PlaceSearchEngine.parseCoordinateOrMapUrl("セブンイレブン 渋谷"))

        val dist = PlaceSearchEngine.haversineDistanceMeters(35.6595, 139.7004, 35.6605, 139.7014)
        assertTrue(dist in 120..180)
    }

    @Test
    fun `nominatim JSON parser extracts store branches with optional distance bias`() {
        val json = """
            [
              {
                "name": "セブンイレブン 渋谷店",
                "display_name": "セブンイレブン 渋谷店, 東京都渋谷区",
                "lat": "35.6595",
                "lon": "139.7004"
              },
              {
                "display_name": "ローソン 新宿駅前店, 東京都新宿区",
                "lat": "35.6896",
                "lon": "139.7006"
              }
            ]
        """.trimIndent()

        val parsed = PlaceSearchEngine.parseNominatimSearchJson(json, biasLat = 35.6595, biasLng = 139.7004)
        assertEquals(2, parsed.size)
        assertEquals("セブンイレブン 渋谷店", parsed[0].label)
        assertEquals(0, parsed[0].distanceMeters)
        assertEquals("ローソン 新宿駅前店", parsed[1].label)
        assertTrue((parsed[1].distanceMeters ?: 0) > 2500)
    }

    @Test
    fun `geofence evaluator handles request IDs, enter-exit modes, and cooldowns`() {
        val id = "place-uuid-42"
        val reqId = GeofenceEvaluator.requestIdFor(id)
        assertEquals("zero_place_place-uuid-42", reqId)
        assertEquals(id, GeofenceEvaluator.parsePlaceId(reqId))
        assertNull(GeofenceEvaluator.parsePlaceId("other_prefix_123"))

        assertTrue(GeofenceEvaluator.shouldTrigger(PlaceTriggerMode.ENTER, isEnter = true))
        assertFalse(GeofenceEvaluator.shouldTrigger(PlaceTriggerMode.ENTER, isEnter = false))
        assertFalse(GeofenceEvaluator.shouldTrigger(PlaceTriggerMode.EXIT, isEnter = true))
        assertTrue(GeofenceEvaluator.shouldTrigger(PlaceTriggerMode.EXIT, isEnter = false))
        assertTrue(GeofenceEvaluator.shouldTrigger(PlaceTriggerMode.BOTH, isEnter = true))
        assertTrue(GeofenceEvaluator.shouldTrigger(PlaceTriggerMode.BOTH, isEnter = false))

        assertFalse(GeofenceEvaluator.isWithinCooldown(0L, 1_000_000L))
        assertTrue(GeofenceEvaluator.isWithinCooldown(1_000_000L, 1_000_000L + 10 * 60_000L))
        assertFalse(GeofenceEvaluator.isWithinCooldown(1_000_000L, 1_000_000L + 31 * 60_000L))
    }

    @Test
    fun `multi-location place tag persists multiple branches, single home address, and backup v3 roundtrip`() = runTest {
        val convTag = tagRepository.createTag("便利店")
        tagRepository.updateTagPlaceConfig(convTag.id, isPlaceTag = true, triggerMode = PlaceTriggerMode.ENTER)

        val branch1 = tagRepository.addTagPlace(
            tagId = convTag.id,
            label = "7-11 站前店",
            address = "A 街 1-2",
            latitude = 35.6595,
            longitude = 139.7004,
            radiusMeters = 150
        )
        val branch2 = tagRepository.addTagPlace(
            tagId = convTag.id,
            label = "罗森 公司楼下",
            address = "B 街 3-4",
            latitude = 35.6812,
            longitude = 139.7671,
            radiusMeters = 200
        )
        val branch3 = tagRepository.addTagPlace(
            tagId = convTag.id,
            label = "全家 公寓旁",
            address = "C 街 5-6",
            latitude = 35.6900,
            longitude = 139.7100,
            radiusMeters = 120
        )
        tagRepository.updateTagPlace(branch3.copy(isEnabled = false))

        val homeTag = tagRepository.createTag("家")
        tagRepository.updateTagPlaceConfig(homeTag.id, isPlaceTag = true, triggerMode = PlaceTriggerMode.BOTH)
        tagRepository.addTagPlace(
            tagId = homeTag.id,
            label = "我的家",
            address = "住宅区 88 号",
            latitude = 35.6400,
            longitude = 139.6900,
            radiusMeters = 100
        )

        val activeTags = tagRepository.observeActiveTags().first()
        val loadedConv = activeTags.first { it.id == convTag.id }
        assertTrue(loadedConv.isPlaceTag)
        assertEquals(PlaceTriggerMode.ENTER, loadedConv.placeTriggerMode)
        assertEquals(3, loadedConv.places.size)

        val activePlaces = database.tagDao().getActiveGeofencePlaces()
        // 2 enabled convenience stores + 1 enabled home = 3 (branch3 is disabled)
        assertEquals(3, activePlaces.size)
        assertTrue(activePlaces.any { it.id == branch1.id })
        assertTrue(activePlaces.any { it.id == branch2.id })
        assertFalse(activePlaces.any { it.id == branch3.id })

        // Verify incomplete task query respects tag tree & excludes completed tasks
        val subTag = tagRepository.createTag("买零食", parentId = convTag.id)
        val todoTask = taskRepository.createTask("取快递 & 寄信")
        tagRepository.attachTagToTask(todoTask.id, convTag.id)
        val subTagTask = taskRepository.createTask("买咖啡")
        tagRepository.attachTagToTask(subTagTask.id, subTag.id)
        val doneTask = taskRepository.createTask("已买完")
        tagRepository.attachTagToTask(doneTask.id, convTag.id)
        taskRepository.setTaskCompleted(doneTask.id, true)

        val triggerableTasks = database.tagDao().getActiveIncompleteTasksForTagTree(convTag.id)
        assertEquals(listOf(todoTask.id, subTagTask.id).toSet(), triggerableTasks.map { it.id }.toSet())

        // Verify Backup v3 export/import preserves Place Tag flags & all multiple places
        val json = backupManager.exportJson()
        val parsedRoot = JSONObject(json)
        assertEquals(ZeroBackupManager.CURRENT_SCHEMA_VERSION, parsedRoot.getInt("schemaVersion"))
        assertEquals(4, parsedRoot.getJSONArray("tagPlaces").length())

        backupManager.importJson(json, replaceExisting = true)
        val restoredTags = tagRepository.observeActiveTags().first()
        val restoredConv = restoredTags.first { it.id == convTag.id }
        val restoredHome = restoredTags.first { it.id == homeTag.id }
        assertTrue(restoredConv.isPlaceTag)
        assertEquals(PlaceTriggerMode.ENTER, restoredConv.placeTriggerMode)
        assertEquals(3, restoredConv.places.size)
        assertEquals(PlaceTriggerMode.BOTH, restoredHome.placeTriggerMode)
        assertEquals(1, restoredHome.places.size)
    }
}
