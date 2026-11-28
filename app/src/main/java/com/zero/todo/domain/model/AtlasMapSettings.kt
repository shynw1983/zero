package com.zero.todo.domain.model

enum class AtlasMapProvider { GOOGLE_MAPS, MAPBOX }

enum class AtlasMapStyle { STANDARD, SATELLITE }

data class AtlasMapSettings(
    val provider: AtlasMapProvider = AtlasMapProvider.GOOGLE_MAPS,
    val mapboxStyle: AtlasMapStyle = AtlasMapStyle.STANDARD,
    val mapboxToken: String = ""
) {
    val useMapbox: Boolean
        get() = provider == AtlasMapProvider.MAPBOX && acceptsMapboxToken(mapboxToken)

    companion object {
        private val publicToken = Regex("pk\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")

        fun acceptsMapboxToken(raw: String): Boolean = publicToken.matches(raw.trim())
    }
}
