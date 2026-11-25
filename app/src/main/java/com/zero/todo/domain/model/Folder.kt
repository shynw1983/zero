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
