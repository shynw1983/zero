package com.zero.todo.feature.folder

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zero.todo.R
import com.zero.todo.core.designsystem.ZeroCardShape
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.hairlineDividerBottom
import com.zero.todo.core.designsystem.rememberZeroHaptics
import com.zero.todo.core.geofence.GeofencePermissionState
import com.zero.todo.core.geofence.PlaceSearchEngine
import com.zero.todo.core.geofence.PlaceSearchResult
import com.zero.todo.domain.model.PlaceTriggerMode
import com.zero.todo.domain.model.TagItem
import com.zero.todo.domain.model.TagPlace
import kotlinx.coroutines.launch

private val RADIUS_PRESETS = listOf(100, 150, 300, 500, 1_000, 2_000, TagPlace.MAX_RADIUS_METERS)

private sealed interface PlaceEditorTarget {
    data class Existing(val place: TagPlace) : PlaceEditorTarget
    data class PendingNew(
        val defaultLabel: String,
        val address: String,
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Int
    ) : PlaceEditorTarget
}

private fun nextRadiusPreset(current: Int): Int {
    val idx = RADIUS_PRESETS.indexOf(current)
    return if (idx == -1) RADIUS_PRESETS.first() else RADIUS_PRESETS[(idx + 1) % RADIUS_PRESETS.size]
}

@Composable
fun TagPlaceConfigDialog(
    tag: TagItem,
    displayPath: String,
    activeTaskCount: Int,
    permissionStateProvider: () -> GeofencePermissionState,
    onUpdatePlaceConfig: (tagId: String, isPlaceTag: Boolean, triggerMode: PlaceTriggerMode) -> Unit,
    onAddPlace: (
        tagId: String,
        label: String,
        address: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int
    ) -> Unit,
    onUpdatePlace: (TagPlace) -> Unit,
    onDeletePlace: (placeId: String) -> Unit,
    onResyncGeofences: () -> Unit,
    onTestPlaceReminder: (TagItem, TagPlace) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ZeroTheme.colors
    val haptics = rememberZeroHaptics()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    var permissionState by remember(tag.id) { mutableStateOf(permissionStateProvider()) }
    var searchQuery by remember(tag.id) { mutableStateOf("") }
    var customLabelDraft by remember(tag.id) { mutableStateOf("") }
    var selectedRadius by remember(tag.id) { mutableIntStateOf(150) }
    var isSearching by remember(tag.id) { mutableStateOf(false) }
    var isFetchingCurrent by remember(tag.id) { mutableStateOf(false) }
    var searchResults by remember(tag.id) { mutableStateOf<List<PlaceSearchResult>>(emptyList()) }
    var searchAttemptedEmpty by remember(tag.id) { mutableStateOf(false) }
    var statusNotice by remember(tag.id) { mutableStateOf<String?>(null) }
    var editorTarget by remember(tag.id) { mutableStateOf<PlaceEditorTarget?>(null) }

    val noActiveTasksMsg = stringResource(R.string.place_no_active_tasks)
    val locationUnavailableMsg = stringResource(R.string.place_location_unavailable)

    // Refresh permission badge whenever returning from system Settings / permission sheet
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val updated = permissionStateProvider()
                if (updated != permissionState) {
                    permissionState = updated
                    onResyncGeofences()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        permissionState = permissionStateProvider()
        onResyncGeofences()
    }

    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionState = permissionStateProvider()
        onResyncGeofences()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            permissionState == GeofencePermissionState.FOREGROUND_ONLY
        ) {
            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    fun requestPermissionsOrOpenSettings() {
        when (permissionState) {
            GeofencePermissionState.DENIED -> {
                foregroundPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            GeofencePermissionState.FOREGROUND_ONLY -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
            GeofencePermissionState.GRANTED_BACKGROUND -> Unit
        }
    }

    fun openAppLocationSettings() {
        runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun performSearch() {
        val q = searchQuery.trim()
        if (q.isEmpty() || isSearching) return
        focusManager.clearFocus()
        keyboardController?.hide()
        isSearching = true
        searchAttemptedEmpty = false
        statusNotice = null
        scope.launch {
            val currentCoord = PlaceSearchEngine.fetchCurrentLocation(context)
            val results = PlaceSearchEngine.searchPlaces(
                context = context,
                query = q,
                biasLat = currentCoord?.first,
                biasLng = currentCoord?.second
            )
            searchResults = results
            searchAttemptedEmpty = results.isEmpty()
            isSearching = false
        }
    }

    fun addCurrentGpsLocation() {
        if (isFetchingCurrent) return
        if (permissionState == GeofencePermissionState.DENIED) {
            requestPermissionsOrOpenSettings()
            return
        }
        statusNotice = null
        isFetchingCurrent = true
        scope.launch {
            val coords = PlaceSearchEngine.fetchCurrentLocation(context)
            if (coords == null) {
                statusNotice = locationUnavailableMsg
                isFetchingCurrent = false
                return@launch
            }
            val (revLabel, revAddr) = PlaceSearchEngine.reverseGeocode(
                context,
                coords.first,
                coords.second
            )
            val alias = customLabelDraft.trim()
            if (alias.isNotEmpty()) {
                onAddPlace(
                    tag.id,
                    alias,
                    revAddr,
                    coords.first,
                    coords.second,
                    selectedRadius
                )
                customLabelDraft = ""
                haptics.confirm()
            } else {
                editorTarget = PlaceEditorTarget.PendingNew(
                    defaultLabel = revLabel,
                    address = revAddr,
                    latitude = coords.first,
                    longitude = coords.second,
                    radiusMeters = selectedRadius
                )
                haptics.reorderTick()
            }
            isFetchingCurrent = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .fillMaxWidth()
                .clip(ZeroCardShape)
                .background(colors.surfaceElevated)
                .hairlineBorder(shape = ZeroCardShape)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .hairlineDividerBottom()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = if (tag.isPlaceTag) colors.accent else colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.place_tag_dialog_title, "#$displayPath"),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 560.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Place Tag Toggle
                item(key = "place_toggle") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ZeroSmallShape)
                            .background(colors.surface)
                            .hairlineBorder(
                                shape = ZeroSmallShape,
                                color = if (tag.isPlaceTag) colors.accent.copy(alpha = 0.45f) else colors.hairlineBorder
                            )
                            .clickable {
                                haptics.reorderTick()
                                val nextEnabled = !tag.isPlaceTag
                                onUpdatePlaceConfig(tag.id, nextEnabled, tag.placeTriggerMode)
                                if (nextEnabled && permissionState == GeofencePermissionState.DENIED) {
                                    requestPermissionsOrOpenSettings()
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.place_tag_enable_label),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary
                            )
                            Text(
                                text = stringResource(R.string.place_tag_enable_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted
                            )
                        }
                        Switch(
                            checked = tag.isPlaceTag,
                            onCheckedChange = { enabled ->
                                haptics.reorderTick()
                                onUpdatePlaceConfig(tag.id, enabled, tag.placeTriggerMode)
                                if (enabled && permissionState == GeofencePermissionState.DENIED) {
                                    requestPermissionsOrOpenSettings()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.onAccent,
                                checkedTrackColor = colors.accent
                            )
                        )
                    }
                }

                // Location Permission Status Banner (when not fully granted for background geofencing)
                if (tag.isPlaceTag && permissionState != GeofencePermissionState.GRANTED_BACKGROUND) {
                    item(key = "permission_banner") {
                        val isDenied = permissionState == GeofencePermissionState.DENIED
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ZeroSmallShape)
                                .background(colors.priorityMedium.copy(alpha = 0.12f))
                                .hairlineBorder(
                                    shape = ZeroSmallShape,
                                    color = colors.priorityMedium.copy(alpha = 0.55f)
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    if (isDenied) R.string.place_perm_denied else R.string.place_perm_fg_only
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textPrimary
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(ZeroSmallShape)
                                        .background(colors.accent)
                                        .clickable { requestPermissionsOrOpenSettings() }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = stringResource(
                                            if (isDenied) R.string.place_perm_grant_fg else R.string.place_perm_grant_bg
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.onAccent
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(ZeroSmallShape)
                                        .hairlineBorder(shape = ZeroSmallShape)
                                        .clickable { openAppLocationSettings() }
                                        .padding(horizontal = 10.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.place_perm_settings),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                } else if (tag.isPlaceTag) {
                    item(key = "permission_ok") {
                        Text(
                            text = stringResource(R.string.place_perm_bg_ok),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.accent
                        )
                    }
                }

                // Trigger Mode Selector (Enter / Exit / Both)
                item(key = "trigger_mode") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.place_trigger_mode_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val options = listOf(
                                PlaceTriggerMode.ENTER to R.string.place_trigger_enter,
                                PlaceTriggerMode.EXIT to R.string.place_trigger_exit,
                                PlaceTriggerMode.BOTH to R.string.place_trigger_both
                            )
                            options.forEach { (mode, labelRes) ->
                                val selected = tag.placeTriggerMode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 38.dp)
                                        .clip(ZeroSmallShape)
                                        .background(if (selected) colors.accentSubtle else colors.surface)
                                        .hairlineBorder(
                                            shape = ZeroSmallShape,
                                            color = if (selected) colors.accent else colors.hairlineBorder
                                        )
                                        .clickable {
                                            haptics.reorderTick()
                                            onUpdatePlaceConfig(tag.id, tag.isPlaceTag, mode)
                                        }
                                        .padding(horizontal = 6.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(labelRes),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) colors.accent else colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Saved Locations Header + List (1 or multiple addresses per tag!)
                item(key = "saved_header") {
                    Text(
                        text = stringResource(R.string.place_saved_locations_title, tag.places.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary
                    )
                }

                if (tag.places.isEmpty()) {
                    item(key = "saved_empty") {
                        Text(
                            text = stringResource(R.string.place_saved_locations_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ZeroSmallShape)
                                .background(colors.surface)
                                .hairlineBorder(shape = ZeroSmallShape)
                                .padding(12.dp)
                        )
                    }
                } else {
                    items(items = tag.places, key = { it.id }) { place ->
                        SavedPlaceRow(
                            place = place,
                            onToggleEnabled = {
                                haptics.reorderTick()
                                onUpdatePlace(place.copy(isEnabled = !place.isEnabled))
                            },
                            onCycleRadius = {
                                haptics.reorderTick()
                                onUpdatePlace(place.copy(radiusMeters = nextRadiusPreset(place.radiusMeters)))
                            },
                            onEdit = {
                                haptics.reorderTick()
                                editorTarget = PlaceEditorTarget.Existing(place)
                            },
                            onTestAlert = {
                                if (activeTaskCount <= 0) {
                                    statusNotice = noActiveTasksMsg
                                } else {
                                    statusNotice = null
                                    haptics.confirm()
                                    onTestPlaceReminder(tag, place)
                                }
                            },
                            onDelete = {
                                haptics.heavyClick()
                                onDeletePlace(place.id)
                            }
                        )
                    }
                }

                // Add New Place Section (Custom label + Radius + 1-Tap Current GPS + Search Box)
                item(key = "add_place_section") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ZeroSmallShape)
                            .background(colors.surface)
                            .hairlineBorder(shape = ZeroSmallShape)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Optional Alias Input + Default Radius Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(ZeroSmallShape)
                                    .background(colors.background)
                                    .hairlineBorder(shape = ZeroSmallShape)
                                    .padding(horizontal = 10.dp, vertical = 9.dp)
                            ) {
                                if (customLabelDraft.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.place_label_placeholder),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = customLabelDraft,
                                    onValueChange = { customLabelDraft = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
                                    cursorBrush = SolidColor(colors.accent),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(ZeroSmallShape)
                                    .background(colors.background)
                                    .hairlineBorder(shape = ZeroSmallShape)
                                    .clickable {
                                        haptics.reorderTick()
                                        selectedRadius = nextRadiusPreset(selectedRadius)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.place_radius_value, radiusLabel(selectedRadius)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.accent
                                )
                            }
                        }

                        // 1-Tap Use Current Location
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 42.dp)
                                .clip(ZeroSmallShape)
                                .background(colors.accentSubtle)
                                .hairlineBorder(shape = ZeroSmallShape, color = colors.accent.copy(alpha = 0.55f))
                                .clickable(enabled = !isFetchingCurrent) { addCurrentGpsLocation() }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                        ) {
                            if (isFetchingCurrent) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = colors.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.place_fetching_location),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.accent
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.MyLocation,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.place_use_current_btn),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.accent
                                )
                            }
                        }

                        // Address / Store / Maps URL / Coordinate Search Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(ZeroSmallShape)
                                    .background(colors.background)
                                    .hairlineBorder(shape = ZeroSmallShape)
                                    .padding(horizontal = 10.dp, vertical = 10.dp)
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.place_search_placeholder),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
                                    cursorBrush = SolidColor(colors.accent),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { performSearch() }),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .heightIn(min = 38.dp)
                                    .clip(ZeroSmallShape)
                                    .background(if (searchQuery.isNotBlank()) colors.accent else colors.background)
                                    .hairlineBorder(
                                        shape = ZeroSmallShape,
                                        color = if (searchQuery.isNotBlank()) colors.accent else colors.hairlineBorder
                                    )
                                    .clickable(enabled = searchQuery.isNotBlank() && !isSearching) {
                                        performSearch()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSearching) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        color = colors.onAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = stringResource(R.string.place_search_btn),
                                        tint = if (searchQuery.isNotBlank()) colors.onAccent else colors.textMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        statusNotice?.let { notice ->
                            Text(
                                text = notice,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.priorityUrgent
                            )
                        }

                        if (searchAttemptedEmpty) {
                            Text(
                                text = stringResource(R.string.place_search_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted
                            )
                        }

                        if (searchResults.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.place_search_results_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                searchResults.forEach { result ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(ZeroSmallShape)
                                            .background(colors.background)
                                            .hairlineBorder(shape = ZeroSmallShape)
                                            .clickable {
                                                val alias = customLabelDraft.trim()
                                                if (alias.isNotEmpty()) {
                                                    onAddPlace(
                                                        tag.id,
                                                        alias,
                                                        result.address,
                                                        result.latitude,
                                                        result.longitude,
                                                        selectedRadius
                                                    )
                                                    customLabelDraft = ""
                                                    haptics.confirm()
                                                } else {
                                                    editorTarget = PlaceEditorTarget.PendingNew(
                                                        defaultLabel = result.label,
                                                        address = result.address,
                                                        latitude = result.latitude,
                                                        longitude = result.longitude,
                                                        radiusMeters = selectedRadius
                                                    )
                                                    haptics.reorderTick()
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AddLocationAlt,
                                            contentDescription = null,
                                            tint = colors.accent,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = result.label,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = result.address,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.textMuted,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        result.distanceMeters?.let { dist ->
                                            Text(
                                                text = stringResource(R.string.place_distance_away, dist),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.accent
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editorTarget?.let { target ->
        PlaceNameEditorDialog(
            target = target,
            onDismiss = { editorTarget = null },
            onSave = { finalLabel, finalRadius ->
                when (target) {
                    is PlaceEditorTarget.Existing -> {
                        onUpdatePlace(target.place.copy(label = finalLabel, radiusMeters = finalRadius))
                    }
                    is PlaceEditorTarget.PendingNew -> {
                        onAddPlace(
                            tag.id,
                            finalLabel,
                            target.address,
                            target.latitude,
                            target.longitude,
                            finalRadius
                        )
                    }
                }
                editorTarget = null
                haptics.confirm()
            }
        )
    }
}

@Composable
private fun PlaceNameEditorDialog(
    target: PlaceEditorTarget,
    onDismiss: () -> Unit,
    onSave: (label: String, radiusMeters: Int) -> Unit
) {
    val colors = ZeroTheme.colors
    val initialLabel = when (target) {
        is PlaceEditorTarget.Existing -> target.place.label
        is PlaceEditorTarget.PendingNew -> target.defaultLabel
    }
    val initialRadius = when (target) {
        is PlaceEditorTarget.Existing -> target.place.radiusMeters
        is PlaceEditorTarget.PendingNew -> target.radiusMeters
    }
    val addressPreview = when (target) {
        is PlaceEditorTarget.Existing -> target.place.address
        is PlaceEditorTarget.PendingNew -> target.address
    }

    var textFieldState by remember(target) {
        mutableStateOf(TextFieldValue(text = initialLabel, selection = TextRange(initialLabel.length)))
    }
    var radiusMeters by remember(target) { mutableIntStateOf(initialRadius) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(target) {
        runCatching { focusRequester.requestFocus() }
    }
    val trimmed = textFieldState.text.trim()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ZeroCardShape)
                .background(colors.surfaceElevated)
                .hairlineBorder(shape = ZeroCardShape)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(
                    if (target is PlaceEditorTarget.Existing) {
                        R.string.place_edit_dialog_title
                    } else {
                        R.string.place_confirm_add_title
                    }
                ),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ZeroSmallShape)
                    .background(colors.surface)
                    .hairlineBorder(shape = ZeroSmallShape, color = colors.accent.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                if (textFieldState.text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.place_label_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = textFieldState,
                    onValueChange = { textFieldState = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (trimmed.isNotEmpty()) {
                                onSave(trimmed, radiusMeters)
                            }
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }

            if (addressPreview.isNotBlank()) {
                Text(
                    text = addressPreview,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            RADIUS_PRESETS.chunked(4).forEach { presets ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    presets.forEach { preset ->
                        val selected = radiusMeters == preset
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .clip(ZeroSmallShape)
                                .background(if (selected) colors.accentSubtle else colors.surface)
                                .hairlineBorder(
                                    shape = ZeroSmallShape,
                                    color = if (selected) colors.accent else colors.hairlineBorder
                                )
                                .clickable { radiusMeters = preset }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = radiusLabel(preset),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) colors.accent else colors.textSecondary
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(ZeroSmallShape)
                        .hairlineBorder(shape = ZeroSmallShape)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(ZeroSmallShape)
                        .background(if (trimmed.isNotEmpty()) colors.accent else colors.surface)
                        .hairlineBorder(
                            shape = ZeroSmallShape,
                            color = if (trimmed.isNotEmpty()) colors.accent else colors.hairlineBorder
                        )
                        .clickable(enabled = trimmed.isNotEmpty()) {
                            onSave(trimmed, radiusMeters)
                        }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_save),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (trimmed.isNotEmpty()) colors.onAccent else colors.textMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedPlaceRow(
    place: TagPlace,
    onToggleEnabled: () -> Unit,
    onCycleRadius: () -> Unit,
    onEdit: () -> Unit,
    onTestAlert: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = ZeroTheme.colors
    var menuOpen by remember(place.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ZeroSmallShape)
            .background(colors.surface)
            .hairlineBorder(
                shape = ZeroSmallShape,
                color = if (place.isEnabled) colors.accent.copy(alpha = 0.4f) else colors.hairlineBorder
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.LocationOn,
                contentDescription = null,
                tint = if (place.isEnabled) colors.accent else colors.textMuted,
                modifier = Modifier.size(17.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = place.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (place.isEnabled) colors.textPrimary else colors.textMuted
                )
                Text(
                    text = place.address.ifBlank {
                        stringResource(
                            R.string.place_coordinates_display,
                            place.latitude,
                            place.longitude
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(ZeroSmallShape)
                    .clickable(onClick = onCycleRadius)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.place_radius_value, radiusLabel(place.radiusMeters)),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary
                )
            }
            if (!place.isEnabled) {
                Text(
                    text = stringResource(R.string.place_disabled_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted
                )
            }
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = stringResource(R.string.place_actions),
                        tint = colors.textSecondary
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.place_edit_action)) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(if (place.isEnabled) R.string.place_disable else R.string.place_enable))
                        },
                        onClick = {
                            menuOpen = false
                            onToggleEnabled()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.place_test_alert)) },
                        onClick = {
                            menuOpen = false
                            onTestAlert()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete), color = colors.priorityUrgent) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun radiusLabel(meters: Int): String =
    if (meters >= 1_000 && meters % 1_000 == 0) {
        stringResource(R.string.place_radius_kilometers, meters / 1_000)
    } else {
        stringResource(R.string.place_radius_meters, meters)
    }
