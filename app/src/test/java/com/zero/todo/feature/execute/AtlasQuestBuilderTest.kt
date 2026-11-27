package com.zero.todo.feature.execute

import com.zero.todo.data.repository.TagWithCount
import com.zero.todo.domain.model.ExecuteTheme
import com.zero.todo.domain.model.PlaceTriggerMode
import com.zero.todo.domain.model.TagItem
import com.zero.todo.domain.model.TagPlace
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AtlasQuestBuilderTest {

    @Test
    fun `builds multi-branch markers, highlights nearest branch, detects geofence entry, and separates anywhere tasks`() {
        val convTag = TagItem(
            id = "tag-conv",
            name = "便利店",
            isPlaceTag = true,
            placeTriggerMode = PlaceTriggerMode.ENTER,
            places = listOf(
                TagPlace(
                    id = "p-far",
                    tagId = "tag-conv",
                    label = "7-11 Far Branch",
                    latitude = 35.6900,
                    longitude = 139.7000,
                    radiusMeters = 150,
                    isEnabled = true
                ),
                TagPlace(
                    id = "p-near",
                    tagId = "tag-conv",
                    label = "Lawson Nearby",
                    latitude = 35.6598,
                    longitude = 139.7006,
                    radiusMeters = 150,
                    isEnabled = true
                ),
                TagPlace(
                    id = "p-paused",
                    tagId = "tag-conv",
                    label = "Paused Branch",
                    latitude = 35.6596,
                    longitude = 139.7005,
                    radiusMeters = 150,
                    isEnabled = false
                )
            )
        )
        val homeTag = TagItem(
            id = "tag-home",
            name = "家",
            isPlaceTag = true,
            placeTriggerMode = PlaceTriggerMode.BOTH,
            places = listOf(
                TagPlace(
                    id = "p-home",
                    tagId = "tag-home",
                    label = "Home",
                    latitude = 35.6750,
                    longitude = 139.7100,
                    radiusMeters = 200,
                    isEnabled = true
                )
            )
        )
        val nonPlaceTag = TagItem(id = "tag-work", name = "Work", isPlaceTag = false)

        val buyCoffee = TaskItem(
            id = "t1",
            title = "买咖啡",
            priority = TaskPriority.HIGH,
            tags = listOf(convTag)
        )
        val takeTrash = TaskItem(
            id = "t2",
            title = "整理客厅",
            priority = TaskPriority.MEDIUM,
            tags = listOf(homeTag)
        )
        val anywhereTask = TaskItem(
            id = "t3",
            title = "回复邮件",
            priority = TaskPriority.URGENT,
            tags = listOf(nonPlaceTag)
        )

        val snapshot = AtlasQuestBuilder.build(
            allActiveTasks = listOf(buyCoffee, takeTrash, anywhereTask),
            tagsWithCounts = listOf(
                TagWithCount(convTag, 1),
                TagWithCount(homeTag, 1),
                TagWithCount(nonPlaceTag, 1)
            ),
            tagPathById = mapOf("tag-conv" to "便利店", "tag-home" to "家"),
            runwayQueue = listOf(anywhereTask, buyCoffee, takeTrash),
            // User stands 35m away from p-near (inside 150m geofence!)
            userLat = 35.6595,
            userLng = 139.7004
        )

        // Only 3 enabled places with incomplete tasks (paused branch omitted)
        assertEquals(3, snapshot.markers.size)
        val first = snapshot.markers.first()
        assertEquals("p-near", first.place.id)
        assertTrue(first.isInsideGeofence)
        assertTrue(first.isNearestBranchForTag)

        val farConv = snapshot.markers.first { it.place.id == "p-far" }
        assertFalse(farConv.isNearestBranchForTag)
        assertFalse(farConv.isInsideGeofence)

        // Route has User + nearest branch per tag (conv + home = 3 points total)
        assertEquals(3, snapshot.routeWaypoints.size)
        assertEquals(35.6598, snapshot.routeWaypoints[1].first, 0.0001)
        assertEquals(35.6750, snapshot.routeWaypoints[2].first, 0.0001)

        // Anywhere tasks exclude place-bound tasks already mapped
        assertEquals(listOf("t3"), snapshot.anywhereTasks.map { it.id })
    }

    @Test
    fun `heading cone, distance formatting, and ATLAS theme enum resolve cleanly`() {
        val cone = AtlasQuestBuilder.computeHeadingCone(35.6595, 139.7004, bearingDegrees = 90f)
        assertEquals(7, cone.size)
        assertEquals(35.6595, cone.first().first, 0.0001)
        // Bearing east increases longitude
        assertTrue(cone[3].second > 139.7004)

        assertEquals("240m", AtlasQuestBuilder.formatDistance(240))
        assertEquals("1.5km", AtlasQuestBuilder.formatDistance(1500))
        assertEquals(ExecuteTheme.ATLAS, ExecuteTheme.fromStorage("ATLAS"))
    }
}
