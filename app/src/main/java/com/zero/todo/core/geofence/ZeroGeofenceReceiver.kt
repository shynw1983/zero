package com.zero.todo.core.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.zero.todo.core.notification.ZeroNotificationHelper
import com.zero.todo.domain.model.PlaceTriggerMode
import com.zero.todo.widget.ZeroWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ZeroGeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val triggeredPairs: List<Pair<String, Boolean>> = when (intent.action) {
            ACTION_PROXIMITY_ALERT -> {
                val placeId = intent.getStringExtra(EXTRA_PLACE_ID) ?: return
                val entering = intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, true)
                listOf(placeId to entering)
            }
            ACTION_GEOFENCE_EVENT -> {
                val event = GeofencingEvent.fromIntent(intent) ?: return
                if (event.hasError()) return
                val isEnter = when (event.geofenceTransition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER -> true
                    Geofence.GEOFENCE_TRANSITION_EXIT -> false
                    else -> return
                }
                event.triggeringGeofences.orEmpty()
                    .mapNotNull { g -> GeofenceEvaluator.parsePlaceId(g.requestId)?.let { it to isEnter } }
            }
            else -> {
                val event = GeofencingEvent.fromIntent(intent) ?: return
                if (event.hasError()) return
                val isEnter = when (event.geofenceTransition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER -> true
                    Geofence.GEOFENCE_TRANSITION_EXIT -> false
                    else -> return
                }
                event.triggeringGeofences.orEmpty()
                    .mapNotNull { g -> GeofenceEvaluator.parsePlaceId(g.requestId)?.let { it to isEnter } }
            }
        }
        if (triggeredPairs.isEmpty()) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val ep = ZeroWidgetUpdater.entryPoint(context)
                val tagDao = ep.tagDao()
                val prefs = context.getSharedPreferences(COOLDOWN_PREFS, Context.MODE_PRIVATE)
                val now = System.currentTimeMillis()
                val notifiedTagEvents = HashSet<String>()

                for ((placeId, isEnter) in triggeredPairs) {
                    val place = tagDao.getTagPlaceById(placeId) ?: continue
                    if (!place.isEnabled) continue
                    val tag = tagDao.getTagById(place.tagId) ?: continue
                    if (tag.isDeleted || !tag.isPlaceTag) continue

                    val mode = PlaceTriggerMode.fromStorage(tag.placeTriggerMode)
                    if (!GeofenceEvaluator.shouldTrigger(mode, isEnter)) continue

                    // Avoid duplicate notifications if two branches of the same tag are nearby
                    val tagEventKey = "${tag.id}:$isEnter"
                    if (!notifiedTagEvents.add(tagEventKey)) continue

                    val cooldownKey = "last_${tag.id}_$isEnter"
                    val lastFired = prefs.getLong(cooldownKey, 0L)
                    if (GeofenceEvaluator.isWithinCooldown(lastFired, now)) continue

                    val tasks = tagDao.getActiveIncompleteTasksForTagTree(tag.id)
                    if (tasks.isEmpty()) continue

                    prefs.edit().putLong(cooldownKey, now).apply()
                    ZeroNotificationHelper.showPlaceReminder(
                        context = context,
                        tagId = tag.id,
                        tagName = tag.name,
                        placeLabel = place.label,
                        isEnter = isEnter,
                        taskTitles = tasks.map { it.title }
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_GEOFENCE_EVENT = "com.zero.todo.action.GEOFENCE_EVENT"
        const val ACTION_PROXIMITY_ALERT = "com.zero.todo.action.PROXIMITY_ALERT"
        const val EXTRA_PLACE_ID = "extra_place_id"
        private const val COOLDOWN_PREFS = "zero_geofence_cooldowns"
    }
}
