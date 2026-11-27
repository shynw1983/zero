package com.zero.todo.core.geofence

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class PlaceSearchResult(
    val label: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Int? = null
)

object PlaceSearchEngine {

    private val COORD_REGEX = Regex("""(-?\d{1,3}(?:\.\d+)?)\s*,\s*(-?\d{1,3}(?:\.\d+)?)""")

    /**
     * Parses raw latitude/longitude pairs (`35.6595, 139.7004`), `geo:lat,lng`, or Google Maps URLs
     * containing `@lat,lng`, `q=lat,lng`, or `ll=lat,lng`.
     */
    fun parseCoordinateOrMapUrl(raw: String): Pair<Double, Double>? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val match = COORD_REGEX.find(text) ?: return null
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lng = match.groupValues[2].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return lat to lng
    }

    fun haversineDistanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Int {
        val earthRadiusM = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        val c = 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
        return (earthRadiusM * c).toInt()
    }

    fun parseNominatimSearchJson(
        rawJson: String,
        biasLat: Double? = null,
        biasLng: Double? = null
    ): List<PlaceSearchResult> {
        val array = runCatching { JSONArray(rawJson) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val lat = obj.optString("lat").toDoubleOrNull() ?: continue
                val lng = obj.optString("lon").toDoubleOrNull() ?: continue
                val displayName = obj.optString("display_name").trim()
                val shortName = obj.optString("name").trim()
                    .ifBlank { displayName.substringBefore(',').trim() }
                    .ifBlank { "%.5f, %.5f".format(Locale.US, lat, lng) }
                val dist = if (biasLat != null && biasLng != null) {
                    haversineDistanceMeters(biasLat, biasLng, lat, lng)
                } else {
                    null
                }
                add(
                    PlaceSearchResult(
                        label = shortName,
                        address = displayName.ifBlank { shortName },
                        latitude = lat,
                        longitude = lng,
                        distanceMeters = dist
                    )
                )
            }
        }
    }

    suspend fun searchPlaces(
        context: Context,
        query: String,
        biasLat: Double? = null,
        biasLng: Double? = null
    ): List<PlaceSearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        // 1. Direct coordinates or Maps URL
        parseCoordinateOrMapUrl(trimmed)?.let { (lat, lng) ->
            val (label, address) = reverseGeocode(context, lat, lng)
            val dist = if (biasLat != null && biasLng != null) {
                haversineDistanceMeters(biasLat, biasLng, lat, lng)
            } else {
                null
            }
            return@withContext listOf(
                PlaceSearchResult(
                    label = label,
                    address = address,
                    latitude = lat,
                    longitude = lng,
                    distanceMeters = dist
                )
            )
        }

        // 2. OpenStreetMap Nominatim (great for brand/store POIs like 7-Eleven, Lawson, etc.)
        val nominatim = runCatching {
            queryNominatim(trimmed, biasLat, biasLng)
        }.getOrDefault(emptyList())
        if (nominatim.isNotEmpty()) return@withContext nominatim

        // 3. Fallback to Android system Geocoder
        runCatching {
            querySystemGeocoder(context, trimmed, biasLat, biasLng)
        }.getOrDefault(emptyList())
    }

    suspend fun reverseGeocode(
        context: Context,
        lat: Double,
        lng: Double
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val coordLabel = "%.5f, %.5f".format(Locale.US, lat, lng)
        val systemResult = runCatching {
            @Suppress("DEPRECATION")
            val list = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1)
            val addr = list?.firstOrNull()
            if (addr != null) {
                val full = (0..addr.maxAddressLineIndex)
                    .mapNotNull { idx -> addr.getAddressLine(idx)?.trim()?.takeIf { it.isNotEmpty() } }
                    .joinToString(", ")
                val name = addr.featureName?.takeIf { it.isNotBlank() && !it.equals(full, ignoreCase = true) }
                    ?: addr.thoroughfare?.takeIf { it.isNotBlank() }
                    ?: addr.subLocality?.takeIf { it.isNotBlank() }
                    ?: addr.locality?.takeIf { it.isNotBlank() }
                    ?: full.substringBefore(',').trim().ifBlank { coordLabel }
                name to full.ifBlank { coordLabel }
            } else {
                null
            }
        }.getOrNull()
        if (systemResult != null) return@withContext systemResult

        val osmResult = runCatching {
            val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$lat&lon=$lng"
            val body = httpGet(url)
            val obj = JSONObject(body)
            val displayName = obj.optString("display_name").trim()
            val name = obj.optString("name").trim()
                .ifBlank { displayName.substringBefore(',').trim() }
                .ifBlank { coordLabel }
            name to displayName.ifBlank { coordLabel }
        }.getOrNull()

        osmResult ?: (coordLabel to coordLabel)
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(context: Context): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return@withContext null

        val gmsAvailable = runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }.getOrDefault(false)

        if (gmsAvailable) {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val current = withTimeoutOrNull(8_000L) {
                suspendCancellableCoroutine<Pair<Double, Double>?> { cont ->
                    val cts = CancellationTokenSource()
                    cont.invokeOnCancellation { cts.cancel() }
                    client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                        .addOnSuccessListener { loc ->
                            if (cont.isActive) {
                                cont.resume(loc?.let { it.latitude to it.longitude })
                            }
                        }
                        .addOnFailureListener {
                            if (cont.isActive) cont.resume(null)
                        }
                        .addOnCanceledListener {
                            if (cont.isActive) cont.resume(null)
                        }
                }
            }
            if (current != null) return@withContext current

            val lastFused = withTimeoutOrNull(3_000L) {
                suspendCancellableCoroutine<Pair<Double, Double>?> { cont ->
                    client.lastLocation
                        .addOnSuccessListener { loc ->
                            if (cont.isActive) cont.resume(loc?.let { it.latitude to it.longitude })
                        }
                        .addOnFailureListener {
                            if (cont.isActive) cont.resume(null)
                        }
                }
            }
            if (lastFused != null) return@withContext lastFused
        }

        val lm = context.getSystemService(LocationManager::class.java) ?: return@withContext null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        val bestLast = providers
            .mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
        bestLast?.let { it.latitude to it.longitude }
    }

    private fun queryNominatim(query: String, biasLat: Double?, biasLng: Double?): List<PlaceSearchResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val viewboxParam = if (biasLat != null && biasLng != null) {
            val delta = 0.08
            "&viewbox=${biasLng - delta},${biasLat + delta},${biasLng + delta},${biasLat - delta}"
        } else {
            ""
        }
        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&limit=8&q=$encoded$viewboxParam"
        val body = httpGet(url)
        val results = parseNominatimSearchJson(body, biasLat, biasLng)
        return if (biasLat != null && biasLng != null) {
            results.sortedBy { it.distanceMeters ?: Int.MAX_VALUE }
        } else {
            results
        }
    }

    private fun querySystemGeocoder(
        context: Context,
        query: String,
        biasLat: Double?,
        biasLng: Double?
    ): List<PlaceSearchResult> {
        @Suppress("DEPRECATION")
        val raw = if (biasLat != null && biasLng != null) {
            Geocoder(context, Locale.getDefault()).getFromLocationName(
                query,
                6,
                (biasLat - 0.15).coerceAtLeast(-90.0),
                (biasLng - 0.15).coerceAtLeast(-180.0),
                (biasLat + 0.15).coerceAtMost(90.0),
                (biasLng + 0.15).coerceAtMost(180.0)
            )
        } else {
            Geocoder(context, Locale.getDefault()).getFromLocationName(query, 6)
        }
        return raw.orEmpty().map { addr ->
            val full = (0..addr.maxAddressLineIndex)
                .mapNotNull { idx -> addr.getAddressLine(idx)?.trim()?.takeIf { it.isNotEmpty() } }
                .joinToString(", ")
            val label = addr.featureName?.takeIf { it.isNotBlank() } ?: query
            val dist = if (biasLat != null && biasLng != null) {
                haversineDistanceMeters(biasLat, biasLng, addr.latitude, addr.longitude)
            } else {
                null
            }
            PlaceSearchResult(
                label = label,
                address = full.ifBlank { label },
                latitude = addr.latitude,
                longitude = addr.longitude,
                distanceMeters = dist
            )
        }
    }

    private fun httpGet(urlString: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "ZERO-Todo-Android/2.12 (https://github.com/shynw1983/zero)")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
