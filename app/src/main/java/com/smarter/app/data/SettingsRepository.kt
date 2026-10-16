package com.smarter.app.data

import android.content.Context
import com.smarter.core.blocker.BlockerSettings
import java.time.LocalDate

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var blockerSettings: BlockerSettings
        get() {
            val defaults = BlockerSettings()
            return BlockerSettings(
                dailyAllowanceMinutes = prefs.getInt(KEY_ALLOWANCE, defaults.dailyAllowanceMinutes),
                puzzlesPerUnlock = prefs.getInt(KEY_PUZZLES, defaults.puzzlesPerUnlock),
                unlockMinutes = prefs.getInt(KEY_UNLOCK_MINUTES, defaults.unlockMinutes),
                startRating = prefs.getInt(KEY_START_RATING, defaults.startRating),
                escalate = prefs.getBoolean(KEY_ESCALATE, defaults.escalate),
            )
        }
        set(value) {
            prefs.edit()
                .putInt(KEY_ALLOWANCE, value.dailyAllowanceMinutes)
                .putInt(KEY_PUZZLES, value.puzzlesPerUnlock)
                .putInt(KEY_UNLOCK_MINUTES, value.unlockMinutes)
                .putInt(KEY_START_RATING, value.startRating)
                .putBoolean(KEY_ESCALATE, value.escalate)
                .apply()
        }

    var blockedApps: Set<String>
        // Copy: the set returned by SharedPreferences must not be modified or kept.
        get() = prefs.getStringSet(KEY_BLOCKED, null)?.toSet().orEmpty()
        set(value) = prefs.edit().putStringSet(KEY_BLOCKED, value.toSet()).apply()

    /** Fetch puzzles from Lichess when online instead of only using the bundled pack. */
    var onlinePuzzles: Boolean
        get() = prefs.getBoolean(KEY_ONLINE, true)
        set(value) = prefs.edit().putBoolean(KEY_ONLINE, value).apply()

    val recentPuzzleIds: Set<String>
        get() = prefs.getString(KEY_RECENT, "").orEmpty().split(',').filter { it.isNotEmpty() }.toSet()

    @Synchronized
    fun rememberPuzzle(id: String) {
        val recent = prefs.getString(KEY_RECENT, "").orEmpty().split(',').filter { it.isNotEmpty() && it != id }
        prefs.edit().putString(KEY_RECENT, (recent + id).takeLast(MAX_RECENT).joinToString(",")).apply()
    }

    /** Puzzles solved today (resets at local midnight). */
    fun solvedToday(today: LocalDate = LocalDate.now()): Int =
        if (prefs.getString(KEY_STATS_DATE, null) == today.toString()) prefs.getInt(KEY_SOLVED_TODAY, 0) else 0

    val solvedAllTime: Int get() = prefs.getInt(KEY_SOLVED_ALL, 0)

    @Synchronized
    fun recordSolved(today: LocalDate = LocalDate.now()) {
        prefs.edit()
            .putString(KEY_STATS_DATE, today.toString())
            .putInt(KEY_SOLVED_TODAY, solvedToday(today) + 1)
            .putInt(KEY_SOLVED_ALL, solvedAllTime + 1)
            .apply()
    }

    private companion object {
        const val KEY_ALLOWANCE = "daily_allowance_minutes"
        const val KEY_PUZZLES = "puzzles_per_unlock"
        const val KEY_UNLOCK_MINUTES = "unlock_minutes"
        const val KEY_START_RATING = "start_rating"
        const val KEY_ESCALATE = "escalate"
        const val KEY_BLOCKED = "blocked_apps"
        const val KEY_ONLINE = "online_puzzles"
        const val KEY_RECENT = "recent_puzzles"
        const val KEY_STATS_DATE = "stats_date"
        const val KEY_SOLVED_TODAY = "solved_today"
        const val KEY_SOLVED_ALL = "solved_all"
        const val MAX_RECENT = 200
    }
}
