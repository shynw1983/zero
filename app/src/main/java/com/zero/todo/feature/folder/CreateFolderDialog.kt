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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.zero.todo.R
import com.zero.todo.core.designsystem.ZeroCardShape
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.domain.model.Folder

@Composable
fun CreateFolderDialog(
    parentFolder: Folder?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, parentId: String?) -> Unit
) {
    val colors = ZeroTheme.colors
    var folderName by rememberSaveable(parentFolder?.id) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun submit() {
        val trimmed = folderName.trim()
        if (trimmed.isNotEmpty()) {
            onConfirm(trimmed, parentFolder?.id)
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
                text = if (parentFolder == null) {
                    stringResource(R.string.dialog_create_folder_title)
                } else {
                    stringResource(R.string.dialog_create_subfolder_title, parentFolder.name)
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
                        color = if (folderName.isNotBlank()) colors.accent else colors.hairlineBorder
                    )
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                if (folderName.isEmpty()) {
                    Text(
                        text = stringResource(R.string.dialog_folder_name_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted
                    )
                }
                BasicTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
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
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
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
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                val canSubmit = folderName.isNotBlank()
                Box(
                    modifier = Modifier
                        .clip(ZeroSmallShape)
                        .background(if (canSubmit) colors.accent else colors.surface)
                        .hairlineBorder(
                            shape = ZeroSmallShape,
                            color = if (canSubmit) colors.accent else colors.hairlineBorder
                        )
                        .clickable(enabled = canSubmit, onClick = ::submit)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_create),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (canSubmit) colors.onAccent else colors.textMuted
                    )
                }
            }
        }
    }
}
