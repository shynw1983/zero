package com.zero.todo.feature.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.zero.todo.R
import com.zero.todo.core.designsystem.ZeroCardShape
import com.zero.todo.core.designsystem.ZeroDimens
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.parseFolderColorHex
import com.zero.todo.domain.model.Folder
import com.zero.todo.domain.model.FolderNode
import com.zero.todo.domain.model.ProjectFolderColors
import com.zero.todo.domain.model.TagItem
import com.zero.todo.domain.model.TagTreeBuilder

@Composable
fun FolderPickerDialog(
    folderTree: List<FolderNode>,
    currentFolderId: String?,
    folderColorHexById: Map<String, String> = emptyMap(),
    folderFullPathById: Map<String, String> = emptyMap(),
    onSelectFolder: (folderId: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ZeroTheme.colors
    val flatAllNodes = remember(folderTree) {
        buildList {
            fun walk(nodes: List<FolderNode>) {
                for (node in nodes) {
                    add(node)
                    walk(node.children)
                }
            }
            walk(folderTree)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ZeroCardShape)
                .background(colors.surfaceElevated)
                .hairlineBorder(shape = ZeroCardShape)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.dialog_move_to_folder_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item(key = "move_inbox") {
                    val selected = currentFolderId == null
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
                                onSelectFolder(null)
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Inbox,
                            contentDescription = null,
                            tint = if (selected) colors.accent else colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.nav_inbox),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) colors.accent else colors.textPrimary,
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

                items(items = flatAllNodes, key = { it.folder.id }) { node ->
                    val selected = node.folder.id == currentFolderId
                    val indent = ZeroDimens.TreeIndentPerLevel * node.depth.coerceAtMost(ZeroDimens.MaxVisibleTreeDepth)
                    val projectColor = parseFolderColorHex(folderColorHexById[node.folder.id])
                    val parentHint = folderFullPathById[node.folder.id]
                        ?.substringBeforeLast('/', "")
                        ?.takeIf { it.isNotBlank() && node.depth > 0 }
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
                                onSelectFolder(node.folder.id)
                                onDismiss()
                            }
                            .padding(start = 12.dp + indent, end = 12.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = when {
                                selected -> colors.accent
                                projectColor != null -> projectColor
                                else -> colors.textSecondary
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = node.folder.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected) colors.accent else colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (parentHint != null) {
                                Text(
                                    text = parentHint,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
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
                        .padding(horizontal = 14.dp, vertical = 8.dp)
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
fun RenameFolderDialog(
    folder: Folder,
    onDismiss: () -> Unit,
    onConfirm: (folderId: String, newName: String) -> Unit
) {
    val colors = ZeroTheme.colors
    var nameDraft by rememberSaveable(folder.id) { mutableStateOf(folder.name) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun submit() {
        val trimmed = nameDraft.trim()
        if (trimmed.isNotEmpty()) {
            onConfirm(folder.id, trimmed)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ZeroCardShape)
                .background(colors.surfaceElevated)
                .hairlineBorder(shape = ZeroCardShape)
                .padding(20.dp)
        ) {
            Text(
                text = stringResource(R.string.dialog_rename_folder_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ZeroSmallShape)
                    .background(colors.surface)
                    .hairlineBorder(
                        shape = ZeroSmallShape,
                        color = if (nameDraft.isNotBlank()) colors.accent else colors.hairlineBorder
                    )
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                BasicTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .clip(ZeroSmallShape)
                        .hairlineBorder(shape = ZeroSmallShape)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textSecondary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                val canSave = nameDraft.isNotBlank()
                Box(
                    modifier = Modifier
                        .clip(ZeroSmallShape)
                        .background(if (canSave) colors.accent else colors.surface)
                        .clickable(enabled = canSave, onClick = ::submit)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_save),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (canSave) colors.onAccent else colors.textMuted
                    )
                }
            }
        }
    }
}

/**
 * Anti-misclick confirmation dialog before deleting a folder.
 * Explains clearly that contained tasks are safely returned to Inbox.
 */
@Composable
fun DeleteFolderConfirmDialog(
    folder: Folder,
    onDismiss: () -> Unit,
    onConfirmDelete: (folderId: String) -> Unit
) {
    val colors = ZeroTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ZeroCardShape)
                .background(colors.surfaceElevated)
                .hairlineBorder(shape = ZeroCardShape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.dialog_delete_folder_title, folder.name),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary
            )
            Text(
                text = stringResource(R.string.dialog_delete_folder_message),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .clip(ZeroSmallShape)
                        .hairlineBorder(shape = ZeroSmallShape)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textSecondary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(ZeroSmallShape)
                        .background(colors.priorityUrgent)
                        .clickable {
                            onConfirmDelete(folder.id)
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onAccent
                    )
                }
            }
        }
    }
}

/**
 * Touch-friendly 50dp action menu for managing a Folder (replaces cramped 22dp row icons).
 */
@Composable
fun FolderActionMenuDialog(
    folder: Folder,
    inheritedColorHex: String? = null,
    rootProjectName: String? = null,
    onSelectColor: ((String?) -> Unit)? = null,
    onCreateSubfolder: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ZeroTheme.colors
    var activeColorHex by remember(folder.id, folder.colorHex) { mutableStateOf(folder.colorHex) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ZeroCardShape)
                .background(colors.surfaceElevated)
                .hairlineBorder(shape = ZeroCardShape)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            if (folder.parentId == null && onSelectColor != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ZeroSmallShape)
                        .background(colors.surface)
                        .hairlineBorder(shape = ZeroSmallShape)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.folder_color_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted
                    )
                    FolderColorSwatchRow(
                        selectedColorHex = activeColorHex,
                        onSelectColor = { hex ->
                            activeColorHex = hex
                            onSelectColor(hex)
                        }
                    )
                }
            } else if (folder.parentId != null && !inheritedColorHex.isNullOrBlank() && !rootProjectName.isNullOrBlank()) {
                val dotColor = parseFolderColorHex(inheritedColorHex)
                if (dotColor != null) {
                    Row(
                        modifier = Modifier.padding(bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Text(
                            text = stringResource(R.string.folder_color_inherited_hint, rootProjectName),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            TactileMenuRow(
                icon = Icons.Rounded.CreateNewFolder,
                label = stringResource(R.string.cd_create_subfolder),
                tint = colors.accent,
                onClick = {
                    onDismiss()
                    onCreateSubfolder()
                }
            )
            TactileMenuRow(
                icon = Icons.Rounded.Edit,
                label = stringResource(R.string.dialog_rename_folder_title),
                tint = colors.textPrimary,
                onClick = {
                    onDismiss()
                    onRename()
                }
            )
            TactileMenuRow(
                icon = Icons.Rounded.DeleteOutline,
                label = stringResource(R.string.cd_delete_folder),
                tint = colors.priorityUrgent,
                onClick = {
                    onDismiss()
                    onDelete()
                }
            )
        }
    }
}

@Composable
fun FolderColorSwatchRow(
    selectedColorHex: String?,
    onSelectColor: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val noneSelected = selectedColorHex.isNullOrBlank()
        Box(
            modifier = Modifier
                .height(30.dp)
                .clip(CircleShape)
                .background(if (noneSelected) colors.accentSubtle else colors.surfaceElevated)
                .hairlineBorder(
                    shape = CircleShape,
                    color = if (noneSelected) colors.accent else colors.hairlineBorder
                )
                .clickable { onSelectColor(null) }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.folder_color_none),
                style = MaterialTheme.typography.labelSmall,
                color = if (noneSelected) colors.accent else colors.textMuted
            )
        }
        ProjectFolderColors.presets.forEach { hex ->
            val swatchColor = parseFolderColorHex(hex) ?: return@forEach
            val selected = selectedColorHex.equals(hex, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (selected) swatchColor.copy(alpha = 0.20f) else Color.Transparent)
                    .hairlineBorder(
                        shape = CircleShape,
                        color = if (selected) swatchColor else colors.hairlineBorder
                    )
                    .clickable { onSelectColor(hex) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(swatchColor)
                )
            }
        }
    }
}

/**
 * Touch-friendly 50dp action menu for managing a tag.
 */
@Composable
fun TagActionMenuDialog(
    tag: TagItem,
    displayTitle: String = "#${tag.name}",
    onConfigurePlace: () -> Unit = {},
    onCreateSubtag: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ZeroTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ZeroCardShape)
                .background(colors.surfaceElevated)
                .hairlineBorder(shape = ZeroCardShape)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            TactileMenuRow(
                icon = Icons.Rounded.LocationOn,
                label = if (tag.isPlaceTag) {
                    stringResource(R.string.place_tag_menu_active, tag.places.count { it.isEnabled })
                } else {
                    stringResource(R.string.place_tag_menu_action)
                },
                tint = if (tag.isPlaceTag) colors.accent else colors.textPrimary,
                onClick = {
                    onDismiss()
                    onConfigurePlace()
                }
            )
            TactileMenuRow(
                icon = Icons.Rounded.Add,
                label = stringResource(R.string.action_create_subtag),
                tint = colors.textPrimary,
                onClick = {
                    onDismiss()
                    onCreateSubtag()
                }
            )
            TactileMenuRow(
                icon = Icons.Rounded.Edit,
                label = stringResource(R.string.dialog_rename_tag_title),
                tint = colors.textPrimary,
                onClick = {
                    onDismiss()
                    onRename()
                }
            )
            TactileMenuRow(
                icon = Icons.Rounded.DeleteOutline,
                label = stringResource(R.string.action_delete_tag),
                tint = colors.priorityUrgent,
                onClick = {
                    onDismiss()
                    onDelete()
                }
            )
        }
    }
}

@Composable
internal fun TaskContextMenuDialog(
    title: String,
    onOpenProject: (() -> Unit)?,
    onOpenMainTask: (() -> Unit)?,
    onSkipOccurrence: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val colors = ZeroTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ZeroCardShape)
                .background(colors.surfaceElevated)
                .hairlineBorder(shape = ZeroCardShape)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            onOpenProject?.let {
                TactileMenuRow(Icons.AutoMirrored.Rounded.DriveFileMove, stringResource(R.string.task_open_project), colors.textPrimary, it)
            }
            onOpenMainTask?.let {
                TactileMenuRow(Icons.Rounded.ArrowUpward, stringResource(R.string.task_open_main_task), colors.textPrimary, it)
            }
            onSkipOccurrence?.let {
                TactileMenuRow(Icons.Rounded.SkipNext, stringResource(R.string.task_skip_occurrence), colors.textPrimary, it)
            }
        }
    }
}

@Composable
fun CreateTagDialog(
    parentTag: TagItem?,
    parentPath: String? = parentTag?.name,
    onDismiss: () -> Unit,
    onConfirm: (name: String, parentId: String?) -> Unit
) {
    val colors = ZeroTheme.colors
    var nameDraft by rememberSaveable(parentTag?.id) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun submit() {
        val segments = TagTreeBuilder.parseTagPathSegments(nameDraft)
        if (segments.isNotEmpty()) {
            onConfirm(segments.joinToString("/"), parentTag?.id)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ZeroCardShape)
                .background(colors.surfaceElevated)
                .hairlineBorder(shape = ZeroCardShape)
                .padding(20.dp)
        ) {
            Text(
                text = if (parentTag != null) {
                    stringResource(R.string.dialog_create_subtag_title, parentPath ?: parentTag.name)
                } else {
                    stringResource(R.string.dialog_create_tag_title)
                },
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ZeroSmallShape)
                    .background(colors.surface)
                    .hairlineBorder(
                        shape = ZeroSmallShape,
                        color = if (nameDraft.isNotBlank()) colors.accent else colors.hairlineBorder
                    )
                    .padding(horizontal = 12.dp, vertical = 14.dp)
            ) {
                if (nameDraft.isEmpty()) {
                    Text(
                        text = stringResource(R.string.dialog_create_tag_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted
                    )
                }
                BasicTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(ZeroSmallShape)
                        .hairlineBorder(shape = ZeroSmallShape)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textSecondary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                val canSave = TagTreeBuilder.parseTagPathSegments(nameDraft).isNotEmpty()
                Box(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(ZeroSmallShape)
                        .background(if (canSave) colors.accent else colors.surface)
                        .clickable(enabled = canSave, onClick = ::submit)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.action_create),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (canSave) colors.onAccent else colors.textMuted
                    )
                }
            }
        }
    }
}

@Composable
fun RenameTagDialog(
    tag: TagItem,
    onDismiss: () -> Unit,
    onConfirm: (tagId: String, newName: String) -> Unit
) {
    val colors = ZeroTheme.colors
    var nameDraft by rememberSaveable(tag.id) { mutableStateOf(tag.name) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun submit() {
        val trimmed = nameDraft.trim().removePrefix("#")
        if (trimmed.isNotEmpty()) onConfirm(tag.id, trimmed)
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ZeroCardShape)
                .background(colors.surfaceElevated)
                .hairlineBorder(shape = ZeroCardShape)
                .padding(20.dp)
        ) {
            Text(
                text = stringResource(R.string.dialog_rename_tag_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ZeroSmallShape)
                    .background(colors.surface)
                    .hairlineBorder(
                        shape = ZeroSmallShape,
                        color = if (nameDraft.isNotBlank()) colors.accent else colors.hairlineBorder
                    )
                    .padding(horizontal = 12.dp, vertical = 14.dp)
            ) {
                BasicTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(ZeroSmallShape)
                        .hairlineBorder(shape = ZeroSmallShape)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textSecondary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                val canSave = nameDraft.trim().removePrefix("#").isNotEmpty()
                Box(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(ZeroSmallShape)
                        .background(if (canSave) colors.accent else colors.surface)
                        .clickable(enabled = canSave, onClick = ::submit)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.action_save),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (canSave) colors.onAccent else colors.textMuted
                    )
                }
            }
        }
    }
}

@Composable
fun DeleteTagConfirmDialog(
    tag: TagItem,
    onDismiss: () -> Unit,
    onConfirmDelete: (tagId: String) -> Unit
) {
    val colors = ZeroTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ZeroCardShape)
                .background(colors.surfaceElevated)
                .hairlineBorder(shape = ZeroCardShape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.dialog_delete_tag_title, tag.name),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary
            )
            Text(
                text = stringResource(R.string.dialog_delete_tag_message),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(ZeroSmallShape)
                        .hairlineBorder(shape = ZeroSmallShape)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textSecondary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(ZeroSmallShape)
                        .background(colors.priorityUrgent)
                        .clickable {
                            onConfirmDelete(tag.id)
                            onDismiss()
                        }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onAccent
                    )
                }
            }
        }
    }
}

@Composable
private fun TactileMenuRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    val colors = ZeroTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clip(ZeroSmallShape)
            .background(colors.surface)
            .hairlineBorder(shape = ZeroSmallShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (tint == colors.priorityUrgent) colors.priorityUrgent else colors.textPrimary
        )
    }
}
