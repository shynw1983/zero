package com.zero.todo.feature.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.zero.todo.R
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import kotlin.math.roundToInt

internal class SidebarTreeDrag(private val listState: LazyListState) {
    private data class DraggedRow(
        val tree: SidebarTree,
        val id: String,
        val label: String,
        val pin: PinnableContainer.PinnedHandle?
    )

    private var source by mutableStateOf<DraggedRow?>(null)
    private var fingerInRoot by mutableStateOf(Offset.Zero)
    var listCoordinates by mutableStateOf<LayoutCoordinates?>(null)

    val activeTree get() = source?.tree
    val activeId get() = source?.id
    val label get() = source?.label

    fun isDragging(tree: SidebarTree, id: String): Boolean = activeTree == tree && activeId == id

    fun start(tree: SidebarTree, id: String, label: String, finger: Offset, pin: PinnableContainer?) {
        if (source != null) return
        fingerInRoot = finger
        source = DraggedRow(tree, id, label, pin?.pin())
    }

    fun move(tree: SidebarTree, id: String, finger: Offset) {
        if (isDragging(tree, id)) fingerInRoot = finger
    }

    fun stop(tree: SidebarTree, id: String) {
        if (!isDragging(tree, id)) return
        val finished = source
        source = null
        finished?.pin?.release()
    }

    fun positionInList(): Offset? {
        val coordinates = listCoordinates?.takeIf { it.isAttached } ?: return null
        return fingerInRoot - coordinates.positionInRoot()
    }

    fun target(folderRows: List<SidebarTreeRow>, tagRows: List<SidebarTreeRow>): SidebarDropTarget? {
        val row = source ?: return null
        val position = positionInList() ?: return null
        val layout = listState.layoutInfo
        if (position.x < 0f || position.x >= layout.viewportSize.width ||
            position.y < 0f || position.y >= layout.viewportSize.height
        ) return null
        return sidebarDropTarget(
            tree = row.tree,
            sourceId = row.id,
            rows = if (row.tree == SidebarTree.FOLDERS) folderRows else tagRows,
            visibleBounds = layout.visibleItemsInfo.mapNotNull { item ->
                val key = item.key as? String ?: return@mapNotNull null
                SidebarRowBounds(key, item.offset, item.size)
            },
            pointerY = position.y + layout.viewportStartOffset
        )
    }
}

@Composable
internal fun rememberSidebarTreeDrag(listState: LazyListState): SidebarTreeDrag {
    val drag = remember(listState) { SidebarTreeDrag(listState) }
    val density = LocalDensity.current
    val edge = with(density) { 48.dp.toPx() }
    val maxScroll = with(density) { 14.dp.toPx() }
    LaunchedEffect(drag.activeTree, drag.activeId, edge) {
        while (drag.activeId != null) {
            withFrameNanos { }
            val position = drag.positionInList() ?: continue
            val viewport = listState.layoutInfo.viewportSize
            val edgeSize = edge.coerceAtMost(viewport.height / 2f)
            if (edgeSize <= 0f || position.x < 0f || position.x >= viewport.width ||
                position.y < 0f || position.y >= viewport.height
            ) continue
            val scroll = when {
                position.y < edgeSize -> -(edgeSize - position.y) / edgeSize
                position.y > viewport.height - edgeSize -> (position.y - viewport.height + edgeSize) / edgeSize
                else -> 0f
            }
            if (scroll != 0f) listState.scrollBy(scroll * maxScroll)
        }
    }
    return drag
}

@Composable
internal fun SidebarTreeGrip(
    drag: SidebarTreeDrag,
    tree: SidebarTree,
    id: String,
    label: String,
    tint: Color,
    optionsLabel: String,
    onOpenMenu: () -> Unit,
    onDrop: () -> Unit
) {
    val pin = LocalPinnableContainer.current
    val currentLabel by rememberUpdatedState(label)
    val currentPin by rememberUpdatedState(pin)
    val currentOnDrop by rememberUpdatedState(onDrop)
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(44.dp)
            .clip(ZeroSmallShape)
            .onGloballyPositioned { coordinates = it }
            .pointerInput(drag, tree, id) {
                try {
                    detectVerticalDragGestures(
                        onDragStart = { position ->
                            coordinates?.takeIf { it.isAttached }?.let {
                                drag.start(tree, id, currentLabel, it.localToRoot(position), currentPin)
                            }
                        },
                        onVerticalDrag = { change, _ ->
                            coordinates?.takeIf { it.isAttached }?.let {
                                drag.move(tree, id, it.localToRoot(change.position))
                            }
                        },
                        onDragEnd = {
                            if (drag.isDragging(tree, id)) {
                                currentOnDrop()
                                drag.stop(tree, id)
                            }
                        },
                        onDragCancel = { drag.stop(tree, id) }
                    )
                } finally {
                    drag.stop(tree, id)
                }
            }
            .clickable(onClickLabel = optionsLabel, onClick = onOpenMenu),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.DragHandle,
            contentDescription = stringResource(R.string.cd_drag_reorder),
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
internal fun BoxScope.SidebarDragPreview(drag: SidebarTreeDrag) {
    val label = drag.label ?: return
    val colors = ZeroTheme.colors
    Text(
        text = label,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelMedium,
        color = colors.textPrimary,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .offset {
                val y = drag.positionInList()?.y ?: 0f
                val maxY = ((drag.listCoordinates?.size?.height ?: 0) - 34.dp.toPx()).coerceAtLeast(0f)
                IntOffset(0, (y - 17.dp.toPx()).coerceIn(0f, maxY).roundToInt())
            }
            .padding(end = 12.dp)
            .widthIn(max = 148.dp)
            .clip(ZeroSmallShape)
            .background(colors.surfaceElevated)
            .hairlineBorder(shape = ZeroSmallShape, color = colors.accent)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    )
}

internal fun Modifier.sidebarDragDecoration(
    active: Boolean,
    zone: SidebarDropZone?,
    accent: Color,
    indent: Dp = 0.dp
): Modifier {
    val faded = if (active) graphicsLayer { alpha = 0.38f } else this
    if (zone == null) return faded
    return faded.drawWithContent {
        drawContent()
        val end = size.width - 8.dp.toPx()
        val start = (8.dp + indent).toPx().coerceAtMost(end)
        if (end <= start) return@drawWithContent
        when (zone) {
            SidebarDropZone.BEFORE, SidebarDropZone.AFTER -> {
                val y = if (zone == SidebarDropZone.BEFORE) 2.dp.toPx() else size.height - 2.dp.toPx()
                drawLine(accent, Offset(start, y), Offset(end, y), strokeWidth = 2.dp.toPx())
            }
            SidebarDropZone.INSIDE, SidebarDropZone.TOP_LEVEL -> {
                val inset = 3.dp.toPx()
                val radius = CornerRadius(6.dp.toPx())
                val bounds = Size(end - start, size.height - inset * 2)
                drawRoundRect(accent.copy(alpha = 0.14f), Offset(start, inset), bounds, radius)
                drawRoundRect(accent, Offset(start, inset), bounds, radius, style = Stroke(1.dp.toPx()))
            }
        }
    }
}
