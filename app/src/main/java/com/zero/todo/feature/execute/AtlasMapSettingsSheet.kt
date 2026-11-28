package com.zero.todo.feature.execute

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.zero.todo.R
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.domain.model.AtlasMapProvider
import com.zero.todo.domain.model.AtlasMapSettings
import com.zero.todo.domain.model.AtlasMapStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AtlasMapSettingsSheet(
    settings: AtlasMapSettings,
    onSave: (AtlasMapSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var provider by remember { mutableStateOf(settings.provider) }
    var style by remember { mutableStateOf(settings.mapboxStyle) }
    var token by remember { mutableStateOf(settings.mapboxToken) }
    val selectingMapbox = provider == AtlasMapProvider.MAPBOX
    val validToken = AtlasMapSettings.acceptsMapboxToken(token)
    val colors = ZeroTheme.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceElevated,
        contentColor = colors.textPrimary
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = ExecuteMaxContentWidth)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.atlas_map_settings), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) {
                AtlasMapOption(
                    stringResource(R.string.atlas_map_google),
                    selected = !selectingMapbox,
                    onSelect = { provider = AtlasMapProvider.GOOGLE_MAPS }
                )
                AtlasMapOption(
                    stringResource(R.string.atlas_map_mapbox),
                    selected = selectingMapbox,
                    onSelect = { provider = AtlasMapProvider.MAPBOX }
                )
            }
            if (selectingMapbox) {
                Text(stringResource(R.string.atlas_map_style), style = MaterialTheme.typography.titleSmall)
                Column(Modifier.selectableGroup()) {
                    AtlasMapOption(
                        stringResource(R.string.atlas_map_standard),
                        selected = style == AtlasMapStyle.STANDARD,
                        onSelect = { style = AtlasMapStyle.STANDARD }
                    )
                    AtlasMapOption(
                        stringResource(R.string.atlas_map_satellite),
                        selected = style == AtlasMapStyle.SATELLITE,
                        onSelect = { style = AtlasMapStyle.SATELLITE }
                    )
                }
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(stringResource(R.string.atlas_mapbox_public_token)) },
                    supportingText = { Text(stringResource(R.string.atlas_mapbox_token_hint)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    isError = token.isNotBlank() && !validToken,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Button(
                enabled = !selectingMapbox || validToken,
                onClick = {
                    val savedToken = if (validToken || token.isBlank()) token.trim() else settings.mapboxToken
                    onSave(AtlasMapSettings(provider, style, savedToken))
                },
                modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun AtlasMapOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
