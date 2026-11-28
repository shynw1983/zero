package com.zero.todo.feature.execute

import com.google.android.gms.maps.model.LatLng
import com.zero.todo.feature.execute.AtlasCameraTarget.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AtlasStartupCameraTest {
    private val task = LatLng(34.6937, 135.5023)
    private val user = LatLng(43.0618, 141.3545)

    @Test
    fun `initial map waits for a requested location even when a task is ready`() {
        assertNull(atlasStartupCameraTarget(null, task, waitingForLocation = true))
        assertEquals(
            AtlasCameraTarget(user, Source.LOCATION),
            atlasStartupCameraTarget(user, task, waitingForLocation = true)
        )
    }

    @Test
    fun `a map opened without a fix uses a task or the overview as its starting viewport`() {
        val taskTarget = atlasStartupCameraTarget(null, task, waitingForLocation = false)!!
        val overviewTarget = atlasStartupCameraTarget(null, null, waitingForLocation = false)!!

        assertEquals(AtlasCameraTarget(task, Source.TASK), taskTarget)
        assertEquals(Source.OVERVIEW, overviewTarget.source)
        assertEquals(LatLng(35.6812, 139.7671), overviewTarget.cameraPosition(is3DMode = true).target)
    }

    @Test
    fun `a late task upgrades the overview and the first device fix upgrades either fallback`() {
        assertEquals(
            AtlasCameraTarget(task, Source.TASK),
            atlasStartupCameraTarget(null, task, waitingForLocation = false, appliedSource = Source.OVERVIEW)
        )
        for (fallback in listOf(Source.OVERVIEW, Source.TASK)) {
            assertEquals(
                AtlasCameraTarget(user, Source.LOCATION),
                atlasStartupCameraTarget(user, task, waitingForLocation = false, appliedSource = fallback)
            )
        }
    }

    @Test
    fun `GPS updates and later task changes leave an already located camera in place`() {
        assertNull(atlasStartupCameraTarget(user, task, waitingForLocation = false, appliedSource = Source.LOCATION))
        assertNull(atlasStartupCameraTarget(null, task, waitingForLocation = false, appliedSource = Source.LOCATION))
    }
}
