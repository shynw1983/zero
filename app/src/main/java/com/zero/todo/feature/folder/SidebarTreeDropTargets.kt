package com.zero.todo.feature.folder

internal enum class SidebarTree(val headerKey: String) {
    FOLDERS("folders_header"),
    TAGS("tags_header");

    fun rowKey(id: String): String = "${name}_$id"
}

internal data class SidebarTreeRow(
    val id: String,
    val parentId: String?,
    val depth: Int
)

internal data class SidebarRowBounds(val key: String, val top: Int, val size: Int)

internal enum class SidebarDropZone { BEFORE, INSIDE, AFTER, TOP_LEVEL }

internal data class SidebarDropTarget(
    val markerKey: String,
    val zone: SidebarDropZone,
    val parentId: String?,
    val precedingId: String? = null,
    val followingId: String? = null,
    val markerDepth: Int = 0
)

internal fun sidebarDropTarget(
    tree: SidebarTree,
    sourceId: String,
    rows: List<SidebarTreeRow>,
    visibleBounds: List<SidebarRowBounds>,
    pointerY: Float
): SidebarDropTarget? {
    val hit = visibleBounds.firstOrNull { pointerY >= it.top && pointerY < it.top + it.size } ?: return null
    val source = rows.firstOrNull { it.id == sourceId } ?: return null
    val target = if (hit.key == tree.headerKey) {
        SidebarDropTarget(
            markerKey = hit.key,
            zone = SidebarDropZone.TOP_LEVEL,
            parentId = null,
            followingId = rows.firstOrNull { it.parentId == null && it.id != sourceId }?.id
        )
    } else {
        val index = rows.indexOfFirst { tree.rowKey(it.id) == hit.key }
        val row = rows.getOrNull(index) ?: return null
        var ancestor: String? = row.id
        val visited = hashSetOf(sourceId)
        val byId = rows.associateBy { it.id }
        while (ancestor != null) {
            if (!visited.add(ancestor)) return null
            ancestor = byId[ancestor]?.parentId
        }
        val fraction = (pointerY - hit.top) / hit.size
        when {
            fraction < 0.32f -> SidebarDropTarget(
                hit.key, SidebarDropZone.BEFORE, row.parentId, followingId = row.id, markerDepth = row.depth
            )
            fraction >= 0.68f -> {
                val visibleKeys = visibleBounds.mapTo(hashSetOf()) { it.key }
                val lastVisibleChild = rows.asSequence().drop(index + 1)
                    .takeWhile { it.depth > row.depth }
                    .lastOrNull { tree.rowKey(it.id) in visibleKeys }
                val marker = lastVisibleChild?.let { tree.rowKey(it.id) } ?: hit.key
                SidebarDropTarget(
                    marker, SidebarDropZone.AFTER, row.parentId, precedingId = row.id, markerDepth = row.depth
                )
            }
            else -> SidebarDropTarget(hit.key, SidebarDropZone.INSIDE, row.id, markerDepth = row.depth)
        }
    }

    if (source.parentId == target.parentId) {
        val siblings = rows.filter { it.parentId == source.parentId }
        val index = siblings.indexOf(source)
        val alreadyInPlace = when {
            target.precedingId != null -> siblings.getOrNull(index - 1)?.id == target.precedingId
            target.followingId != null -> siblings.getOrNull(index + 1)?.id == target.followingId
            else -> index == siblings.lastIndex
        }
        if (alreadyInPlace) return null
    }
    return target
}
