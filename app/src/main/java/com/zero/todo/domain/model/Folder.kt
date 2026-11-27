package com.zero.todo.domain.model

data class Folder(
    val id: String,
    val parentId: String? = null,
    val name: String,
    val icon: String? = null,
    val colorHex: String? = null,
    val sortOrder: Double = 0.0,
    val isExpanded: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
)

data class FolderNode(
    val folder: Folder,
    val depth: Int,
    val children: List<FolderNode> = emptyList(),
    val directTaskCount: Int = 0,
    val totalTaskCount: Int = directTaskCount
)

object FolderTreeBuilder {

    private val folderComparator = compareBy<Folder>(
        { it.sortOrder },
        { it.createdAt },
        { it.id }
    )

    /**
     * Builds an immutable forest of [FolderNode] from a flat list of folders in O(N log N).
     *
     * - Ignores soft-deleted folders defensively.
     * - Sorts each sibling group by `(sortOrder, createdAt, id)`.
     * - Computes both [FolderNode.directTaskCount] and recursive [FolderNode.totalTaskCount].
     * - Guards against cyclic parent links so malformed data can never cause stack overflow.
     */
    fun build(
        folders: List<Folder>,
        directTaskCounts: Map<String, Int> = emptyMap(),
        promoteOrphansToRoot: Boolean = false
    ): List<FolderNode> {
        val activeFolders = folders.filterNot { it.isDeleted }
        if (activeFolders.isEmpty()) return emptyList()

        val activeIds = activeFolders.mapTo(HashSet(activeFolders.size)) { it.id }
        val childrenByParent = HashMap<String?, MutableList<Folder>>()

        for (folder in activeFolders) {
            val effectiveParentId = when {
                folder.parentId == null -> null
                folder.parentId in activeIds -> folder.parentId
                promoteOrphansToRoot -> null
                else -> continue
            }
            childrenByParent.getOrPut(effectiveParentId) { mutableListOf() }.add(folder)
        }

        val visited = HashSet<String>(activeFolders.size)

        fun buildNodes(parentId: String?, depth: Int): List<FolderNode> {
            val siblings = childrenByParent[parentId]?.sortedWith(folderComparator) ?: return emptyList()
            val result = ArrayList<FolderNode>(siblings.size)
            for (folder in siblings) {
                if (!visited.add(folder.id)) continue
                val childNodes = buildNodes(folder.id, depth + 1)
                val directCount = directTaskCounts[folder.id] ?: 0
                val totalCount = directCount + childNodes.sumOf { it.totalTaskCount }
                result.add(
                    FolderNode(
                        folder = folder,
                        depth = depth,
                        children = childNodes,
                        directTaskCount = directCount,
                        totalTaskCount = totalCount
                    )
                )
            }
            return result
        }

        return buildNodes(parentId = null, depth = 0)
    }

    /**
     * Flattens a [FolderNode] forest in pre-order DFS for list rendering, skipping children
     * of collapsed nodes (`isExpanded == false`).
     */
    fun flattenVisible(roots: List<FolderNode>): List<FolderNode> {
        val result = mutableListOf<FolderNode>()
        fun visit(nodes: List<FolderNode>) {
            for (node in nodes) {
                result.add(node)
                if (node.folder.isExpanded && node.children.isNotEmpty()) {
                    visit(node.children)
                }
            }
        }
        visit(roots)
        return result
    }
}

/**
 * Controls how nested folder paths are surfaced on task cards, headers, widgets, and reminders.
 */
enum class FolderPathDisplayMode {
    /** Disambiguate automatically when duplicate leaf folder names exist; keep unique leaves concise. */
    SMART_DISAMBIGUATE,

    /** Always pair top-level project root with leaf folder (e.g. Project/.../Sub). */
    ROOT_AND_LEAF,

    /** Always display the complete nested hierarchy path. */
    FULL_PATH;

    companion object {
        fun fromStorage(raw: String?): FolderPathDisplayMode =
            entries.firstOrNull { it.name == raw } ?: SMART_DISAMBIGUATE
    }
}

/**
 * Formatted folder badge split into dimmed parent prefix + prominent leaf name, plus optional
 * inherited top-level project color hex.
 */
data class FolderBadgeDisplay(
    val prefix: String?,
    val leaf: String,
    val colorHex: String? = null
) {
    val fullText: String
        get() = if (prefix.isNullOrBlank()) leaf else "$prefix$leaf"
}

/**
 * Calm matte architectural color swatches assignable to top-level project folders and automatically
 * inherited by all descendant subfolders.
 */
object ProjectFolderColors {
    const val SLATE_BLUE = "#6E8FB5"
    const val SAGE = "#6E9A84"
    const val WARM_CLAY = "#C07D60"
    const val AMBER = "#C49A52"
    const val DUSTY_PLUM = "#9B7FA8"
    const val TERRACOTTA = "#B86B6B"

    val presets: List<String> = listOf(
        SLATE_BLUE,
        SAGE,
        WARM_CLAY,
        AMBER,
        DUSTY_PLUM,
        TERRACOTTA
    )
}

/**
 * Precomputed immutable index of active folder paths, duplicate leaf counts, and inherited
 * top-level project colors for O(1) badge formatting across UI, search, notifications, and widgets.
 */
class FolderContextIndex internal constructor(
    val segmentsById: Map<String, List<String>>,
    val ancestorIdsById: Map<String, List<String>>,
    val fullPathById: Map<String, String>,
    val colorHexById: Map<String, String>,
    val rootNameById: Map<String, String>,
    private val leafNameCount: Map<String, Int>,
    private val rootLeafPairCount: Map<Pair<String, String>, Int>
) {
    fun badgeFor(
        folderId: String,
        mode: FolderPathDisplayMode,
        relativeToFolderId: String? = null
    ): FolderBadgeDisplay? {
        val segments = segmentsById[folderId] ?: return null
        if (segments.isEmpty()) return null
        val color = colorHexById[folderId]
        val ancestors = ancestorIdsById[folderId].orEmpty()

        // Relative truncation inside an open parent folder with includeSubfolders enabled
        if (relativeToFolderId != null) {
            val anchorIdx = ancestors.indexOf(relativeToFolderId)
            if (anchorIdx in 0 until ancestors.lastIndex) {
                val rel = segments.drop(anchorIdx + 1)
                return when (rel.size) {
                    1 -> FolderBadgeDisplay(null, rel[0], color)
                    2 -> FolderBadgeDisplay("${rel[0]}/", rel[1], color)
                    else -> if (mode == FolderPathDisplayMode.FULL_PATH) {
                        FolderBadgeDisplay(rel.dropLast(1).joinToString("/") + "/", rel.last(), color)
                    } else {
                        FolderBadgeDisplay("${rel.first()}/…/", rel.last(), color)
                    }
                }
            }
        }

        if (segments.size == 1) {
            return FolderBadgeDisplay(null, segments[0], color)
        }

        val leaf = segments.last()
        return when (mode) {
            FolderPathDisplayMode.FULL_PATH -> FolderBadgeDisplay(
                prefix = segments.dropLast(1).joinToString("/") + "/",
                leaf = leaf,
                colorHex = color
            )

            FolderPathDisplayMode.ROOT_AND_LEAF -> if (segments.size == 2) {
                FolderBadgeDisplay("${segments[0]}/", leaf, color)
            } else {
                FolderBadgeDisplay("${segments.first()}/…/", leaf, color)
            }

            FolderPathDisplayMode.SMART_DISAMBIGUATE -> {
                val normalizedLeaf = leaf.trim().lowercase()
                if ((leafNameCount[normalizedLeaf] ?: 0) <= 1) {
                    FolderBadgeDisplay(null, leaf, color)
                } else if (segments.size == 2) {
                    FolderBadgeDisplay("${segments[0]}/", leaf, color)
                } else {
                    val pair = segments.first().trim().lowercase() to normalizedLeaf
                    if ((rootLeafPairCount[pair] ?: 0) > 1) {
                        FolderBadgeDisplay(segments.dropLast(1).joinToString("/") + "/", leaf, color)
                    } else {
                        FolderBadgeDisplay("${segments.first()}/…/", leaf, color)
                    }
                }
            }
        }
    }

    fun monolithBadgeFor(folderId: String, mode: FolderPathDisplayMode): FolderBadgeDisplay? {
        val segments = segmentsById[folderId] ?: return null
        if (segments.isEmpty()) return null
        val color = colorHexById[folderId]
        if (segments.size == 1) {
            return FolderBadgeDisplay(null, segments[0], color)
        }
        val leaf = segments.last()
        return when (mode) {
            FolderPathDisplayMode.FULL_PATH -> FolderBadgeDisplay(
                prefix = segments.dropLast(1).joinToString(" · ") + " · ",
                leaf = leaf,
                colorHex = color
            )

            FolderPathDisplayMode.ROOT_AND_LEAF -> if (segments.size == 2) {
                FolderBadgeDisplay("${segments[0]} · ", leaf, color)
            } else {
                FolderBadgeDisplay("${segments.first()} · … · ", leaf, color)
            }

            FolderPathDisplayMode.SMART_DISAMBIGUATE -> {
                if ((leafNameCount[leaf.trim().lowercase()] ?: 0) <= 1) {
                    FolderBadgeDisplay(null, leaf, color)
                } else {
                    FolderBadgeDisplay(
                        prefix = segments.dropLast(1).joinToString(" · ") + " · ",
                        leaf = leaf,
                        colorHex = color
                    )
                }
            }
        }
    }

    fun breadcrumbSegments(folderId: String): List<String> = segmentsById[folderId].orEmpty()
}

object FolderPathFormatter {
    fun buildIndex(folders: List<Folder>): FolderContextIndex {
        val active = folders.filterNot { it.isDeleted }
        if (active.isEmpty()) {
            return FolderContextIndex(
                segmentsById = emptyMap(),
                ancestorIdsById = emptyMap(),
                fullPathById = emptyMap(),
                colorHexById = emptyMap(),
                rootNameById = emptyMap(),
                leafNameCount = emptyMap(),
                rootLeafPairCount = emptyMap()
            )
        }

        val byId = active.associateBy { it.id }
        val segmentsById = HashMap<String, List<String>>(active.size)
        val ancestorIdsById = HashMap<String, List<String>>(active.size)
        val fullPathById = HashMap<String, String>(active.size)
        val colorHexById = HashMap<String, String>(active.size)
        val rootNameById = HashMap<String, String>(active.size)
        val leafNameCount = HashMap<String, Int>(active.size)
        val rootLeafPairCount = HashMap<Pair<String, String>, Int>()

        for (folder in active) {
            val chain = mutableListOf<Folder>()
            val visited = HashSet<String>()
            var current: Folder? = folder
            while (current != null && visited.add(current.id)) {
                chain.add(current)
                current = current.parentId?.let { byId[it] }
            }
            val ordered = chain.reversed()
            val segments = ordered.map { it.name }
            val ids = ordered.map { it.id }
            segmentsById[folder.id] = segments
            ancestorIdsById[folder.id] = ids
            fullPathById[folder.id] = segments.joinToString("/")
            rootNameById[folder.id] = ordered.first().name

            // Nearest explicit color from self up to root (top-level color inherits to all descendants)
            val inheritedColor = chain.firstNotNullOfOrNull { f ->
                f.colorHex?.trim()?.takeIf { it.isNotBlank() }
            }
            if (inheritedColor != null) {
                colorHexById[folder.id] = inheritedColor
            }

            val normLeaf = folder.name.trim().lowercase()
            leafNameCount[normLeaf] = (leafNameCount[normLeaf] ?: 0) + 1
            if (segments.size >= 2) {
                val pair = segments.first().trim().lowercase() to normLeaf
                rootLeafPairCount[pair] = (rootLeafPairCount[pair] ?: 0) + 1
            }
        }

        return FolderContextIndex(
            segmentsById = segmentsById,
            ancestorIdsById = ancestorIdsById,
            fullPathById = fullPathById,
            colorHexById = colorHexById,
            rootNameById = rootNameById,
            leafNameCount = leafNameCount,
            rootLeafPairCount = rootLeafPairCount
        )
    }
}
