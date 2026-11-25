package com.zero.todo.feature.folder

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Inbox
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
import androidx.compose.ui.graphics.SolidColor
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
import com.zero.todo.domain.model.Folder
import com.zero.todo.domain.model.FolderNode

@Composable
fun FolderPickerDialog(
    folderTree: List<FolderNode>,
    currentFolderId: String?,
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
                            tint = if (selected) colors.accent else colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = node.folder.name,
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
