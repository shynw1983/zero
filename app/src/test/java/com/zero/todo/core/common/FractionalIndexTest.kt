package com.zero.todo.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FractionalIndexTest {

    @Test
    fun `first, before, and after maintain strict ordering with generous spacing`() {
        val first = FractionalIndex.first()
        val beforeFirst = FractionalIndex.before(first)
        val afterFirst = FractionalIndex.after(first)

        assertEquals(FractionalIndex.INITIAL_GAP, first, 0.0)
        assertTrue(beforeFirst < first)
        assertTrue(first < afterFirst)
        assertEquals(FractionalIndex.INITIAL_GAP, first - beforeFirst, 0.0)
        assertEquals(FractionalIndex.INITIAL_GAP, afterFirst - first, 0.0)
    }

    @Test
    fun `between computes exact midpoint and handles open bounds`() {
        assertEquals(FractionalIndex.INITIAL_GAP, FractionalIndex.between(null, null)!!, 0.0)
        assertEquals(0.0, FractionalIndex.between(null, 1024.0)!!, 0.0)
        assertEquals(2048.0, FractionalIndex.between(1024.0, null)!!, 0.0)
        assertEquals(1536.0, FractionalIndex.between(1024.0, 2048.0)!!, 0.0)
    }

    @Test
    fun `between returns null when bounds are inverted, equal, or narrower than MIN_GAP`() {
        assertNull(FractionalIndex.between(10.0, 10.0))
        assertNull(FractionalIndex.between(20.0, 10.0))
        assertNull(FractionalIndex.between(1.0, 1.0 + FractionalIndex.MIN_GAP / 2.0))
        assertTrue(FractionalIndex.needsRebalance(1.0, 1.0 + FractionalIndex.MIN_GAP / 2.0))
        assertFalse(FractionalIndex.needsRebalance(1.0, 2.0))
    }

    @Test
    fun `computeMoveByIndex updates single item in O(1) when gap is sufficient`() {
        val siblings = listOf("a" to 1024.0, "b" to 2048.0, "c" to 3072.0)

        val moveCToMiddle = FractionalIndex.computeMoveByIndex(siblings, movingId = "c", targetIndex = 1)
        assertTrue(moveCToMiddle is FractionalIndex.OrderUpdate.Single)
        val single = moveCToMiddle as FractionalIndex.OrderUpdate.Single
        assertEquals("c", single.itemId)
        assertEquals(1536.0, single.sortOrder, 0.0)
    }

    @Test
    fun `computeMoveBetween triggers rebalance when gap drops below MIN_GAP`() {
        val tightSiblings = listOf(
            "a" to 100.0,
            "b" to 100.0 + FractionalIndex.MIN_GAP / 4.0,
            "c" to 200.0
        )

        val update = FractionalIndex.computeMoveBetween(
            orderedSiblings = tightSiblings,
            movingId = "c",
            beforeId = "a",
            afterId = "b"
        )

        assertTrue(update is FractionalIndex.OrderUpdate.Rebalanced)
        val rebalanced = (update as FractionalIndex.OrderUpdate.Rebalanced).sortOrders
        assertEquals(listOf("a", "c", "b"), rebalanced.entries.sortedBy { it.value }.map { it.key })
        assertEquals(1024.0, rebalanced["a"]!!, 0.0)
        assertEquals(2048.0, rebalanced["c"]!!, 0.0)
        assertEquals(3072.0, rebalanced["b"]!!, 0.0)
    }

    @Test
    fun `repeated insertions into the same gap always preserve order through automatic rebalancing`() {
        var ordered = mutableListOf("start" to 1024.0, "end" to 2048.0)
        var rebalanceCount = 0

        for (i in 1..60) {
            val newId = "item_$i"
            val update = FractionalIndex.computeMoveByIndex(
                orderedSiblings = ordered,
                movingId = newId,
                targetIndex = 1 // always right after "start"
            )
            when (update) {
                is FractionalIndex.OrderUpdate.Single -> {
                    ordered.add(newId to update.sortOrder)
                }
                is FractionalIndex.OrderUpdate.Rebalanced -> {
                    rebalanceCount++
                    ordered = update.sortOrders.entries
                        .map { it.key to it.value }
                        .toMutableList()
                }
            }
            ordered.sortBy { it.second }
            assertEquals("start", ordered.first().first)
            assertEquals(newId, ordered[1].first)
            assertEquals("end", ordered.last().first)
            // Verify strict monotonic increasing sortOrder across all items
            for (j in 0 until ordered.lastIndex) {
                assertTrue(
                    "Expected strictly increasing sortOrder at $j",
                    ordered[j].second < ordered[j + 1].second
                )
            }
        }

        assertNotNull(ordered)
        assertTrue("Expected at least one automatic rebalance after 60 insertions", rebalanceCount >= 1)
    }
}
