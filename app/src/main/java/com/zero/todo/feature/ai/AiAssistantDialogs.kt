package com.zero.todo.feature.ai

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zero.todo.R
import com.zero.todo.core.common.AiImageEncoder
import com.zero.todo.core.designsystem.ZeroCardShape
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.hairlineDividerBottom
import com.zero.todo.core.designsystem.rememberZeroHaptics
import com.zero.todo.data.ai.AiBreakdownDialogState
import com.zero.todo.data.ai.AiCaptureDialogState
import com.zero.todo.data.ai.AiCapturedTaskDraft
import com.zero.todo.data.ai.AiConfig
import com.zero.todo.data.ai.AiImagePayload
import com.zero.todo.data.ai.AiProvider
import com.zero.todo.data.ai.AiSubtaskDraft
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.TaskPriority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AiCaptureDialog(
    state: AiCaptureDialogState,
    aiConfig: AiConfig,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onAnalyze: (text: String, image: AiImagePayload?) -> Unit,
    onApplyTasks: (List<AiCapturedTaskDraft>) -> Unit
) {
    if (!state.isOpen) return

    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val haptics = rememberZeroHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var inputText by remember(state.initialText, state.isOpen) { mutableStateOf(state.initialText) }
    var attachedImagePayload by remember(state.isOpen) { mutableStateOf<AiImagePayload?>(null) }
    var attachedPreviewBitmap by remember(state.isOpen) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var encodingImage by remember(state.isOpen) { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val selectedTaskIndices = remember(state.results) {
        mutableStateListOf<Boolean>().apply {
            repeat(state.results.size) { add(true) }
        }
    }

    fun attachImageFromUri(uri: Uri) {
        encodingImage = true
        scope.launch {
            val payload = AiImageEncoder.encodeUriToJpegPayload(context, uri)
            val thumb = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                        BitmapFactory.decodeStream(stream, null, opts)
                    }
                }.getOrNull()
            }
            attachedImagePayload = payload
            attachedPreviewBitmap = thumb
            encodingImage = false
            if (payload != null && aiConfig.isConfigured && inputText.isBlank()) {
                onAnalyze(inputText, payload)
            }
        }
    }

    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val uri = pendingCameraUri
        if (saved && uri != null) {
            attachImageFromUri(uri)
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            attachImageFromUri(uri)
        }
    }

    fun launchCamera() {
        runCatching {
            val uri = AiImageEncoder.createTempCaptureUri(context)
            pendingCameraUri = uri
            takePhotoLauncher.launch(uri)
        }
    }

    LaunchedEffect(state.isOpen, state.autoLaunchCamera, aiConfig.isConfigured) {
        if (state.isOpen && state.autoLaunchCamera && aiConfig.isConfigured) {
            launchCamera()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .fillMaxWidth()
                .clip(ZeroCardShape)
                .background(colors.surfaceElevated)
                .hairlineBorder(shape = ZeroCardShape)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .hairlineDividerBottom()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.ai_capture_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary
                    )
                    if (aiConfig.isConfigured) {
                        val providerName = when (aiConfig.provider) {
                            AiProvider.GEMINI -> stringResource(R.string.ai_provider_gemini)
                            AiProvider.OPENAI -> stringResource(R.string.ai_provider_openai)
                        }
                        Text(
                            text = "$providerName · ${aiConfig.activeModel}",
                            style = tabular.dateCompact,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(ZeroSmallShape)
                                .background(colors.surface)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (!aiConfig.isConfigured) {
                AiNotConfiguredBody(
                    onOpenSettings = {
                        onDismiss()
                        onOpenSettings()
                    },
                    onDismiss = onDismiss
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Camera + Gallery + Thumbnail Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .height(38.dp)
                                .clip(ZeroSmallShape)
                                .background(colors.surface)
                                .hairlineBorder(shape = ZeroSmallShape)
                                .clickable {
                                    haptics.reorderTick()
                                    launchCamera()
                                }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoCamera,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.ai_capture_camera_btn),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textPrimary
                            )
                        }

                        Row(
                            modifier = Modifier
                                .height(38.dp)
                                .clip(ZeroSmallShape)
                                .background(colors.surface)
                                .hairlineBorder(shape = ZeroSmallShape)
                                .clickable {
                                    haptics.reorderTick()
                                    pickImageLauncher.launch("image/*")
                                }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Image,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.ai_capture_gallery_btn),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textPrimary
                            )
                        }

                        if (encodingImage) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = colors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    attachedPreviewBitmap?.let { bmp ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ZeroSmallShape)
                                .background(colors.surface)
                                .hairlineBorder(shape = ZeroSmallShape)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(ZeroSmallShape)
                            )
                            Text(
                                text = stringResource(R.string.ai_capture_image_attached),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    attachedImagePayload = null
                                    attachedPreviewBitmap = null
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = null,
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Input box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 76.dp, max = 140.dp)
                            .clip(ZeroSmallShape)
                            .background(colors.surface)
                            .hairlineBorder(shape = ZeroSmallShape)
                            .padding(12.dp)
                    ) {
                        if (inputText.isEmpty()) {
                            Text(
                                text = stringResource(R.string.ai_capture_input_placeholder),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted
                            )
                        }
                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                            cursorBrush = SolidColor(colors.accent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    val canAnalyze = !state.isAnalyzing && !encodingImage &&
                        (inputText.isNotBlank() || attachedImagePayload != null)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(ZeroSmallShape)
                            .background(if (canAnalyze) colors.accent else colors.surface)
                            .hairlineBorder(
                                shape = ZeroSmallShape,
                                color = if (canAnalyze) colors.accent else colors.hairlineBorder
                            )
                            .clickable(enabled = canAnalyze) {
                                haptics.heavyClick()
                                onAnalyze(inputText, attachedImagePayload)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.isAnalyzing) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = colors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.ai_analyzing),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.textSecondary
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (canAnalyze) colors.onAccent else colors.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.ai_capture_analyze_btn),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (canAnalyze) colors.onAccent else colors.textMuted
                                )
                            }
                        }
                    }

                    state.errorMessage?.let { err ->
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.priorityUrgent
                        )
                    }

                    if (state.results.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(state.results) { idx, draft ->
                                val checked = selectedTaskIndices.getOrElse(idx) { true }
                                AiCapturedTaskPreviewCard(
                                    draft = draft,
                                    checked = checked,
                                    onToggle = {
                                        if (idx < selectedTaskIndices.size) {
                                            selectedTaskIndices[idx] = !selectedTaskIndices[idx]
                                        }
                                    }
                                )
                            }
                        }

                        val chosen = state.results.filterIndexed { i, _ -> selectedTaskIndices.getOrElse(i) { false } }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(ZeroSmallShape)
                                .background(if (chosen.isNotEmpty()) colors.accent else colors.surface)
                                .clickable(enabled = chosen.isNotEmpty()) {
                                    haptics.confirm()
                                    onApplyTasks(chosen)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.ai_capture_apply_count, chosen.size),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (chosen.isNotEmpty()) colors.onAccent else colors.textMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiCapturedTaskPreviewCard(
    draft: AiCapturedTaskDraft,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ZeroSmallShape)
            .background(colors.surface)
            .hairlineBorder(
                shape = ZeroSmallShape,
                color = if (checked) colors.accent else colors.hairlineBorder
            )
            .clickable(onClick = onToggle)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AiCheckBox(checked = checked)
            Text(
                text = draft.title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (draft.priority != TaskPriority.NONE) {
                Text(
                    text = "P${draft.priority.value}",
                    style = tabular.priorityTag,
                    color = colors.accent
                )
            }
        }

        val metaParts = buildList {
            draft.folderPath?.let { add("/$it") }
            draft.dueDateIso?.let { add(it) }
            draft.estimatedMinutes?.let { add("${it}m") }
            draft.tags.forEach { add("#$it") }
        }
        if (metaParts.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .padding(start = 26.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                metaParts.forEach { chip ->
                    Text(
                        text = chip,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                        modifier = Modifier
                            .clip(ZeroSmallShape)
                            .background(colors.background)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        draft.firstStep?.let { step ->
            Text(
                text = "▸ $step",
                style = MaterialTheme.typography.labelSmall,
                color = colors.accent,
                modifier = Modifier.padding(start = 26.dp)
            )
        }

        if (draft.subtasks.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(start = 26.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                draft.subtasks.forEach { sub ->
                    val tagSuffix = if (sub.tags.isNotEmpty()) {
                        "  " + sub.tags.joinToString(" ") { "#$it" }
                    } else ""
                    Text(
                        text = "↳ ${sub.title}$tagSuffix",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun AiBreakdownDialog(
    state: AiBreakdownDialogState,
    aiConfig: AiConfig,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    onApply: (
        task: TaskItem,
        firstStep: String?,
        estimatedMinutes: Int?,
        subtasks: List<AiSubtaskDraft>,
        checklistItems: List<String>
    ) -> Unit
) {
    val task = state.task ?: return
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val haptics = rememberZeroHaptics()

    val draft = state.draft
    var applyFirstStep by remember(draft) { mutableStateOf(draft?.firstStep != null) }
    var applyEffort by remember(draft) { mutableStateOf(draft?.estimatedMinutes != null) }
    val selectedSubtasks = remember(draft) {
        mutableStateListOf<Boolean>().apply {
            repeat(draft?.subtasks?.size ?: 0) { add(true) }
        }
    }
    val selectedChecklist = remember(draft) {
        mutableStateListOf<Boolean>().apply {
            repeat(draft?.checklistItems?.size ?: 0) { add(true) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .fillMaxWidth()
                .clip(ZeroCardShape)
                .background(colors.surfaceElevated)
                .hairlineBorder(shape = ZeroCardShape)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .hairlineDividerBottom()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (!aiConfig.isConfigured) {
                AiNotConfiguredBody(
                    onOpenSettings = {
                        onDismiss()
                        onOpenSettings()
                    },
                    onDismiss = onDismiss
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when {
                        state.isAnalyzing -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 28.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = colors.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.ai_analyzing),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary
                                )
                            }
                        }

                        state.errorMessage != null -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = state.errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.priorityUrgent
                                )
                                Row(
                                    modifier = Modifier
                                        .clip(ZeroSmallShape)
                                        .background(colors.accentSubtle)
                                        .hairlineBorder(shape = ZeroSmallShape, color = colors.accent)
                                        .clickable(onClick = onRetry)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.ai_retry),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.accent
                                    )
                                }
                            }
                        }

                        draft != null -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!draft.firstStep.isNullOrBlank()) {
                                    item(key = "first_step") {
                                        AiToggleOptionRow(
                                            checked = applyFirstStep,
                                            label = stringResource(R.string.now_first_step_label),
                                            valueText = draft.firstStep,
                                            onClick = { applyFirstStep = !applyFirstStep }
                                        )
                                    }
                                }
                                if (draft.estimatedMinutes != null) {
                                    item(key = "effort") {
                                        AiToggleOptionRow(
                                            checked = applyEffort,
                                            label = stringResource(R.string.detail_effort),
                                            valueText = "${draft.estimatedMinutes}m",
                                            onClick = { applyEffort = !applyEffort }
                                        )
                                    }
                                }
                                if (draft.subtasks.isNotEmpty()) {
                                    item(key = "subtasks_hdr") {
                                        Text(
                                            text = stringResource(R.string.ai_breakdown_subtasks_label),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colors.textMuted,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                    itemsIndexed(draft.subtasks) { i, sub ->
                                        val checked = selectedSubtasks.getOrElse(i) { true }
                                        val tagNote = if (sub.tags.isNotEmpty()) {
                                            sub.tags.joinToString(" ") { "#$it" }
                                        } else null
                                        AiToggleOptionRow(
                                            checked = checked,
                                            label = tagNote,
                                            valueText = sub.title,
                                            onClick = {
                                                if (i < selectedSubtasks.size) {
                                                    selectedSubtasks[i] = !selectedSubtasks[i]
                                                }
                                            }
                                        )
                                    }
                                }
                                if (draft.checklistItems.isNotEmpty()) {
                                    item(key = "checklist_hdr") {
                                        Text(
                                            text = stringResource(R.string.ai_breakdown_checklist_label),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colors.textMuted,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                    itemsIndexed(draft.checklistItems) { i, line ->
                                        val checked = selectedChecklist.getOrElse(i) { true }
                                        AiToggleOptionRow(
                                            checked = checked,
                                            label = null,
                                            valueText = "- [ ] $line",
                                            onClick = {
                                                if (i < selectedChecklist.size) {
                                                    selectedChecklist[i] = !selectedChecklist[i]
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .clip(ZeroSmallShape)
                                        .hairlineBorder(shape = ZeroSmallShape)
                                        .clickable(onClick = onDismiss),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.action_cancel),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colors.textSecondary
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1.4f)
                                        .height(42.dp)
                                        .clip(ZeroSmallShape)
                                        .background(colors.accent)
                                        .clickable {
                                            haptics.confirm()
                                            onApply(
                                                task,
                                                if (applyFirstStep) draft.firstStep else null,
                                                if (applyEffort) draft.estimatedMinutes else null,
                                                draft.subtasks.filterIndexed { i, _ -> selectedSubtasks.getOrElse(i) { false } },
                                                draft.checklistItems.filterIndexed { i, _ -> selectedChecklist.getOrElse(i) { false } }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.ai_breakdown_apply_btn),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colors.onAccent
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiNotConfiguredBody(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ZeroTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.ai_not_configured_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(ZeroSmallShape)
                    .hairlineBorder(shape = ZeroSmallShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary
                )
            }
            Box(
                modifier = Modifier
                    .weight(1.4f)
                    .height(40.dp)
                    .clip(ZeroSmallShape)
                    .background(colors.accent)
                    .clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.ai_open_settings_btn),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onAccent
                )
            }
        }
    }
}

@Composable
private fun AiToggleOptionRow(
    checked: Boolean,
    label: String?,
    valueText: String,
    onClick: () -> Unit
) {
    val colors = ZeroTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ZeroSmallShape)
            .background(colors.surface)
            .hairlineBorder(
                shape = ZeroSmallShape,
                color = if (checked) colors.accent else colors.hairlineBorder
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AiCheckBox(checked = checked)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textPrimary
            )
            if (!label.isNullOrBlank()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted
                )
            }
        }
    }
}

@Composable
private fun AiCheckBox(checked: Boolean) {
    val colors = ZeroTheme.colors
    Box(
        modifier = Modifier
            .size(18.dp)
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
                modifier = Modifier.size(12.dp)
            )
        }
    }
}
