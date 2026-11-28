package com.zero.todo.feature.execute

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AtlasMapboxLanguageTest {
    @Test
    fun `application locales use the Mapbox language code`() {
        val languages = mapOf("ja-JP" to "ja", "ko-KR" to "ko", "en-US" to "en", "zh" to "zh-Hans")
        languages.forEach { (tag, expected) ->
            assertEquals(expected, Locale.forLanguageTag(tag).toMapboxLabelLanguage())
        }
    }

    @Test
    fun `Chinese requests a supported script even when the locale only specifies a region`() {
        val languages = mapOf(
            "zh-CN" to "zh-Hans",
            "zh-TW" to "zh-Hant",
            "zh-HK" to "zh-Hant",
            "zh-Hant" to "zh-Hant",
            "zh-Hans-TW" to "zh-Hans"
        )
        languages.forEach { (tag, expected) ->
            assertEquals(expected, Locale.forLanguageTag(tag).toMapboxLabelLanguage())
        }
    }
}
