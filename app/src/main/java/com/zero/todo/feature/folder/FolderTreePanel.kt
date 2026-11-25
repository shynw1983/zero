package com.zero.todo.feature.folder

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zero.todo.R
import com.zero.todo.core.designsystem.ZeroAccent
import com.zero.todo.core.designsystem.ZeroDimens
import com.zero.todo.core.designsystem.ZeroMotion
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.hairlineDividerBottom
import com.zero.todo.core.designsystem.hairlineDividerTop
import com.zero.todo.domain.model.Folder
import com.zero.todo.domain.model.FolderNode
import com.zero.todo.domain.model.FolderTreeBuilder

@Composable
fun FolderTreePanel(
    folderTree: List<FolderNode>,
    selectedDestination: NavigationDestination,
    smartViewCounts: SmartViewCounts,
    includeSubfolders: Boolean,
    isDarkTheme: Boolean,
    activeAccent: ZeroAccent,
    onSelectDestination: (NavigationDestination) -> Unit,
    onToggleFolderExpanded: (folderId: String, isExpanded: Boolean) -> Unit,
    onRequestCreateFolder: (parentFolder: Folder?) -> Unit,
    onRequestRenameFolder: (folder: Folder) -> Unit,
    onDeleteFolder: (folderId: String) -> Unit,
    onToggleIncludeSubfolders: () -> Unit,
    onToggleTheme: () -> Unit,
    onToggleAccent: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val visibleNodes = remember(folderTree) { FolderTreeBuilder.flattenVisible(folderTree) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Brand Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .hairlineDividerBottom()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(colors.accent)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ZERO",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Switch Signal Orange / Cyber Cyan
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(ZeroSmallShape)
                        .hairlineBorder(shape = ZeroSmallShape)
                        .clickable(onClick = onToggleAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(activeAccent.colorFor(isDarkTheme))
                    )
                }

                // Switch Dark / Light mode
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(ZeroSmallShape)
                        .hairlineBorder(shape = ZeroSmallShape)
                        .clickable(onClick = onToggleTheme),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                        contentDescription = stringResource(R.string.cd_toggle_theme),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Open Settings (Accent / Anti-Misclick / Backup & Export)
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(ZeroSmallShape)
                        .hairlineBorder(shape = ZeroSmallShape)
                        .clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.cd_open_settings),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Scrollable Smart Views + Infinite Folder Tree
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item(key = "smart_inbox") {
                SmartViewRow(
                    label = stringResource(R.string.nav_inbox),
                    icon = Icons.Rounded.Inbox,
                    count = smartViewCounts.inbox,
                    selected = selectedDestination == NavigationDestination.Inbox,
                    onClick = { onSelectDestination(NavigationDestination.Inbox) }
                )
            }
            item(key = "smart_today") {
                SmartViewRow(
                    label = stringResource(R.string.nav_today),
                    icon = Icons.Rounded.Today,
                    count = smartViewCounts.today,
                    selected = selectedDestination == NavigationDestination.Today,
                    onClick = { onSelectDestination(NavigationDestination.Today) }
                )
            }
            item(key = "smart_tomorrow") {
                SmartViewRow(
                    label = stringResource(R.string.nav_tomorrow),
                    icon = Icons.Rounded.WbSunny,
                    count = smartViewCounts.tomorrow,
                    selected = selectedDestination == NavigationDestination.Tomorrow,
                    onClick = { onSelectDestination(NavigationDestination.Tomorrow) }
                )
            }
            item(key = "smart_next7") {
                SmartViewRow(
                    label = stringResource(R.string.nav_next_7_days),
                    icon = Icons.Rounded.CalendarMonth,
                    count = smartViewCounts.next7Days,
                    selected = selectedDestination == NavigationDestination.Next7Days,
                    onClick = { onSelectDestination(NavigationDestination.Next7Days) }
                )
            }
            item(key = "smart_calendar") {
                SmartViewRow(
                    label = stringResource(R.string.nav_calendar),
                    icon = Icons.Rounded.CalendarMonth,
                    count = 0,
                    selected = selectedDestination == NavigationDestination.Calendar,
                    onClick = { onSelectDestination(NavigationDestination.Calendar) }
                )
            }

            // Folder Section Header
            item(key = "folders_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp)
                        .hairlineDividerTop()
                        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.section_folders),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IncludeSubfoldersPill(
                            includeSubfolders = includeSubfolders,
                            onToggle = onToggleIncludeSubfolders
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { onRequestCreateFolder(null) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CreateNewFolder,
                                contentDescription = stringResource(R.string.cd_create_root_folder),
                                tint = colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (visibleNodes.isEmpty()) {
                item(key = "folders_empty") {
                    Text(
                        text = stringResource(R.string.folders_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            } else {
                items(
                    items = visibleNodes,
                    key = { it.folder.id }
                ) { node ->
                    val isSelected = selectedDestination is NavigationDestination.FolderView &&
                        selectedDestination.folderId == node.folder.id
                    FolderTreeRow(
                        node = node,
                        isSelected = isSelected,
                        showAggregatedCount = includeSubfolders,
                        onSelect = { onSelectDestination(NavigationDestination.FolderView(node.folder.id)) },
                        onToggleExpand = {
                            onToggleFolderExpanded(node.folder.id, !node.folder.isExpanded)
                        },
                        onCreateSubfolder = { onRequestCreateFolder(node.folder) },
                        onRename = { onRequestRenameFolder(node.folder) },
                        onDelete = { onDeleteFolder(node.folder.id) }
                    )
                }
            }
        }

        // Minimalist status footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .hairlineDividerTop()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FOLDERS",
                style = tabular.dateCompact,
                color = colors.textMuted
            )
            Text(
                text = visibleNodes.size.toString().padStart(2, '0'),
                style = tabular.counter,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
fun IncludeSubfoldersPill(
    includeSubfolders: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val bgColor = if (includeSubfolders) colors.accentSubtle else colors.surface
    val borderColor = if (includeSubfolders) colors.accent else colors.hairlineBorder
    val textColor = if (includeSubfolders) colors.accent else colors.textSecondary

    Box(
        modifier = modifier
            .clip(ZeroSmallShape)
            .background(bgColor)
            .hairlineBorder(shape = ZeroSmallShape, color = borderColor)
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(
                if (includeSubfolders) R.string.filter_include_subfolders else R.string.filter_current_folder
            ),
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}

@Composable
private fun SmartViewRow(
    label: String,
    icon: ImageVector,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(ZeroSmallShape)
            .background(if (selected) colors.surfaceElevated else colors.background)
            .then(
                if (selected) Modifier.hairlineBorder(shape = ZeroSmallShape) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) colors.accent else colors.textSecondary,
            modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colors.textPrimary else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (count > 0) {
            Text(
                text = count.toString(),
                style = tabular.counter,
                color = if (selected) colors.accent else colors.textMuted
            )
        }
    }
}

@Composable
private fun FolderTreeRow(
    node: FolderNode,
    isSelected: Boolean,
    showAggregatedCount: Boolean,
    onSelect: () -> Unit,
    onToggleExpand: () -> Unit,
    onCreateSubfolder: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val hasChildren = node.children.isNotEmpty()
    val visualDepth = node.depth.coerceAtMost(ZeroDimens.MaxVisibleTreeDepth)
    val startIndent = ZeroDimens.TreeIndentPerLevel * visualDepth

    val chevronRotation by animateFloatAsState(
        targetValue = if (node.folder.isExpanded) 90f else 0f,
        animationSpec = ZeroMotion.FloatSpring,
        label = "folder_chevron_rotation"
    )

    val displayedCount = if (showAggregatedCount) node.totalTaskCount else node.directTaskCount

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(ZeroSmallShape)
            .background(if (isSelected) colors.surfaceElevated else colors.background)
            .then(
                if (isSelected) Modifier.hairlineBorder(shape = ZeroSmallShape) else Modifier
            )
            .clickable(onClick = onSelect)
            .padding(start = 6.dp + startIndent, end = 6.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Expand / Collapse Chevron
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(ZeroSmallShape)
                .then(
                    if (hasChildren) Modifier.clickable(onClick = onToggleExpand) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (hasChildren) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(
                        if (node.folder.isExpanded) R.string.cd_collapse else R.string.cd_expand
                    ),
                    tint = colors.textMuted,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(chevronRotation)
                )
            }
        }

        Icon(
            imageVector = if (hasChildren && node.folder.isExpanded) {
                Icons.Rounded.FolderOpen
            } else {
                Icons.Rounded.Folder
            },
            contentDescription = null,
            tint = if (isSelected) colors.accent else colors.textSecondary,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Depth indicator for levels > 4 to prevent horizontal clipping
        if (node.depth > ZeroDimens.MaxVisibleTreeDepth) {
            Text(
                text = "L${node.depth + 1}",
                style = tabular.dateCompact,
                color = colors.accent,
                modifier = Modifier
                    .clip(ZeroSmallShape)
                    .background(colors.accentSubtle)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }

        // Folder name (horizontal scroll fallback for deep nested names)
        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = node.folder.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) colors.textPrimary else colors.textSecondary,
                maxLines = 1,
                softWrap = false
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        if (displayedCount > 0) {
            Text(
                text = displayedCount.toString(),
                style = tabular.counter,
                color = if (isSelected) colors.accent else colors.textMuted,
                modifier = Modifier.padding(end = 4.dp)
            )
        }

        // Create Subfolder action
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(ZeroSmallShape)
                .clickable(onClick = onCreateSubfolder),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.cd_create_subfolder),
                tint = colors.textMuted,
                modifier = Modifier.size(14.dp)
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(ZeroSmallShape)
                    .clickable(onClick = onRename),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.dialog_rename_folder_title),
                    tint = colors.textMuted,
                    modifier = Modifier.size(13.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(ZeroSmallShape)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.cd_delete_folder),
                    tint = colors.textMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
