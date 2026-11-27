package com.zero.todo.data.ai

import com.zero.todo.domain.model.TaskPriority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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
                - TARGET OUTPUT LANGUAGE: Japanese (日本語) [locale=$languageTag].
                - STRICT TRANSLATION: Regardless of whether the user's voice transcript, pasted note, or image is in Chinese, English, Korean, or mixed languages, translate and summarize EVERY generated text field (`title`, `firstStep`, `description`, `subtasks[].title`, `checklistItems[]`) into natural, concise Japanese.
                - STYLE (新聞見出し・体言止め・常体): Write like Japanese newspaper headlines or telegraphic action labels—ultra-concise, noun-ended or plain-form, action-first, zero fluff.
                - IDIOMATIC JAPANESE ONLY (中国語語彙の直訳・偽日本語禁止): Always translate concepts into everyday natural Japanese used in Japan (prefer standard Katakana/native words over literal Chinese-to-Kanji conversion). Examples: 后备箱→トランク (NEVER 後備箱), 手机→スマホ (NEVER 手機), 快递→宅配便/荷物 (NEVER 快逓), 电脑→PC (NEVER 電脳), 物业→管理会社.
                - STRICTLY FORBIDDEN (敬語・丁寧語禁止): NEVER use polite forms, honorifics, or soft endings (`です`, `ます`, `してください`, `しましょう`, `お願いします`, `確認が必要です`, `〜すること`).
                - Examples -> title: "仕訳資料送付", "レターパック購入", "11月分領収書照合", "税理士へ連絡" (NEVER "仕訳資料を税理士に送付してください"); firstStep: "領収書フォルダを開く"; checklist: "枚数確認", "切手代確認".
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

    fun buildCaptureSystemPrompt(context: AiWorkspaceContext): String = """
You convert natural text, chat screenshots, bills, letters, or voice notes into structured tasks for ZERO.
Always reply strictly with JSON matching:
{
  "tasks": [
    {
      "title": "telegraphic headline task title in target UI language",
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
${buildLanguageStyleDirective(context.languageTag)}
- Context date: ${context.currentDateIso} (${context.currentWeekday}).
- Existing folders: ${context.folderPaths.take(40).joinToString(", ").ifBlank { "(none)" }}
- Existing tags: ${context.tagPaths.take(40).joinToString(", ").ifBlank { "(none)" }}
- Prefer reusing existing folderPaths and tags accurately.
- FIRST STEP DISCIPLINE: Default "firstStep" to JSON null for straightforward or single-action physical tasks (e.g., "トランク整理", "ゴミ出し", "牛乳購入", "薬を飲む"). ONLY set "firstStep" when the task is heavy/multi-step/vague AND the starter action genuinely lowers mental friction (e.g., "確定申告" → "領収書フォルダを開く"). NEVER output obvious tautologies like "トランクを開く" for "トランク整理" or "電話を取る" for "電話する".
- Never include the final completion step itself as a subtask; if the task is small/single-sitting, keep subtasks empty.
    """.trimIndent()

    fun buildBreakdownSystemPrompt(context: AiWorkspaceContext): String = """
You help break down a task for ZERO (an anti-procrastination GTD app).
Reply strictly with JSON:
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
${buildLanguageStyleDirective(context.languageTag)}
- Context date: ${context.currentDateIso}.
- Existing tags: ${context.tagPaths.take(40).joinToString(", ").ifBlank { "(none)" }}
- Keep subtasks focused (0-4 items) ONLY for genuine separate prerequisites; put quick in-sitting verification steps into checklistItems (0-5 items).
- Avoid obvious tautologies in firstStep (never output trivial restatements of the task title like "トランクを開く" for "トランク整理").
    """.trimIndent()
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
        context: AiWorkspaceContext
    ): Result<List<AiCapturedTaskDraft>> = withContext(Dispatchers.IO) {
        runCatching {
            require(config.isConfigured) { "API key is empty" }
            require(rawText.isNotBlank() || image != null) { "Input text and image are both empty" }

            val systemPrompt = ZeroAiJsonParser.buildCaptureSystemPrompt(context)

            val userPrompt = buildString {
                if (rawText.isNotBlank()) {
                    append("Note:\n").append(rawText.trim())
                } else {
                    append("Extract actionable tasks, deadlines, and reference details from this image.")
                }
            }

            val raw = executePrompt(
                config = config,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                image = image,
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
        expectJson: Boolean
    ): String {
        val model = config.activeModel
        val apiKey = config.activeApiKey
        return when (config.provider) {
            AiProvider.GEMINI -> {
                val encodedModel = URLEncoder.encode(model, "UTF-8")
                val encodedKey = URLEncoder.encode(apiKey, "UTF-8")
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$encodedModel:generateContent?key=$encodedKey"
                val body = ZeroAiJsonParser.buildGeminiRequestJson(systemPrompt, userPrompt, image, expectJson)
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
