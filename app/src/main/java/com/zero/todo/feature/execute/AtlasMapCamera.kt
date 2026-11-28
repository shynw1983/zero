package com.zero.todo.feature.execute

import androidx.compose.ui.geometry.Offset
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng

internal interface AtlasMapCamera {
    val position: CameraPosition
    fun move(position: CameraPosition)
    fun animate(position: CameraPosition, durationMs: Int)
    fun projection(): ((LatLng) -> Offset?)?
}

internal class AtlasGoogleMapCamera(private val map: GoogleMap) : AtlasMapCamera {
    override val position: CameraPosition get() = map.cameraPosition

    override fun move(position: CameraPosition) {
        map.moveCamera(CameraUpdateFactory.newCameraPosition(position))
    }

    override fun animate(position: CameraPosition, durationMs: Int) {
        map.animateCamera(CameraUpdateFactory.newCameraPosition(position), durationMs, null)
    }

    override fun projection(): ((LatLng) -> Offset?)? {
        val projection = runCatching { map.projection }.getOrNull() ?: return null
        return { point ->
            runCatching {
                val pixel = projection.toScreenLocation(point)
                Offset(pixel.x.toFloat(), pixel.y.toFloat())
            }.getOrNull()
        }
    }
}
