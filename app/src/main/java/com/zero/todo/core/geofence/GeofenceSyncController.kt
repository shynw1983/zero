package com.zero.todo.core.geofence

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.zero.todo.core.notification.ZeroNotificationHelper
import com.zero.todo.data.local.dao.TagDao
import com.zero.todo.domain.model.PlaceTriggerMode
import com.zero.todo.domain.model.TagItem
import com.zero.todo.domain.model.TagPlace
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

object GeofenceEvaluator {
    const val REQUEST_ID_PREFIX = "zero_place_"
    const val DEFAULT_COOLDOWN_MS = 30L * 60L * 1000L

    fun requestIdFor(placeId: String): String = "$REQUEST_ID_PREFIX$placeId"

    fun parsePlaceId(requestId: String?): String? {
        val clean = requestId?.trim() ?: return null
        return if (clean.startsWith(REQUEST_ID_PREFIX) && clean.length > REQUEST_ID_PREFIX.length) {
            clean.removePrefix(REQUEST_ID_PREFIX)
        } else {
            null
        }
    }

    fun effectiveTriggerMode(
        tagDefaultMode: PlaceTriggerMode,
        taskOverride: PlaceTriggerMode?
    ): PlaceTriggerMode = taskOverride ?: tagDefaultMode

    fun shouldTrigger(triggerMode: PlaceTriggerMode, isEnter: Boolean): Boolean = when (triggerMode) {
        PlaceTriggerMode.ENTER -> isEnter
        PlaceTriggerMode.EXIT -> !isEnter
        PlaceTriggerMode.BOTH -> true
    }

    fun shouldTriggerTask(
        tagDefaultMode: PlaceTriggerMode,
        taskOverride: PlaceTriggerMode?,
        isEnter: Boolean
    ): Boolean = shouldTrigger(effectiveTriggerMode(tagDefaultMode, taskOverride), isEnter)

    fun aggregateModeForGeofence(
        tagDefaultMode: PlaceTriggerMode,
        taskOverrides: List<PlaceTriggerMode?>
    ): PlaceTriggerMode {
        var needsEnter = shouldTrigger(tagDefaultMode, isEnter = true)
        var needsExit = shouldTrigger(tagDefaultMode, isEnter = false)
        for (override in taskOverrides) {
            if (override == null) continue
            if (shouldTrigger(override, isEnter = true)) needsEnter = true
            if (shouldTrigger(override, isEnter = false)) needsExit = true
            if (needsEnter && needsExit) break
        }
        return when {
            needsEnter && needsExit -> PlaceTriggerMode.BOTH
            needsExit -> PlaceTriggerMode.EXIT
            else -> PlaceTriggerMode.ENTER
        }
    }

    fun isWithinCooldown(
        lastFiredAtMillis: Long,
        nowMillis: Long,
        cooldownMillis: Long = DEFAULT_COOLDOWN_MS
    ): Boolean {
        if (lastFiredAtMillis <= 0L) return false
        return (nowMillis - lastFiredAtMillis) in 0 until cooldownMillis
    }
}

enum class GeofencePermissionState {
    GRANTED_BACKGROUND,
    FOREGROUND_ONLY,
    DENIED
}

interface GeofenceSyncController {
    fun permissionState(): GeofencePermissionState
    suspend fun syncAllGeofences(): Int
    fun triggerTestNotification(
        tag: TagItem,
        place: TagPlace,
        isEnter: Boolean,
        taskTitles: List<String>
    )
}

object NoOpGeofenceSyncController : GeofenceSyncController {
    override fun permissionState(): GeofencePermissionState = GeofencePermissionState.DENIED
    override suspend fun syncAllGeofences(): Int = 0
    override fun triggerTestNotification(
        tag: TagItem,
        place: TagPlace,
        isEnter: Boolean,
        taskTitles: List<String>
    ) = Unit
}

@Singleton
class AndroidGeofenceSyncController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tagDao: TagDao
) : GeofenceSyncController {

    private val registrationLock = Mutex()

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun permissionState(): GeofencePermissionState {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine) return GeofencePermissionState.DENIED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasBg = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasBg) return GeofencePermissionState.FOREGROUND_ONLY
        }
        return GeofencePermissionState.GRANTED_BACKGROUND
    }

    override suspend fun syncAllGeofences(): Int = registrationLock.withLock {
        refreshGeofenceRegistrations()
    }

    @SuppressLint("MissingPermission")
    private suspend fun refreshGeofenceRegistrations(): Int = withContext(Dispatchers.IO) {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine) return@withContext 0

        val activePlaces = tagDao.getActiveGeofencePlaces()
        val tagsById = tagDao.getActiveTagsSnapshot().associateBy { it.id }

        val gmsAvailable = runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }.getOrDefault(false)

        if (gmsAvailable) {
            val client = LocationServices.getGeofencingClient(context)
            val geofencePending = createGmsGeofencePendingIntent()
            runCatching {
                suspendCancellableCoroutine<Unit> { cont ->
                    client.removeGeofences(geofencePending)
                        .addOnCompleteListener { if (cont.isActive) cont.resume(Unit) }
                }
            }
            clearProximityAlertsFallback()

            if (activePlaces.isEmpty()) return@withContext 0

            val bounded = activePlaces.take(MAX_GEOFENCES)
            val overridesByTagId = bounded
                .map { it.tagId }
                .distinct()
                .associateWith { tagId ->
                    tagDao.getActiveIncompleteTasksForTagTree(tagId).map {
                        PlaceTriggerMode.fromStorageOrNull(it.placeTriggerOverride)
                    }
                }
            val geofences = bounded.map { place ->
                val tagDefault = PlaceTriggerMode.fromStorage(tagsById[place.tagId]?.placeTriggerMode)
                val mode = GeofenceEvaluator.aggregateModeForGeofence(
                    tagDefaultMode = tagDefault,
                    taskOverrides = overridesByTagId[place.tagId].orEmpty()
                )
                val transitionMask = when (mode) {
                    PlaceTriggerMode.ENTER -> Geofence.GEOFENCE_TRANSITION_ENTER
                    PlaceTriggerMode.EXIT -> Geofence.GEOFENCE_TRANSITION_EXIT
                    PlaceTriggerMode.BOTH -> Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
                }
                Geofence.Builder()
                    .setRequestId(GeofenceEvaluator.requestIdFor(place.id))
                    .setCircularRegion(
                        place.latitude,
                        place.longitude,
                        place.radiusMeters.coerceIn(TagPlace.MIN_RADIUS_METERS, TagPlace.MAX_RADIUS_METERS).toFloat()
                    )
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(transitionMask)
                    .build()
            }

            val request = GeofencingRequest.Builder()
                .setInitialTrigger(0)
                .addGeofences(geofences)
                .build()

            val registered = runCatching {
                suspendCancellableCoroutine<Boolean> { cont ->
                    client.addGeofences(request, geofencePending)
                        .addOnSuccessListener { if (cont.isActive) cont.resume(true) }
                        .addOnFailureListener { if (cont.isActive) cont.resume(false) }
                }
            }.getOrDefault(false)

            if (registered) return@withContext bounded.size
        }

        // Fallback via Android LocationManager proximity alerts (works on non-GMS Chinese ROMs)
        val lm = context.getSystemService(LocationManager::class.java) ?: return@withContext 0
        clearProximityAlertsFallback()
        val bounded = activePlaces.take(MAX_GEOFENCES)
        val newIds = HashSet<String>(bounded.size)
        bounded.forEach { place ->
            val pi = createProximityPendingIntent(place.id)
            runCatching {
                lm.addProximityAlert(
                    place.latitude,
                    place.longitude,
                    place.radiusMeters.coerceIn(TagPlace.MIN_RADIUS_METERS, TagPlace.MAX_RADIUS_METERS).toFloat(),
                    -1L,
                    pi
                )
                newIds += place.id
            }
        }
        prefs.edit().putStringSet(KEY_PROXIMITY_PLACE_IDS, newIds).apply()
        newIds.size
    }

    override fun triggerTestNotification(
        tag: TagItem,
        place: TagPlace,
        isEnter: Boolean,
        taskTitles: List<String>
    ) {
        ZeroNotificationHelper.showPlaceReminder(
            context = context,
            tagId = tag.id,
            tagName = tag.name,
            placeLabel = place.label,
            isEnter = isEnter,
            taskTitles = taskTitles
        )
    }

    @SuppressLint("MissingPermission")
    private fun clearProximityAlertsFallback() {
        val lm = context.getSystemService(LocationManager::class.java) ?: return
        val previous = prefs.getStringSet(KEY_PROXIMITY_PLACE_IDS, emptySet()).orEmpty()
        previous.forEach { placeId ->
            val pi = createProximityPendingIntent(placeId)
            runCatching { lm.removeProximityAlert(pi) }
            pi.cancel()
        }
        if (previous.isNotEmpty()) {
            prefs.edit().remove(KEY_PROXIMITY_PLACE_IDS).apply()
        }
    }

    private fun createGmsGeofencePendingIntent(): PendingIntent {
        val intent = Intent(context, ZeroGeofenceReceiver::class.java).apply {
            action = ZeroGeofenceReceiver.ACTION_GEOFENCE_EVENT
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, GMS_REQUEST_CODE, intent, flags)
    }

    private fun createProximityPendingIntent(placeId: String): PendingIntent {
        val intent = Intent(context, ZeroGeofenceReceiver::class.java).apply {
            action = ZeroGeofenceReceiver.ACTION_PROXIMITY_ALERT
            data = Uri.parse("zero://geofence-place/$placeId")
            putExtra(ZeroGeofenceReceiver.EXTRA_PLACE_ID, placeId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val reqCode = 300_000 + (placeId.hashCode() and 0x0FFFFFFF)
        return PendingIntent.getBroadcast(context, reqCode, intent, flags)
    }

    companion object {
        private const val MAX_GEOFENCES = 100
        private const val GMS_REQUEST_CODE = 290_001
        private const val PREFS_NAME = "zero_geofence_prefs"
        private const val KEY_PROXIMITY_PLACE_IDS = "registered_proximity_place_ids"
    }
}
