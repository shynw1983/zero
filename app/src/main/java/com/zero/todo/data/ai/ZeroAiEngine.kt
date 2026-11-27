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
                val title = item.optString("title", "").trim()
                if (title.isEmpty()) continue
                add(
                    AiCapturedTaskDraft(
                        title = title,
                        folderPath = item.optString("folderPath", "").trim().trimStart('/').ifBlank { null },
                        tags = item.optJSONArray("tags").toCleanTagList(),
                        dueDateIso = item.optString("dueDate", "").trim().ifBlank { null },
                        priority = normalizePriority(item.optIntOrNull("priority")),
                        estimatedMinutes = normalizeEffortMinutes(item.optIntOrNull("estimatedMinutes")),
                        firstStep = item.optString("firstStep", "").trim().take(36).ifBlank { null },
                        description = item.optString("description", "").trim(),
                        subtasks = parseSubtasks(item.optJSONArray("subtasks"))
                    )
                )
            }
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
                    val line = checklistArr.optString(i, "").trim()
                        .removePrefix("- [ ]")
                        .removePrefix("- ")
                        .trim()
                    if (line.isNotEmpty()) add(line)
                }
            }
        }
        return AiBreakdownDraft(
            firstStep = obj.optString("firstStep", "").trim().take(36).ifBlank { null },
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
                val title = obj.optString("title", "").trim()
                if (title.isEmpty()) continue
                add(
                    AiSubtaskDraft(
                        title = title,
                        tags = obj.optJSONArray("tags").toCleanTagList(),
                        dueDateIso = obj.optString("dueDate", "").trim().ifBlank { null },
                        priority = normalizePriority(obj.optIntOrNull("priority")),
                        estimatedMinutes = normalizeEffortMinutes(obj.optIntOrNull("estimatedMinutes"))
                    )
                )
            }
        }
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
                val t = optString(i, "").trim().removePrefix("#").trim()
                if (t.isNotEmpty() && !contains(t)) add(t)
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

            val systemPrompt = """
You convert natural text, chat screenshots, bills, letters, or voice notes into structured tasks for ZERO.
Always reply strictly with JSON matching:
{
  "tasks": [
    {
      "title": "concise actionable task title",
      "folderPath": "match existing folder path if relevant, or null",
      "tags": ["existing tag path or short scene tag without #"],
      "dueDate": "YYYY-MM-DD" or "YYYY-MM-DD HH:mm" or null,
      "priority": 1|2|3|4|null (1=urgent, 2=high, 3=medium, 4/null=normal),
      "estimatedMinutes": 5|25|60|null,
      "firstStep": "one tiny <=20 char starter action or null",
      "description": "extra reference details or key numbers extracted from image/text, empty string if none",
      "subtasks": [
        {
          "title": "only predecessor steps requiring a DIFFERENT location/time/preparation (NOT repeating main final action)",
          "tags": ["scene tag if distinct"],
          "dueDate": null or "YYYY-MM-DD",
          "estimatedMinutes": 5|25|60|null
        }
      ]
    }
  ]
}
ZERO rules:
- Use the user's language (or match the note/image language). Current context: ${context.currentDateIso} (${context.currentWeekday}), app locale=${context.languageTag}.
- Existing folders: ${context.folderPaths.take(40).joinToString(", ").ifBlank { "(none)" }}
- Existing tags: ${context.tagPaths.take(40).joinToString(", ").ifBlank { "(none)" }}
- Prefer reusing existing folderPaths and tags accurately.
- Never include the final completion step itself as a subtask; if the task is small/single-sitting, keep subtasks empty.
            """.trimIndent()

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
            val parsed = ZeroAiJsonParser.parseCapturedTasks(raw)
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
            val systemPrompt = """
You help break down a task for ZERO (an anti-procrastination GTD app).
Reply strictly with JSON:
{
  "firstStep": "one ultra-tiny <=20 char physical starter action to beat procrastination",
  "estimatedMinutes": 5|25|60,
  "subtasks": [
    {
      "title": "predecessor step needing a DIFFERENT location, person, or prep BEFORE finishing the main task",
      "tags": ["matching existing tag path or short scene tag without #"],
      "estimatedMinutes": 5|25|60
    }
  ],
  "checklistItems": [
    "short check item done in the SAME sitting while executing the main task"
  ]
}
ZERO rules:
- Match task language. Context date=${context.currentDateIso}.
- Existing tags: ${context.tagPaths.take(40).joinToString(", ").ifBlank { "(none)" }}
- Keep subtasks focused (0-4 items) ONLY for genuine separate prerequisites; put quick in-sitting verification steps into checklistItems (0-5 items).
            """.trimIndent()

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
