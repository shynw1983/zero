package com.zero.todo.data.ai

import androidx.test.core.app.ApplicationProvider
import com.zero.todo.data.preferences.SharedPrefsUserPreferencesRepository
import com.zero.todo.domain.model.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class ZeroAiEngineTest {

    @Test
    fun parsesCodeFencedCapturedTasksWithSubtasksAndDates() {
        val raw = """
            ```json
            {
              "tasks": [
                {
                  "title": "仕訳資料送付",
                  "folderPath": "/丸九/コンサルティング/経理",
                  "tags": ["#税理士", "急ぎ"],
                  "dueDate": "2026-12-02 15:30",
                  "priority": 1,
                  "estimatedMinutes": 30,
                  "firstStep": "10月分領収書フォルダを開く",
                  "description": "税理士事務所宛",
                  "subtasks": [
                    {
                      "title": "レターパック購入",
                      "tags": ["#外出/コンビニ"],
                      "estimatedMinutes": 8
                    }
                  ]
                }
              ]
            }
            ```
        """.trimIndent()

        val drafts = ZeroAiJsonParser.parseCapturedTasks(raw)
        assertEquals(1, drafts.size)
        val first = drafts.first()
        assertEquals("仕訳資料送付", first.title)
        assertEquals("丸九/コンサルティング/経理", first.folderPath)
        assertEquals(listOf("税理士", "急ぎ"), first.tags)
        assertEquals(TaskPriority.URGENT, first.priority)
        assertEquals(25, first.estimatedMinutes) // normalized from 30 -> 25
        assertEquals("10月分領収書フォルダを開く", first.firstStep)
        assertEquals(1, first.subtasks.size)
        assertEquals("レターパック購入", first.subtasks.first().title)
        assertEquals(listOf("外出/コンビニ"), first.subtasks.first().tags)
        assertEquals(5, first.subtasks.first().estimatedMinutes) // normalized from 8 -> 5

        val parsedDue = ZeroAiJsonParser.parseIsoDueDate(first.dueDateIso, ZoneOffset.UTC)
        assertNotNull(parsedDue)
        assertFalse(parsedDue!!.second)
        assertEquals(
            LocalDateTime.of(2026, 12, 2, 15, 30).atZone(ZoneOffset.UTC).toInstant().toEpochMilli(),
            parsedDue.first
        )
    }

    @Test
    fun parsesTaskBreakdownDraftAndStripsChecklistPrefixes() {
        val raw = """
            {
              "firstStep": "Open bank statement PDF",
              "estimatedMinutes": 60,
              "subtasks": [
                { "title": "Buy Letter Pack", "tags": ["Outing/Store"] }
              ],
              "checklistItems": [
                "- [ ] Count receipts",
                "- Check stamp fee",
                "Seal envelope"
              ]
            }
        """.trimIndent()

        val draft = ZeroAiJsonParser.parseBreakdown(raw)
        assertEquals("Open bank statement PDF", draft.firstStep)
        assertEquals(60, draft.estimatedMinutes)
        assertEquals(1, draft.subtasks.size)
        assertEquals(listOf("Count receipts", "Check stamp fee", "Seal envelope"), draft.checklistItems)
    }

    @Test
    fun buildsGeminiAndOpenAiMultimodalRequestsCleanly() {
        val image = AiImagePayload(base64Jpeg = "QUJD", mimeType = "image/jpeg")

        val geminiJson = ZeroAiJsonParser.buildGeminiRequestJson(
            systemPrompt = "sys",
            userPrompt = "usr",
            image = image
        )
        val geminiParts = geminiJson.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        assertEquals(2, geminiParts.length())
        assertEquals("image/jpeg", geminiParts.getJSONObject(1).getJSONObject("inline_data").getString("mime_type"))
        assertEquals("QUJD", geminiParts.getJSONObject(1).getJSONObject("inline_data").getString("data"))

        val openAiJson = ZeroAiJsonParser.buildOpenAiRequestJson(
            model = "gpt-4o-mini",
            systemPrompt = "sys",
            userPrompt = "usr",
            image = image
        )
        assertEquals("gpt-4o-mini", openAiJson.getString("model"))
        val userContent = openAiJson.getJSONArray("messages").getJSONObject(1).getJSONArray("content")
        assertEquals(2, userContent.length())
        assertEquals(
            "data:image/jpeg;base64,QUJD",
            userContent.getJSONObject(1).getJSONObject("image_url").getString("url")
        )
    }

    @Test
    fun aiConfigPersistsBothProvidersIndependentlyInSharedPrefs() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getSharedPreferences(SharedPrefsUserPreferencesRepository.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()

        val repo1 = SharedPrefsUserPreferencesRepository(ctx)
        assertEquals(AiProvider.GEMINI, repo1.preferences.value.aiConfig.provider)
        assertFalse(repo1.preferences.value.aiConfig.isConfigured)

        repo1.setAiConfig(
            AiConfig(
                provider = AiProvider.OPENAI,
                geminiApiKey = "AIza-test",
                geminiModel = "gemini-2.5-pro",
                openAiApiKey = "sk-test",
                openAiModel = "gpt-4o",
                openAiBaseUrl = "https://proxy.example.com/v1"
            )
        )

        val repo2 = SharedPrefsUserPreferencesRepository(ctx)
        val loaded = repo2.preferences.value.aiConfig
        assertEquals(AiProvider.OPENAI, loaded.provider)
        assertTrue(loaded.isConfigured)
        assertEquals("AIza-test", loaded.geminiApiKey)
        assertEquals("gemini-2.5-pro", loaded.geminiModel)
        assertEquals("sk-test", loaded.openAiApiKey)
        assertEquals("gpt-4o", loaded.openAiModel)
        assertEquals("https://proxy.example.com/v1", loaded.openAiBaseUrl)
    }
}
