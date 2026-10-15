package com.smarter.core.blocker

/**
 * Decides whether the app in the foreground may keep running. The platform layer reports
 * foreground changes, screen on/off and periodic ticks; the engine charges the elapsed time to
 * the foreground app if it is blocked, and says when its allowance has run out.
 */
class BlockerEngine(
    private val ledger: UsageLedger,
    private val settings: () -> BlockerSettings,
    private val blockedApps: () -> Set<String>,
) {
    sealed interface Decision {
        /** The foreground app is not one we limit. */
        data object NotTracked : Decision

        data class Allowed(val packageName: String, val remainingMs: Long) : Decision

        /** Time is up: show the puzzle lock screen for [packageName]. */
        data class Locked(val packageName: String) : Decision
    }

    var foregroundApp: String? = null
        private set
    private var since = 0L
    private var screenOn = true

    fun onForeground(packageName: String?, now: Long): Decision {
        chargeElapsed(now)
        foregroundApp = packageName
        return evaluate(now)
    }

    fun onTick(now: Long): Decision {
        chargeElapsed(now)
        return evaluate(now)
    }

    fun onScreenOff(now: Long) {
        chargeElapsed(now)
        screenOn = false
    }

    fun onScreenOn(now: Long): Decision {
        screenOn = true
        since = now
        return evaluate(now)
    }

    private fun chargeElapsed(now: Long) {
        val app = foregroundApp
        if (screenOn && app != null && app in blockedApps()) {
            // Ticks arrive every second while a blocked app is open; a large gap means we were
            // not running (e.g. the process was paused), so don't charge for it.
            val elapsed = (now - since).coerceIn(0, MAX_CHARGE_MS)
            val remaining = ledger.remainingMs(app, settings(), now)
            ledger.addUsage(app, minOf(elapsed, remaining), now)
        }
        since = now
    }

    private fun evaluate(now: Long): Decision {
        val app = foregroundApp
        if (!screenOn || app == null || app !in blockedApps()) return Decision.NotTracked
        val remaining = ledger.remainingMs(app, settings(), now)
        return if (remaining > 0) Decision.Allowed(app, remaining) else Decision.Locked(app)
    }

    companion object {
        const val MAX_CHARGE_MS = 30_000L
    }
}
