package com.zero.todo.data.ai

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.zero.todo.core.common.VoiceAutoStopDecision
import com.zero.todo.core.common.VoiceAutoStopDetector
import com.zero.todo.core.common.ZeroShareIntentParser
import com.zero.todo.data.preferences.SharedPrefsUserPreferencesRepository
import com.zero.todo.domain.model.PriorityDisplayStyle
import com.zero.todo.domain.model.TaskPriority
import com.zero.todo.feature.ai.nextPreviewPriority
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

    @Test
    fun aiConfigCustomInstructionsPersistsCleanly() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getSharedPreferences(SharedPrefsUserPreferencesRepository.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()

        val repo = SharedPrefsUserPreferencesRepository(ctx)
        repo.setAiConfig(AiConfig(customInstructions = "煤炉=メルカリ, 上架=出品"))
        val loaded = SharedPrefsUserPreferencesRepository(ctx).preferences.value.aiConfig
        assertEquals("煤炉=メルカリ, 上架=出品", loaded.customInstructions)
    }

    @Test
    fun languageStyleDirectiveEnforcesUiTranslationAndNoHonorificsAcrossLocales() {
        val jaDirective = ZeroAiJsonParser.buildLanguageStyleDirective("ja-JP")
        assertTrue(jaDirective.contains("簡体字・中国語熟語の残留完全禁止"))
        assertTrue(jaDirective.contains("メルカリ"))
        assertTrue(jaDirective.contains("出品"))
        assertTrue(jaDirective.contains("支払い方法変更"))
        assertTrue(jaDirective.contains("トランク"))
        assertTrue(jaDirective.contains("敬語・丁寧語禁止"))
        assertTrue(jaDirective.contains("してください"))

        val zhDirective = ZeroAiJsonParser.buildLanguageStyleDirective("zh-CN")
        assertTrue(zhDirective.contains("Simplified Chinese"))
        assertTrue(zhDirective.contains("极简动宾短语"))

        val koDirective = ZeroAiJsonParser.buildLanguageStyleDirective("ko-KR")
        assertTrue(koDirective.contains("Korean"))
        assertTrue(koDirective.contains("개조식·명사형"))
        assertTrue(koDirective.contains("존댓말·해요체·합니다체 금지"))

        val enDirective = ZeroAiJsonParser.buildLanguageStyleDirective("en-US")
        assertTrue(enDirective.contains("TARGET OUTPUT LANGUAGE: English"))
        assertTrue(enDirective.contains("Newspaper Headline"))
        assertTrue(enDirective.contains("Please"))

        val fallbackDirective = ZeroAiJsonParser.buildLanguageStyleDirective("fr-FR")
        assertTrue(fallbackDirective.contains("TARGET OUTPUT LANGUAGE: English"))
    }

    @Test
    fun captureAndBreakdownPromptsEmbedUiLocaleDirectiveAndPreserveWorkspaceTaxonomy() {
        val ctx = AiWorkspaceContext(
            languageTag = "ja",
            currentDateIso = "2026-11-27",
            currentWeekday = "Friday",
            folderPaths = listOf("丸九/経理"),
            tagPaths = listOf("外出/コンビニ"),
            customInstructions = "煤炉=メルカリ"
        )
        val capturePrompt = ZeroAiJsonParser.buildCaptureSystemPrompt(ctx)
        assertTrue(capturePrompt.contains("タスク構造化アシスタント"))
        assertTrue(capturePrompt.contains("丸九/経理"))
        assertTrue(capturePrompt.contains("外出/コンビニ"))
        assertTrue(capturePrompt.contains("do NOT translate existing folderPath or existing tag paths"))
        assertTrue(capturePrompt.contains("FIRST STEP DISCIPLINE"))
        assertTrue(capturePrompt.contains("JSON null for simple/obvious/physical tasks"))
        assertTrue(capturePrompt.contains("USER CUSTOM GLOSSARY / RULES"))
        assertTrue(capturePrompt.contains("煤炉=メルカリ"))

        val userPrompt = ZeroAiJsonParser.buildCaptureUserPrompt(
            effectiveText = "已经拍完照的商品 煤炉上架",
            hasImage = false,
            hasAudio = false,
            languageTag = "ja"
        )
        assertTrue(userPrompt.contains("簡体字を一切残さず自然な日本語"))
        assertTrue(userPrompt.contains("已经拍完照的商品 煤炉上架"))

        val breakdownPrompt = ZeroAiJsonParser.buildBreakdownSystemPrompt(ctx)
        assertTrue(breakdownPrompt.contains("新聞見出し・体言止め"))
        assertTrue(breakdownPrompt.contains("敬語・丁寧語禁止"))
        assertTrue(breakdownPrompt.contains("外出/コンビニ"))
        assertTrue(breakdownPrompt.contains("Avoid obvious tautologies in firstStep"))
        assertTrue(breakdownPrompt.contains("煤炉=メルカリ"))
    }

    @Test
    fun ignoresJsonNullAndLiteralNullSentinelsAndSanitizesUnmatchedFoldersToInbox() {
        val raw = """
            {
              "tasks": [
                {
                  "title": "トランク整理",
                  "folderPath": null,
                  "tags": [null, "null", "#none"],
                  "dueDate": null,
                  "priority": null,
                  "estimatedMinutes": 25,
                  "firstStep": null,
                  "description": null
                },
                {
                  "title": "別タスク",
                  "folderPath": "/null",
                  "dueDate": "null",
                  "priority": 4,
                  "firstStep": "null",
                  "description": "none",
                  "subtasks": [
                    {
                      "title": "ツール準備",
                      "dueDate": "null",
                      "tags": ["null"]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = ZeroAiJsonParser.parseCapturedTasks(raw)
        assertEquals(2, parsed.size)
        val first = parsed[0]
        assertEquals(null, first.folderPath)
        assertEquals(null, first.dueDateIso)
        assertEquals(TaskPriority.NONE, first.priority)
        assertEquals(null, first.firstStep)
        assertEquals("", first.description)
        assertTrue(first.tags.isEmpty())

        val second = parsed[1]
        assertEquals(null, second.folderPath)
        assertEquals(null, second.dueDateIso)
        assertEquals(TaskPriority.NONE, second.priority)
        assertEquals(null, second.firstStep)
        assertEquals("", second.description)
        assertEquals(null, second.subtasks.first().dueDateIso)
        assertTrue(second.subtasks.first().tags.isEmpty())

        val sanitized = ZeroAiJsonParser.sanitizeCapturedDrafts(
            drafts = listOf(
                first.copy(folderPath = "車"),
                first.copy(folderPath = "経理"),
                first.copy(folderPath = "丸九/コンサルティング/経理"),
                first.copy(folderPath = "Inbox")
            ),
            existingFolderPaths = listOf("丸九/コンサルティング/経理", "個人/家事")
        )
        assertEquals(null, sanitized[0].folderPath)
        assertEquals("丸九/コンサルティング/経理", sanitized[1].folderPath)
        assertEquals("丸九/コンサルティング/経理", sanitized[2].folderPath)
        assertEquals(null, sanitized[3].folderPath)
    }

    @Test
    fun priorityDisplayStyleDefaultsToDotsAndPersistsInSharedPrefs() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getSharedPreferences(SharedPrefsUserPreferencesRepository.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()

        val repo1 = SharedPrefsUserPreferencesRepository(ctx)
        assertEquals(PriorityDisplayStyle.DOTS, repo1.preferences.value.priorityDisplayStyle)

        repo1.setPriorityDisplayStyle(PriorityDisplayStyle.WORDS)
        assertEquals(PriorityDisplayStyle.WORDS, SharedPrefsUserPreferencesRepository(ctx).preferences.value.priorityDisplayStyle)

        repo1.setPriorityDisplayStyle(PriorityDisplayStyle.MARKS)
        assertEquals(PriorityDisplayStyle.MARKS, SharedPrefsUserPreferencesRepository(ctx).preferences.value.priorityDisplayStyle)

        assertEquals(PriorityDisplayStyle.DOTS, PriorityDisplayStyle.fromStorage("unknown"))
    }

    @Test
    fun shareIntentParserExtractsTextSubjectAndImageStreamsSafely() {
        val textIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Tax Docs")
            putExtra(Intent.EXTRA_TEXT, "Mail to accountant before Friday")
        }
        val textPayload = ZeroShareIntentParser.extract(textIntent)
        assertNotNull(textPayload)
        assertEquals("Tax Docs\nMail to accountant before Friday", textPayload!!.text)
        assertEquals(null, textPayload.imageUri)

        val imageIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, Uri.parse("content://media/external/images/media/42"))
        }
        val imagePayload = ZeroShareIntentParser.extract(imageIntent)
        assertNotNull(imagePayload)
        assertEquals("", imagePayload!!.text)
        assertEquals("content://media/external/images/media/42", imagePayload.imageUri)

        val nonSend = Intent(Intent.ACTION_VIEW).apply {
            putExtra(Intent.EXTRA_TEXT, "Ignored")
        }
        assertEquals(null, ZeroShareIntentParser.extract(nonSend))
        assertEquals(null, ZeroShareIntentParser.extract(Intent(Intent.ACTION_SEND)))
    }

    @Test
    fun nextPreviewPriorityCyclesThroughAllUrgencyLevelsCleanly() {
        assertEquals(TaskPriority.MEDIUM, nextPreviewPriority(TaskPriority.NONE))
        assertEquals(TaskPriority.HIGH, nextPreviewPriority(TaskPriority.MEDIUM))
        assertEquals(TaskPriority.URGENT, nextPreviewPriority(TaskPriority.HIGH))
        assertEquals(TaskPriority.NONE, nextPreviewPriority(TaskPriority.URGENT))
    }

    @Test
    fun buildsGeminiAudioPayloadAndParsesOpenAiWhisperTranscriptionResponse() {
        val audio = AiAudioPayload(
            bytes = byteArrayOf(1, 2, 3, 4),
            base64Data = "AQIDBA==",
            mimeType = "audio/mp4"
        )
        val geminiJson = ZeroAiJsonParser.buildGeminiRequestJson(
            systemPrompt = "sys",
            userPrompt = "voice note",
            image = null,
            audio = audio
        )
        val parts = geminiJson.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        assertEquals(2, parts.length())
        assertEquals("audio/mp4", parts.getJSONObject(1).getJSONObject("inline_data").getString("mime_type"))
        assertEquals("AQIDBA==", parts.getJSONObject(1).getJSONObject("inline_data").getString("data"))

        assertEquals(
            "https://api.openai.com/v1/audio/transcriptions",
            ZeroAiJsonParser.resolveOpenAiTranscriptionsUrl("")
        )
        assertEquals(
            "https://proxy.example.com/v1/audio/transcriptions",
            ZeroAiJsonParser.resolveOpenAiTranscriptionsUrl("https://proxy.example.com/v1/chat/completions")
        )
        assertEquals(
            "整理后备箱",
            ZeroAiJsonParser.extractOpenAiTranscriptionText("{\"text\":\"整理后备箱\"}")
        )
    }

    @Test
    fun voiceAutoStopDetectorStopsOnPostSpeechSilenceAndIgnoresBriefPauses() {
        val detector = VoiceAutoStopDetector()
        // Ambient background hum (~900) for first 500ms should stay CONTINUE
        repeat(5) {
            val tick = detector.onSample(amplitude = 900, stepMs = 100L)
            assertEquals(VoiceAutoStopDecision.CONTINUE, tick.decision)
        }
        // Clear speech (~4500) for 1000ms
        repeat(10) {
            val tick = detector.onSample(amplitude = 4_500, stepMs = 100L)
            assertEquals(VoiceAutoStopDecision.CONTINUE, tick.decision)
            assertTrue(tick.hasHeardSpeech)
            assertTrue(tick.normalizedLevel > 0.3f)
        }
        // Brief mid-sentence pause (800ms) should NOT auto-stop
        repeat(8) {
            val tick = detector.onSample(amplitude = 700, stepMs = 100L)
            assertEquals(VoiceAutoStopDecision.CONTINUE, tick.decision)
        }
        // Resume speech briefly (400ms)
        repeat(4) {
            assertEquals(VoiceAutoStopDecision.CONTINUE, detector.onSample(amplitude = 4_200, stepMs = 100L).decision)
        }
        // End-of-utterance silence reaches 1600ms -> AUTO_STOP_AND_ANALYZE
        var finalDecision = VoiceAutoStopDecision.CONTINUE
        repeat(16) {
            finalDecision = detector.onSample(amplitude = 650, stepMs = 100L).decision
        }
        assertEquals(VoiceAutoStopDecision.AUTO_STOP_AND_ANALYZE, finalDecision)

        // Separate session with only silence cancels cleanly at 7s
        val silentDetector = VoiceAutoStopDetector()
        var silentDecision = VoiceAutoStopDecision.CONTINUE
        repeat(70) {
            silentDecision = silentDetector.onSample(amplitude = 400, stepMs = 100L).decision
        }
        assertEquals(VoiceAutoStopDecision.AUTO_CANCEL_NO_SPEECH, silentDecision)
    }
}
