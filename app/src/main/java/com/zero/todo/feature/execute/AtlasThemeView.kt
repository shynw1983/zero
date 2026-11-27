package com.zero.todo.feature.execute

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Route
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
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
import com.google.android.gms.maps.model.Dot
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolygonOptions
import com.google.android.gms.maps.model.PolylineOptions
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

private const val PLAYER_MARKER_TAG = "__zero_player__"

@Composable
fun AtlasThemeView(
    state: ExecuteUiState,
    isMapDark: Boolean = state.isDarkTheme,
    onToggleMapDark: () -> Unit = {},
    topContentPadding: Dp = 68.dp,
    bottomContentPadding: Dp = 148.dp,
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

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasLocationPermission = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    // Live GPS updates (GMS FusedLocation + Android LocationManager fallback)
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

    // Live Compass Azimuth Sensor (throttled to >= 4 deg delta for smooth battery-safe rotation)
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
                if (diff >= 4f) {
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
        var stage = 0 // 0=none, 1=created, 2=started, 3=resumed
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

    // Apply light/dark custom style whenever map or toggle changes
    LaunchedEffect(googleMapRef, isMapDark) {
        val gMap = googleMapRef ?: return@LaunchedEffect
        val json = if (isMapDark) AtlasMapStyles.OBSIDIAN_DARK_STYLE_JSON else AtlasMapStyles.COOL_PAPER_LIGHT_STYLE_JSON
        runCatching { gMap.setMapStyle(MapStyleOptions(json)) }
    }

    // Pad Google Maps camera focus & Google watermark cleanly between floating frosted top & bottom chrome
    val statusTopPx = WindowInsets.statusBars.getTop(density)
    val navBottomPx = WindowInsets.navigationBars.getBottom(density)
    val mapTopPadPx = with(density) { topContentPadding.roundToPx() } + statusTopPx
    val mapBottomPadPx = with(density) { bottomContentPadding.roundToPx() } + navBottomPx
    LaunchedEffect(googleMapRef, mapTopPadPx, mapBottomPadPx) {
        googleMapRef?.setPadding(0, mapTopPadPx, 0, mapBottomPadPx)
    }

    // Center camera smoothly on initial player fix or first marker
    fun animateCameraTo(lat: Double, lng: Double, zoom: Float? = null) {
        val gMap = googleMapRef ?: return
        val targetZoom = zoom ?: if (is3DMode) 16.5f else 15.1f
        val cam = CameraPosition.Builder()
            .target(LatLng(lat, lng))
            .zoom(targetZoom)
            .tilt(if (is3DMode) 55f else 0f)
            .bearing(0f)
            .build()
        gMap.animateCamera(CameraUpdateFactory.newCameraPosition(cam), 450, null)
    }

    LaunchedEffect(googleMapRef, userLat, userLng, snapshot.markers.firstOrNull()?.place?.id) {
        val gMap = googleMapRef ?: return@LaunchedEffect
        if (hasInitialCameraCentered) return@LaunchedEffect
        val lat = userLat ?: snapshot.markers.firstOrNull()?.place?.latitude ?: 35.6812
        val lng = userLng ?: snapshot.markers.firstOrNull()?.place?.longitude ?: 139.7671
        val zoom = if (userLat != null || snapshot.markers.isNotEmpty()) 16.4f else 12.5f
        val cam = CameraPosition.Builder()
            .target(LatLng(lat, lng))
            .zoom(zoom)
            .tilt(if (is3DMode) 55f else 0f)
            .build()
        gMap.moveCamera(CameraUpdateFactory.newCameraPosition(cam))
        if (userLat != null || snapshot.markers.isNotEmpty()) {
            hasInitialCameraCentered = true
        }
    }

    // Smoothly change camera tilt when switching 3D <-> 2D
    LaunchedEffect(googleMapRef, is3DMode) {
        val gMap = googleMapRef ?: return@LaunchedEffect
        val cur = gMap.cameraPosition
        val updated = CameraPosition.Builder(cur)
            .tilt(if (is3DMode) 55f else 0f)
            .zoom(if (is3DMode) cur.zoom.coerceAtLeast(16.0f) else cur.zoom.coerceAtMost(15.8f))
            .build()
        gMap.animateCamera(CameraUpdateFactory.newCameraPosition(updated), 380, null)
    }

    val accentArgb = colors.accent.toArgb()
    val urgentArgb = colors.priorityUrgent.toArgb()
    val surfaceArgb = if (isMapDark) 0xFF141419.toInt() else 0xFFFFFFFF.toInt()
    val textArgb = if (isMapDark) 0xFFF4F4F5.toInt() else 0xFF111827.toInt()

    // Sync markers, ground geofence circles, heading FOV cone, and dotted quest route
    LaunchedEffect(
        googleMapRef,
        snapshot,
        selectedMarker?.place?.id,
        userLat,
        userLng,
        compassBearing,
        isMapDark,
        showQuestRoute
    ) {
        val gMap = googleMapRef ?: return@LaunchedEffect
        gMap.clear()

        // 1. Dotted Nearest-Neighbor Quest Route
        if (showQuestRoute && snapshot.routeWaypoints.size >= 2) {
            gMap.addPolyline(
                PolylineOptions()
                    .addAll(snapshot.routeWaypoints.map { LatLng(it.first, it.second) })
                    .width(6f)
                    .color(ColorUtils.setAlphaComponent(accentArgb, 210))
                    .pattern(listOf(Dot(), Gap(16f)))
                    .zIndex(2f)
            )
        }

        // 2. Quest Markers + Ground Geofence Circles
        snapshot.markers.forEach { qMarker ->
            val isSelected = qMarker.place.id == selectedMarker?.place?.id
            val center = LatLng(qMarker.place.latitude, qMarker.place.longitude)
            val baseColor = if (qMarker.highestPriority == TaskPriority.URGENT) urgentArgb else accentArgb

            val circleStrokeAlpha = when {
                qMarker.isInsideGeofence -> 230
                isSelected -> 185
                qMarker.isNearestBranchForTag -> 115
                else -> 65
            }
            val circleFillAlpha = when {
                qMarker.isInsideGeofence -> 48
                isSelected -> 30
                else -> 14
            }
            gMap.addCircle(
                CircleOptions()
                    .center(center)
                    .radius(qMarker.place.radiusMeters.toDouble())
                    .strokeWidth(if (isSelected || qMarker.isInsideGeofence) 4f else 2f)
                    .strokeColor(ColorUtils.setAlphaComponent(baseColor, circleStrokeAlpha))
                    .fillColor(ColorUtils.setAlphaComponent(baseColor, circleFillAlpha))
                    .zIndex(1f)
            )

            val badgeIcon = createQuestBeaconIcon(
                density = context.resources.displayMetrics.density,
                count = qMarker.tasks.size,
                isSelected = isSelected,
                isNearest = qMarker.isNearestBranchForTag,
                isInside = qMarker.isInsideGeofence,
                accentColor = baseColor,
                fillColor = surfaceArgb,
                textColor = textArgb
            )
            val markerAlpha = if (isSelected || qMarker.isNearestBranchForTag || qMarker.isInsideGeofence) 1f else 0.72f
            gMap.addMarker(
                MarkerOptions()
                    .position(center)
                    .icon(badgeIcon)
                    .anchor(0.5f, 0.5f)
                    .alpha(markerAlpha)
                    .zIndex(if (isSelected) 5f else 3f)
            )?.apply {
                tag = qMarker.place.id
            }
        }

        // 3. Player Marker & Compass Heading Cone
        val pLat = userLat
        val pLng = userLng
        if (pLat != null && pLng != null) {
            val coneVertices = AtlasQuestBuilder.computeHeadingCone(pLat, pLng, compassBearing)
            gMap.addPolygon(
                PolygonOptions()
                    .addAll(coneVertices.map { LatLng(it.first, it.second) })
                    .strokeWidth(0f)
                    .fillColor(ColorUtils.setAlphaComponent(accentArgb, 60))
                    .zIndex(4f)
            )
            val playerIcon = createPlayerDotIcon(
                density = context.resources.displayMetrics.density,
                accentColor = accentArgb
            )
            gMap.addMarker(
                MarkerOptions()
                    .position(LatLng(pLat, pLng))
                    .icon(playerIcon)
                    .anchor(0.5f, 0.5f)
                    .zIndex(6f)
            )?.apply {
                tag = PLAYER_MARKER_TAG
            }
        }
    }

    val hudBg = if (isMapDark) Color(0xB5141822) else Color(0xD6FFFFFF)
    val hudSheen = if (isMapDark) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.38f)
    val hudBorder = if (isMapDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.10f)
    val hudPrimaryText = if (isMapDark) Color(0xFFF4F4F5) else Color(0xFF111827)
    val hudSecondaryText = if (isMapDark) Color(0xFFC0C6D4) else Color(0xFF52525B)

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Live 3D Google Map Surface
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
                    gMap.setOnMarkerClickListener { clicked ->
                        val id = clicked.tag as? String
                        if (id != null && id != PLAYER_MARKER_TAG) {
                            haptics.reorderTick()
                            selectedPlaceId = id
                            animateCameraTo(clicked.position.latitude, clicked.position.longitude)
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

        // 2. Top-Left Tactical Status Chips & Permission Notice
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 12.dp, top = topContentPadding + 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(ZeroCapsuleShape)
                        .background(hudBg)
                        .background(Brush.verticalGradient(listOf(hudSheen, Color.Transparent)))
                        .hairlineBorder(shape = ZeroCapsuleShape, color = hudBorder)
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = stringResource(R.string.atlas_places_count, snapshot.markers.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = hudPrimaryText
                    )
                    selectedMarker?.distanceMeters?.let { dist ->
                        Text(
                            text = "· ${AtlasQuestBuilder.formatDistance(dist)}",
                            style = tabular.dateCompact,
                            color = colors.accent
                        )
                    }
                }

                if (snapshot.anywhereTasks.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .clip(ZeroCapsuleShape)
                            .background(hudBg)
                            .background(Brush.verticalGradient(listOf(hudSheen, Color.Transparent)))
                            .hairlineBorder(shape = ZeroCapsuleShape, color = hudBorder)
                            .clickable {
                                haptics.reorderTick()
                                showAnywhereSheet = true
                            }
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Explore,
                            contentDescription = null,
                            tint = hudSecondaryText,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.atlas_anywhere_chip, snapshot.anywhereTasks.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = hudPrimaryText
                        )
                    }
                }
            }

            if (!hasLocationPermission) {
                Row(
                    modifier = Modifier
                        .clip(ZeroSmallShape)
                        .background(hudBg)
                        .background(Brush.verticalGradient(listOf(hudSheen, Color.Transparent)))
                        .hairlineBorder(shape = ZeroSmallShape, color = colors.priorityMedium.copy(alpha = 0.65f))
                        .clickable {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MyLocation,
                        contentDescription = null,
                        tint = colors.priorityMedium,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = stringResource(R.string.atlas_gps_need_perm),
                        style = MaterialTheme.typography.bodySmall,
                        color = hudPrimaryText
                    )
                    Text(
                        text = stringResource(R.string.atlas_grant_gps),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.accent
                    )
                }
            }
        }

        // 3. Top-Right Minimalist Map Hardware Controls (Theme Light/Dark, 3D/2D, Route, Recenter)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 12.dp, top = topContentPadding + 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            AtlasControlButton(
                bg = hudBg,
                sheen = hudSheen,
                border = hudBorder,
                onClick = {
                    haptics.reorderTick()
                    onToggleMapDark()
                }
            ) {
                Icon(
                    imageVector = if (isMapDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                    contentDescription = stringResource(R.string.atlas_toggle_map_theme),
                    tint = hudPrimaryText,
                    modifier = Modifier.size(19.dp)
                )
            }

            AtlasControlButton(
                bg = hudBg,
                sheen = hudSheen,
                border = if (is3DMode) colors.accent.copy(alpha = 0.7f) else hudBorder,
                onClick = {
                    haptics.reorderTick()
                    is3DMode = !is3DMode
                }
            ) {
                Text(
                    text = stringResource(if (is3DMode) R.string.atlas_mode_3d else R.string.atlas_mode_2d),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (is3DMode) colors.accent else hudSecondaryText
                )
            }

            AtlasControlButton(
                bg = hudBg,
                sheen = hudSheen,
                border = if (showQuestRoute) colors.accent.copy(alpha = 0.7f) else hudBorder,
                onClick = {
                    haptics.reorderTick()
                    showQuestRoute = !showQuestRoute
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Route,
                    contentDescription = stringResource(R.string.atlas_toggle_route),
                    tint = if (showQuestRoute) colors.accent else hudSecondaryText,
                    modifier = Modifier.size(19.dp)
                )
            }

            AtlasControlButton(
                bg = hudBg,
                sheen = hudSheen,
                border = hudBorder,
                onClick = {
                    haptics.reorderTick()
                    if (!hasLocationPermission) {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                        return@AtlasControlButton
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
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.MyLocation,
                    contentDescription = stringResource(R.string.atlas_recenter),
                    tint = colors.accent,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        // 4. Bottom Floating Quest HUD Card
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .widthIn(max = ExecuteMaxContentWidth)
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = bottomContentPadding + 8.dp)
        ) {
            if (selectedMarker != null) {
                val safeTaskIdx = focusedTaskIdxInPlace.coerceIn(0, selectedMarker.tasks.lastIndex)
                val focusedTask = selectedMarker.tasks[safeTaskIdx]
                AtlasQuestHudCard(
                    marker = selectedMarker,
                    taskIndex = safeTaskIdx,
                    focusedTask = focusedTask,
                    hudBg = hudBg,
                    hudSheen = hudSheen,
                    hudBorder = hudBorder,
                    primaryText = hudPrimaryText,
                    secondaryText = hudSecondaryText,
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
                    onCompleteTask = { onCompleteTask(focusedTask) }
                )
            } else {
                AtlasEmptyQuestCard(
                    hudBg = hudBg,
                    hudSheen = hudSheen,
                    hudBorder = hudBorder,
                    primaryText = hudPrimaryText,
                    secondaryText = hudSecondaryText,
                    accentColor = colors.accent,
                    onOpenVault = onSwitchToVault
                )
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

@Composable
private fun AtlasControlButton(
    bg: Color,
    sheen: Color,
    border: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(ZeroSmallShape)
            .background(bg)
            .background(Brush.verticalGradient(listOf(sheen, Color.Transparent)))
            .hairlineBorder(shape = ZeroSmallShape, color = border)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun AtlasQuestHudCard(
    marker: AtlasQuestMarker,
    taskIndex: Int,
    focusedTask: TaskItem,
    hudBg: Color,
    hudSheen: Color,
    hudBorder: Color,
    primaryText: Color,
    secondaryText: Color,
    accentColor: Color,
    onPrevTask: () -> Unit,
    onNextTask: () -> Unit,
    onNavigate: () -> Unit,
    onOpenTaskDetail: () -> Unit,
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(hudBg)
            .background(Brush.verticalGradient(listOf(hudSheen, Color.Transparent)))
            .hairlineBorder(
                shape = RoundedCornerShape(18.dp),
                color = if (marker.isInsideGeofence) accentColor else hudBorder
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Place Header + Distance + Navigation CTA
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = marker.place.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (marker.isInsideGeofence) {
                        Text(
                            text = stringResource(R.string.atlas_inside_geofence),
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            modifier = Modifier
                                .clip(ZeroCapsuleShape)
                                .background(accentColor.copy(alpha = 0.16f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    } else if (marker.distanceMeters != null) {
                        Text(
                            text = AtlasQuestBuilder.formatDistance(marker.distanceMeters),
                            style = tabular.dateCompact,
                            color = accentColor
                        )
                    }
                }
                Text(
                    text = "#${marker.tagPath}",
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                modifier = Modifier
                    .heightIn(min = 38.dp)
                    .clip(ZeroSmallShape)
                    .background(accentColor.copy(alpha = 0.14f))
                    .hairlineBorder(shape = ZeroSmallShape, color = accentColor.copy(alpha = 0.5f))
                    .clickable(onClick = onNavigate)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Navigation,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = stringResource(R.string.atlas_navigate_btn),
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor
                )
            }
        }

        // Active Task Row + Multi-task Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ZeroSmallShape)
                .background(primaryText.copy(alpha = 0.05f))
                .clickable(onClick = onOpenTaskDetail)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = focusedTask.title,
                style = MaterialTheme.typography.bodyMedium,
                color = primaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (marker.tasks.size > 1) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevTask, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = null,
                            tint = secondaryText,
                            modifier = Modifier.size(18.dp)
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
                    IconButton(onClick = onNextTask, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = secondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Tactile 0.3s Hold-to-Complete Anti-Misclick Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(ZeroSmallShape)
                .background(accentColor.copy(alpha = 0.14f))
                .hairlineBorder(shape = ZeroSmallShape, color = accentColor.copy(alpha = 0.55f))
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
                                holdProgress = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
                                if (elapsed >= durationMs) {
                                    completed = true
                                    holdProgress = 1f
                                    haptics.confirm()
                                    onCompleteTask()
                                    break
                                }
                                delay(16L)
                                released = tryAwaitRelease()
                            }
                            if (!completed) {
                                holdProgress = 0f
                            }
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            if (animatedHold > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedHold)
                        .fillMaxHeight()
                        .background(accentColor.copy(alpha = 0.45f))
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(
                        if (holdProgress > 0.05f) R.string.atlas_keep_holding else R.string.atlas_hold_complete
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = accentColor
                )
            }
        }
    }
}

@Composable
private fun AtlasEmptyQuestCard(
    hudBg: Color,
    hudSheen: Color,
    hudBorder: Color,
    primaryText: Color,
    secondaryText: Color,
    accentColor: Color,
    onOpenVault: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(hudBg)
            .background(Brush.verticalGradient(listOf(hudSheen, Color.Transparent)))
            .hairlineBorder(shape = RoundedCornerShape(18.dp), color = hudBorder)
            .padding(14.dp),
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
            color = secondaryText
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .clip(ZeroSmallShape)
                .background(accentColor.copy(alpha = 0.14f))
                .hairlineBorder(shape = ZeroSmallShape, color = accentColor.copy(alpha = 0.5f))
                .clickable(onClick = onOpenVault)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.atlas_open_vault_tags),
                style = MaterialTheme.typography.labelLarge,
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
    val sizePx = (28f * density).toInt().coerceAtLeast(36)
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = sizePx / 2f
    val cy = sizePx / 2f

    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorUtils.setAlphaComponent(accentColor, 64)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, sizePx * 0.48f, haloPaint)

    val whiteRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, sizePx * 0.30f, whiteRingPaint)

    val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, sizePx * 0.21f, corePaint)
    return BitmapDescriptorFactory.fromBitmap(bmp)
}

private fun createQuestBeaconIcon(
    density: Float,
    count: Int,
    isSelected: Boolean,
    isNearest: Boolean,
    isInside: Boolean,
    accentColor: Int,
    fillColor: Int,
    textColor: Int
): BitmapDescriptor {
    val sizeDp = if (isSelected || isInside) 38f else 32f
    val sizePx = (sizeDp * density).toInt().coerceAtLeast(44)
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = sizePx / 2f
    val cy = sizePx / 2f

    if (isSelected || isInside || isNearest) {
        val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(accentColor, if (isSelected || isInside) 72 else 42)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, sizePx * 0.48f, pulsePaint)
    }

    val radius = sizePx * 0.35f
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isSelected || isInside) accentColor else fillColor
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, radius, bodyPaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isSelected || isInside) 0xFFFFFFFF.toInt() else accentColor
        style = Paint.Style.STROKE
        strokeWidth = (if (isSelected) 2.2f else 1.6f) * density
    }
    canvas.drawCircle(cx, cy, radius, borderPaint)

    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isSelected || isInside) 0xFFFFFFFF.toInt() else textColor
        textSize = 12f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val label = count.coerceAtMost(99).toString()
    val textY = cy - (labelPaint.descent() + labelPaint.ascent()) / 2f
    canvas.drawText(label, cx, textY, labelPaint)

    val bounds = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
    bounds.hashCode()
    return BitmapDescriptorFactory.fromBitmap(bmp)
}
