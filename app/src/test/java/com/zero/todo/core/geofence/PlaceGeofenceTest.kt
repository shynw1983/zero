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
    fun `mergeAndSortByDistance orders stores strictly nearest to farthest and deduplicates nearby hits`() {
        val merged = PlaceSearchEngine.mergeAndSortByDistance(
            results = listOf(
                PlaceSearchResult(
                    label = "セブンイレブン 遠方",
                    address = "3.4km away",
                    latitude = 35.6900,
                    longitude = 139.7000
                ),
                PlaceSearchResult(
                    label = "セブンイレブン 近所",
                    address = "博多駅前2丁目",
                    latitude = 35.6602,
                    longitude = 139.7008
                ),
                PlaceSearchResult(
                    label = "セブンイレブン 近所 (重複)",
                    address = "短",
                    latitude = 35.6603,
                    longitude = 139.7009
                ),
                PlaceSearchResult(
                    label = "セブンイレブン 中間",
                    address = "1.5km away",
                    latitude = 35.6730,
                    longitude = 139.7020
                )
            ),
            biasLat = 35.6595,
            biasLng = 139.7004
        )

        assertEquals(3, merged.size)
        assertEquals("セブンイレブン 近所", merged[0].label)
        assertEquals("セブンイレブン 中間", merged[1].label)
        assertEquals("セブンイレブン 遠方", merged[2].label)
        assertTrue((merged[0].distanceMeters ?: 0) < (merged[1].distanceMeters ?: 0))
        assertTrue((merged[1].distanceMeters ?: 0) < (merged[2].distanceMeters ?: 0))
    }

    @Test
    fun `place label resolver filters Japanese house numbers while preserving store names`() {
        assertTrue(PlaceSearchEngine.isHouseNumberOrAddressCode("１８"))
        assertTrue(PlaceSearchEngine.isHouseNumberOrAddressCode("18"))
        assertTrue(PlaceSearchEngine.isHouseNumberOrAddressCode("1-18"))
        assertTrue(PlaceSearchEngine.isHouseNumberOrAddressCode("１−１８"))
        assertTrue(PlaceSearchEngine.isHouseNumberOrAddressCode("１丁目１−１８"))
        assertTrue(PlaceSearchEngine.isHouseNumberOrAddressCode("3番地2号"))
        assertTrue(PlaceSearchEngine.isHouseNumberOrAddressCode("〒815-0082"))
        assertTrue(PlaceSearchEngine.isHouseNumberOrAddressCode("2F"))
        assertTrue(PlaceSearchEngine.isHouseNumberOrAddressCode("B1F"))
        assertTrue(PlaceSearchEngine.isHouseNumberOrAddressCode("No. 18"))
        assertTrue(PlaceSearchEngine.isHouseNumberOrAddressCode(""))
        assertTrue(PlaceSearchEngine.isHouseNumberOrAddressCode(null))

        assertFalse(PlaceSearchEngine.isHouseNumberOrAddressCode("セブン-イレブン 福岡大楠１丁目店"))
        assertFalse(PlaceSearchEngine.isHouseNumberOrAddressCode("7-Eleven"))
        assertFalse(PlaceSearchEngine.isHouseNumberOrAddressCode("ローソンストア100"))
        assertFalse(PlaceSearchEngine.isHouseNumberOrAddressCode("3COINS"))
        assertFalse(PlaceSearchEngine.isHouseNumberOrAddressCode("大楠１丁目"))
        assertFalse(PlaceSearchEngine.isHouseNumberOrAddressCode("博多駅前"))

        // Forward search: featureName is "１８" -> preserve searched merchant name
        assertEquals(
            "セブン-イレブン 福岡大楠１丁目店",
            PlaceSearchEngine.resolveGeocoderLabel(
                featureName = "１８",
                subThoroughfare = "１８",
                thoroughfare = "１",
                subLocality = "大楠１丁目",
                locality = "福岡市",
                postalCode = "815-0082",
                fullAddress = "日本、〒815-0082 福岡県福岡市南区大楠１丁目１−１８",
                fallbackQuery = "セブン-イレブン 福岡大楠１丁目店",
                defaultFallback = "セブン-イレブン 福岡大楠１丁目店"
            )
        )

        // Forward search: featureName is a genuine POI name -> keep distinct POI name
        assertEquals(
            "セブン-イレブン 福岡大楠１丁目店",
            PlaceSearchEngine.resolveGeocoderLabel(
                featureName = "セブン-イレブン 福岡大楠１丁目店",
                subThoroughfare = "１８",
                thoroughfare = "１",
                subLocality = "大楠１丁目",
                locality = "福岡市",
                postalCode = "815-0082",
                fullAddress = "日本、〒815-0082 福岡県福岡市南区大楠１丁目１−１８",
                fallbackQuery = "セブンイレブン",
                defaultFallback = "セブンイレブン"
            )
        )

        // Reverse geocode: featureName is "１８" -> fall back to readable neighborhood ("大楠１丁目")
        assertEquals(
            "大楠１丁目",
            PlaceSearchEngine.resolveGeocoderLabel(
                featureName = "１８",
                subThoroughfare = "１８",
                thoroughfare = "１",
                subLocality = "大楠１丁目",
                locality = "福岡市",
                postalCode = "815-0082",
                fullAddress = "日本、〒815-0082 福岡県福岡市南区大楠１丁目１−１８",
                fallbackQuery = null,
                defaultFallback = "33.57500, 130.41200"
            )
        )

        // Nominatim search JSON: ignore house-number-only "name": "18"
        val nominatimHouseJson = """
            [
              {
                "name": "18",
                "display_name": "18, 大楠一丁目, 南区, 福岡市, 福岡県, 815-0082, 日本",
                "lat": "33.5750",
                "lon": "130.4120"
              }
            ]
        """.trimIndent()
        val parsed = PlaceSearchEngine.parseNominatimSearchJson(
            rawJson = nominatimHouseJson,
            biasLat = 33.5750,
            biasLng = 130.4120,
            fallbackQuery = "セブン-イレブン 福岡大楠１丁目店"
        )
        assertEquals(1, parsed.size)
        assertEquals("大楠一丁目", parsed[0].label)
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
            radiusMeters = 3_000
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
        assertEquals(3_000, activePlaces.first { it.id == branch2.id }.radiusMeters)
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
        assertEquals(3_000, restoredConv.places.first { it.id == branch2.id }.radiusMeters)
        assertEquals(PlaceTriggerMode.BOTH, restoredHome.placeTriggerMode)
        assertEquals(1, restoredHome.places.size)
    }

    @Test
    fun `per-task place trigger override aggregates geofence transitions, filters alerts, and survives Room plus backup v4`() = runTest {
        // #家 defaults to ENTER; "收衣服" follows tag default (null), "出门倒垃圾" overrides to EXIT
        assertEquals(
            PlaceTriggerMode.ENTER,
            GeofenceEvaluator.effectiveTriggerMode(PlaceTriggerMode.ENTER, null)
        )
        assertEquals(
            PlaceTriggerMode.EXIT,
            GeofenceEvaluator.effectiveTriggerMode(PlaceTriggerMode.ENTER, PlaceTriggerMode.EXIT)
        )
        assertEquals(
            PlaceTriggerMode.BOTH,
            GeofenceEvaluator.aggregateModeForGeofence(
                tagDefaultMode = PlaceTriggerMode.ENTER,
                taskOverrides = listOf(null, PlaceTriggerMode.EXIT)
            )
        )
        // Arriving home: triggers "收衣服", does NOT trigger "出门倒垃圾"
        assertTrue(GeofenceEvaluator.shouldTriggerTask(PlaceTriggerMode.ENTER, null, isEnter = true))
        assertFalse(GeofenceEvaluator.shouldTriggerTask(PlaceTriggerMode.ENTER, PlaceTriggerMode.EXIT, isEnter = true))
        // Leaving home: does NOT trigger "收衣服", triggers "出门倒垃圾"
        assertFalse(GeofenceEvaluator.shouldTriggerTask(PlaceTriggerMode.ENTER, null, isEnter = false))
        assertTrue(GeofenceEvaluator.shouldTriggerTask(PlaceTriggerMode.ENTER, PlaceTriggerMode.EXIT, isEnter = false))

        val homeTag = tagRepository.createTag("家")
        tagRepository.updateTagPlaceConfig(homeTag.id, isPlaceTag = true, triggerMode = PlaceTriggerMode.ENTER)
        tagRepository.addTagPlace(
            tagId = homeTag.id,
            label = "我家",
            address = "中央区 1-1",
            latitude = 33.5900,
            longitude = 130.4017,
            radiusMeters = 150
        )

        val arriveTask = taskRepository.createTask("到家收衣服")
        val leaveTask = taskRepository.createTask("出门带垃圾", placeTriggerOverride = PlaceTriggerMode.EXIT)
        tagRepository.attachTagToTask(arriveTask.id, homeTag.id)
        tagRepository.attachTagToTask(leaveTask.id, homeTag.id)

        val loadedLeave = taskRepository.getTaskById(leaveTask.id)
        assertEquals(PlaceTriggerMode.EXIT, loadedLeave?.placeTriggerOverride)

        val json = backupManager.exportJson()
        assertEquals(ZeroBackupManager.CURRENT_SCHEMA_VERSION, JSONObject(json).getInt("schemaVersion"))
        backupManager.importJson(json, replaceExisting = true)

        assertNull(taskRepository.getTaskById(arriveTask.id)?.placeTriggerOverride)
        assertEquals(PlaceTriggerMode.EXIT, taskRepository.getTaskById(leaveTask.id)?.placeTriggerOverride)

        // Legacy v3 backup without placeTriggerOverride still imports cleanly as null
        val legacyRoot = JSONObject(json).apply {
            put("schemaVersion", 3)
            val tasksArr = getJSONArray("tasks")
            for (i in 0 until tasksArr.length()) {
                tasksArr.getJSONObject(i).remove("placeTriggerOverride")
            }
        }
        backupManager.importJson(legacyRoot.toString(), replaceExisting = true)
        assertNull(taskRepository.getTaskById(leaveTask.id)?.placeTriggerOverride)
    }
}
