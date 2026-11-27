package com.zero.todo.feature.ai

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.zero.todo.core.common.VoiceAutoStopDecision
import com.zero.todo.core.common.VoiceAutoStopDetector
import com.zero.todo.core.common.ZeroAudioRecorder
import com.zero.todo.core.designsystem.ZeroCardShape
import com.zero.todo.core.designsystem.ZeroPriorityBadge
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.hairlineDividerBottom
import com.zero.todo.core.designsystem.rememberZeroHaptics
import com.zero.todo.data.ai.AiAudioPayload
import com.zero.todo.data.ai.AiBreakdownDialogState
import com.zero.todo.data.ai.AiCaptureDialogState
import com.zero.todo.data.ai.AiCapturedTaskDraft
import com.zero.todo.data.ai.AiConfig
import com.zero.todo.data.ai.AiImagePayload
import com.zero.todo.data.ai.AiProvider
import com.zero.todo.data.ai.AiSubtaskDraft
import com.zero.todo.data.ai.ZeroAiJsonParser
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.TaskPriority
import com.zero.todo.feature.calendar.ZeroMiniCalendarDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun nextPreviewPriority(current: TaskPriority): TaskPriority = when (current) {
    TaskPriority.NONE -> TaskPriority.MEDIUM
    TaskPriority.MEDIUM -> TaskPriority.HIGH
    TaskPriority.HIGH -> TaskPriority.URGENT
    TaskPriority.URGENT -> TaskPriority.NONE
}

@Composable
fun AiCaptureDialog(
    state: AiCaptureDialogState,
    aiConfig: AiConfig,
    availableFolderPaths: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onAnalyze: (text: String, image: AiImagePayload?, audio: AiAudioPayload?) -> Unit,
    onApplyTasks: (List<AiCapturedTaskDraft>) -> Unit
) {
    if (!state.isOpen) return

    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val haptics = rememberZeroHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permissionDeniedText = stringResource(R.string.ai_voice_permission_denied)
    val recordingFailedText = stringResource(R.string.ai_voice_recording_failed)

    var inputText by remember(state.sessionId, state.isOpen) { mutableStateOf(state.initialText) }
    var attachedImagePayload by remember(state.sessionId, state.isOpen) { mutableStateOf<AiImagePayload?>(null) }
    var attachedPreviewBitmap by remember(state.sessionId, state.isOpen) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var encodingImage by remember(state.sessionId, state.isOpen) { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val audioRecorder = remember { ZeroAudioRecorder() }
    var isRecording by remember(state.sessionId, state.isOpen) { mutableStateOf(false) }
    var recordingSeconds by remember(state.sessionId, state.isOpen) { mutableIntStateOf(0) }
    var voiceLevel by remember(state.sessionId, state.isOpen) { mutableFloatStateOf(0f) }
    var voiceError by remember(state.sessionId, state.isOpen) { mutableStateOf<String?>(null) }

    DisposableEffect(state.sessionId, state.isOpen) {
        onDispose {
            audioRecorder.cancel()
        }
    }

    fun finishRecordingAndAnalyze() {
        if (!isRecording) return
        isRecording = false
        voiceLevel = 0f
        val audioPayload = audioRecorder.stopAndReadPayload()
        if (audioPayload == null) {
            voiceError = recordingFailedText
        } else if (aiConfig.isConfigured) {
            haptics.confirm()
            onAnalyze(inputText, attachedImagePayload, audioPayload)
        }
    }

    LaunchedEffect(isRecording) {
        if (!isRecording) {
            recordingSeconds = 0
            voiceLevel = 0f
            return@LaunchedEffect
        }
        recordingSeconds = 0
        voiceLevel = 0f
        val detector = VoiceAutoStopDetector()
        var elapsed = 0L
        while (isRecording) {
            delay(100L)
            if (!isRecording) break
            elapsed += 100L
            recordingSeconds = (elapsed / 1_000L).toInt()
            val tick = detector.onSample(audioRecorder.currentAmplitude(), 100L)
            voiceLevel = tick.normalizedLevel
            when (tick.decision) {
                VoiceAutoStopDecision.AUTO_STOP_AND_ANALYZE -> {
                    finishRecordingAndAnalyze()
                    break
                }
                VoiceAutoStopDecision.AUTO_CANCEL_NO_SPEECH -> {
                    audioRecorder.cancel()
                    isRecording = false
                    voiceLevel = 0f
                    voiceError = recordingFailedText
                    break
                }
                VoiceAutoStopDecision.CONTINUE -> Unit
            }
        }
    }

    val draftEdits = remember(state.results) {
        mutableStateListOf<AiCapturedTaskDraft>().apply {
            addAll(state.results)
        }
    }
    val selectedTaskIndices = remember(state.results) {
        mutableStateListOf<Boolean>().apply {
            repeat(state.results.size) { add(true) }
        }
    }
    var editingFolderIndex by remember(state.results) { mutableStateOf<Int?>(null) }
    var editingDateIndex by remember(state.results) { mutableStateOf<Int?>(null) }

    fun attachImageFromUri(uri: Uri, autoAnalyzeText: String? = null) {
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
            if (payload != null && aiConfig.isConfigured && (autoAnalyzeText != null || inputText.isBlank())) {
                onAnalyze(autoAnalyzeText ?: inputText, payload, null)
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

    fun startRecordingInternal() {
        voiceError = null
        if (audioRecorder.start(context)) {
            isRecording = true
        } else {
            isRecording = false
            voiceError = recordingFailedText
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startRecordingInternal()
        } else {
            voiceError = permissionDeniedText
        }
    }

    fun launchCamera() {
        runCatching {
            val uri = AiImageEncoder.createTempCaptureUri(context)
            pendingCameraUri = uri
            takePhotoLauncher.launch(uri)
        }
    }

    fun toggleVoiceRecording() {
        if (isRecording) {
            finishRecordingAndAnalyze()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startRecordingInternal()
        } else {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(state.sessionId, state.isOpen, aiConfig.isConfigured) {
        if (!state.isOpen || !aiConfig.isConfigured) return@LaunchedEffect
        val sharedImg = state.initialImageUri
        when {
            !sharedImg.isNullOrBlank() -> {
                val parsedUri = runCatching { Uri.parse(sharedImg) }.getOrNull()
                if (parsedUri != null) {
                    attachImageFromUri(
                        uri = parsedUri,
                        autoAnalyzeText = if (state.autoAnalyzeOnOpen) state.initialText else null
                    )
                }
            }
            state.autoStartVoice -> {
                toggleVoiceRecording()
            }
            state.autoAnalyzeOnOpen && state.initialText.isNotBlank() -> {
                onAnalyze(state.initialText, null, null)
            }
            state.autoLaunchCamera -> {
                launchCamera()
            }
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
                    // Camera + Gallery + Voice (equal width so labels never clip on compact screens)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(ZeroSmallShape)
                                .background(colors.surface)
                                .hairlineBorder(shape = ZeroSmallShape)
                                .clickable {
                                    haptics.reorderTick()
                                    launchCamera()
                                }
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoCamera,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = stringResource(R.string.ai_capture_camera_btn),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(ZeroSmallShape)
                                .background(colors.surface)
                                .hairlineBorder(shape = ZeroSmallShape)
                                .clickable {
                                    haptics.reorderTick()
                                    pickImageLauncher.launch("image/*")
                                }
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Image,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = stringResource(R.string.ai_capture_gallery_btn),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(ZeroSmallShape)
                                .background(if (isRecording) colors.priorityUrgent.copy(alpha = 0.16f) else colors.surface)
                                .hairlineBorder(
                                    shape = ZeroSmallShape,
                                    color = if (isRecording) colors.priorityUrgent else colors.hairlineBorder
                                )
                                .clickable {
                                    haptics.reorderTick()
                                    toggleVoiceRecording()
                                }
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                contentDescription = null,
                                tint = if (isRecording) colors.priorityUrgent else colors.accent,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = stringResource(
                                    if (isRecording) R.string.ai_voice_stop_and_analyze else R.string.ai_capture_voice_btn
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isRecording) colors.priorityUrgent else colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (encodingImage) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = colors.accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.ai_capture_image_attached),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted
                            )
                        }
                    }

                    if (isRecording) {
                        val mins = recordingSeconds / 60
                        val secs = recordingSeconds % 60
                        val timerLabel = "%02d:%02d".format(mins, secs)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ZeroSmallShape)
                                .background(colors.priorityUrgent.copy(alpha = 0.10f))
                                .hairlineBorder(
                                    shape = ZeroSmallShape,
                                    color = colors.priorityUrgent.copy(alpha = 0.55f)
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.height(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                val weights = listOf(0.55f, 1.0f, 0.7f)
                                weights.forEach { w ->
                                    val barHeight = (5f + (voiceLevel * w * 11f)).dp
                                    Box(
                                        modifier = Modifier
                                            .width(2.5.dp)
                                            .height(barHeight)
                                            .clip(ZeroSmallShape)
                                            .background(colors.priorityUrgent)
                                    )
                                }
                            }
                            Text(
                                text = timerLabel,
                                style = tabular.dateCompact,
                                color = colors.priorityUrgent
                            )
                            Text(
                                text = stringResource(R.string.ai_voice_recording_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(ZeroSmallShape)
                                    .background(colors.accent)
                                    .clickable {
                                        haptics.confirm()
                                        toggleVoiceRecording()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.ai_voice_stop_and_analyze),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onAccent
                                )
                            }
                            IconButton(
                                onClick = {
                                    audioRecorder.cancel()
                                    isRecording = false
                                    voiceError = null
                                },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.action_cancel),
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    voiceError?.let { vErr ->
                        Text(
                            text = vErr,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.priorityUrgent
                        )
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

                    val canAnalyze = !state.isAnalyzing && !encodingImage && !isRecording &&
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
                                onAnalyze(inputText, attachedImagePayload, null)
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

                    if (draftEdits.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(draftEdits) { idx, draft ->
                                val checked = selectedTaskIndices.getOrElse(idx) { true }
                                AiCapturedTaskPreviewCard(
                                    draft = draft,
                                    checked = checked,
                                    onToggle = {
                                        if (idx < selectedTaskIndices.size) {
                                            selectedTaskIndices[idx] = !selectedTaskIndices[idx]
                                        }
                                    },
                                    onEditFolder = {
                                        haptics.reorderTick()
                                        editingFolderIndex = idx
                                    },
                                    onEditDate = {
                                        haptics.reorderTick()
                                        editingDateIndex = idx
                                    },
                                    onCyclePriority = {
                                        haptics.reorderTick()
                                        if (idx < draftEdits.size) {
                                            draftEdits[idx] = draftEdits[idx].copy(
                                                priority = nextPreviewPriority(draftEdits[idx].priority)
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        val chosen = draftEdits.filterIndexed { i, _ -> selectedTaskIndices.getOrElse(i) { false } }
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

    val folderTargetIdx = editingFolderIndex
    if (folderTargetIdx != null && folderTargetIdx in draftEdits.indices) {
        AiDraftFolderPickerDialog(
            currentFolderPath = draftEdits[folderTargetIdx].folderPath,
            availableFolderPaths = availableFolderPaths,
            onSelectFolder = { selectedPath ->
                draftEdits[folderTargetIdx] = draftEdits[folderTargetIdx].copy(folderPath = selectedPath)
                editingFolderIndex = null
            },
            onDismiss = { editingFolderIndex = null }
        )
    }

    val dateTargetIdx = editingDateIndex
    if (dateTargetIdx != null && dateTargetIdx in draftEdits.indices) {
        val currentIso = draftEdits[dateTargetIdx].dueDateIso
        val parsedLocalDate = remember(currentIso) {
            ZeroAiJsonParser.parseIsoDueDate(currentIso, ZoneId.systemDefault())?.let { (millis, _) ->
                Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
            }
        }
        ZeroMiniCalendarDialog(
            selectedDate = parsedLocalDate,
            onSelectDate = { picked: LocalDate ->
                draftEdits[dateTargetIdx] = draftEdits[dateTargetIdx].copy(
                    dueDateIso = picked.format(DateTimeFormatter.ISO_LOCAL_DATE)
                )
            },
            onClearDate = {
                draftEdits[dateTargetIdx] = draftEdits[dateTargetIdx].copy(dueDateIso = null)
            },
            onDismiss = { editingDateIndex = null }
        )
    }
}

@Composable
private fun AiDraftFolderPickerDialog(
    currentFolderPath: String?,
    availableFolderPaths: List<String>,
    onSelectFolder: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ZeroTheme.colors
    val haptics = rememberZeroHaptics()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ZeroCardShape)
                .background(colors.surfaceElevated)
                .hairlineBorder(shape = ZeroCardShape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.ai_capture_pick_folder_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    val isInbox = currentFolderPath == null
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ZeroSmallShape)
                            .background(if (isInbox) colors.accentSubtle else colors.surface)
                            .hairlineBorder(
                                shape = ZeroSmallShape,
                                color = if (isInbox) colors.accent else colors.hairlineBorder
                            )
                            .clickable {
                                haptics.reorderTick()
                                onSelectFolder(null)
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.nav_inbox),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isInbox) colors.accent else colors.textPrimary
                        )
                        if (isInbox) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                itemsIndexed(availableFolderPaths) { _, path ->
                    val selected = currentFolderPath.equals(path, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ZeroSmallShape)
                            .background(if (selected) colors.accentSubtle else colors.surface)
                            .hairlineBorder(
                                shape = ZeroSmallShape,
                                color = if (selected) colors.accent else colors.hairlineBorder
                            )
                            .clickable {
                                haptics.reorderTick()
                                onSelectFolder(path)
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = if (selected) colors.accent else colors.textMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "/$path",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) colors.accent else colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .clip(ZeroSmallShape)
                        .hairlineBorder(shape = ZeroSmallShape)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun AiCapturedTaskPreviewCard(
    draft: AiCapturedTaskDraft,
    checked: Boolean,
    onToggle: () -> Unit,
    onEditFolder: () -> Unit,
    onEditDate: () -> Unit,
    onCyclePriority: () -> Unit
) {
    val colors = ZeroTheme.colors
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
            val pColor = when (draft.priority) {
                TaskPriority.URGENT -> colors.priorityUrgent
                TaskPriority.HIGH -> colors.priorityHigh
                TaskPriority.MEDIUM -> colors.priorityMedium
                TaskPriority.NONE -> colors.textMuted
            }
            Box(
                modifier = Modifier
                    .clip(ZeroSmallShape)
                    .clickable(onClick = onCyclePriority)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (draft.priority != TaskPriority.NONE) {
                    ZeroPriorityBadge(
                        priority = draft.priority,
                        color = pColor
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Flag,
                        contentDescription = null,
                        tint = colors.textMuted.copy(alpha = 0.45f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        val inboxLabel = stringResource(R.string.nav_inbox)
        Row(
            modifier = Modifier
                .padding(start = 26.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Editable Folder / Inbox Chip
            Row(
                modifier = Modifier
                    .clip(ZeroSmallShape)
                    .background(colors.background)
                    .hairlineBorder(
                        shape = ZeroSmallShape,
                        color = if (draft.folderPath != null) colors.accent.copy(alpha = 0.45f) else colors.hairlineBorder
                    )
                    .clickable(onClick = onEditFolder)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = draft.folderPath?.let { "/$it" } ?: inboxLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (draft.folderPath != null) colors.textPrimary else colors.textSecondary
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(12.dp)
                )
            }

            // Editable Date Chip
            Text(
                text = draft.dueDateIso ?: stringResource(R.string.ai_capture_add_date),
                style = MaterialTheme.typography.labelSmall,
                color = if (draft.dueDateIso != null) colors.accent else colors.textMuted,
                modifier = Modifier
                    .clip(ZeroSmallShape)
                    .background(colors.background)
                    .hairlineBorder(
                        shape = ZeroSmallShape,
                        color = if (draft.dueDateIso != null) colors.accent.copy(alpha = 0.45f) else colors.hairlineBorder
                    )
                    .clickable(onClick = onEditDate)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )

            draft.estimatedMinutes?.let { mins ->
                Text(
                    text = "${mins}m",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                    modifier = Modifier
                        .clip(ZeroSmallShape)
                        .background(colors.background)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            draft.tags.forEach { tag ->
                Text(
                    text = "#$tag",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                    modifier = Modifier
                        .clip(ZeroSmallShape)
                        .background(colors.background)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
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
