package com.zero.todo.feature.execute

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.PolygonOptions
import com.zero.todo.R
import com.zero.todo.core.designsystem.ZeroCapsuleShape
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.rememberZeroHaptics
import com.zero.todo.core.geofence.PlaceSearchEngine
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.TaskPriority
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val PLAYER_MARKER_TAG = "__zero_player__"

private data class AtlasShockwave(
    val id: Long,
    val lat: Double,
    val lng: Double
)

@Composable
fun AtlasThemeView(
    state: ExecuteUiState,
    isMapDark: Boolean = state.isDarkTheme,
    onToggleMapDark: () -> Unit = {},
    bottomModeBarHeight: Dp = 64.dp,
    isQuickInputExpanded: Boolean = false,
    onToggleQuickInput: () -> Unit = {},
    onOpenTriage: () -> Unit = {},
    onOpenThemePicker: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onCompleteTask: (TaskItem) -> Unit,
    onOpenTaskDetail: (TaskItem) -> Unit,
    onSwitchToVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = rememberZeroHaptics()
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val scope = rememberCoroutineScope()

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var userLat by remember { mutableStateOf<Double?>(null) }
    var userLng by remember { mutableStateOf<Double?>(null) }
    var compassBearing by remember { mutableFloatStateOf(0f) }

    var is3DMode by remember { mutableStateOf(true) }
    var showQuestRoute by remember { mutableStateOf(true) }
    var selectedPlaceId by remember { mutableStateOf<String?>(null) }
    var focusedTaskIdxInPlace by remember(selectedPlaceId) { mutableIntStateOf(0) }
    var showAnywhereSheet by remember { mutableStateOf(false) }

    var googleMapRef by remember { mutableStateOf<GoogleMap?>(null) }
    var hasInitialCameraCentered by remember { mutableStateOf(false) }
    var playerMarkerRef by remember { mutableStateOf<Marker?>(null) }
    var headingConeRef by remember { mutableStateOf<Polygon?>(null) }
    var cameraMoveTick by remember { mutableLongStateOf(0L) }

    // 60fps Spatial Motion & HUD Choreography states
    var chromeEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { chromeEntered = true }
    val chromeMotion by animateFloatAsState(
        targetValue = if (chromeEntered) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.80f, stiffness = 360f),
        label = "atlas_chrome_spring"
    )
    var activeHoldProgress by remember { mutableFloatStateOf(0f) }
    var shockwaveEvent by remember { mutableStateOf<AtlasShockwave?>(null) }
    val shockwaveProgress = remember { Animatable(0f) }
    LaunchedEffect(shockwaveEvent?.id) {
        if (shockwaveEvent != null) {
            shockwaveProgress.snapTo(0f)
            shockwaveProgress.animateTo(1f, tween(durationMillis = 720, easing = FastOutSlowInEasing))
            shockwaveEvent = null
        }
    }

    val motionTransition = rememberInfiniteTransition(label = "atlas_spatial_motion")
    val breathePhase by motionTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "atlas_breathe_phase"
    )
    val streamPhase by motionTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "atlas_stream_phase"
    )
    val statusDotPulse by motionTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "atlas_status_dot"
    )

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasLocationPermission = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    // Live GPS updates
    @SuppressLint("MissingPermission")
    DisposableEffect(hasLocationPermission) {
        if (!hasLocationPermission) return@DisposableEffect onDispose {}

        val initJob = scope.launch {
            PlaceSearchEngine.fetchCurrentLocation(context)?.let { (lat, lng) ->
                userLat = lat
                userLng = lng
            }
        }

        val gmsAvailable = runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }.getOrDefault(false)

        var gmsClient: com.google.android.gms.location.FusedLocationProviderClient? = null
        var gmsCallback: LocationCallback? = null
        var lm: LocationManager? = null
        var lmListener: LocationListener? = null

        if (gmsAvailable) {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    userLat = loc.latitude
                    userLng = loc.longitude
                }
            }
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4_000L)
                .setMinUpdateIntervalMillis(2_000L)
                .setMinUpdateDistanceMeters(4f)
                .build()
            runCatching {
                client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            }
            gmsClient = client
            gmsCallback = callback
        } else {
            val manager = context.getSystemService(LocationManager::class.java)
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    userLat = location.latitude
                    userLng = location.longitude
                }
            }
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
                runCatching {
                    manager?.requestLocationUpdates(provider, 4_000L, 4f, listener, Looper.getMainLooper())
                }
            }
            lm = manager
            lmListener = listener
        }

        onDispose {
            initJob.cancel()
            if (gmsClient != null && gmsCallback != null) {
                runCatching { gmsClient.removeLocationUpdates(gmsCallback) }
            }
            if (lm != null && lmListener != null) {
                runCatching { lm.removeUpdates(lmListener) }
            }
        }
    }

    // Smooth azimuth compass for directional sector
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensorManager == null || rotationSensor == null) {
            return@DisposableEffect onDispose {}
        }
        val rotMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
                SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                SensorManager.getOrientation(rotMatrix, orientation)
                val deg = ((Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f)
                val diff = abs(deg - compassBearing).let { if (it > 180f) 360f - it else it }
                if (diff >= 5f) {
                    compassBearing = deg
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    val snapshot = remember(
        state.allActiveTasks,
        state.tagsWithCounts,
        state.tagPathById,
        state.runwayQueue,
        userLat,
        userLng
    ) {
        AtlasQuestBuilder.build(
            allActiveTasks = state.allActiveTasks,
            tagsWithCounts = state.tagsWithCounts,
            tagPathById = state.tagPathById,
            runwayQueue = state.runwayQueue,
            userLat = userLat,
            userLng = userLng
        )
    }

    val selectedMarker = remember(snapshot.markers, selectedPlaceId) {
        snapshot.markers.firstOrNull { it.place.id == selectedPlaceId }
            ?: snapshot.markers.firstOrNull()
    }
    LaunchedEffect(snapshot.markers) {
        if (selectedPlaceId != null && snapshot.markers.none { it.place.id == selectedPlaceId }) {
            selectedPlaceId = snapshot.markers.firstOrNull()?.place?.id
        }
    }

    val mapView = remember {
        runCatching { MapsInitializer.initialize(context, MapsInitializer.Renderer.LATEST) {} }
        MapView(context)
    }

    DisposableEffect(lifecycleOwner, mapView) {
        var stage = 0
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> if (stage < 1) {
                    mapView.onCreate(Bundle())
                    stage = 1
                }
                Lifecycle.Event.ON_START -> {
                    if (stage < 1) {
                        mapView.onCreate(Bundle())
                        stage = 1
                    }
                    if (stage < 2) {
                        mapView.onStart()
                        stage = 2
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (stage < 1) {
                        mapView.onCreate(Bundle())
                        stage = 1
                    }
                    if (stage < 2) {
                        mapView.onStart()
                        stage = 2
                    }
                    if (stage < 3) {
                        mapView.onResume()
                        stage = 3
                    }
                }
                Lifecycle.Event.ON_PAUSE -> if (stage >= 3) {
                    mapView.onPause()
                    stage = 2
                }
                Lifecycle.Event.ON_STOP -> if (stage >= 2) {
                    mapView.onStop()
                    stage = 1
                }
                Lifecycle.Event.ON_DESTROY -> if (stage >= 1) {
                    mapView.onDestroy()
                    stage = 0
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (stage >= 3) mapView.onPause()
            if (stage >= 2) mapView.onStop()
            if (stage >= 1) mapView.onDestroy()
            googleMapRef = null
        }
    }

    LaunchedEffect(googleMapRef, isMapDark) {
        val gMap = googleMapRef ?: return@LaunchedEffect
        val json = if (isMapDark) AtlasMapStyles.OBSIDIAN_DARK_STYLE_JSON else AtlasMapStyles.COOL_PAPER_LIGHT_STYLE_JSON
        runCatching { gMap.setMapStyle(MapStyleOptions(json)) }
    }

    fun animateCameraTo(
        lat: Double,
        lng: Double,
        zoom: Float? = null,
        orbitParallax: Boolean = false
    ) {
        val gMap = googleMapRef ?: return
        val targetZoom = zoom ?: if (is3DMode) 17.2f else 15.2f
        val curBearing = gMap.cameraPosition.bearing
        val targetBearing = when {
            !is3DMode -> 0f
            orbitParallax -> (curBearing + 14f) % 360f
            else -> 22f
        }
        val cam = CameraPosition.Builder()
            .target(LatLng(lat, lng))
            .zoom(targetZoom)
            .tilt(if (is3DMode) 62f else 0f)
            .bearing(targetBearing)
            .build()
        gMap.animateCamera(CameraUpdateFactory.newCameraPosition(cam), if (orbitParallax) 650 else 480, null)
    }

    // Cinematic 3D fly-in swoop on first entry
    LaunchedEffect(googleMapRef, userLat, userLng, snapshot.markers.firstOrNull()?.place?.id) {
        val gMap = googleMapRef ?: return@LaunchedEffect
        if (hasInitialCameraCentered) return@LaunchedEffect
        val hasFix = userLat != null || snapshot.markers.isNotEmpty()
        val lat = userLat ?: snapshot.markers.firstOrNull()?.place?.latitude ?: 35.6812
        val lng = userLng ?: snapshot.markers.firstOrNull()?.place?.longitude ?: 139.7671
        if (!hasFix) {
            val overview = CameraPosition.Builder()
                .target(LatLng(lat, lng))
                .zoom(13.2f)
                .tilt(24f)
                .bearing(0f)
                .build()
            gMap.moveCamera(CameraUpdateFactory.newCameraPosition(overview))
            return@LaunchedEffect
        }

        val startCam = CameraPosition.Builder()
            .target(LatLng(lat, lng))
            .zoom(14.8f)
            .tilt(22f)
            .bearing(0f)
            .build()
        gMap.moveCamera(CameraUpdateFactory.newCameraPosition(startCam))
        delay(60L)
        val landCam = CameraPosition.Builder()
            .target(LatLng(lat, lng))
            .zoom(if (is3DMode) 17.2f else 15.2f)
            .tilt(if (is3DMode) 62f else 0f)
            .bearing(if (is3DMode) 22f else 0f)
            .build()
        gMap.animateCamera(CameraUpdateFactory.newCameraPosition(landCam), 1050, null)
        hasInitialCameraCentered = true
    }

    LaunchedEffect(googleMapRef, is3DMode) {
        val gMap = googleMapRef ?: return@LaunchedEffect
        val cur = gMap.cameraPosition
        val updated = CameraPosition.Builder(cur)
            .tilt(if (is3DMode) 62f else 0f)
            .bearing(if (is3DMode && cur.bearing == 0f) 22f else if (!is3DMode) 0f else cur.bearing)
            .zoom(if (is3DMode) cur.zoom.coerceAtLeast(16.7f) else cur.zoom.coerceAtMost(15.6f))
            .build()
        gMap.animateCamera(CameraUpdateFactory.newCameraPosition(updated), 420, null)
    }

    val accentArgb = colors.accent.toArgb()
    val urgentArgb = colors.priorityUrgent.toArgb()

    // Effect 1: Clean geofence circles & floating architectural pill pins (routes handled by 60fps overlay)
    LaunchedEffect(
        googleMapRef,
        snapshot,
        selectedMarker?.place?.id,
        isMapDark,
        accentArgb
    ) {
        val gMap = googleMapRef ?: return@LaunchedEffect
        gMap.clear()
        playerMarkerRef = null
        headingConeRef = null

        snapshot.markers.forEach { qMarker ->
            val isSelected = qMarker.place.id == selectedMarker?.place?.id
            val center = LatLng(qMarker.place.latitude, qMarker.place.longitude)
            val baseColor = if (qMarker.highestPriority == TaskPriority.URGENT) urgentArgb else accentArgb

            val strokeAlpha = when {
                qMarker.isInsideGeofence -> 165
                isSelected -> 125
                else -> 50
            }
            val fillAlpha = when {
                qMarker.isInsideGeofence -> 28
                isSelected -> 14
                else -> 5
            }
            gMap.addCircle(
                CircleOptions()
                    .center(center)
                    .radius(qMarker.place.radiusMeters.toDouble())
                    .strokeWidth(2.2f)
                    .strokeColor(ColorUtils.setAlphaComponent(baseColor, strokeAlpha))
                    .fillColor(ColorUtils.setAlphaComponent(baseColor, fillAlpha))
                    .zIndex(1f)
            )

            val pinIcon = createQuestPinIcon(
                density = context.resources.displayMetrics.density,
                count = qMarker.tasks.size,
                label = qMarker.place.label,
                isSelected = isSelected,
                isNearest = qMarker.isNearestBranchForTag,
                isInside = qMarker.isInsideGeofence,
                isMapDark = isMapDark,
                accentColor = baseColor
            )
            gMap.addMarker(
                MarkerOptions()
                    .position(center)
                    .icon(pinIcon)
                    .anchor(0.5f, 1.0f)
                    .alpha(if (isSelected || qMarker.isNearestBranchForTag || qMarker.isInsideGeofence) 1f else 0.84f)
                    .zIndex(if (isSelected) 5f else 3f)
            )?.apply {
                tag = qMarker.place.id
            }
        }
        cameraMoveTick++
    }

    // Effect 2: In-place Player Dot & subtle directional sector (no marker flicker)
    LaunchedEffect(
        googleMapRef,
        userLat,
        userLng,
        compassBearing,
        isMapDark,
        accentArgb,
        snapshot,
        selectedMarker?.place?.id
    ) {
        val gMap = googleMapRef ?: return@LaunchedEffect
        val pLat = userLat ?: return@LaunchedEffect
        val pLng = userLng ?: return@LaunchedEffect
        val playerLatLng = LatLng(pLat, pLng)
        val coneVertices = AtlasQuestBuilder.computeHeadingCone(
            lat = pLat,
            lng = pLng,
            bearingDegrees = compassBearing,
            rangeMeters = 55.0,
            halfAngleDeg = 24.0
        ).map { LatLng(it.first, it.second) }

        val sectorAlpha = if (isMapDark) 34 else 26
        val existingCone = headingConeRef
        if (existingCone == null) {
            headingConeRef = gMap.addPolygon(
                PolygonOptions()
                    .addAll(coneVertices)
                    .strokeWidth(0f)
                    .fillColor(ColorUtils.setAlphaComponent(accentArgb, sectorAlpha))
                    .zIndex(4f)
            )
        } else {
            existingCone.points = coneVertices
            existingCone.fillColor = ColorUtils.setAlphaComponent(accentArgb, sectorAlpha)
        }

        val playerIcon = createPlayerDotIcon(
            density = context.resources.displayMetrics.density,
            accentColor = accentArgb
        )
        val existingMarker = playerMarkerRef
        if (existingMarker == null) {
            playerMarkerRef = gMap.addMarker(
                MarkerOptions()
                    .position(playerLatLng)
                    .icon(playerIcon)
                    .anchor(0.5f, 0.5f)
                    .zIndex(6f)
            )?.apply {
                tag = PLAYER_MARKER_TAG
            }
        } else {
            existingMarker.position = playerLatLng
            existingMarker.setIcon(playerIcon)
        }
        cameraMoveTick++
    }

    val surfaceBg = if (isMapDark) Color(0xEB12151D) else Color(0xF5FFFFFF)
    val surfaceBorder = if (isMapDark) Color.White.copy(alpha = 0.11f) else Color.Black.copy(alpha = 0.08f)
    val primaryText = if (isMapDark) Color(0xFFF3F4F6) else Color(0xFF111827)
    val secondaryText = if (isMapDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        val statusTopPx = WindowInsets.statusBars.getTop(density)
        val navBottomPx = WindowInsets.navigationBars.getBottom(density)
        val mapTopPadPx = with(density) { 58.dp.roundToPx() } + statusTopPx
        val mapBottomPadPx = with(density) { (bottomModeBarHeight + 112.dp).roundToPx() } + navBottomPx
        LaunchedEffect(googleMapRef, mapTopPadPx, mapBottomPadPx) {
            googleMapRef?.setPadding(0, mapTopPadPx, 0, mapBottomPadPx)
        }

        // 1. Live 3D Architectural Google Map Surface
        AndroidView(
            factory = {
                mapView.getMapAsync { gMap ->
                    gMap.isBuildingsEnabled = true
                    gMap.isTrafficEnabled = false
                    gMap.uiSettings.apply {
                        isCompassEnabled = false
                        isMapToolbarEnabled = false
                        isMyLocationButtonEnabled = false
                        isTiltGesturesEnabled = true
                        isRotateGesturesEnabled = true
                        isZoomControlsEnabled = false
                    }
                    gMap.setOnCameraMoveListener { cameraMoveTick++ }
                    gMap.setOnCameraIdleListener { cameraMoveTick++ }
                    gMap.setOnMarkerClickListener { clicked ->
                        val id = clicked.tag as? String
                        if (id != null && id != PLAYER_MARKER_TAG) {
                            haptics.reorderTick()
                            selectedPlaceId = id
                            animateCameraTo(
                                lat = clicked.position.latitude,
                                lng = clicked.position.longitude,
                                orbitParallax = true
                            )
                            true
                        } else {
                            false
                        }
                    }
                    googleMapRef = gMap
                }
                mapView
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. 60fps GPU Spatial Motion Layer (Energy Stream Route, Player Breathe Halo, Hold Ring & Shockwave)
        AtlasSpatialMotionOverlay(
            googleMap = googleMapRef,
            cameraMoveTick = cameraMoveTick,
            userLat = userLat,
            userLng = userLng,
            selectedMarker = selectedMarker,
            routeWaypoints = snapshot.routeWaypoints,
            showQuestRoute = showQuestRoute,
            breathePhase = breathePhase,
            streamPhase = streamPhase,
            holdProgress = activeHoldProgress,
            shockwave = shockwaveEvent,
            shockwaveProgress = shockwaveProgress.value,
            accentColor = colors.accent,
            modifier = Modifier.fillMaxSize()
        )

        // 3. Minimal Floating Top Bar (spring float-in entrance)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .graphicsLayer {
                    alpha = chromeMotion
                    translationY = (1f - chromeMotion) * -14.dp.toPx()
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .height(38.dp)
                            .shadow(8.dp, ZeroCapsuleShape, clip = false)
                            .clip(ZeroCapsuleShape)
                            .background(surfaceBg)
                            .hairlineBorder(shape = ZeroCapsuleShape, color = surfaceBorder)
                            .padding(horizontal = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(colors.accent.copy(alpha = statusDotPulse))
                        )
                        Text(
                            text = "ATLAS",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryText
                        )
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelMedium,
                            color = secondaryText
                        )
                        Text(
                            text = stringResource(R.string.atlas_places_count, snapshot.markers.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = secondaryText
                        )
                        selectedMarker?.distanceMeters?.let { dist ->
                            Text(
                                text = AtlasQuestBuilder.formatDistance(dist),
                                style = tabular.dateCompact,
                                color = colors.accent
                            )
                        }
                    }

                    if (snapshot.anywhereTasks.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .height(38.dp)
                                .shadow(8.dp, ZeroCapsuleShape, clip = false)
                                .clip(ZeroCapsuleShape)
                                .background(surfaceBg)
                                .hairlineBorder(shape = ZeroCapsuleShape, color = surfaceBorder)
                                .clickable {
                                    haptics.reorderTick()
                                    showAnywhereSheet = true
                                }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.atlas_anywhere_chip, snapshot.anywhereTasks.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = primaryText
                            )
                        }
                    }
                }

                if (!hasLocationPermission) {
                    Row(
                        modifier = Modifier
                            .shadow(6.dp, ZeroCapsuleShape, clip = false)
                            .clip(ZeroCapsuleShape)
                            .background(surfaceBg)
                            .hairlineBorder(shape = ZeroCapsuleShape, color = colors.priorityMedium.copy(alpha = 0.55f))
                            .clickable {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MyLocation,
                            contentDescription = null,
                            tint = colors.priorityMedium,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.atlas_gps_need_perm),
                            style = MaterialTheme.typography.labelMedium,
                            color = primaryText
                        )
                        Text(
                            text = stringResource(R.string.atlas_grant_gps),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.accent
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.triageCount > 0) {
                    Row(
                        modifier = Modifier
                            .height(38.dp)
                            .shadow(8.dp, ZeroCapsuleShape, clip = false)
                            .clip(ZeroCapsuleShape)
                            .background(surfaceBg)
                            .hairlineBorder(shape = ZeroCapsuleShape, color = surfaceBorder)
                            .clickable {
                                haptics.confirm()
                                onOpenTriage()
                            }
                            .padding(horizontal = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Layers,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = state.triageCount.toString(),
                            style = tabular.dateCompact,
                            color = colors.accent
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .height(38.dp)
                        .shadow(8.dp, ZeroCapsuleShape, clip = false)
                        .clip(ZeroCapsuleShape)
                        .background(surfaceBg)
                        .hairlineBorder(shape = ZeroCapsuleShape, color = surfaceBorder)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    TopIconAction(
                        onClick = {
                            haptics.reorderTick()
                            onToggleQuickInput()
                        }
                    ) {
                        Icon(
                            imageVector = if (isQuickInputExpanded) Icons.Rounded.Close else Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.shortcut_quick_input),
                            tint = if (isQuickInputExpanded) colors.accent else primaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    TopIconAction(
                        onClick = {
                            haptics.reorderTick()
                            onOpenThemePicker()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = stringResource(R.string.cd_switch_execute_theme),
                            tint = colors.accent,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    TopIconAction(
                        onClick = {
                            haptics.reorderTick()
                            onOpenSettings()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.cd_open_settings),
                            tint = secondaryText,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }

        // 4. Right-Edge Minimalist Map Control Pill
        AtlasControlDock(
            isMapDark = isMapDark,
            is3DMode = is3DMode,
            showQuestRoute = showQuestRoute,
            bg = surfaceBg,
            border = surfaceBorder,
            primaryText = primaryText,
            secondaryText = secondaryText,
            accentColor = colors.accent,
            onToggleMapDark = {
                haptics.reorderTick()
                onToggleMapDark()
            },
            onToggle3D = {
                haptics.reorderTick()
                is3DMode = !is3DMode
            },
            onToggleRoute = {
                haptics.reorderTick()
                showQuestRoute = !showQuestRoute
            },
            onRecenter = {
                haptics.reorderTick()
                if (!hasLocationPermission) {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                    return@AtlasControlDock
                }
                val curLat = userLat
                val curLng = userLng
                if (curLat != null && curLng != null) {
                    animateCameraTo(curLat, curLng)
                } else {
                    scope.launch {
                        PlaceSearchEngine.fetchCurrentLocation(context)?.let { (lat, lng) ->
                            userLat = lat
                            userLng = lng
                            animateCameraTo(lat, lng)
                        }
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .graphicsLayer {
                    alpha = chromeMotion
                    translationX = (1f - chromeMotion) * 14.dp.toPx()
                }
                .padding(end = 14.dp, top = 62.dp)
        )

        // 5. Slim Responsive Quest Mission Card
        val showMissionCard = !isQuickInputExpanded
        val cardAlignment = if (isWideScreen) Alignment.BottomStart else Alignment.BottomCenter
        val cardWidthMod = if (isWideScreen) {
            Modifier.width(312.dp)
        } else {
            Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
        }
        val cardPadding = if (isWideScreen) {
            Modifier.padding(start = 14.dp, bottom = bottomModeBarHeight + 10.dp)
        } else {
            Modifier.padding(horizontal = 14.dp).padding(bottom = bottomModeBarHeight + 6.dp)
        }

        AnimatedVisibility(
            visible = showMissionCard,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(140)),
            modifier = Modifier
                .align(cardAlignment)
                .navigationBarsPadding()
                .graphicsLayer {
                    alpha = chromeMotion
                    translationY = (1f - chromeMotion) * 18.dp.toPx()
                }
                .then(cardPadding)
        ) {
            Box(modifier = cardWidthMod) {
                if (selectedMarker != null) {
                    val safeTaskIdx = focusedTaskIdxInPlace.coerceIn(0, selectedMarker.tasks.lastIndex)
                    val focusedTask = selectedMarker.tasks[safeTaskIdx]
                    AtlasQuestHudCard(
                        marker = selectedMarker,
                        taskIndex = safeTaskIdx,
                        focusedTask = focusedTask,
                        surfaceBg = surfaceBg,
                        surfaceBorder = surfaceBorder,
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        accentColor = colors.accent,
                        onPrevTask = {
                            haptics.reorderTick()
                            focusedTaskIdxInPlace = (safeTaskIdx - 1 + selectedMarker.tasks.size) % selectedMarker.tasks.size
                        },
                        onNextTask = {
                            haptics.reorderTick()
                            focusedTaskIdxInPlace = (safeTaskIdx + 1) % selectedMarker.tasks.size
                        },
                        onNavigate = {
                            haptics.confirm()
                            launchMapsNavigation(
                                context = context,
                                lat = selectedMarker.place.latitude,
                                lng = selectedMarker.place.longitude,
                                label = selectedMarker.place.label
                            )
                        },
                        onOpenTaskDetail = { onOpenTaskDetail(focusedTask) },
                        onHoldProgressChange = { activeHoldProgress = it },
                        onCompleteTask = {
                            shockwaveEvent = AtlasShockwave(
                                id = System.currentTimeMillis(),
                                lat = selectedMarker.place.latitude,
                                lng = selectedMarker.place.longitude
                            )
                            activeHoldProgress = 0f
                            onCompleteTask(focusedTask)
                        }
                    )
                } else {
                    AtlasEmptyQuestCard(
                        surfaceBg = surfaceBg,
                        surfaceBorder = surfaceBorder,
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        accentColor = colors.accent,
                        onOpenVault = onSwitchToVault
                    )
                }
            }
        }
    }

    if (showAnywhereSheet) {
        AtlasAnywhereTasksSheet(
            tasks = snapshot.anywhereTasks,
            onSelectTask = { task ->
                showAnywhereSheet = false
                onOpenTaskDetail(task)
            },
            onCompleteTask = { task ->
                haptics.confirm()
                onCompleteTask(task)
            },
            onDismiss = { showAnywhereSheet = false }
        )
    }
}

/**
 * High-precision 60fps Compose Canvas overlay synced directly to GoogleMap [Projection].
 * Renders flowing energy pulses along routes, soft Apple-style breathing locator halo,
 * live hold-to-complete charging reticle, and architectural completion shockwaves.
 */
@Composable
private fun AtlasSpatialMotionOverlay(
    googleMap: GoogleMap?,
    cameraMoveTick: Long,
    userLat: Double?,
    userLng: Double?,
    selectedMarker: AtlasQuestMarker?,
    routeWaypoints: List<Pair<Double, Double>>,
    showQuestRoute: Boolean,
    breathePhase: Float,
    streamPhase: Float,
    holdProgress: Float,
    shockwave: AtlasShockwave?,
    shockwaveProgress: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (googleMap == null) return@Canvas
        // Read cameraMoveTick so pan/zoom/tilt/fly anims invalidate projection cleanly at 60fps
        @Suppress("UNUSED_VARIABLE")
        val tick = cameraMoveTick
        val projection = runCatching { googleMap.projection }.getOrNull() ?: return@Canvas

        fun project(lat: Double, lng: Double): Offset? = runCatching {
            val pt = projection.toScreenLocation(LatLng(lat, lng))
            Offset(pt.x.toFloat(), pt.y.toFloat())
        }.getOrNull()

        // 1. Subtle Route Track + 60fps Flowing SignalOrange Comet Pulses
        if (showQuestRoute && routeWaypoints.size >= 2) {
            val screenPts = routeWaypoints.mapNotNull { project(it.first, it.second) }
            if (screenPts.size >= 2) {
                var totalLen = 0f
                val segLens = FloatArray(screenPts.size - 1)
                for (i in 0 until screenPts.lastIndex) {
                    val segLen = hypot(
                        (screenPts[i + 1].x - screenPts[i].x).toDouble(),
                        (screenPts[i + 1].y - screenPts[i].y).toDouble()
                    ).toFloat()
                    segLens[i] = segLen
                    totalLen += segLen
                    drawLine(
                        color = accentColor.copy(alpha = 0.28f),
                        start = screenPts[i],
                        end = screenPts[i + 1],
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                if (totalLen > 12f) {
                    fun sampleAt(distAlong: Float): Offset {
                        var rem = distAlong.coerceIn(0f, totalLen)
                        for (i in segLens.indices) {
                            val len = segLens[i]
                            if (rem <= len || i == segLens.lastIndex) {
                                val t = if (len > 0f) (rem / len).coerceIn(0f, 1f) else 0f
                                val a = screenPts[i]
                                val b = screenPts[i + 1]
                                return Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                            }
                            rem -= len
                        }
                        return screenPts.last()
                    }

                    // Draw 2 staggered luminous stream pulses gliding toward target buildings
                    val tailSpan = (totalLen * 0.14f).coerceIn(22.dp.toPx(), 64.dp.toPx())
                    for (pulseIdx in 0..1) {
                        val p = (streamPhase + pulseIdx * 0.5f) % 1f
                        val headDist = p * totalLen
                        val steps = 8
                        for (s in 0 until steps) {
                            val fracA = s.toFloat() / steps
                            val fracB = (s + 1).toFloat() / steps
                            val dA = (headDist - tailSpan * (1f - fracA)).coerceAtLeast(0f)
                            val dB = (headDist - tailSpan * (1f - fracB)).coerceAtLeast(0f)
                            if (dB > dA) {
                                drawLine(
                                    color = accentColor.copy(alpha = fracB * 0.85f),
                                    start = sampleAt(dA),
                                    end = sampleAt(dB),
                                    strokeWidth = (1.6f + fracB * 2.2f).dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                        val headPos = sampleAt(headDist)
                        drawCircle(
                            color = accentColor.copy(alpha = 0.32f),
                            radius = 5.5.dp.toPx(),
                            center = headPos
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.dp.toPx(),
                            center = headPos
                        )
                    }
                }
            }
        }

        // 2. Soft Apple-Style Breathing Halo around Player Locator
        if (userLat != null && userLng != null) {
            project(userLat, userLng)?.let { playerPt ->
                val radiusPx = (10f + breathePhase * 22f).dp.toPx()
                val fade = (1f - breathePhase).coerceIn(0f, 1f)
                drawCircle(
                    color = accentColor.copy(alpha = fade * 0.34f),
                    radius = radiusPx,
                    center = playerPt,
                    style = Stroke(width = 1.4.dp.toPx())
                )
            }
        }

        // 3. Active Geofence Breathing Rim + Hold-to-Complete Tightening Reticle
        selectedMarker?.let { sel ->
            project(sel.place.latitude, sel.place.longitude)?.let { tgtPt ->
                if (sel.isInsideGeofence && holdProgress <= 0.01f) {
                    val rPx = (14f + breathePhase * 24f).dp.toPx()
                    val fade = (1f - breathePhase).coerceIn(0f, 1f)
                    drawCircle(
                        color = accentColor.copy(alpha = fade * 0.42f),
                        radius = rPx,
                        center = tgtPt,
                        style = Stroke(width = 1.6.dp.toPx())
                    )
                }

                if (holdProgress > 0.01f) {
                    val ringRadius = (42f - holdProgress * 24f).dp.toPx()
                    drawCircle(
                        color = accentColor.copy(alpha = 0.25f + holdProgress * 0.45f),
                        radius = ringRadius,
                        center = tgtPt,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawArc(
                        color = accentColor,
                        startAngle = -90f,
                        sweepAngle = 360f * holdProgress,
                        useCenter = false,
                        topLeft = Offset(tgtPt.x - ringRadius, tgtPt.y - ringRadius),
                        size = Size(ringRadius * 2f, ringRadius * 2f),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }

        // 4. Completion Architectural Shockwave & 8 Radial Sparks
        if (shockwave != null && shockwaveProgress < 1f) {
            project(shockwave.lat, shockwave.lng)?.let { swPt ->
                val fade = (1f - shockwaveProgress).coerceIn(0f, 1f)
                val swRadius = (10f + shockwaveProgress * 94f).dp.toPx()
                val strokeW = (3.2f - shockwaveProgress * 2.5f).coerceAtLeast(0.6f).dp.toPx()
                drawCircle(
                    color = accentColor.copy(alpha = fade * 0.92f),
                    radius = swRadius,
                    center = swPt,
                    style = Stroke(width = strokeW)
                )
                val sparkInner = (14f + shockwaveProgress * 64f).dp.toPx()
                val sparkOuter = sparkInner + (10f * fade).dp.toPx()
                for (i in 0 until 8) {
                    val rad = Math.toRadians(i * 45.0)
                    val cx = cos(rad).toFloat()
                    val sy = sin(rad).toFloat()
                    drawLine(
                        color = accentColor.copy(alpha = fade * 0.85f),
                        start = Offset(swPt.x + cx * sparkInner, swPt.y + sy * sparkInner),
                        end = Offset(swPt.x + cx * sparkOuter, swPt.y + sy * sparkOuter),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun TopIconAction(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun AtlasControlDock(
    isMapDark: Boolean,
    is3DMode: Boolean,
    showQuestRoute: Boolean,
    bg: Color,
    border: Color,
    primaryText: Color,
    secondaryText: Color,
    accentColor: Color,
    onToggleMapDark: () -> Unit,
    onToggle3D: () -> Unit,
    onToggleRoute: () -> Unit,
    onRecenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .width(38.dp)
            .shadow(8.dp, shape, clip = false)
            .clip(shape)
            .background(bg)
            .hairlineBorder(shape = shape, color = border),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DockButton(onClick = onToggleMapDark) {
            Icon(
                imageVector = if (isMapDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                contentDescription = stringResource(R.string.atlas_toggle_map_theme),
                tint = primaryText,
                modifier = Modifier.size(17.dp)
            )
        }
        DockDivider(border)
        DockButton(onClick = onToggle3D) {
            Text(
                text = stringResource(if (is3DMode) R.string.atlas_mode_3d else R.string.atlas_mode_2d),
                style = MaterialTheme.typography.labelMedium,
                color = if (is3DMode) accentColor else secondaryText
            )
        }
        DockDivider(border)
        DockButton(onClick = onToggleRoute) {
            Icon(
                imageVector = Icons.Rounded.Route,
                contentDescription = stringResource(R.string.atlas_toggle_route),
                tint = if (showQuestRoute) accentColor else secondaryText,
                modifier = Modifier.size(17.dp)
            )
        }
        DockDivider(border)
        DockButton(onClick = onRecenter) {
            Icon(
                imageVector = Icons.Rounded.MyLocation,
                contentDescription = stringResource(R.string.atlas_recenter),
                tint = accentColor,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun DockButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun DockDivider(color: Color) {
    Box(
        modifier = Modifier
            .width(20.dp)
            .height(0.5.dp)
            .background(color)
    )
}

@Composable
private fun AtlasQuestHudCard(
    marker: AtlasQuestMarker,
    taskIndex: Int,
    focusedTask: TaskItem,
    surfaceBg: Color,
    surfaceBorder: Color,
    primaryText: Color,
    secondaryText: Color,
    accentColor: Color,
    onPrevTask: () -> Unit,
    onNextTask: () -> Unit,
    onNavigate: () -> Unit,
    onOpenTaskDetail: () -> Unit,
    onHoldProgressChange: (Float) -> Unit,
    onCompleteTask: () -> Unit
) {
    val haptics = rememberZeroHaptics()
    val tabular = ZeroTheme.tabularTypography
    var holdProgress by remember(focusedTask.id) { mutableFloatStateOf(0f) }
    val animatedHold by animateFloatAsState(
        targetValue = holdProgress,
        animationSpec = tween(durationMillis = 40),
        label = "atlas_hold_progress"
    )
    val cardShape = RoundedCornerShape(18.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, cardShape, clip = false)
            .clip(cardShape)
            .background(surfaceBg)
            .hairlineBorder(
                shape = cardShape,
                color = if (marker.isInsideGeofence) accentColor.copy(alpha = 0.65f) else surfaceBorder
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: Place Title + Metadata + Circular Navigate Icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = marker.place.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "#${marker.tagPath}",
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (marker.isInsideGeofence) {
                        Text(
                            text = "· ${stringResource(R.string.atlas_inside_geofence)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor
                        )
                    } else if (marker.distanceMeters != null) {
                        Text(
                            text = "· ${AtlasQuestBuilder.formatDistance(marker.distanceMeters)}",
                            style = tabular.dateCompact,
                            color = accentColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.14f))
                    .hairlineBorder(shape = CircleShape, color = accentColor.copy(alpha = 0.42f))
                    .clickable(onClick = onNavigate),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Navigation,
                    contentDescription = stringResource(R.string.atlas_navigate_btn),
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Row 2: Fluid Animated Task Title + Switcher + Inline Hold-to-Complete Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(ZeroSmallShape)
                    .background(primaryText.copy(alpha = 0.045f))
                    .clickable(onClick = onOpenTaskDetail)
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AnimatedContent(
                    targetState = focusedTask,
                    transitionSpec = {
                        (fadeIn(tween(180)) + slideInHorizontally(tween(180)) { it / 6 }) togetherWith
                            (fadeOut(tween(130)) + slideOutHorizontally(tween(130)) { -it / 6 })
                    },
                    modifier = Modifier.weight(1f),
                    label = "atlas_task_swap"
                ) { task ->
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (marker.tasks.size > 1) {
                    IconButton(onClick = onPrevTask, modifier = Modifier.size(22.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = null,
                            tint = secondaryText,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.atlas_open_tasks_at_place,
                            taskIndex + 1,
                            marker.tasks.size
                        ),
                        style = tabular.dateCompact,
                        color = secondaryText
                    )
                    IconButton(onClick = onNextTask, modifier = Modifier.size(22.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = secondaryText,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .height(34.dp)
                    .widthIn(min = 84.dp)
                    .clip(ZeroCapsuleShape)
                    .background(accentColor.copy(alpha = 0.14f))
                    .hairlineBorder(shape = ZeroCapsuleShape, color = accentColor.copy(alpha = 0.48f))
                    .pointerInput(focusedTask.id) {
                        detectTapGestures(
                            onPress = {
                                haptics.reorderTick()
                                var completed = false
                                val start = System.currentTimeMillis()
                                val durationMs = 300L
                                var released = false
                                while (!released && !completed) {
                                    val elapsed = System.currentTimeMillis() - start
                                    val progress = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
                                    holdProgress = progress
                                    onHoldProgressChange(progress)
                                    if (elapsed >= durationMs) {
                                        completed = true
                                        holdProgress = 1f
                                        onHoldProgressChange(1f)
                                        haptics.confirm()
                                        onCompleteTask()
                                        break
                                    }
                                    delay(16L)
                                    released = tryAwaitRelease()
                                }
                                if (!completed) {
                                    holdProgress = 0f
                                    onHoldProgressChange(0f)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                if (animatedHold > 0.01f) {
                    Box(modifier = Modifier.matchParentSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedHold)
                                .fillMaxHeight()
                                .background(accentColor.copy(alpha = 0.45f))
                        )
                    }
                }
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(
                            if (holdProgress > 0.05f) R.string.atlas_keep_holding else R.string.atlas_hold_short
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun AtlasEmptyQuestCard(
    surfaceBg: Color,
    surfaceBorder: Color,
    primaryText: Color,
    secondaryText: Color,
    accentColor: Color,
    onOpenVault: () -> Unit
) {
    val cardShape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, cardShape, clip = false)
            .clip(cardShape)
            .background(surfaceBg)
            .hairlineBorder(shape = cardShape, color = surfaceBorder)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.atlas_empty_title),
            style = MaterialTheme.typography.titleSmall,
            color = primaryText
        )
        Text(
            text = stringResource(R.string.atlas_empty_desc),
            style = MaterialTheme.typography.bodySmall,
            color = secondaryText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(ZeroCapsuleShape)
                .background(accentColor.copy(alpha = 0.14f))
                .hairlineBorder(shape = ZeroCapsuleShape, color = accentColor.copy(alpha = 0.45f))
                .clickable(onClick = onOpenVault),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.atlas_open_vault_tags),
                style = MaterialTheme.typography.labelMedium,
                color = accentColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AtlasAnywhereTasksSheet(
    tasks: List<TaskItem>,
    onSelectTask: (TaskItem) -> Unit,
    onCompleteTask: (TaskItem) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ZeroTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceElevated,
        contentColor = colors.textPrimary
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = ExecuteMaxContentWidth)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.atlas_anywhere_sheet_title, tasks.size),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary
            )
            if (tasks.isEmpty()) {
                Text(
                    text = stringResource(R.string.atlas_anywhere_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = tasks, key = { it.id }) { task ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ZeroSmallShape)
                                .background(colors.surface)
                                .hairlineBorder(shape = ZeroSmallShape)
                                .clickable { onSelectTask(task) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(colors.accent.copy(alpha = 0.14f))
                                    .hairlineBorder(shape = CircleShape, color = colors.accent.copy(alpha = 0.55f))
                                    .clickable { onCompleteTask(task) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = stringResource(R.string.action_done),
                                    tint = colors.accent,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

private fun launchMapsNavigation(context: android.content.Context, lat: Double, lng: Double, label: String) {
    val gmmIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("google.navigation:q=$lat,$lng&mode=w")
    ).apply {
        setPackage("com.google.android.apps.maps")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val launched = runCatching {
        context.startActivity(gmmIntent)
        true
    }.getOrDefault(false)
    if (!launched) {
        val geoFallback = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(geoFallback) }
    }
}

private fun createPlayerDotIcon(density: Float, accentColor: Int): BitmapDescriptor {
    val sizePx = (26f * density).toInt().coerceAtLeast(36)
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = sizePx / 2f
    val cy = sizePx / 2f

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x28000000
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy + 1f * density, sizePx * 0.34f, shadowPaint)

    val whiteRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, sizePx * 0.32f, whiteRingPaint)

    val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, sizePx * 0.21f, corePaint)

    return BitmapDescriptorFactory.fromBitmap(bmp)
}

/**
 * Clean floating architectural pill pin with a tiny 5dp triangular pointer tail (`anchor(0.5f, 1.0f)`).
 */
private fun createQuestPinIcon(
    density: Float,
    count: Int,
    label: String,
    isSelected: Boolean,
    isNearest: Boolean,
    isInside: Boolean,
    isMapDark: Boolean,
    accentColor: Int
): BitmapDescriptor {
    val highlighted = isSelected || isInside
    val showLabel = highlighted || isNearest
    val shortTitle = if (showLabel && label.isNotBlank()) {
        val trimmed = label.trim()
        if (trimmed.length > 10) trimmed.take(10) + "…" else trimmed
    } else {
        null
    }

    val titleColor = when {
        highlighted -> 0xFFFFFFFF.toInt()
        isMapDark -> 0xFFF3F4F6.toInt()
        else -> 0xFF111827.toInt()
    }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = titleColor
        textSize = 11.5f * density
        isFakeBoldText = highlighted
    }
    val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (highlighted) accentColor else 0xFFFFFFFF.toInt()
        textSize = 10.5f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    val badgeSize = 19f * density
    val pillPadH = 6f * density
    val titleW = shortTitle?.let { titlePaint.measureText(it) } ?: 0f
    val pillW = if (shortTitle != null) {
        pillPadH + badgeSize + 5f * density + titleW + pillPadH + 2f * density
    } else {
        badgeSize + 6f * density
    }
    val pillH = 26f * density
    val tailH = 5f * density
    val pad = 4f * density

    val widthPx = (pillW + pad * 2f).toInt().coerceAtLeast((30f * density).toInt())
    val heightPx = (pillH + tailH + pad * 2f).toInt().coerceAtLeast((36f * density).toInt())

    val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = widthPx / 2f
    val pillTop = pad
    val pillBottom = pillTop + pillH
    val pillRect = RectF(cx - pillW / 2f, pillTop, cx + pillW / 2f, pillBottom)
    val corner = pillH / 2f

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33000000
        style = Paint.Style.FILL
    }
    val shadowRect = RectF(pillRect).apply { offset(0f, 1.5f * density) }
    canvas.drawRoundRect(shadowRect, corner, corner, shadowPaint)

    val fillCol = when {
        highlighted -> accentColor
        isMapDark -> 0xF2151923.toInt()
        else -> 0xFAFFFFFF.toInt()
    }
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillCol
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(pillRect, corner, corner, bgPaint)

    val tailPath = Path().apply {
        moveTo(cx - 4.5f * density, pillBottom - 0.5f * density)
        lineTo(cx, pillBottom + tailH)
        lineTo(cx + 4.5f * density, pillBottom - 0.5f * density)
        close()
    }
    canvas.drawPath(tailPath, bgPaint)

    val borderCol = when {
        highlighted -> 0x33FFFFFF
        isMapDark -> 0x24FFFFFF
        else -> 0x16000000
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = borderCol
        style = Paint.Style.STROKE
        strokeWidth = 0.8f * density
    }
    canvas.drawRoundRect(pillRect, corner, corner, borderPaint)

    val badgeCx = if (shortTitle != null) pillRect.left + pillPadH + badgeSize / 2f else cx
    val badgeCy = pillTop + pillH / 2f
    val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (highlighted) 0xFFFFFFFF.toInt() else accentColor
        style = Paint.Style.FILL
    }
    canvas.drawCircle(badgeCx, badgeCy, badgeSize / 2f, badgeBgPaint)

    val countStr = count.coerceAtMost(99).toString()
    val countY = badgeCy - (countPaint.descent() + countPaint.ascent()) / 2f
    canvas.drawText(countStr, badgeCx, countY, countPaint)

    if (shortTitle != null) {
        val textX = pillRect.left + pillPadH + badgeSize + 5.5f * density
        val textY = badgeCy - (titlePaint.descent() + titlePaint.ascent()) / 2f
        canvas.drawText(shortTitle, textX, textY, titlePaint)
    }

    return BitmapDescriptorFactory.fromBitmap(bmp)
}
