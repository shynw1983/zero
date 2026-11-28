package com.zero.todo.feature.execute

import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng

internal data class AtlasCameraTarget(val point: LatLng, val source: Source) {
    enum class Source { OVERVIEW, TASK, LOCATION }

    fun cameraPosition(is3DMode: Boolean): CameraPosition {
        val isOverview = source == Source.OVERVIEW
        return CameraPosition(
            point,
            if (isOverview) 13.2f else if (is3DMode) 17.2f else 15.2f,
            if (!is3DMode) 0f else if (isOverview) 24f else 62f,
            if (is3DMode && !isOverview) 22f else 0f
        )
    }
}

private val atlasOverview = AtlasCameraTarget(LatLng(35.6812, 139.7671), AtlasCameraTarget.Source.OVERVIEW)

internal fun atlasStartupCameraTarget(
    userLocation: LatLng?,
    taskLocation: LatLng?,
    waitingForLocation: Boolean,
    appliedSource: AtlasCameraTarget.Source? = null
): AtlasCameraTarget? {
    val target = when {
        userLocation != null -> AtlasCameraTarget(userLocation, AtlasCameraTarget.Source.LOCATION)
        waitingForLocation -> return null
        taskLocation != null -> AtlasCameraTarget(taskLocation, AtlasCameraTarget.Source.TASK)
        else -> atlasOverview
    }
    return target.takeIf { appliedSource == null || it.source > appliedSource }
}
