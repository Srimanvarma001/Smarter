package com.smarter.core.blocker

import com.smarter.core.puzzle.Difficulty
import kotlin.math.min

data class BlockerSettings(
    /** Free minutes per blocked app per day before puzzles are required. */
    val dailyAllowanceMinutes: Int = 30,
    /** Puzzles to solve for the first unlock of the day. */
    val puzzlesPerUnlock: Int = 3,
    /** Minutes of extra time each unlock grants. */
    val unlockMinutes: Int = 10,
    /** Difficulty of the first unlock of the day. */
    val difficulty: Difficulty = Difficulty.NORMAL,
    /** Each further unlock of the same app on the same day needs more, harder puzzles. */
    val escalate: Boolean = true,
) {
    fun puzzlesRequired(unlocksToday: Int): Int =
        puzzlesPerUnlock + if (escalate) min(unlocksToday, MAX_EXTRA_PUZZLES) else 0

    fun difficultyFor(unlocksToday: Int): Difficulty =
        if (escalate) difficulty.harder(unlocksToday) else difficulty

    companion object {
        const val MAX_EXTRA_PUZZLES = 3
    }
}
