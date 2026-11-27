package com.zero.todo.data.ai

import com.zero.todo.domain.model.TaskPriority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure-Kotlin JSON request/response parser for both Gemini and OpenAI protocols,
 * decoupled from Android framework APIs so unit tests run directly on JVM / Robolectric.
 */
object ZeroAiJsonParser {

    fun stripCodeFence(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val lines = trimmed.lines()
        if (lines.size <= 2) return trimmed.removePrefix("```").removeSuffix("```").trim()
        val endIdx = if (lines.last().trim().startsWith("```")) lines.lastIndex else lines.size
        return lines.subList(1, endIdx).joinToString("\n").trim()
    }

    fun normalizePriority(value: Int?): TaskPriority = when (value) {
        1 -> TaskPriority.URGENT
        2 -> TaskPriority.HIGH
        3 -> TaskPriority.MEDIUM
        else -> TaskPriority.NONE
    }

    fun normalizeEffortMinutes(value: Int?): Int? {
        if (value == null || value <= 0) return null
        return when {
            value <= 10 -> 5
            value <= 40 -> 25
            else -> 60
        }
    }

    fun parseIsoDueDate(raw: String?, zoneId: ZoneId = ZoneId.systemDefault()): Pair<Long, Boolean>? {
        val clean = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        // Timed formats: YYYY-MM-DD HH:mm or YYYY-MM-DDTHH:mm(:ss)
        val normalizedTimed = clean.replace('T', ' ')
        if (normalizedTimed.length >= 16 && normalizedTimed[10] == ' ') {
            val prefix = normalizedTimed.take(16)
            val parsedTime = runCatching {
                LocalDateTime.parse(prefix, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            }.getOrNull()
            if (parsedTime != null) {
                return parsedTime.atZone(zoneId).toInstant().toEpochMilli() to false
            }
        }
        // All-day YYYY-MM-DD
        val datePrefix = clean.take(10)
        val parsedDate = runCatching { LocalDate.parse(datePrefix) }.getOrNull() ?: return null
        return parsedDate.atStartOfDay(zoneId).toInstant().toEpochMilli() to true
    }

    fun parseCapturedTasks(rawJson: String): List<AiCapturedTaskDraft> {
        val clean = stripCodeFence(rawJson)
        val array: JSONArray = when {
            clean.startsWith("[") -> JSONArray(clean)
            clean.startsWith("{") -> {
                val obj = JSONObject(clean)
                obj.optJSONArray("tasks") ?: JSONArray().put(obj)
            }
            else -> {
                val startObj = clean.indexOf('{')
                val endObj = clean.lastIndexOf('}')
                val startArr = clean.indexOf('[')
                val endArr = clean.lastIndexOf(']')
                when {
                    startObj >= 0 && endObj > startObj && (startArr < 0 || startObj < startArr) -> {
                        val obj = JSONObject(clean.substring(startObj, endObj + 1))
                        obj.optJSONArray("tasks") ?: JSONArray().put(obj)
                    }
                    startArr >= 0 && endArr > startArr -> JSONArray(clean.substring(startArr, endArr + 1))
                    else -> error("Model did not return valid JSON")
                }
            }
        }
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val title = item.optNullableString("title") ?: continue
                val folderPath = item.optNullableString("folderPath")
                    ?.trimStart('/')
                    ?.trim()
                    ?.let(::sanitizeNullableString)
                val dueDateIso = item.optNullableString("dueDate")
                    ?.takeIf { parseIsoDueDate(it) != null }
                add(
                    AiCapturedTaskDraft(
                        title = title,
                        folderPath = folderPath,
                        tags = item.optJSONArray("tags").toCleanTagList(),
                        dueDateIso = dueDateIso,
                        priority = normalizePriority(item.optIntOrNull("priority")),
                        estimatedMinutes = normalizeEffortMinutes(item.optIntOrNull("estimatedMinutes")),
                        firstStep = item.optNullableString("firstStep")?.take(36),
                        description = item.optNullableString("description").orEmpty(),
                        subtasks = parseSubtasks(item.optJSONArray("subtasks"))
                    )
                )
            }
        }
    }

    fun sanitizeCapturedDrafts(
        drafts: List<AiCapturedTaskDraft>,
        existingFolderPaths: List<String>
    ): List<AiCapturedTaskDraft> {
        val canonicalFolders = existingFolderPaths
            .map { it.trim().trim('/') }
            .filter { it.isNotEmpty() }
            .distinct()
        val exactByLower = canonicalFolders.associateBy { it.lowercase() }
        val leafGroups = canonicalFolders.groupBy { it.substringAfterLast('/').lowercase() }

        return drafts.map { draft ->
            val rawFolder = draft.folderPath?.trim()?.trim('/')?.let(::sanitizeNullableString)
            val resolvedFolder = when {
                rawFolder == null -> null
                exactByLower.containsKey(rawFolder.lowercase()) -> exactByLower.getValue(rawFolder.lowercase())
                else -> {
                    val leafMatches = leafGroups[rawFolder.substringAfterLast('/').lowercase()].orEmpty()
                    if (leafMatches.size == 1) leafMatches.first() else null
                }
            }
            draft.copy(folderPath = resolvedFolder)
        }
    }

    fun parseBreakdown(rawJson: String): AiBreakdownDraft {
        val clean = stripCodeFence(rawJson)
        val jsonStr = if (clean.startsWith("{")) {
            clean
        } else {
            val start = clean.indexOf('{')
            val end = clean.lastIndexOf('}')
            if (start >= 0 && end > start) clean.substring(start, end + 1)
            else error("Model did not return valid JSON object")
        }
        val obj = JSONObject(jsonStr)
        val checklistArr = obj.optJSONArray("checklistItems")
        val checklist = buildList {
            if (checklistArr != null) {
                for (i in 0 until checklistArr.length()) {
                    val rawLine = checklistArr.optNullableString(i) ?: continue
                    val line = rawLine
                        .removePrefix("- [ ]")
                        .removePrefix("- ")
                        .trim()
                        .let(::sanitizeNullableString)
                        ?: continue
                    add(line)
                }
            }
        }
        return AiBreakdownDraft(
            firstStep = obj.optNullableString("firstStep")?.take(36),
            estimatedMinutes = normalizeEffortMinutes(obj.optIntOrNull("estimatedMinutes")),
            subtasks = parseSubtasks(obj.optJSONArray("subtasks")),
            checklistItems = checklist
        )
    }

    private fun parseSubtasks(array: JSONArray?): List<AiSubtaskDraft> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val title = obj.optNullableString("title") ?: continue
                val dueDateIso = obj.optNullableString("dueDate")
                    ?.takeIf { parseIsoDueDate(it) != null }
                add(
                    AiSubtaskDraft(
                        title = title,
                        tags = obj.optJSONArray("tags").toCleanTagList(),
                        dueDateIso = dueDateIso,
                        priority = normalizePriority(obj.optIntOrNull("priority")),
                        estimatedMinutes = normalizeEffortMinutes(obj.optIntOrNull("estimatedMinutes"))
                    )
                )
            }
        }
    }

    private val NULL_SENTINELS = setOf("null", "none", "nil", "n/a", "undefined", "-")

    private fun sanitizeNullableString(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (trimmed.lowercase() in NULL_SENTINELS) null else trimmed
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return sanitizeNullableString(opt(key)?.toString())
    }

    private fun JSONArray.optNullableString(index: Int): String? {
        if (index < 0 || index >= length() || isNull(index)) return null
        return sanitizeNullableString(opt(index)?.toString())
    }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) {
            when (val raw = opt(key)) {
                is Number -> raw.toInt()
                is String -> raw.trim().toIntOrNull()
                else -> null
            }
        } else {
            null
        }

    private fun JSONArray?.toCleanTagList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val raw = optNullableString(i) ?: continue
                val t = raw.removePrefix("#").trim().let(::sanitizeNullableString) ?: continue
                if (!contains(t)) add(t)
            }
        }
    }

    fun buildGeminiRequestJson(
        systemPrompt: String,
        userPrompt: String,
        image: AiImagePayload?,
        audio: AiAudioPayload? = null,
        expectJson: Boolean = true
    ): JSONObject {
        val parts = JSONArray().put(JSONObject().put("text", userPrompt))
        if (image != null) {
            parts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", image.mimeType)
                        .put("data", image.base64Jpeg)
                )
            )
        }
        if (audio != null) {
            parts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", audio.mimeType)
                        .put("data", audio.base64Data)
                )
            )
        }
        val genConfig = JSONObject().put("temperature", 0.2)
        if (expectJson) {
            genConfig.put("response_mime_type", "application/json")
        }
        return JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", genConfig)
    }

    fun buildOpenAiRequestJson(
        model: String,
        systemPrompt: String,
        userPrompt: String,
        image: AiImagePayload?,
        expectJson: Boolean = true
    ): JSONObject {
        val userMessage = if (image != null) {
            JSONObject()
                .put("role", "user")
                .put(
                    "content",
                    JSONArray()
                        .put(JSONObject().put("type", "text").put("text", userPrompt))
                        .put(
                            JSONObject()
                                .put("type", "image_url")
                                .put(
                                    "image_url",
                                    JSONObject().put("url", "data:${image.mimeType};base64,${image.base64Jpeg}")
                                )
                        )
                )
        } else {
            JSONObject()
                .put("role", "user")
                .put("content", userPrompt)
        }
        return JSONObject()
            .put("model", model)
            .put("temperature", 0.2)
            .apply {
                if (expectJson) {
                    put("response_format", JSONObject().put("type", "json_object"))
                }
            }
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(userMessage)
            )
    }

    fun extractGeminiText(responseBody: String): String {
        val root = JSONObject(responseBody)
        val candidates = root.optJSONArray("candidates") ?: error("Empty Gemini response")
        val first = candidates.optJSONObject(0) ?: error("No Gemini candidate returned")
        val parts = first.optJSONObject("content")?.optJSONArray("parts") ?: error("Missing content parts")
        val text = buildString {
            for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i) ?: continue
                if (p.has("text")) append(p.optString("text"))
            }
        }.trim()
        if (text.isEmpty()) error("Empty Gemini text output")
        return text
    }

    fun extractOpenAiText(responseBody: String): String {
        val root = JSONObject(responseBody)
        val choices = root.optJSONArray("choices") ?: error("Empty OpenAI response")
        val first = choices.optJSONObject(0) ?: error("No OpenAI choice returned")
        val message = first.optJSONObject("message") ?: error("Missing message object")
        val text = message.optString("content", "").trim()
        if (text.isEmpty()) error("Empty OpenAI text output")
        return text
    }

    fun resolveOpenAiTranscriptionsUrl(openAiBaseUrl: String): String {
        val raw = openAiBaseUrl.trim().ifBlank { AiConfig.DEFAULT_OPENAI_BASE_URL }.trimEnd('/')
        val base = raw.removeSuffix("/chat/completions").trimEnd('/')
        return if (base.endsWith("/audio/transcriptions")) base else "$base/audio/transcriptions"
    }

    fun extractOpenAiTranscriptionText(responseBody: String): String {
        val trimmed = responseBody.trim()
        val parsed = runCatching {
            if (trimmed.startsWith("{")) JSONObject(trimmed).optString("text", "").trim() else trimmed
        }.getOrDefault(trimmed)
        if (parsed.isBlank()) error("Empty audio transcription")
        return parsed
    }

    fun extractApiError(responseBody: String?, httpCode: Int): String {
        val raw = responseBody?.trim().orEmpty()
        if (raw.isNotEmpty()) {
            val jsonMsg = runCatching {
                JSONObject(raw).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
            }.getOrNull()
            if (jsonMsg != null) return "HTTP $httpCode: $jsonMsg"
        }
        return "HTTP $httpCode"
    }

    /**
     * Builds strict UI-language translation and telegraphic headline style instructions
     * so voice notes, pasted chats, or photographed documents in any language are summarized
     * concisely into the active app language without honorifics or conversational filler.
     */
    fun buildLanguageStyleDirective(languageTag: String): String {
        val primaryLang = languageTag.trim().lowercase().substringBefore('-').substringBefore('_')
        val styleSpec = when (primaryLang) {
            "ja" -> """
                - 出力言語（最重要）: 必ず自然な「日本語」のみ（locale=$languageTag）。
                - 簡体字・中国語熟語の残留完全禁止: 入力（音声・メモ・画像）が中国語・英語・韓国語・多言語混在であっても、`title` / `firstStep` / `description` / `subtasks[].title` / `checklistItems[]` のすべてを完全に自然な日本語へ意訳・要約すること。中国語の漢字をそのまま並べたり（例：「煤炉商品上架」「支付方式变更」「後備箱整理」）、簡体字を残すことは絶対禁止。
                - 日本在住者の日常語彙・スラング意訳辞書（必ず現地の表現へ変換）:
                  * 煤炉 → メルカリ / 上架 → 出品（「已经拍完照的商品 煤炉上架」→「撮影済み商品 メルカリ出品」）
                  * 支付方式变更 → 支払い方法変更 / 取消・退订 → 解約
                  * 后备箱 → トランク / 手机 → スマホ / 快递 → 宅配便・荷物 / 电脑 → PC / 物业 → 管理会社
                  * 日亚 → Amazon / 乐天 → 楽天 / 大创 → ダイソー / 唐吉诃德 → ドンキ / 转运 → 転送便
                - 文体（新聞見出し・体言止め・常体）: 最小限の文字数で一目で動作が分かる見出しにする。
                - 敬語・丁寧語禁止: 「です」「ます」「してください」「しましょう」「お願いします」「確認が必要です」「〜すること」は絶対使わない。
                - 良い例 -> title: "撮影済み商品 メルカリ出品", "支払い方法変更", "仕訳資料送付", "レターパック購入", "トランク整理"; checklist: "枚数確認", "送料確認"。
                - 自己検証: JSON を返す直前に、各テキストに中国語の言い回しや不自然な直訳漢字が残っていないか確認し、残っていれば自然な日本語へ書き直すこと。
            """.trimIndent()

            "zh" -> """
                - TARGET OUTPUT LANGUAGE: Simplified Chinese (简体中文) [locale=$languageTag].
                - STRICT TRANSLATION: Regardless of whether the user's voice transcript, pasted note, or image is in Japanese, English, Korean, or mixed languages, translate and summarize EVERY generated text field (`title`, `firstStep`, `description`, `subtasks[].title`, `checklistItems[]`) into concise Simplified Chinese.
                - STYLE (极简动宾短语・新闻标题体): Strip conversational filler cleanly. Keep task titles crisp and direct (typically 4-14 chars).
                - STRICTLY FORBIDDEN: NEVER use polite or filler phrasing (`请`, `记得`, `需要去`, `麻烦`, `一下`, `务必`).
                - Examples -> title: "寄送仕訳资料", "便利店买信封", "核对11月收据", "联系税务师" (NOT "请记得去买信封然后寄给税务师"); firstStep: "打开收据文件夹"; checklist: "核对张数", "粘贴邮票".
            """.trimIndent()

            "ko" -> """
                - TARGET OUTPUT LANGUAGE: Korean (한국어) [locale=$languageTag].
                - STRICT TRANSLATION: Regardless of whether the user's voice transcript, pasted note, or image is in Japanese, Chinese, English, or mixed languages, translate and summarize EVERY generated text field (`title`, `firstStep`, `description`, `subtasks[].title`, `checklistItems[]`) into concise Korean.
                - STYLE (신문 헤드라인·개조식·명사형): Use ultra-concise noun-ending or telegraphic bullet labels with zero conversational padding.
                - STRICTLY FORBIDDEN (존댓말·해요체·합니다체 금지): NEVER use honorific or polite endings (`~하세요`, `~하십시오`, `~합니다`, `~해요`, `~바랍니다`, `해주세요`).
                - Examples -> title: "회계자료 발송", "봉투 구매", "11월 영수증 대조", "세무사 연락" (NEVER "회계자료를 세무사에게 발송하세요"); firstStep: "영수증 폴더 열기"; checklist: "매수 확인", "우표 부착".
            """.trimIndent()

            else -> """
                - TARGET OUTPUT LANGUAGE: English [locale=$languageTag].
                - STRICT TRANSLATION: Regardless of whether the user's voice transcript, pasted note, or image is in Japanese, Chinese, Korean, or any other language, translate and summarize EVERY generated text field (`title`, `firstStep`, `description`, `subtasks[].title`, `checklistItems[]`) into crisp, telegraphic English.
                - STYLE (Newspaper Headline / Telegraphic Imperative): Keep titles compact (2-6 words), action-first, scannable at a glance.
                - STRICTLY FORBIDDEN: NEVER use polite or conversational filler (`Please`, `Remember to`, `You need to`, `Make sure to`, `Could you`).
                - Examples -> title: "Mail tax docs", "Buy Letter Pack", "Verify Nov receipts", "Call tax accountant" (NEVER "Please remember to mail the tax documents"); firstStep: "Open receipt folder"; checklist: "Count receipts", "Apply stamp".
            """.trimIndent()
        }
        return buildString {
            append(styleSpec)
            append('\n')
            append("- TAXONOMY & DATA FIDELITY: Preserve exact numbers, amounts, dates, IDs, and proper names accurately when translating. When reusing an entry from `Existing folders` or `Existing tags`, keep its EXACT original spelling/language—do NOT translate existing folderPath or existing tag paths; only write brand-new scene tags concisely in the target UI language.")
        }
    }

    fun buildCaptureSystemPrompt(context: AiWorkspaceContext): String {
        val primaryLang = context.languageTag.trim().lowercase().substringBefore('-').substringBefore('_')
        val header = if (primaryLang == "ja") {
            "あなたは GTD アプリ「ZERO」専用のタスク構造化アシスタントです。ユーザーの自然文・音声・画像から実行可能なタスクを抽出し、必ず有効な JSON のみを返してください。"
        } else {
            "You convert natural text, chat screenshots, bills, letters, or voice notes into structured tasks for ZERO.\nAlways reply strictly with JSON matching:"
        }
        val customSection = context.customInstructions.trim().takeIf { it.isNotEmpty() }?.let {
            "\n- USER CUSTOM GLOSSARY / RULES (HIGHEST PRIORITY):\n${it.take(600)}"
        }.orEmpty()
        return """
$header
{
  "tasks": [
    {
      "title": "concise newspaper-headline task title in target UI language",
      "folderPath": "EXACT path from Existing folders if clearly matched, otherwise JSON null (NEVER invent a new folder name; unmatched tasks default to Inbox)",
      "tags": ["existing tag path (exact spelling) or concise scene tag without # in target UI language"],
      "dueDate": "YYYY-MM-DD" or "YYYY-MM-DD HH:mm", or JSON null if no date is mentioned,
      "priority": 1|2|3|4 (1=urgent, 2=high, 3=medium, 4=lowest/normal; default to 4 when urgency is unknown),
      "estimatedMinutes": 5|25|60|null,
      "firstStep": "JSON null for simple/obvious/physical tasks; ONLY populate a <=20 char starter action when the task is complex, ambiguous, or procrastination-prone",
      "description": "concise reference details or key numbers in target UI language, empty string if none",
      "subtasks": [
        {
          "title": "predecessor step requiring a DIFFERENT location/time/prep in target UI language (NOT repeating main final action)",
          "tags": ["scene tag if distinct"],
          "dueDate": null or "YYYY-MM-DD",
          "estimatedMinutes": 5|25|60|null
        }
      ]
    }
  ]
}
ZERO rules:
${buildLanguageStyleDirective(context.languageTag)}$customSection
- Context date: ${context.currentDateIso} (${context.currentWeekday}).
- Existing folders: ${context.folderPaths.take(40).joinToString(", ").ifBlank { "(none)" }}
- Existing tags: ${context.tagPaths.take(40).joinToString(", ").ifBlank { "(none)" }}
- Prefer reusing existing folderPaths and tags accurately.
- FIRST STEP DISCIPLINE: Default "firstStep" to JSON null for straightforward or single-action physical tasks (e.g., "トランク整理", "ゴミ出し", "牛乳購入", "薬を飲む"). ONLY set "firstStep" when the task is heavy/multi-step/vague AND the starter action genuinely lowers mental friction (e.g., "確定申告" → "領収書フォルダを開く"). NEVER output obvious tautologies like "トランクを開く" for "トランク整理" or "電話を取る" for "電話する".
- Never include the final completion step itself as a subtask; if the task is small/single-sitting, keep subtasks empty.
        """.trimIndent()
    }

    fun buildBreakdownSystemPrompt(context: AiWorkspaceContext): String {
        val primaryLang = context.languageTag.trim().lowercase().substringBefore('-').substringBefore('_')
        val header = if (primaryLang == "ja") {
            "あなたは先延ばし防止 GTD アプリ「ZERO」のタスク分解アシスタントです。必ず以下の形式の JSON のみを返してください。"
        } else {
            "You help break down a task for ZERO (an anti-procrastination GTD app).\nReply strictly with JSON:"
        }
        val customSection = context.customInstructions.trim().takeIf { it.isNotEmpty() }?.let {
            "\n- USER CUSTOM GLOSSARY / RULES (HIGHEST PRIORITY):\n${it.take(600)}"
        }.orEmpty()
        return """
$header
{
  "firstStep": "one ultra-tiny <=20 char physical starter action in target UI language",
  "estimatedMinutes": 5|25|60,
  "subtasks": [
    {
      "title": "predecessor step needing a DIFFERENT location, person, or prep BEFORE finishing main task, in target UI language",
      "tags": ["matching existing tag path (exact spelling) or short scene tag without #"],
      "estimatedMinutes": 5|25|60
    }
  ],
  "checklistItems": [
    "concise check item done in the SAME sitting while executing the main task, in target UI language"
  ]
}
ZERO rules:
${buildLanguageStyleDirective(context.languageTag)}$customSection
- Context date: ${context.currentDateIso}.
- Existing tags: ${context.tagPaths.take(40).joinToString(", ").ifBlank { "(none)" }}
- Keep subtasks focused (0-4 items) ONLY for genuine separate prerequisites; put quick in-sitting verification steps into checklistItems (0-5 items).
- Avoid obvious tautologies in firstStep (never output trivial restatements of the task title like "トランクを開く" for "トランク整理").
        """.trimIndent()
    }

    fun buildCaptureUserPrompt(
        effectiveText: String,
        hasImage: Boolean,
        hasAudio: Boolean,
        languageTag: String
    ): String {
        val primaryLang = languageTag.trim().lowercase().substringBefore('-').substringBefore('_')
        return buildString {
            if (effectiveText.isNotBlank()) {
                when (primaryLang) {
                    "ja" -> append("以下の内容を、中国語表現や簡体字を一切残さず自然な日本語（新聞見出し風・敬語なし）へ意訳してタスクJSONにしてください。\n\nメモ:\n")
                    "zh" -> append("请将以下内容精准提炼为极简简体中文动宾短语任务 JSON：\n\n记录：\n")
                    "ko" -> append("아래 내용을 간결한 한국어 명사형·개조식(존댓말 금지) 태스크 JSON으로 번역·요약하세요.\n\n메모:\n")
                    else -> append("Translate and summarize the following into concise newspaper-headline English tasks JSON:\n\nNote:\n")
                }
                append(effectiveText)
            } else if (hasAudio && !hasImage) {
                when (primaryLang) {
                    "ja" -> append("音声メモを聞き取り、中国語や他言語が混在していても必ず自然な日本語（新聞見出し風・敬語なし）でタスクJSONにしてください。")
                    "zh" -> append("请识别语音内容并翻译整理为极简简体中文动宾短语任务 JSON。")
                    "ko" -> append("음성 메모를 듣고 자연스러운 한국어 개조식(존댓말 금지) 태스크 JSON으로 정리하세요.")
                    else -> append("Listen carefully to this voice note and translate/summarize all actionable tasks into concise English JSON.")
                }
            } else if (hasAudio) {
                when (primaryLang) {
                    "ja" -> append("音声メモと添付画像を確認し、自然な日本語（新聞見出し風・敬語なし）でタスクJSONにしてください。")
                    else -> append("Listen to this voice note and inspect the attached image, then extract all actionable tasks in target UI language JSON.")
                }
            } else {
                when (primaryLang) {
                    "ja" -> append("添付画像から対応すべきタスク・期限・補足情報を読み取り、自然な日本語（新聞見出し風・敬語なし）でタスクJSONにしてください。")
                    "zh" -> append("请识别图片中的待办事项、截止日期与关键参考信息，整理为简体中文极简任务 JSON。")
                    "ko" -> append("첨부 이미지에서 할 일, 마감일, 참고 정보를 추출하여 간결한 한국어(존댓말 금지) 태스크 JSON으로 정리하세요.")
                    else -> append("Extract actionable tasks, deadlines, and reference details from this image in concise English JSON.")
                }
            }
        }
    }
}

@Singleton
class ZeroAiEngine @Inject constructor() {

    suspend fun testConnection(config: AiConfig): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(config.isConfigured) { "API key is empty" }
            val text = executePrompt(
                config = config,
                systemPrompt = "Reply only with valid JSON: {\"ok\":true}",
                userPrompt = "Ping",
                image = null,
                expectJson = true
            )
            if (text.isBlank()) error("Empty reply")
            config.activeModel
        }
    }

    suspend fun captureTasks(
        config: AiConfig,
        rawText: String,
        image: AiImagePayload?,
        audio: AiAudioPayload? = null,
        context: AiWorkspaceContext
    ): Result<List<AiCapturedTaskDraft>> = withContext(Dispatchers.IO) {
        runCatching {
            require(config.isConfigured) { "API key is empty" }
            require(rawText.isNotBlank() || image != null || audio != null) { "Input text, image, and audio are all empty" }

            val systemPrompt = ZeroAiJsonParser.buildCaptureSystemPrompt(context)

            val transcribedAudio = if (audio != null && config.provider == AiProvider.OPENAI) {
                transcribeOpenAiAudio(config, audio)
            } else {
                null
            }
            val effectiveText = listOfNotNull(
                rawText.trim().takeIf { it.isNotEmpty() },
                transcribedAudio?.trim()?.takeIf { it.isNotEmpty() }
            ).joinToString("\n")

            val userPrompt = ZeroAiJsonParser.buildCaptureUserPrompt(
                effectiveText = effectiveText,
                hasImage = image != null,
                hasAudio = audio != null,
                languageTag = context.languageTag
            )

            val raw = executePrompt(
                config = config,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                image = image,
                audio = if (config.provider == AiProvider.GEMINI) audio else null,
                expectJson = true
            )
            val parsed = ZeroAiJsonParser.sanitizeCapturedDrafts(
                drafts = ZeroAiJsonParser.parseCapturedTasks(raw),
                existingFolderPaths = context.folderPaths
            )
            if (parsed.isEmpty()) error("No actionable tasks detected")
            parsed
        }
    }

    suspend fun breakdownTask(
        config: AiConfig,
        taskTitle: String,
        taskDescription: String,
        existingFolderPath: String?,
        existingTags: List<String>,
        context: AiWorkspaceContext
    ): Result<AiBreakdownDraft> = withContext(Dispatchers.IO) {
        runCatching {
            require(config.isConfigured) { "API key is empty" }
            val systemPrompt = ZeroAiJsonParser.buildBreakdownSystemPrompt(context)

            val userPrompt = buildString {
                append("Task: ").append(taskTitle.trim())
                if (!existingFolderPath.isNullOrBlank()) {
                    append("\nFolder: ").append(existingFolderPath)
                }
                if (existingTags.isNotEmpty()) {
                    append("\nCurrent tags: ").append(existingTags.joinToString(", ") { "#$it" })
                }
                if (taskDescription.isNotBlank()) {
                    append("\nNotes:\n").append(taskDescription.trim().take(800))
                }
            }

            val raw = executePrompt(
                config = config,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                image = null,
                expectJson = true
            )
            ZeroAiJsonParser.parseBreakdown(raw)
        }
    }

    private fun executePrompt(
        config: AiConfig,
        systemPrompt: String,
        userPrompt: String,
        image: AiImagePayload?,
        audio: AiAudioPayload? = null,
        expectJson: Boolean
    ): String {
        val model = config.activeModel
        val apiKey = config.activeApiKey
        return when (config.provider) {
            AiProvider.GEMINI -> {
                val encodedModel = URLEncoder.encode(model, "UTF-8")
                val encodedKey = URLEncoder.encode(apiKey, "UTF-8")
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$encodedModel:generateContent?key=$encodedKey"
                val body = ZeroAiJsonParser.buildGeminiRequestJson(systemPrompt, userPrompt, image, audio, expectJson)
                val response = postJson(url, body.toString(), headers = emptyMap())
                ZeroAiJsonParser.extractGeminiText(response)
            }

            AiProvider.OPENAI -> {
                val base = config.openAiBaseUrl.trim().ifBlank { AiConfig.DEFAULT_OPENAI_BASE_URL }.trimEnd('/')
                val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
                val body = ZeroAiJsonParser.buildOpenAiRequestJson(model, systemPrompt, userPrompt, image, expectJson)
                val response = postJson(url, body.toString(), headers = mapOf("Authorization" to "Bearer $apiKey"))
                ZeroAiJsonParser.extractOpenAiText(response)
            }
        }
    }

    private fun transcribeOpenAiAudio(config: AiConfig, audio: AiAudioPayload): String {
        val url = ZeroAiJsonParser.resolveOpenAiTranscriptionsUrl(config.openAiBaseUrl)
        val boundary = "----ZeroAudioBoundary${System.currentTimeMillis()}"
        val payloadStream = ByteArrayOutputStream()
        fun writeStr(s: String) = payloadStream.write(s.toByteArray(Charsets.UTF_8))

        writeStr("--$boundary\r\n")
        writeStr("Content-Disposition: form-data; name=\"model\"\r\n\r\n")
        writeStr("whisper-1\r\n")

        writeStr("--$boundary\r\n")
        writeStr("Content-Disposition: form-data; name=\"file\"; filename=\"voice.m4a\"\r\n")
        writeStr("Content-Type: ${audio.mimeType}\r\n\r\n")
        payloadStream.write(audio.bytes)
        writeStr("\r\n--$boundary--\r\n")

        val bodyBytes = payloadStream.toByteArray()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${config.activeApiKey}")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
        }
        val responseBody = try {
            conn.outputStream.use { out -> out.write(bodyBytes) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                error(ZeroAiJsonParser.extractApiError(body, code))
            }
            body
        } finally {
            conn.disconnect()
        }
        return ZeroAiJsonParser.extractOpenAiTranscriptionText(responseBody)
    }

    private fun postJson(urlString: String, jsonBody: String, headers: Map<String, String>): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        return try {
            conn.outputStream.use { out ->
                out.write(jsonBody.toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                error(ZeroAiJsonParser.extractApiError(body, code))
            }
            body
        } finally {
            conn.disconnect()
        }
    }
}
