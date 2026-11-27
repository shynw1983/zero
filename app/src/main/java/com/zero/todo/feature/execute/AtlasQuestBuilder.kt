package com.zero.todo.feature.execute

import com.zero.todo.core.geofence.PlaceSearchEngine
import com.zero.todo.data.repository.TagWithCount
import com.zero.todo.domain.model.TagItem
import com.zero.todo.domain.model.TagPlace
import com.zero.todo.domain.model.TagTreeBuilder
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.TaskPriority
import java.util.Locale
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class AtlasQuestMarker(
    val place: TagPlace,
    val tag: TagItem,
    val tagPath: String,
    val tasks: List<TaskItem>,
    val distanceMeters: Int?,
    val isNearestBranchForTag: Boolean,
    val isInsideGeofence: Boolean,
    val highestPriority: TaskPriority
)

data class AtlasQuestSnapshot(
    val markers: List<AtlasQuestMarker>,
    val routeWaypoints: List<Pair<Double, Double>>,
    val anywhereTasks: List<TaskItem>
)

object AtlasQuestBuilder {

    private const val EARTH_RADIUS_M = 6_371_000.0
    private const val MAX_ROUTE_STOPS = 6

    fun build(
        allActiveTasks: List<TaskItem>,
        tagsWithCounts: List<TagWithCount>,
        tagPathById: Map<String, String>,
        runwayQueue: List<TaskItem>,
        userLat: Double?,
        userLng: Double?
    ): AtlasQuestSnapshot {
        val allTags = tagsWithCounts.map { it.tag }.filterNot { it.isDeleted }
        val incompleteTasks = allActiveTasks.filter { !it.isDeleted && !it.isCompleted }

        val emittedMarkers = mutableListOf<AtlasQuestMarker>()
        val coveredTaskIds = HashSet<String>()

        for (tag in allTags) {
            if (!tag.isPlaceTag) continue
            val enabledPlaces = tag.places.filter { it.isEnabled }
            if (enabledPlaces.isEmpty()) continue

            val treeIds = TagTreeBuilder.collectDescendantIds(tag.id, allTags) + tag.id
            val tagTasks = incompleteTasks
                .filter { task -> task.tags.any { it.id in treeIds } }
                .sortedWith(
                    compareBy<TaskItem>(
                        { it.priority.value },
                        { it.sortOrder },
                        { it.createdAt }
                    )
                )
            if (tagTasks.isEmpty()) continue

            tagTasks.forEach { coveredTaskIds += it.id }
            val topPriority = tagTasks.minByOrNull { it.priority.value }?.priority ?: TaskPriority.NONE
            val pathLabel = tagPathById[tag.id] ?: tag.name

            val distances = enabledPlaces.map { place ->
                if (userLat != null && userLng != null) {
                    PlaceSearchEngine.haversineDistanceMeters(
                        userLat,
                        userLng,
                        place.latitude,
                        place.longitude
                    )
                } else {
                    null
                }
            }
            val minDistIndex = if (userLat != null && userLng != null) {
                distances.indices.minByOrNull { distances[it] ?: Int.MAX_VALUE } ?: 0
            } else {
                0
            }

            enabledPlaces.forEachIndexed { idx, place ->
                val dist = distances[idx]
                emittedMarkers += AtlasQuestMarker(
                    place = place,
                    tag = tag,
                    tagPath = pathLabel,
                    tasks = tagTasks,
                    distanceMeters = dist,
                    isNearestBranchForTag = idx == minDistIndex,
                    isInsideGeofence = dist != null && dist <= place.radiusMeters,
                    highestPriority = topPriority
                )
            }
        }

        val sortedMarkers = emittedMarkers.sortedWith(
            compareByDescending<AtlasQuestMarker> { it.isInsideGeofence }
                .thenBy { it.distanceMeters ?: Int.MAX_VALUE }
                .thenBy { it.highestPriority.value }
                .thenBy { !it.isNearestBranchForTag }
        )

        val routeWaypoints = buildNearestNeighborRoute(
            markers = sortedMarkers,
            userLat = userLat,
            userLng = userLng
        )

        val anywhereTasks = runwayQueue.filter { it.id !in coveredTaskIds }

        return AtlasQuestSnapshot(
            markers = sortedMarkers,
            routeWaypoints = routeWaypoints,
            anywhereTasks = anywhereTasks
        )
    }

    private fun buildNearestNeighborRoute(
        markers: List<AtlasQuestMarker>,
        userLat: Double?,
        userLng: Double?
    ): List<Pair<Double, Double>> {
        if (userLat == null || userLng == null || markers.isEmpty()) return emptyList()
        // Pick one representative branch per tag (nearest branch) for clean multi-stop routing
        val candidates = markers
            .groupBy { it.tag.id }
            .values
            .mapNotNull { branches ->
                branches.firstOrNull { it.isNearestBranchForTag } ?: branches.firstOrNull()
            }
            .toMutableList()

        if (candidates.isEmpty()) return emptyList()
        val route = mutableListOf(userLat to userLng)
        var curLat: Double = userLat
        var curLng: Double = userLng

        while (candidates.isNotEmpty() && route.size <= MAX_ROUTE_STOPS) {
            val nextIdx = candidates.indices.minByOrNull { i ->
                PlaceSearchEngine.haversineDistanceMeters(
                    curLat,
                    curLng,
                    candidates[i].place.latitude,
                    candidates[i].place.longitude
                )
            } ?: break
            val next = candidates.removeAt(nextIdx)
            curLat = next.place.latitude
            curLng = next.place.longitude
            route += (curLat to curLng)
        }
        return route
    }

    /**
     * Computes polygon vertices (starting & anchored at user's position) representing a tactical
     * directional FOV cone rotated by [bearingDegrees] clockwise from true north.
     */
    fun computeHeadingCone(
        lat: Double,
        lng: Double,
        bearingDegrees: Float,
        rangeMeters: Double = 95.0,
        halfAngleDeg: Double = 28.0
    ): List<Pair<Double, Double>> {
        val vertices = mutableListOf(lat to lng)
        val steps = 5
        val startAngle = bearingDegrees.toDouble() - halfAngleDeg
        val sweep = halfAngleDeg * 2.0
        for (i in 0..steps) {
            val az = startAngle + (sweep * i / steps)
            vertices += offsetLatLng(lat, lng, rangeMeters, az)
        }
        return vertices
    }

    fun formatDistance(meters: Int): String =
        if (meters < 1000) "${meters}m" else "%.1fkm".format(Locale.US, meters / 1000.0)

    fun computeBearingDegrees(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lng2 - lng1)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        val deg = Math.toDegrees(atan2(y, x)).toFloat()
        return ((deg % 360f) + 360f) % 360f
    }

    fun cardinalDirection(bearingDegrees: Float): String {
        val norm = ((bearingDegrees % 360f) + 360f) % 360f
        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val idx = (((norm + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)
        return dirs[idx]
    }

    private fun offsetLatLng(
        latDeg: Double,
        lngDeg: Double,
        distanceMeters: Double,
        bearingDeg: Double
    ): Pair<Double, Double> {
        val angularDist = distanceMeters / EARTH_RADIUS_M
        val brng = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(latDeg)
        val lng1 = Math.toRadians(lngDeg)

        val lat2 = asin(
            sin(lat1) * cos(angularDist) +
                cos(lat1) * sin(angularDist) * cos(brng)
        )
        val lng2 = lng1 + atan2(
            sin(brng) * sin(angularDist) * cos(lat1),
            cos(angularDist) - sin(lat1) * sin(lat2)
        )
        return Math.toDegrees(lat2) to Math.toDegrees(lng2)
    }
}
