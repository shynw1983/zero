package com.zero.todo.core.common

/**
 * Double-based fractional indexing for O(1) drag-and-drop reordering.
 *
 * Most drag operations compute a single midpoint value and update only one row.
 * When repeated insertions narrow the gap between adjacent items below [MIN_GAP],
 * [between] returns `null` so the caller can rebalance siblings cleanly in a transaction.
 */
object FractionalIndex {

    const val INITIAL_GAP: Double = 1024.0
    const val MIN_GAP: Double = 1e-6

    sealed interface OrderUpdate {
        data class Single(val itemId: String, val sortOrder: Double) : OrderUpdate
        data class Rebalanced(val sortOrders: Map<String, Double>) : OrderUpdate
    }

    /** Returns the initial sortOrder for the first item in an empty container. */
    fun first(): Double = INITIAL_GAP

    /** Returns a sortOrder strictly smaller than [next]. */
    fun before(next: Double): Double = next - INITIAL_GAP

    /** Returns a sortOrder strictly larger than [previous]. */
    fun after(previous: Double): Double = previous + INITIAL_GAP

    /**
     * Computes a sortOrder strictly between [before] and [after].
     *
     * Returns `null` if:
     * - [before] >= [after] (out-of-order or duplicate sibling indices), or
     * - `after - before < MIN_GAP`, or
     * - floating-point precision collapses the midpoint onto either bound.
     */
    fun between(before: Double?, after: Double?): Double? {
        return when {
            before == null && after == null -> INITIAL_GAP
            before == null -> before(after!!)
            after == null -> after(before)
            !before.isFinite() || !after.isFinite() -> null
            before >= after -> null
            (after - before) < MIN_GAP -> null
            else -> {
                val mid = before + (after - before) / 2.0
                if (mid <= before || mid >= after || !mid.isFinite()) null else mid
            }
        }
    }

    /** Returns whether inserting between [before] and [after] requires rebalancing siblings. */
    fun needsRebalance(before: Double?, after: Double?): Boolean = between(before, after) == null

    /**
     * Generates [count] evenly spaced sortOrder values: `step, 2*step, ..., count*step`.
     */
    fun rebalance(count: Int, step: Double = INITIAL_GAP): List<Double> {
        require(count >= 0) { "count must be non-negative, was $count" }
        require(step > 0.0 && step.isFinite()) { "step must be a positive finite number" }
        return List(count) { index -> (index + 1) * step }
    }

    /**
     * Computes the sortOrder update for placing [movingId] at [targetIndex] among [orderedSiblings]
     * (which may or may not currently contain [movingId]).
     */
    fun computeMoveByIndex(
        orderedSiblings: List<Pair<String, Double>>,
        movingId: String,
        targetIndex: Int
    ): OrderUpdate {
        val remaining = orderedSiblings.filterNot { it.first == movingId }
        val clampedIndex = targetIndex.coerceIn(0, remaining.size)

        val prevOrder = remaining.getOrNull(clampedIndex - 1)?.second
        val nextOrder = remaining.getOrNull(clampedIndex)?.second
        val candidate = between(prevOrder, nextOrder)

        if (candidate != null) {
            return OrderUpdate.Single(itemId = movingId, sortOrder = candidate)
        }

        val reorderedIds = buildList(remaining.size + 1) {
            for (i in 0 until clampedIndex) {
                add(remaining[i].first)
            }
            add(movingId)
            for (i in clampedIndex until remaining.size) {
                add(remaining[i].first)
            }
        }
        val newOrders = rebalance(reorderedIds.size)
        return OrderUpdate.Rebalanced(
            sortOrders = reorderedIds.zip(newOrders).toMap()
        )
    }

    /**
     * Computes the sortOrder update for placing [movingId] between [beforeId] and [afterId]
     * within [orderedSiblings].
     *
     * - If [beforeId] is non-null, inserts immediately after [beforeId].
     * - Else if [afterId] is non-null, inserts immediately before [afterId].
     * - If both are null, appends to the end of [orderedSiblings].
     */
    fun computeMoveBetween(
        orderedSiblings: List<Pair<String, Double>>,
        movingId: String,
        beforeId: String?,
        afterId: String?
    ): OrderUpdate {
        val remaining = orderedSiblings.filterNot { it.first == movingId }
        val targetIndex = when {
            beforeId != null -> {
                val idx = remaining.indexOfFirst { it.first == beforeId }
                if (idx >= 0) idx + 1 else remaining.size
            }
            afterId != null -> {
                val idx = remaining.indexOfFirst { it.first == afterId }
                if (idx >= 0) idx else 0
            }
            else -> remaining.size
        }
        return computeMoveByIndex(
            orderedSiblings = remaining,
            movingId = movingId,
            targetIndex = targetIndex
        )
    }
}
