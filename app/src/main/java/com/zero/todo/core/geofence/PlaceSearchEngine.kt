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
import java.text.Normalizer
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
    private val ADDRESS_CODE_TOKEN_REGEX = Regex(
        """(?i)(?:〒|\bno\.?\s*\d+|\b[bB]?\d+\s*[fF]\b|丁目|番地|番|号室|号|棟|階)"""
    )
    private val NON_MEANINGFUL_LABEL_CHARS_REGEX = Regex("""[\s\d\-−ー‐–—‐,./#()（）:：]+""")

    /**
     * Returns true when a raw geocoder label is blank or consists solely of digits,
     * punctuation, postal markers, or Japanese/Chinese/English house/floor counters
     * (e.g. "１８", "1-18", "１丁目１−１８", "3番地", "2F", "〒815-0082"), while keeping
     * real place/brand names that happen to contain digits (e.g. "7-Eleven",
     * "ローソンストア100", "セブン-イレブン 福岡大楠１丁目店", "大楠１丁目").
     */
    fun isHouseNumberOrAddressCode(raw: String?): Boolean {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return true
        val normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFKC)
        val withoutTokens = ADDRESS_CODE_TOKEN_REGEX.replace(normalized, "")
        val remaining = NON_MEANINGFUL_LABEL_CHARS_REGEX.replace(withoutTokens, "")
        return remaining.isEmpty()
    }

    internal fun resolveGeocoderLabel(
        featureName: String?,
        subThoroughfare: String?,
        thoroughfare: String?,
        subLocality: String?,
        locality: String?,
        postalCode: String?,
        fullAddress: String,
        fallbackQuery: String? = null,
        defaultFallback: String = ""
    ): String {
        val cleanedFeature = featureName?.trim()?.takeIf { candidate ->
            candidate.isNotEmpty() &&
                !candidate.equals(fullAddress, ignoreCase = true) &&
                !candidate.equals(subThoroughfare?.trim(), ignoreCase = true) &&
                !candidate.equals(postalCode?.trim(), ignoreCase = true) &&
                !isHouseNumberOrAddressCode(candidate)
        }

        val cleanedQuery = fallbackQuery?.trim()?.takeIf { q ->
            q.isNotEmpty() &&
                parseCoordinateOrMapUrl(q) == null &&
                !isHouseNumberOrAddressCode(q)
        }

        // Forward search: keep featureName only if it is a genuine distinct POI name
        // (not merely a generic street/ward/city component); otherwise preserve the user's searched merchant name.
        if (cleanedQuery != null) {
            val isGenericAddressPart = cleanedFeature != null && (
                cleanedFeature.equals(thoroughfare?.trim(), ignoreCase = true) ||
                    cleanedFeature.equals(subLocality?.trim(), ignoreCase = true) ||
                    cleanedFeature.equals(locality?.trim(), ignoreCase = true)
                )
            return if (cleanedFeature != null && !isGenericAddressPart) {
                cleanedFeature
            } else {
                cleanedQuery
            }
        }

        // Reverse geocode (current GPS / coordinate paste): pick best human-readable place label.
        if (cleanedFeature != null) return cleanedFeature

        val sub = subLocality?.trim()?.takeIf { it.isNotEmpty() && !isHouseNumberOrAddressCode(it) }
        val road = thoroughfare?.trim()?.takeIf { it.isNotEmpty() && !isHouseNumberOrAddressCode(it) }
        if (sub != null && road != null) {
            return when {
                sub.contains(road, ignoreCase = true) -> sub
                road.contains(sub, ignoreCase = true) -> road
                else -> "$sub $road"
            }
        }
        if (road != null) return road
        if (sub != null) return sub

        val city = locality?.trim()?.takeIf { it.isNotEmpty() && !isHouseNumberOrAddressCode(it) }
        if (city != null) return city

        val firstSegment = fullAddress.substringBefore(',').trim()
        if (firstSegment.isNotEmpty() && !isHouseNumberOrAddressCode(firstSegment)) {
            return firstSegment
        }
        return fullAddress.trim().ifBlank { defaultFallback }
    }

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
        biasLng: Double? = null,
        fallbackQuery: String? = null
    ): List<PlaceSearchResult> {
        val array = runCatching { JSONArray(rawJson) }.getOrNull() ?: return emptyList()
        val cleanedQuery = fallbackQuery?.trim()?.takeIf { q ->
            q.isNotEmpty() &&
                parseCoordinateOrMapUrl(q) == null &&
                !isHouseNumberOrAddressCode(q)
        }
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val lat = obj.optString("lat").toDoubleOrNull() ?: continue
                val lng = obj.optString("lon").toDoubleOrNull() ?: continue
                val displayName = obj.optString("display_name").trim()
                val rawName = obj.optString("name").trim().takeUnless { isHouseNumberOrAddressCode(it) }
                val firstReadableSegment = displayName.split(',')
                    .map { it.trim() }
                    .firstOrNull { it.isNotEmpty() && !isHouseNumberOrAddressCode(it) }
                val shortName = rawName
                    ?: firstReadableSegment
                    ?: cleanedQuery
                    ?: displayName.substringBefore(',').trim().ifBlank { "%.5f, %.5f".format(Locale.US, lat, lng) }
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

        // 2. Local bounded Nominatim + system Geocoder combined & sorted strictly nearest-first
        val combined = mutableListOf<PlaceSearchResult>()
        if (biasLat != null && biasLng != null) {
            combined += runCatching {
                queryNominatim(trimmed, biasLat, biasLng, deltaDeg = 0.035, bounded = true, limit = 25)
            }.getOrDefault(emptyList())
            combined += runCatching {
                querySystemGeocoder(context, trimmed, biasLat, biasLng, deltaDeg = 0.035, maxResults = 10)
            }.getOrDefault(emptyList())

            if (combined.size < 5) {
                combined += runCatching {
                    queryNominatim(trimmed, biasLat, biasLng, deltaDeg = 0.18, bounded = true, limit = 15)
                }.getOrDefault(emptyList())
            }
            if (combined.isEmpty()) {
                combined += runCatching {
                    queryNominatim(trimmed, biasLat, biasLng, deltaDeg = 0.45, bounded = false, limit = 15)
                }.getOrDefault(emptyList())
            }
            if (combined.isEmpty()) {
                combined += runCatching {
                    querySystemGeocoder(context, trimmed, biasLat, biasLng, deltaDeg = 0.35, maxResults = 10)
                }.getOrDefault(emptyList())
            }
            return@withContext mergeAndSortByDistance(combined, biasLat, biasLng, maxResults = 12)
        }

        // 3. No GPS fix available: unbounded Nominatim then Android system Geocoder fallback
        val nominatim = runCatching {
            queryNominatim(trimmed, null, null, deltaDeg = 0.08, bounded = false, limit = 12)
        }.getOrDefault(emptyList())
        if (nominatim.isNotEmpty()) return@withContext nominatim

        runCatching {
            querySystemGeocoder(context, trimmed, null, null, deltaDeg = 0.15, maxResults = 8)
        }.getOrDefault(emptyList())
    }

    fun mergeAndSortByDistance(
        results: List<PlaceSearchResult>,
        biasLat: Double?,
        biasLng: Double?,
        dedupeMeters: Int = 35,
        maxResults: Int = 12
    ): List<PlaceSearchResult> {
        if (results.isEmpty()) return emptyList()
        val withDistances = results.map { item ->
            val computed = if (biasLat != null && biasLng != null) {
                haversineDistanceMeters(biasLat, biasLng, item.latitude, item.longitude)
            } else {
                item.distanceMeters
            }
            item.copy(distanceMeters = computed)
        }.sortedWith(
            compareBy<PlaceSearchResult>(
                { it.distanceMeters ?: Int.MAX_VALUE },
                { -it.address.length }
            )
        )

        val deduped = mutableListOf<PlaceSearchResult>()
        for (candidate in withDistances) {
            val isDuplicate = deduped.any { existing ->
                haversineDistanceMeters(
                    existing.latitude,
                    existing.longitude,
                    candidate.latitude,
                    candidate.longitude
                ) <= dedupeMeters
            }
            if (!isDuplicate) {
                deduped += candidate
                if (deduped.size >= maxResults) break
            }
        }
        return deduped
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
                val name = resolveGeocoderLabel(
                    featureName = addr.featureName,
                    subThoroughfare = addr.subThoroughfare,
                    thoroughfare = addr.thoroughfare,
                    subLocality = addr.subLocality,
                    locality = addr.locality,
                    postalCode = addr.postalCode,
                    fullAddress = full,
                    fallbackQuery = null,
                    defaultFallback = coordLabel
                )
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
            val rawName = obj.optString("name").trim().takeUnless { isHouseNumberOrAddressCode(it) }
            val firstReadableSegment = displayName.split(',')
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() && !isHouseNumberOrAddressCode(it) }
            val name = rawName
                ?: firstReadableSegment
                ?: displayName.substringBefore(',').trim().ifBlank { coordLabel }
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

    private fun queryNominatim(
        query: String,
        biasLat: Double?,
        biasLng: Double?,
        deltaDeg: Double = 0.08,
        bounded: Boolean = false,
        limit: Int = 15
    ): List<PlaceSearchResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val viewboxParam = if (biasLat != null && biasLng != null) {
            val left = (biasLng - deltaDeg).coerceAtLeast(-180.0)
            val top = (biasLat + deltaDeg).coerceAtMost(90.0)
            val right = (biasLng + deltaDeg).coerceAtMost(180.0)
            val bottom = (biasLat - deltaDeg).coerceAtLeast(-90.0)
            val boundFlag = if (bounded) "&bounded=1" else ""
            "&viewbox=$left,$top,$right,$bottom$boundFlag"
        } else {
            ""
        }
        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&limit=$limit&q=$encoded$viewboxParam"
        val body = httpGet(url)
        val results = parseNominatimSearchJson(body, biasLat, biasLng, fallbackQuery = query)
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
        biasLng: Double?,
        deltaDeg: Double = 0.15,
        maxResults: Int = 8
    ): List<PlaceSearchResult> {
        @Suppress("DEPRECATION")
        val raw = if (biasLat != null && biasLng != null) {
            Geocoder(context, Locale.getDefault()).getFromLocationName(
                query,
                maxResults,
                (biasLat - deltaDeg).coerceAtLeast(-90.0),
                (biasLng - deltaDeg).coerceAtLeast(-180.0),
                (biasLat + deltaDeg).coerceAtMost(90.0),
                (biasLng + deltaDeg).coerceAtMost(180.0)
            )
        } else {
            Geocoder(context, Locale.getDefault()).getFromLocationName(query, maxResults)
        }
        return raw.orEmpty().map { addr ->
            val full = (0..addr.maxAddressLineIndex)
                .mapNotNull { idx -> addr.getAddressLine(idx)?.trim()?.takeIf { it.isNotEmpty() } }
                .joinToString(", ")
            val label = resolveGeocoderLabel(
                featureName = addr.featureName,
                subThoroughfare = addr.subThoroughfare,
                thoroughfare = addr.thoroughfare,
                subLocality = addr.subLocality,
                locality = addr.locality,
                postalCode = addr.postalCode,
                fullAddress = full,
                fallbackQuery = query,
                defaultFallback = query
            )
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
