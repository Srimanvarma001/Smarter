package com.smarter.core.puzzle

import kotlin.math.abs
import kotlin.random.Random

/** Chooses offline puzzles near a target rating, avoiding ones seen recently. */
class PuzzlePicker(private val puzzles: List<Puzzle>, private val random: Random = Random.Default) {
    val size: Int get() = puzzles.size

    fun pick(targetRating: Int, recentlySeen: Set<String> = emptySet()): Puzzle? {
        if (puzzles.isEmpty()) return null
        val fresh = puzzles.filter { it.id !in recentlySeen }.ifEmpty { puzzles }
        val inWindow = fresh.filter { abs(it.rating - targetRating) <= RATING_WINDOW }
        val pool = inWindow.ifEmpty { fresh.sortedBy { abs(it.rating - targetRating) }.take(FALLBACK_POOL) }
        return pool.random(random)
    }

    companion object {
        /** A target of 1400 serves puzzles rated 1300–1500. */
        const val RATING_WINDOW = 100
        private const val FALLBACK_POOL = 10
    }
}
