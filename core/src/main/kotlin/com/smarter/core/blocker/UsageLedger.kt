package com.smarter.core.blocker

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One blocked app's usage on one calendar day. */
data class AppDay(
    val date: LocalDate,
    val usedMs: Long = 0,
    /** Extra time earned by solving puzzles. */
    val bonusMs: Long = 0,
    val unlocks: Int = 0,
)

interface UsageStore {
    fun load(packageName: String): AppDay?
    fun save(packageName: String, day: AppDay)
}

/** Tracks per-app daily usage against the allowance; resets automatically at local midnight. */
class UsageLedger(
    private val store: UsageStore,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    fun today(packageName: String, now: Long): AppDay {
        val date = Instant.ofEpochMilli(now).atZone(zone()).toLocalDate()
        val stored = store.load(packageName)
        return if (stored?.date == date) stored else AppDay(date)
    }

    fun remainingMs(packageName: String, settings: BlockerSettings, now: Long): Long {
        val day = today(packageName, now)
        val allowance = settings.dailyAllowanceMinutes * MINUTE_MS
        return (allowance + day.bonusMs - day.usedMs).coerceAtLeast(0)
    }

    fun addUsage(packageName: String, ms: Long, now: Long) {
        if (ms <= 0) return
        val day = today(packageName, now)
        store.save(packageName, day.copy(usedMs = day.usedMs + ms))
    }

    /** Records a puzzle unlock: grants [BlockerSettings.unlockMinutes] more minutes today. */
    fun grantUnlock(packageName: String, settings: BlockerSettings, now: Long) {
        val day = today(packageName, now)
        val allowance = settings.dailyAllowanceMinutes * MINUTE_MS
        // Any overrun past the old limit (e.g. while the lock screen was opening) is forgiven,
        // so an unlock always buys the full amount of time.
        val usedMs = day.usedMs.coerceAtMost(allowance + day.bonusMs)
        store.save(
            packageName,
            day.copy(usedMs = usedMs, bonusMs = day.bonusMs + settings.unlockMinutes * MINUTE_MS, unlocks = day.unlocks + 1),
        )
    }

    companion object {
        const val MINUTE_MS = 60_000L
    }
}
