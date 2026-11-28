package com.zero.todo.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zero.todo.domain.model.AtlasMapProvider
import com.zero.todo.domain.model.AtlasMapSettings
import com.zero.todo.domain.model.AtlasMapStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AtlasMapPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `existing preferences open Google Maps`() {
        val settings = SharedPrefsUserPreferencesRepository(context).preferences.value.atlasMapSettings

        assertEquals(AtlasMapProvider.GOOGLE_MAPS, settings.provider)
        assertFalse(settings.useMapbox)
    }

    @Test
    fun `Mapbox style and public access token survive reload and switching back to Google`() {
        val mapbox = AtlasMapSettings(AtlasMapProvider.MAPBOX, AtlasMapStyle.SATELLITE, "pk.test.sample")
        SharedPrefsUserPreferencesRepository(context).setAtlasMapSettings(mapbox)

        val restored = SharedPrefsUserPreferencesRepository(context)
        assertEquals(mapbox, restored.preferences.value.atlasMapSettings)
        assertTrue(restored.preferences.value.atlasMapSettings.useMapbox)

        val google = mapbox.copy(provider = AtlasMapProvider.GOOGLE_MAPS)
        restored.setAtlasMapSettings(google)
        assertEquals(google, SharedPrefsUserPreferencesRepository(context).preferences.value.atlasMapSettings)
        assertFalse(google.useMapbox)
    }

    @Test
    fun `Mapbox requires a public access token`() {
        val settings = AtlasMapSettings(provider = AtlasMapProvider.MAPBOX)

        assertFalse(settings.useMapbox)
        assertFalse(settings.copy(mapboxToken = "sk.test.sample").useMapbox)
        assertTrue(settings.copy(mapboxToken = "pk.test.sample").useMapbox)
    }
}
