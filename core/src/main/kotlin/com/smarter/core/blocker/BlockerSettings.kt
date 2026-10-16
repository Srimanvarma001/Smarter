package com.smarter.core.blocker

import kotlin.math.min

data class BlockerSettings(
    /** Free minutes per blocked app per day before puzzles are required. */
    val dailyAllowanceMinutes: Int = 30,
    /** Puzzles to solve for the first unlock of the day. */
    val puzzlesPerUnlock: Int = 3,
    /** Minutes of extra time each unlock grants. */
    val unlockMinutes: Int = 10,
    /**
     * Puzzle rating of the first puzzle of the day. Ratings are on the Lichess puzzle scale,
     * which is close to chess.com's puzzle rating; the default 1400 serves 1300–1500 puzzles.
     */
    val startRating: Int = DEFAULT_START_RATING,
    /** Each further unlock of the same app on the same day needs more, harder puzzles. */
    val escalate: Boolean = true,
) {
    fun puzzlesRequired(unlocksToday: Int): Int =
        puzzlesPerUnlock + if (escalate) min(unlocksToday, MAX_EXTRA_PUZZLES) else 0

    /**
     * Rating to aim for with the next puzzle: every puzzle solved makes the next one harder,
     * and with [escalate] on every unlock already earned today starts the ladder higher.
     * Failed puzzles don't count, so a miss is followed by a puzzle of the same level.
     */
    fun targetRating(unlocksToday: Int, solvedThisUnlock: Int): Int {
        val unlockBoost = if (escalate) unlocksToday * UNLOCK_STEP else 0
        return (startRating + unlockBoost + solvedThisUnlock * PUZZLE_STEP).coerceAtMost(MAX_RATING)
    }

    companion object {
        const val MAX_EXTRA_PUZZLES = 3
        const val DEFAULT_START_RATING = 1400
        const val MIN_START_RATING = 800
        const val MAX_START_RATING = 2400
        const val START_RATING_STEP = 100
        /** Rating added per puzzle solved in one unlock. */
        const val PUZZLE_STEP = 50
        /** Rating added per unlock already earned today (with escalate on). */
        const val UNLOCK_STEP = 150
        const val MAX_RATING = 2800
    }
}
