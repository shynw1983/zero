package com.zero.todo.feature.tasklist

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.zero.todo.R
import com.zero.todo.core.common.ZeroDateFormatter
import com.zero.todo.core.designsystem.ZeroCapsuleShape
import com.zero.todo.core.designsystem.ZeroCardShape
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.rememberZeroHaptics
import com.zero.todo.core.nlp.ParsedQuickInput
import com.zero.todo.core.nlp.QuickInputTokenType
import com.zero.todo.domain.model.TaskPriority

data class FolderPathSuggestion(
    val folderId: String,
    val fullPath: String
)

/**
 * ZERO's Anti-Procrastination Command-First Quick Input Capsule.
 * - Real-time inline token highlighting via [QuickInputVisualTransformation]
 * - Live minimalist preview capsules below the field (Date/Time, /Folder, !1-!4, #Tag)
 * - Autocomplete dropdown when typing `/...`
 * - Continuous Entry Mode (Enter/Done saves, clears field, keeps keyboard open)
 * - Long-press voice capture entry launching Android SpeechRecognizer
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickInputCapsule(
    text: String,
    parsed: ParsedQuickInput,
    folderSuggestions: List<FolderPathSuggestion>,
    continuousEntryEnabled: Boolean,
    focusRequester: FocusRequester,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSelectFolderSuggestion: (FolderPathSuggestion) -> Unit,
    onToggleContinuousEntry: () -> Unit,
    onVoiceInputRecognized: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val haptics = rememberZeroHaptics()
    val context = LocalContext.current
    val activeLocale = LocalConfiguration.current.locales[0]
    val focusManager = LocalFocusManager.current
    val voicePromptText = stringResource(R.string.quick_input_voice_prompt)
    val voiceUnavailableText = stringResource(R.string.quick_input_voice_unavailable)

    var isFocused by remember { mutableStateOf(false) }
    var voiceStatusNotice by remember { mutableStateOf<String?>(null) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim()
            if (!spoken.isNullOrEmpty()) {
                voiceStatusNotice = null
                haptics.confirm()
                onVoiceInputRecognized(spoken)
            }
        }
    }

    fun launchVoiceInput() {
        haptics.heavyClick()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, activeLocale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, voicePromptText)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            voiceStatusNotice = null
            speechLauncher.launch(intent)
        } else {
            voiceStatusNotice = voiceUnavailableText
        }
    }

    fun performSubmit() {
        if (text.isBlank()) return
        haptics.confirm()
        onSubmit()
        if (continuousEntryEnabled) {
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }

    val tokenVisualTransformation = remember(parsed, colors) {
        QuickInputVisualTransformation(
            parsed = parsed,
            dateColor = colors.accent,
            folderColor = colors.textPrimary,
            priorityColor = colors.accent,
            tagColor = colors.textSecondary,
            subtleBg = colors.accentSubtle
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Folder path autocomplete strip when typing `/...`
        AnimatedVisibility(visible = parsed.activeFolderQuery != null && folderSuggestions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ZeroCardShape)
                    .background(colors.surfaceElevated)
                    .hairlineBorder(shape = ZeroCardShape)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.quick_input_route),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted
                )
                folderSuggestions.forEach { suggestion ->
                    Row(
                        modifier = Modifier
                            .clip(ZeroSmallShape)
                            .background(colors.surface)
                            .hairlineBorder(shape = ZeroSmallShape)
                            .clickable {
                                haptics.reorderTick()
                                onSelectFolderSuggestion(suggestion)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "/${suggestion.fullPath}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        }

        // Main Capsule Container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ZeroCapsuleShape)
                .background(colors.surfaceElevated)
                .hairlineBorder(
                    shape = ZeroCapsuleShape,
                    color = if (isFocused) colors.accent else colors.hairlineBorder
                )
                .combinedClickable(
                    onClick = { focusRequester.requestFocus() },
                    onLongClick = { launchVoiceInput() }
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Continuous Entry Mode indicator toggle
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(ZeroSmallShape)
                        .background(if (continuousEntryEnabled) colors.accentSubtle else colors.surface)
                        .hairlineBorder(
                            shape = ZeroSmallShape,
                            color = if (continuousEntryEnabled) colors.accent else colors.hairlineBorder
                        )
                        .clickable {
                            haptics.reorderTick()
                            onToggleContinuousEntry()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AllInclusive,
                        contentDescription = stringResource(
                            if (continuousEntryEnabled) R.string.cd_continuous_on else R.string.cd_continuous_off
                        ),
                        tint = if (continuousEntryEnabled) colors.accent else colors.textMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Quick Input Field
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp)
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.quick_input_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                            maxLines = 1
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = {
                            voiceStatusNotice = null
                            onTextChange(it)
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                        cursorBrush = SolidColor(colors.accent),
                        visualTransformation = tokenVisualTransformation,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { performSubmit() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { isFocused = it.isFocused }
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Voice-to-Task button (tap or long-press) & Send button
                if (text.isBlank()) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(ZeroCapsuleShape)
                            .background(colors.surface)
                            .hairlineBorder(shape = ZeroCapsuleShape)
                            .combinedClickable(
                                onClick = { launchVoiceInput() },
                                onLongClick = { launchVoiceInput() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = stringResource(R.string.cd_voice_capture),
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(ZeroCapsuleShape)
                            .background(colors.accent)
                            .clickable { performSubmit() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = stringResource(R.string.cd_save_task),
                            tint = colors.onAccent,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            // Live NLP Parsed Preview Capsules Row
            AnimatedVisibility(visible = parsed.hasStructuredTokens || voiceStatusNotice != null) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (voiceStatusNotice != null) {
                        Text(
                            text = voiceStatusNotice!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (parsed.dueDateMillis != null) {
                                ParsedPreviewChip(
                                    icon = Icons.Rounded.Schedule,
                                    label = ZeroDateFormatter.formatDueDate(
                                        dueDateMillis = parsed.dueDateMillis,
                                        isAllDay = parsed.isAllDay,
                                        locale = activeLocale
                                    ),
                                    accent = colors.accent,
                                    useTabularFont = true
                                )
                            }

                            if (parsed.folderPath != null) {
                                ParsedPreviewChip(
                                    icon = Icons.Rounded.Folder,
                                    label = "/${parsed.folderPath.joinToString("/")}",
                                    accent = colors.textPrimary,
                                    useTabularFont = false
                                )
                            }

                            if (parsed.priority != TaskPriority.NONE) {
                                val pColor = when (parsed.priority) {
                                    TaskPriority.URGENT -> colors.priorityUrgent
                                    TaskPriority.HIGH -> colors.priorityHigh
                                    TaskPriority.MEDIUM -> colors.priorityMedium
                                    TaskPriority.NONE -> colors.textMuted
                                }
                                ParsedPreviewChip(
                                    icon = Icons.Rounded.Flag,
                                    label = "P${parsed.priority.value}",
                                    accent = pColor,
                                    useTabularFont = true
                                )
                            }

                            parsed.tags.forEach { tag ->
                                ParsedPreviewChip(
                                    icon = Icons.Rounded.Tag,
                                    label = tag,
                                    accent = colors.textSecondary,
                                    useTabularFont = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParsedPreviewChip(
    icon: ImageVector,
    label: String,
    accent: Color,
    useTabularFont: Boolean
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography

    Row(
        modifier = Modifier
            .clip(ZeroSmallShape)
            .background(colors.surface)
            .hairlineBorder(shape = ZeroSmallShape, color = accent.copy(alpha = 0.55f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = label,
            style = if (useTabularFont) tabular.dateCompact else MaterialTheme.typography.labelSmall,
            color = accent
        )
    }
}

private class QuickInputVisualTransformation(
    private val parsed: ParsedQuickInput,
    private val dateColor: Color,
    private val folderColor: Color,
    private val priorityColor: Color,
    private val tagColor: Color,
    private val subtleBg: Color
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (parsed.tokens.isEmpty() || parsed.rawInput != text.text) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val highlighted = buildAnnotatedString {
            append(text.text)
            for (token in parsed.tokens) {
                val start = token.range.first.coerceIn(0, text.length)
                val endExclusive = (token.range.last + 1).coerceIn(start, text.length)
                if (start < endExclusive) {
                    val style = when (token.type) {
                        QuickInputTokenType.DATE_TIME -> SpanStyle(
                            color = dateColor,
                            fontWeight = FontWeight.Medium
                        )
                        QuickInputTokenType.FOLDER -> SpanStyle(
                            color = folderColor,
                            background = subtleBg,
                            fontWeight = FontWeight.Medium
                        )
                        QuickInputTokenType.PRIORITY -> SpanStyle(
                            color = priorityColor,
                            fontWeight = FontWeight.Medium
                        )
                        QuickInputTokenType.TAG -> SpanStyle(
                            color = tagColor,
                            background = subtleBg
                        )
                    }
                    addStyle(style, start, endExclusive)
                }
            }
        }
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}
