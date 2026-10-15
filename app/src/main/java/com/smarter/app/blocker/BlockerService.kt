package com.smarter.app.blocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.smarter.app.smarter
import com.smarter.app.ui.PuzzleActivity
import com.smarter.app.ui.appLabel
import com.smarter.core.blocker.BlockerEngine
import com.smarter.core.blocker.BlockerEngine.Decision

/**
 * Watches which app is in the foreground, charges time spent in blocked apps, and opens the
 * puzzle lock screen once an app's time for the day runs out.
 */
class BlockerService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var engine: BlockerEngine? = null

    /** Windows from these packages (keyboards, status bar) don't change which app is in use. */
    private var ignoredPackages: Set<String> = emptySet()
    private val warnedApps = mutableSetOf<String>()
    private var lastLockApp: String? = null
    private var lastLockAt = 0L

    private val tick = Runnable { engine?.let { handle(it.onTick(now())) } }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val engine = engine ?: return
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    handler.removeCallbacks(tick)
                    engine.onScreenOff(now())
                }
                Intent.ACTION_SCREEN_ON -> {
                    val keyguard = getSystemService(KeyguardManager::class.java)
                    if (!keyguard.isKeyguardLocked) handle(engine.onScreenOn(now()))
                }
                Intent.ACTION_USER_PRESENT -> handle(engine.onScreenOn(now()))
            }
        }
    }

    override fun onServiceConnected() {
        val app = smarter
        engine = BlockerEngine(app.ledger, { app.settings.blockerSettings }, { app.settings.blockedApps })
        ignoredPackages = buildSet {
            add("com.android.systemui")
            getSystemService(InputMethodManager::class.java).enabledInputMethodList.forEach { add(it.packageName) }
        }
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val engine = engine ?: return
        val packageName = event.packageName?.toString() ?: return
        if (packageName in ignoredPackages) return
        handle(engine.onForeground(packageName, now()))
    }

    private fun handle(decision: Decision) {
        handler.removeCallbacks(tick)
        when (decision) {
            Decision.NotTracked -> Unit
            is Decision.Allowed -> {
                warnIfAlmostUp(decision)
                handler.postDelayed(tick, TICK_MS)
            }
            is Decision.Locked -> {
                showLockScreen(decision.packageName)
                // Keep checking: if the lock screen was debounced or dismissed, show it again.
                handler.postDelayed(tick, TICK_MS)
            }
        }
    }

    private fun warnIfAlmostUp(decision: Decision.Allowed) {
        val app = decision.packageName
        if (decision.remainingMs > WARNING_MS) {
            warnedApps.remove(app)
        } else if (warnedApps.add(app)) {
            Toast.makeText(this, "1 minute left on ${appLabel(app)}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showLockScreen(packageName: String) {
        // The blocked app can report several window changes while the lock screen opens.
        val nowElapsed = SystemClock.elapsedRealtime()
        if (packageName == lastLockApp && nowElapsed - lastLockAt < LOCK_DEBOUNCE_MS) return
        lastLockApp = packageName
        lastLockAt = nowElapsed
        startActivity(PuzzleActivity.lockIntent(this, packageName))
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (engine != null) unregisterReceiver(screenReceiver)
        engine = null
        super.onDestroy()
    }

    private fun now(): Long = System.currentTimeMillis()

    companion object {
        private const val TICK_MS = 1_000L
        private const val WARNING_MS = 60_000L
        private const val LOCK_DEBOUNCE_MS = 1_500L

        fun isEnabled(context: Context): Boolean {
            val manager = context.getSystemService(AccessibilityManager::class.java)
            val component = ComponentName(context, BlockerService::class.java)
            return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo.serviceInfo.let { info -> ComponentName(info.packageName, info.name) == component } }
        }
    }
}
