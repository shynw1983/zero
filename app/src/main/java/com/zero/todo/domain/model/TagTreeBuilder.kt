package com.zero.todo.domain.model

import com.zero.todo.data.repository.TagWithCount

data class TagNode(
    val tag: TagItem,
    val depth: Int,
    val directTaskCount: Int,
    val totalTaskCount: Int,
    val fullPath: String,
    val children: List<TagNode> = emptyList()
)

object TagTreeBuilder {

    val siblingOrder = compareBy<TagItem>({ it.sortOrder ?: Int.MAX_VALUE }, { it.name.lowercase() }, { it.id })

    /**
     * Parses raw tag input such as `"#外出/便利店"` or `"客户/张三"` into clean path segments.
     */
    fun parseTagPathSegments(raw: String): List<String> =
        raw.trim()
            .removePrefix("#")
            .split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /**
     * Builds the active tag tree with manual sibling order and alphabetical fallback.
     * Any tag whose parent is missing or deleted is safely promoted to root level.
     */
    fun buildTree(items: List<TagWithCount>): List<TagNode> {
        val activeItems = items.filter { !it.tag.isDeleted }
        if (activeItems.isEmpty()) return emptyList()

        val activeById = activeItems.associateBy { it.tag.id }
        val countById = activeItems.associate { it.tag.id to it.activeTaskCount }

        // Group children under valid parentId (orphans fall back to null/root)
        val childrenByParent = activeItems
            .map { it.tag }
            .groupBy { tag ->
                val pid = tag.parentId
                if (pid != null && pid != tag.id && pid in activeById) pid else null
            }
            .mapValues { (_, list) -> list.sortedWith(siblingOrder) }

        val visited = mutableSetOf<String>()

        fun buildNode(tag: TagItem, depth: Int, parentPath: String?): TagNode {
            visited += tag.id
            val fullPath = if (parentPath.isNullOrEmpty()) tag.name else "$parentPath/${tag.name}"
            val childNodes = childrenByParent[tag.id].orEmpty()
                .filter { it.id !in visited }
                .map { child -> buildNode(child, depth + 1, fullPath) }
            val direct = countById[tag.id] ?: 0
            val total = direct + childNodes.sumOf { it.totalTaskCount }
            return TagNode(
                tag = tag,
                depth = depth,
                directTaskCount = direct,
                totalTaskCount = total,
                fullPath = fullPath,
                children = childNodes
            )
        }

        val roots = childrenByParent[null].orEmpty().map { root ->
            buildNode(root, depth = 0, parentPath = null)
        }.toMutableList()

        // Guard against any disconnected cycles in malformed persisted data
        for (item in activeItems) {
            if (item.tag.id !in visited) {
                roots += buildNode(item.tag, depth = 0, parentPath = null)
            }
        }
        return roots
    }

    fun flattenVisible(roots: List<TagNode>): List<TagNode> {
        val result = mutableListOf<TagNode>()
        fun walk(node: TagNode) {
            result += node
            if (node.tag.isExpanded) {
                for (child in node.children) {
                    walk(child)
                }
            }
        }
        for (root in roots) {
            walk(root)
        }
        return result
    }

    fun buildPathMap(tags: List<TagItem>): Map<String, String> {
        val activeById = tags.filter { !it.isDeleted }.associateBy { it.id }
        val result = HashMap<String, String>(activeById.size)
        for ((id, tag) in activeById) {
            val segments = mutableListOf(tag.name)
            val seen = hashSetOf(id)
            var currentParent = tag.parentId
            while (currentParent != null && currentParent !in seen) {
                val parent = activeById[currentParent] ?: break
                seen += currentParent
                segments.add(0, parent.name)
                currentParent = parent.parentId
            }
            result[id] = segments.joinToString("/")
        }
        return result
    }

    fun collectDescendantIds(rootTagId: String, tags: List<TagItem>): Set<String> {
        val active = tags.filter { !it.isDeleted }
        if (active.none { it.id == rootTagId }) return emptySet()

        val childrenByParent = active.groupBy { it.parentId }
        val result = linkedSetOf(rootTagId)
        val queue = ArrayDeque<String>()
        queue.addLast(rootTagId)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (child in childrenByParent[current].orEmpty()) {
                if (result.add(child.id)) {
                    queue.addLast(child.id)
                }
            }
        }
        return result
    }
}
