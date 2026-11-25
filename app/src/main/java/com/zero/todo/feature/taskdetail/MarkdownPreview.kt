package com.zero.todo.feature.taskdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.zero.todo.R
import com.zero.todo.core.designsystem.ZeroMonoFontFamily
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.hairlineDividerStart

/**
 * Lightweight Near-Future Minimalism Markdown renderer for task notes preview.
 * Supports `#` / `##` / `###` headings, `- [ ]` / `- [x]` checklists, bullet lists,
 * `>` blockquotes, inline `**bold**`, and `` `inline code` ``.
 */
@Composable
fun ZeroMarkdownPreview(
    markdown: String,
    onToggleChecklistItem: (lineIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val lines = markdown.lines()

    if (markdown.isBlank()) {
        Text(
            text = stringResource(R.string.detail_markdown_empty),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        lines.forEachIndexed { lineIndex, rawLine ->
            val trimmed = rawLine.trimStart()
            when {
                trimmed.isEmpty() -> Spacer(modifier = Modifier.padding(vertical = 2.dp))
                trimmed.startsWith("### ") -> Text(
                    text = renderInlineMarkdown(trimmed.removePrefix("### "), colors.accent, colors.surfaceElevated),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary
                )
                trimmed.startsWith("## ") -> Text(
                    text = renderInlineMarkdown(trimmed.removePrefix("## "), colors.accent, colors.surfaceElevated),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary
                )
                trimmed.startsWith("# ") -> Text(
                    text = renderInlineMarkdown(trimmed.removePrefix("# "), colors.accent, colors.surfaceElevated),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary
                )
                CHECKLIST_MARKER.find(rawLine)?.range?.first == 0 -> {
                    MarkdownChecklistRow(
                        text = trimmed.drop(6),
                        checked = trimmed[3] != ' ',
                        onToggle = { onToggleChecklistItem(lineIndex) }
                    )
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.accent,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = renderInlineMarkdown(trimmed.drop(2), colors.accent, colors.surfaceElevated),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary
                        )
                    }
                }
                trimmed.startsWith("> ") -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .hairlineDividerStart(color = colors.accent, width = 2.dp)
                            .padding(start = 10.dp, top = 2.dp, bottom = 2.dp)
                    ) {
                        Text(
                            text = renderInlineMarkdown(trimmed.removePrefix("> "), colors.accent, colors.surfaceElevated),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary
                        )
                    }
                }
                else -> Text(
                    text = renderInlineMarkdown(rawLine, colors.accent, colors.surfaceElevated),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary
                )
            }
        }
    }
}

@Composable
private fun MarkdownChecklistRow(text: String, checked: Boolean, onToggle: () -> Unit) {
    val colors = ZeroTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(ZeroSmallShape)
                .background(if (checked) colors.accent else colors.background)
                .hairlineBorder(
                    shape = ZeroSmallShape,
                    color = if (checked) colors.accent else colors.textMuted
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
        Text(
            text = renderInlineMarkdown(text, colors.accent, colors.surfaceElevated),
            style = MaterialTheme.typography.bodyMedium.copy(
                textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None
            ),
            color = if (checked) colors.textMuted else colors.textPrimary
        )
    }
}

private val CHECKLIST_MARKER = Regex("""[ \t]*- \[([ xX])\] """)
private val MARKDOWN_LINE_BREAK = Regex("\r\n|\n|\r")

internal fun toggleMarkdownChecklistItem(markdown: String, lineIndex: Int): String {
    if (lineIndex < 0) return markdown
    val start = if (lineIndex == 0) 0 else {
        MARKDOWN_LINE_BREAK.findAll(markdown).elementAtOrNull(lineIndex - 1)?.range?.endInclusive?.plus(1)
            ?: return markdown
    }
    val marker = CHECKLIST_MARKER.find(markdown, start)?.takeIf { it.range.first == start }?.groups?.get(1)
        ?: return markdown
    return markdown.replaceRange(marker.range, if (marker.value == " ") "x" else " ")
}

private fun renderInlineMarkdown(
    text: String,
    accentColor: Color,
    codeBg: Color
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                    append(text.substring(i + 2, end))
                    pop()
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    pushStyle(
                        SpanStyle(
                            fontFamily = ZeroMonoFontFamily,
                            color = accentColor,
                            background = codeBg
                        )
                    )
                    append(text.substring(i + 1, end))
                    pop()
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
