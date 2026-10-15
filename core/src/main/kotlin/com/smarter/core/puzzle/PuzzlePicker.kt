package com.smarter.core.puzzle

import kotlin.math.abs
import kotlin.random.Random

enum class Difficulty(val targetRating: Int, val label: String) {
    EASIEST(800, "Easiest"),
    EASIER(1100, "Easier"),
    NORMAL(1400, "Normal"),
    HARDER(1700, "Harder"),
    HARDEST(2000, "Hardest");

    /** Value of the `difficulty` parameter of Lichess's `/api/puzzle/next`. */
    val apiName: String get() = name.lowercase()

    fun harder(steps: Int): Difficulty = entries[(ordinal + steps).coerceIn(0, entries.lastIndex)]
}

/** Chooses offline puzzles near a target rating, avoiding ones seen recently. */
class PuzzlePicker(private val puzzles: List<Puzzle>, private val random: Random = Random.Default) {
    val size: Int get() = puzzles.size

    fun pick(difficulty: Difficulty, recentlySeen: Set<String> = emptySet()): Puzzle? {
        if (puzzles.isEmpty()) return null
        val fresh = puzzles.filter { it.id !in recentlySeen }.ifEmpty { puzzles }
        val target = difficulty.targetRating
        val inWindow = fresh.filter { abs(it.rating - target) <= RATING_WINDOW }
        val pool = inWindow.ifEmpty { fresh.sortedBy { abs(it.rating - target) }.take(FALLBACK_POOL) }
        return pool.random(random)
    }

    private companion object {
        const val RATING_WINDOW = 250
        const val FALLBACK_POOL = 10
    }
}
