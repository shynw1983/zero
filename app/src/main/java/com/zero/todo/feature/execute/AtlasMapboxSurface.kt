package com.zero.todo.feature.execute

import android.content.Context
import android.graphics.Bitmap
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.gson.JsonPrimitive
import com.mapbox.bindgen.Value
import com.mapbox.common.MapboxCommonSettings
import com.mapbox.common.MapboxOptions
import com.mapbox.common.SettingsServiceFactory
import com.mapbox.common.SettingsServiceStorageType
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapLoadingErrorType
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.LineLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.generated.rasterLayer
import com.mapbox.maps.extension.style.layers.getLayerAs
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.layers.properties.generated.ProjectionName
import com.mapbox.maps.extension.style.projection.generated.Projection
import com.mapbox.maps.extension.style.projection.generated.setProjection
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.ImageSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.generated.imageSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.extension.style.sources.updateImage
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.scalebar.scalebar
import com.zero.todo.R
import com.zero.todo.core.designsystem.ZeroCapsuleShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.domain.model.AtlasMapStyle
import com.zero.todo.domain.model.TagPlace
import com.zero.todo.domain.model.TaskPriority
import java.util.Locale
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
internal fun AtlasMapboxSurface(
    accessToken: String,
    initialCamera: CameraPosition,
    mapStyle: AtlasMapStyle,
    isDark: Boolean,
    is3DMode: Boolean,
    topPadding: Int,
    bottomPadding: Int,
    markers: List<AtlasQuestMarker>,
    selectedPlaceId: String?,
    routeWaypoints: List<Pair<Double, Double>>,
    showQuestRoute: Boolean,
    userLocation: LatLng?,
    compassBearing: Float,
    accentArgb: Int,
    urgentArgb: Int,
    glowArgb: Int,
    onReady: (AtlasMapCamera?) -> Unit,
    onCameraMove: () -> Unit,
    onInteraction: () -> Unit,
    onSelectPlace: (String, LatLng) -> Unit,
    modifier: Modifier = Modifier
) {
    val onReadyNow by rememberUpdatedState(onReady)
    val onCameraMoveNow by rememberUpdatedState(onCameraMove)
    val onInteractionNow by rememberUpdatedState(onInteraction)
    val onSelectPlaceNow by rememberUpdatedState(onSelectPlace)
    val density = LocalDensity.current.density
    val labelLanguage = (LocalConfiguration.current.locales[0] ?: Locale.ENGLISH).toMapboxLabelLanguage()
    var view by remember { mutableStateOf<MapView?>(null) }
    var loadedStyle by remember { mutableStateOf<Style?>(null) }
    var styleFailed by remember { mutableStateOf(false) }

    Box(modifier) {
        AndroidView(
            factory = { context ->
                MapboxOptions.accessToken = accessToken
                AtlasMapboxView(context, initialCamera) { onInteractionNow() }.apply {
                    compass.enabled = false
                    scalebar.enabled = false
                    view = this
                }
            },
            update = {
                SettingsServiceFactory.getInstance(SettingsServiceStorageType.PERSISTENT)
                    .set(MapboxCommonSettings.LANGUAGE, Value.valueOf(labelLanguage))
            },
            onRelease = { it.onDestroy() },
            modifier = Modifier.fillMaxSize()
        )
        if (styleFailed) {
            Text(
                text = stringResource(R.string.atlas_mapbox_load_error),
                textAlign = TextAlign.Center,
                color = ZeroTheme.colors.textPrimary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp)
                    .background(ZeroTheme.colors.surfaceElevated, ZeroCapsuleShape)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }

    val mapView = view ?: return
    val map = mapView.mapboxMap
    val pins = remember(mapView) {
        mapView.annotations.createPointAnnotationManager().apply {
            iconAllowOverlap = true
            iconIgnorePlacement = true
            addClickListener { annotation ->
                val id = annotation.getData()?.asString ?: return@addClickListener false
                onSelectPlaceNow(id, annotation.point.toLatLng())
                true
            }
        }
    }
    val player = remember(mapView) {
        mapView.annotations.createPointAnnotationManager().apply {
            iconAllowOverlap = true
            iconIgnorePlacement = true
        }
    }

    DisposableEffect(mapView) {
        val camera = map.subscribeCameraChanged { onCameraMoveNow() }
        val errors = map.subscribeMapLoadingError {
            if (it.type == MapLoadingErrorType.STYLE ||
                (loadedStyle == null && it.type == MapLoadingErrorType.SOURCE)) {
                styleFailed = true
            }
        }
        onReadyNow(AtlasMapboxCamera(mapView))
        onDispose {
            errors.cancel()
            camera.cancel()
            onReadyNow(null)
        }
    }

    LaunchedEffect(mapView, topPadding, bottomPadding) {
        // Mapbox camera padding and plugin margins use Android pixels.
        map.setCamera(CameraOptions.Builder().padding(EdgeInsets(topPadding.toDouble(), 0.0, bottomPadding.toDouble(), 0.0)).build())
        mapView.logo.marginBottom = bottomPadding.toFloat()
        mapView.attribution.marginBottom = bottomPadding.toFloat()
    }

    DisposableEffect(map, mapStyle) {
        var active = true
        loadedStyle = null
        styleFailed = false
        val styleUri = if (mapStyle == AtlasMapStyle.SATELLITE) Style.STANDARD_SATELLITE else Style.STANDARD
        map.loadStyle(styleUri) {
            if (active) {
                // The shared screen-space route and glow layer needs a Mercator projection.
                it.setProjection(Projection(ProjectionName.MERCATOR))
                loadedStyle = it
                styleFailed = false
                onCameraMoveNow()
            }
        }
        onDispose { active = false }
    }

    val style = loadedStyle ?: return
    LaunchedEffect(style, routeWaypoints, showQuestRoute, accentArgb) {
        style.setQuestRoute(routeWaypoints.takeIf { showQuestRoute }.orEmpty(), accentArgb)
    }
    LaunchedEffect(style, isDark, is3DMode, mapStyle) {
        style.setStyleImportConfigProperty("basemap", "lightPreset", Value.valueOf(if (isDark) "night" else "day"))
        style.setStyleImportConfigProperty("basemap", "showPointOfInterestLabels", Value.valueOf(false))
        if (mapStyle == AtlasMapStyle.STANDARD) {
            style.setStyleImportConfigProperty("basemap", "show3dObjects", Value.valueOf(is3DMode))
            style.setStyleImportConfigProperty("basemap", "theme", Value.valueOf(if (isDark) "monochrome" else "faded"))
        }
    }

    val pinDetails = markers.map { marker ->
        MapboxPin(
            place = marker.place,
            count = marker.tasks.size,
            selected = marker.place.id == selectedPlaceId,
            nearest = marker.isNearestBranchForTag,
            inside = marker.isInsideGeofence,
            urgent = marker.highestPriority == TaskPriority.URGENT
        )
    }
    val activeGlows = remember(style) { mutableSetOf<String>() }
    val glows = remember(isDark, glowArgb, urgentArgb) {
        val strengths = if (isDark) listOf(92, 168, 192) else listOf(78, 152, 174)
        listOf(glowArgb, urgentArgb).associateWith { color ->
            strengths.map { createFeatheredGeofenceBitmap(color, it) }
        }
    }
    LaunchedEffect(style, pinDetails, density, isDark, accentArgb, urgentArgb, glows) {
        val visibleGlows = pinDetails.mapTo(mutableSetOf()) { "atlas-place-${it.place.id}" }
        (activeGlows - visibleGlows).forEach(style::removeGroundImage)
        activeGlows.retainAll(visibleGlows)

        val annotations = pinDetails.map { pin ->
            val point = LatLng(pin.place.latitude, pin.place.longitude)
            val groundId = "atlas-place-${pin.place.id}"
            val tier = when {
                pin.inside -> 2
                pin.selected -> 1
                else -> 0
            }
            val glow = glows.getValue(if (pin.urgent) urgentArgb else glowArgb)[tier]
            style.setGroundImage(groundId, point, pin.place.radiusMeters.toDouble().coerceAtLeast(18.0), glow)
            activeGlows.add(groundId)

            PointAnnotationOptions()
                .withPoint(point.toMapboxPoint())
                .withIconImage(createQuestPinBitmap(
                    density, pin.count, pin.place.label, pin.selected, pin.nearest, pin.inside,
                    isDark, if (pin.urgent) urgentArgb else accentArgb
                ))
                .withIconAnchor(IconAnchor.BOTTOM)
                .withIconOpacity(if (pin.selected || pin.nearest || pin.inside) 1.0 else 0.84)
                .withIconEmissiveStrength(1.0)
                .withSymbolSortKey(if (pin.selected) 5.0 else 3.0)
                .withData(JsonPrimitive(pin.place.id))
        }
        pins.deleteAll()
        pins.create(annotations)
    }

    val playerBitmap = remember(density, accentArgb) { createPlayerDotBitmap(density, accentArgb) }
    var playerPin by remember(player, playerBitmap) { mutableStateOf<PointAnnotation?>(null) }
    LaunchedEffect(style, userLocation, playerBitmap) {
        val point = userLocation ?: return@LaunchedEffect
        val pin = playerPin
        if (pin == null) {
            player.deleteAll()
            playerPin = player.create(PointAnnotationOptions()
                .withPoint(point.toMapboxPoint())
                .withIconImage(playerBitmap)
                .withIconEmissiveStrength(1.0))
        } else {
            pin.point = point.toMapboxPoint()
            player.update(pin)
        }
    }

    val headingBitmap = remember(isDark, glowArgb) { createFeatheredHeadingBitmap(glowArgb, if (isDark) 132 else 110) }
    LaunchedEffect(style, userLocation, compassBearing, headingBitmap) {
        val point = userLocation ?: return@LaunchedEffect
        style.setGroundImage("atlas-heading", point, 65.0, headingBitmap, compassBearing, refreshImage = false)
    }
    LaunchedEffect(style, headingBitmap) {
        style.getSourceAs<ImageSource>("atlas-heading")?.updateImage(headingBitmap)
    }
}

internal fun Locale.toMapboxLabelLanguage(): String = when (language) {
    "zh" -> when {
        script == "Hant" -> "zh-Hant"
        script.isEmpty() && country in listOf("TW", "HK", "MO") -> "zh-Hant"
        else -> "zh-Hans"
    }
    else -> language.ifEmpty { "en" }
}

private data class MapboxPin(
    val place: TagPlace,
    val count: Int,
    val selected: Boolean,
    val nearest: Boolean,
    val inside: Boolean,
    val urgent: Boolean
)

private class AtlasMapboxView(
    context: Context,
    initialCamera: CameraPosition,
    private val onInteraction: () -> Unit
) : MapView(context, MapInitOptions(context, cameraOptions = initialCamera.toMapboxCamera(), textureView = true, styleUri = null)) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) onInteraction()
        return super.dispatchTouchEvent(event)
    }
}

private class AtlasMapboxCamera(private val view: MapView) : AtlasMapCamera {
    private val map get() = view.mapboxMap

    override val position: CameraPosition
        get() = map.cameraState.let {
            CameraPosition(it.center.toLatLng(), (it.zoom + 1.0).toFloat(), it.pitch.toFloat(), it.bearing.toFloat())
        }

    override fun move(position: CameraPosition) {
        view.camera.cancelAllAnimators()
        map.setCamera(position.toMapboxCamera())
    }

    override fun animate(position: CameraPosition, durationMs: Int) {
        map.easeTo(position.toMapboxCamera(), MapAnimationOptions.mapAnimationOptions { duration(durationMs.toLong()) })
    }

    override fun projection(): ((LatLng) -> Offset?)? {
        if (!map.isStyleLoaded()) return null
        return { point ->
            val pixel = map.pixelForCoordinate(point.toMapboxPoint())
            if (pixel.x.isFinite() && pixel.y.isFinite() && (pixel.x != -1.0 || pixel.y != -1.0)) {
                Offset(pixel.x.toFloat(), pixel.y.toFloat())
            } else {
                null
            }
        }
    }
}

private fun CameraPosition.toMapboxCamera(): CameraOptions = CameraOptions.Builder()
    .center(target.toMapboxPoint())
    .zoom(zoom.toDouble() - 1.0)
    .pitch(tilt.toDouble())
    .bearing(bearing.toDouble())
    .build()

private fun LatLng.toMapboxPoint(): Point = Point.fromLngLat(longitude, latitude)
private fun Point.toLatLng(): LatLng = LatLng(latitude(), longitude())

private fun Style.setQuestRoute(waypoints: List<Pair<Double, Double>>, color: Int) {
    val sourceId = "atlas-route"
    val layerId = "atlas-route-glow"
    val existing = getSourceAs<GeoJsonSource>(sourceId)
    if (waypoints.size < 2) {
        if (existing != null) {
            removeStyleLayer(layerId)
            removeStyleSource(sourceId)
        }
        return
    }

    val path = LineString.fromLngLats(waypoints.map { (lat, lng) -> Point.fromLngLat(lng, lat) })
    if (existing == null) {
        addSource(geoJsonSource(sourceId) { geometry(path) })
        // The renderer clips this glow when a route continues past the visible region.
        addLayer(lineLayer(layerId, sourceId) {
            slot("middle")
            lineColor(color)
            lineWidth(6.0)
            lineBlur(3.0)
            lineOpacity(0.40)
            lineEmissiveStrength(1.0)
            lineCap(LineCap.ROUND)
            lineJoin(LineJoin.ROUND)
        })
    } else {
        existing.geometry(path)
        getLayerAs<LineLayer>(layerId)?.lineColor(color)
    }
}

private fun Style.setGroundImage(
    id: String,
    point: LatLng,
    halfWidthMeters: Double,
    bitmap: Bitmap,
    bearing: Float = 0f,
    refreshImage: Boolean = true
) {
    val corners = groundImageCorners(point, halfWidthMeters, bearing)
    val existing = getSourceAs<ImageSource>(id)
    if (existing != null) {
        existing.coordinates(corners)
        if (refreshImage) existing.updateImage(bitmap)
    } else {
        val source = imageSource(id) { coordinates(corners) }
        addSource(source)
        addLayer(rasterLayer("$id-raster", id) {
            slot("middle")
            rasterFadeDuration(0.0)
            rasterEmissiveStrength(1.0)
        })
        source.updateImage(bitmap)
    }
}

private fun Style.removeGroundImage(id: String) {
    removeStyleLayer("$id-raster")
    removeStyleSource(id)
}

private fun groundImageCorners(point: LatLng, halfWidthMeters: Double, bearing: Float): List<List<Double>> {
    val latitude = Math.toRadians(point.latitude)
    val longitude = Math.toRadians(point.longitude)
    val radius = hypot(halfWidthMeters, halfWidthMeters) / 6_371_000.0
    return listOf(-45.0, 45.0, 135.0, 225.0).map { corner ->
        val azimuth = Math.toRadians(bearing + corner)
        val lat = asin((sin(latitude) * cos(radius) + cos(latitude) * sin(radius) * cos(azimuth)).coerceIn(-1.0, 1.0))
        val lng = longitude + atan2(sin(azimuth) * sin(radius) * cos(latitude), cos(radius) - sin(latitude) * sin(lat))
        listOf((Math.toDegrees(lng) + 540.0) % 360.0 - 180.0, Math.toDegrees(lat))
    }
}
