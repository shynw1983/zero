package com.zero.todo.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Automated localization completeness guard for ZERO's 4 mandatory languages:
 * English (values), Simplified Chinese (values-zh), Japanese (values-ja), Korean (values-ko).
 *
 * Any future feature that adds a string resource MUST translate it across all 4 locales
 * with identical format placeholders, or this test will immediately fail the build.
 */
class LocalizationCompletenessTest {

    private val placeholderRegex = Regex("""%(\d+\$)?[sdf]""")

    private fun resolveResDir(): File {
        val candidates = listOf(
            File("src/main/res"),
            File("app/src/main/res")
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate app/src/main/res from ${File(".").absolutePath}")
    }

    private fun parseStringsXml(file: File): Map<String, String> {
        assertTrue("Missing localization file: ${file.path}", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val map = linkedMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as Element
            val name = element.getAttribute("name")
            val value = element.textContent
            assertFalse("Duplicate string key '$name' in ${file.path}", name in map)
            assertTrue("Blank translation for key '$name' in ${file.path}", value.isNotBlank())
            map[name] = value
        }
        return map
    }

    @Test
    fun `all four locales (en, zh, ja, ko) have identical keys and matching format placeholders`() {
        val resDir = resolveResDir()
        val base = parseStringsXml(File(resDir, "values/strings.xml"))
        val localeDirs = listOf("values-zh", "values-ja", "values-ko")

        assertTrue("Base strings.xml must not be empty", base.isNotEmpty())

        for (dir in localeDirs) {
            val translated = parseStringsXml(File(resDir, "$dir/strings.xml"))

            val missingKeys = base.keys - translated.keys
            val extraKeys = translated.keys - base.keys

            assertTrue(
                "Missing keys in $dir/strings.xml: $missingKeys",
                missingKeys.isEmpty()
            )
            assertTrue(
                "Extra/orphaned keys in $dir/strings.xml: $extraKeys",
                extraKeys.isEmpty()
            )

            for ((key, baseValue) in base) {
                val basePlaceholders = placeholderRegex.findAll(baseValue).map { it.value }.sorted().toList()
                val targetValue = translated.getValue(key)
                val targetPlaceholders = placeholderRegex.findAll(targetValue).map { it.value }.sorted().toList()
                assertEquals(
                    "Format placeholder mismatch for key '$key' in $dir/strings.xml",
                    basePlaceholders,
                    targetPlaceholders
                )
            }
        }
    }
}
