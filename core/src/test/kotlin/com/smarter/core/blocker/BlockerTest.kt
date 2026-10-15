package com.smarter.core.blocker

import com.smarter.core.puzzle.Difficulty
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BlockerTest {
    private class MemoryStore : UsageStore {
        val days = mutableMapOf<String, AppDay>()
        override fun load(packageName: String) = days[packageName]
        override fun save(packageName: String, day: AppDay) { days[packageName] = day }
    }

    private val zone: ZoneId = ZoneOffset.UTC
    private fun at(hour: Int, minute: Int = 0, second: Int = 0, day: Int = 1): Long =
        LocalDateTime.of(2026, 10, day, hour, minute, second).atZone(zone).toInstant().toEpochMilli()

    private val store = MemoryStore()
    private val ledger = UsageLedger(store) { zone }
    private var settings = BlockerSettings(dailyAllowanceMinutes = 1, puzzlesPerUnlock = 2, unlockMinutes = 5)
    private val engine = BlockerEngine(ledger, { settings }, { setOf("insta") })

    @Test
    fun locksWhenAllowanceIsUsedUp() {
        assertEquals(BlockerEngine.Decision.NotTracked, engine.onForeground("launcher", at(9)))
        assertIs<BlockerEngine.Decision.Allowed>(engine.onForeground("insta", at(9)))
        var decision: BlockerEngine.Decision = BlockerEngine.Decision.NotTracked
        for (s in 1..59) decision = engine.onTick(at(9, 0, s))
        assertEquals(BlockerEngine.Decision.Allowed("insta", 1_000), decision)
        assertEquals(BlockerEngine.Decision.Locked("insta"), engine.onTick(at(9, 1)))
        // Time spent elsewhere is free, and time after the lock isn't charged.
        engine.onForeground("launcher", at(9, 1, 30))
        assertEquals(60_000, store.days["insta"]!!.usedMs)
    }

    @Test
    fun unlockGrantsMoreTimeAndEscalates() {
        engine.onForeground("insta", at(9))
        for (s in 1..60) engine.onTick(at(9) + s * 1_000L)
        assertEquals(BlockerEngine.Decision.Locked("insta"), engine.onTick(at(9, 1, 1)))

        ledger.grantUnlock("insta", settings, at(9, 2))
        assertEquals(5 * 60_000L, ledger.remainingMs("insta", settings, at(9, 2)))
        val day = ledger.today("insta", at(9, 2))
        assertEquals(1, day.unlocks)
        assertEquals(3, settings.puzzlesRequired(day.unlocks))
        assertEquals(Difficulty.HARDER, settings.difficultyFor(day.unlocks))
        assertEquals(2 + BlockerSettings.MAX_EXTRA_PUZZLES, settings.puzzlesRequired(10))
        assertEquals(Difficulty.HARDEST, settings.difficultyFor(10))
        assertEquals(2, settings.copy(escalate = false).puzzlesRequired(4))
    }

    @Test
    fun resetsAtMidnight() {
        ledger.addUsage("insta", 60_000, at(23, 59))
        assertEquals(0, ledger.remainingMs("insta", settings, at(23, 59, 30)))
        assertEquals(60_000, ledger.remainingMs("insta", settings, at(0, 0, 1, day = 2)))
    }

    @Test
    fun screenOffAndGapsAreNotCharged() {
        engine.onForeground("insta", at(9))
        engine.onTick(at(9, 0, 10))
        engine.onScreenOff(at(9, 0, 20))
        engine.onScreenOn(at(9, 30))
        engine.onTick(at(9, 30, 5))
        assertEquals(25_000, store.days["insta"]!!.usedMs)
        // A long gap between ticks (process paused) is capped.
        engine.onTick(at(10))
        assertEquals(25_000 + BlockerEngine.MAX_CHARGE_MS, store.days["insta"]!!.usedMs)
    }
}
