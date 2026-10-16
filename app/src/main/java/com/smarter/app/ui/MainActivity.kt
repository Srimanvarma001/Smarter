package com.smarter.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.smarter.app.R
import com.smarter.app.blocker.BlockerService
import com.smarter.app.smarter
import com.smarter.core.blocker.BlockerSettings
import com.smarter.core.blocker.UsageLedger

/** Dashboard: blocker status, today's stats, usage per blocked app, and the unlock rules. */
class MainActivity : Activity() {
    private lateinit var statusSlot: LinearLayout
    private lateinit var blockerOffCard: LinearLayout
    private lateinit var solvedToday: TextView
    private lateinit var usedToday: TextView
    private lateinit var unlocksToday: TextView
    private lateinit var appsCard: LinearLayout
    private lateinit var rulesCard: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        statusSlot = LinearLayout(this)
        blockerOffCard = card().apply { setPadding(dp(16), dp(16), dp(16), dp(16)) }
        appsCard = card()
        rulesCard = card()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title("Smarter", 30f), weighted())
            addView(statusSlot, wrap())
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
            addView(header, matchWidth())
            addView(textView("Earn your screen time with chess puzzles.", 15f, R.color.text_secondary), matchWidth(dp(6)))
            addView(blockerOffCard, matchWidth(dp(24)))
            addView(buildStats(), matchWidth(dp(24)))
            addView(section("Blocked apps", button("Edit", ButtonStyle.TEXT, small = true) { openPicker() }), matchWidth(dp(28)))
            addView(appsCard, matchWidth(dp(8)))
            addView(section("Rules"), matchWidth(dp(28)))
            addView(rulesCard, matchWidth(dp(8)))
            addView(button("Practice a puzzle") {
                startActivity(PuzzleActivity.practiceIntent(this@MainActivity))
            }, matchWidth(dp(28)))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(colorOf(R.color.background))
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(content)
        }
        fitSystemBars(scroll)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        renderStatus()
        renderStats()
        renderApps()
        renderRules()
    }

    private fun openPicker() = startActivity(Intent(this, AppPickerActivity::class.java))

    private fun openAccessibilitySettings() = startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    private fun section(title: String, action: View? = null): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(36)
        setPadding(dp(4), 0, 0, 0)
        addView(sectionTitle(title), weighted())
        if (action != null) addView(action, wrap())
    }

    private fun buildStats(): View {
        solvedToday = textView("0", 26f, weight = 700)
        usedToday = textView("0m", 26f, weight = 700)
        unlocksToday = textView("0", 26f, weight = 700)
        fun stat(value: TextView, name: String) = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(value)
            addView(textView(name, 13f, R.color.text_secondary), matchWidth(dp(2)))
        }
        return card().apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            addView(stat(solvedToday, "Solved today"), weighted())
            addView(stat(usedToday, "Time used"), weighted())
            addView(stat(unlocksToday, "Unlocks"), weighted())
        }
    }

    private fun renderStatus() {
        val on = BlockerService.isEnabled(this)
        statusSlot.removeAllViews()
        statusSlot.addView(tag(if (on) "On" else "Off", if (on) R.color.accent else R.color.danger, dot = true).apply {
            contentDescription = if (on) "Blocker is on" else "Blocker is off"
            setOnClickListener { openAccessibilitySettings() }
        })

        blockerOffCard.visibility = if (on) View.GONE else View.VISIBLE
        if (on) return
        blockerOffCard.removeAllViews()
        blockerOffCard.addView(textView("Turn on the blocker", 17f, weight = 600))
        blockerOffCard.addView(
            textView(
                "Enable \"${getString(R.string.accessibility_service_label)}\" in Accessibility settings so Smarter " +
                    "can tell which app is open. It never reads your screen.",
                14f, R.color.text_secondary,
            ),
            matchWidth(dp(6)),
        )
        blockerOffCard.addView(button("Open settings") { openAccessibilitySettings() }, matchWidth(dp(16)))
        blockerOffCard.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(textView("Switch greyed out? Allow restricted settings in App info first.", 13f, R.color.text_secondary), weighted())
            addView(button("App info", ButtonStyle.TEXT, small = true) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            }, wrap(dp(8)))
        }, matchWidth(dp(10)))
    }

    private fun renderStats() {
        val now = System.currentTimeMillis()
        val days = smarter.settings.blockedApps.map { smarter.ledger.today(it, now) }
        solvedToday.text = smarter.settings.solvedToday().toString()
        usedToday.text = "${days.sumOf { it.usedMs } / UsageLedger.MINUTE_MS}m"
        unlocksToday.text = days.sumOf { it.unlocks }.toString()
    }

    private fun renderApps() {
        appsCard.removeAllViews()
        val blocked = smarter.settings.blockedApps.sortedBy { appLabel(it).lowercase() }
        if (blocked.isEmpty()) {
            appsCard.setPadding(dp(16), dp(16), dp(16), dp(16))
            appsCard.addView(textView("No apps blocked yet. Pick the ones that eat your time.", 15f, R.color.text_secondary))
            appsCard.addView(button("Choose apps", ButtonStyle.SECONDARY) { openPicker() }, matchWidth(dp(14)))
            return
        }
        appsCard.setPadding(dp(16), dp(4), dp(16), dp(4))
        val settings = smarter.settings.blockerSettings
        val now = System.currentTimeMillis()
        for ((index, app) in blocked.withIndex()) {
            if (index > 0) appsCard.addView(divider())
            val day = smarter.ledger.today(app, now)
            val limitMs = settings.dailyAllowanceMinutes * UsageLedger.MINUTE_MS + day.bonusMs
            val locked = day.usedMs >= limitMs
            val unlocks = when (day.unlocks) {
                0 -> ""
                1 -> " · 1 unlock"
                else -> " · ${day.unlocks} unlocks"
            }
            val name = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(textView(appLabel(app), 16f, weight = 500), weighted())
                if (locked) addView(tag("Locked", R.color.danger), wrap(dp(8)))
            }
            val text = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(name, matchWidth())
                addView(
                    textView("${minOf(day.usedMs, limitMs) / UsageLedger.MINUTE_MS} of ${limitMs / UsageLedger.MINUTE_MS} min$unlocks", 13f, R.color.text_secondary),
                    matchWidth(dp(2)),
                )
                addView(UsageBar(context).apply {
                    fraction = if (limitMs == 0L) 1f else day.usedMs.toFloat() / limitMs
                    alert = locked
                }, matchWidth(dp(10)))
            }
            appsCard.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(14), 0, dp(14))
                addView(ImageView(context).apply { setImageDrawable(appIcon(app)) }, LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                    marginEnd = dp(14)
                })
                addView(text, weighted())
            }, matchWidth())
        }
    }

    private fun renderRules() {
        rulesCard.removeAllViews()
        val s = smarter.settings.blockerSettings
        val rows = listOf(
            stepper("Free time per app", "${s.dailyAllowanceMinutes} min", null,
                { update { it.copy(dailyAllowanceMinutes = (it.dailyAllowanceMinutes - 5).coerceAtLeast(0)) } },
                { update { it.copy(dailyAllowanceMinutes = (it.dailyAllowanceMinutes + 5).coerceAtMost(600)) } }),
            stepper("Puzzles per unlock", "${s.puzzlesPerUnlock}", null,
                { update { it.copy(puzzlesPerUnlock = (it.puzzlesPerUnlock - 1).coerceAtLeast(1)) } },
                { update { it.copy(puzzlesPerUnlock = (it.puzzlesPerUnlock + 1).coerceAtMost(10)) } }),
            stepper("Time per unlock", "${s.unlockMinutes} min", null,
                { update { it.copy(unlockMinutes = (it.unlockMinutes - 5).coerceAtLeast(5)) } },
                { update { it.copy(unlockMinutes = (it.unlockMinutes + 5).coerceAtMost(120)) } }),
            stepper("Starting rating", "${s.startRating}", "+${BlockerSettings.PUZZLE_STEP} after every puzzle you solve",
                { update { it.copy(startRating = (it.startRating - BlockerSettings.START_RATING_STEP).coerceAtLeast(BlockerSettings.MIN_START_RATING)) } },
                { update { it.copy(startRating = (it.startRating + BlockerSettings.START_RATING_STEP).coerceAtMost(BlockerSettings.MAX_START_RATING)) } }),
            toggle(
                "Harder after each unlock",
                "+1 puzzle (up to +${BlockerSettings.MAX_EXTRA_PUZZLES}) and +${BlockerSettings.UNLOCK_STEP} rating each time",
                s.escalate,
            ) { on -> update(rerenderRules = false) { it.copy(escalate = on) } },
            toggle(
                "Fresh puzzles from Lichess",
                "Online when possible, built-in set offline",
                smarter.settings.onlinePuzzles,
            ) { smarter.settings.onlinePuzzles = it },
        )
        for ((index, row) in rows.withIndex()) {
            if (index > 0) rulesCard.addView(divider())
            rulesCard.addView(row, matchWidth())
        }
    }

    /** Applies [change] to the current settings (read at click time, so rows never undo each other). */
    private fun update(rerenderRules: Boolean = true, change: (BlockerSettings) -> BlockerSettings) {
        smarter.settings.blockerSettings = change(smarter.settings.blockerSettings)
        if (rerenderRules) renderRules()
        renderApps()
    }

    private fun rowText(title: String, description: String?): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(textView(title, 15f))
        if (description != null) addView(textView(description, 13f, R.color.text_secondary), matchWidth(dp(2)))
    }

    private fun stepper(title: String, value: String, description: String?, onMinus: () -> Unit, onPlus: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            addView(rowText(title, description), weighted())
            addView(iconButton("−", 32, "Decrease ${title.lowercase()}", filled = true, onClick = onMinus))
            addView(textView(value, 15f, weight = 600).apply {
                gravity = Gravity.CENTER
                minWidth = dp(68)
            })
            addView(iconButton("+", 32, "Increase ${title.lowercase()}", filled = true, onClick = onPlus))
        }

    private fun toggle(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, dp(14))
            addView(rowText(title, description), weighted())
            addView(ToggleView(context).apply {
                setChecked(checked)
                this.onChange = onChange
            }, wrap(dp(12)))
        }
}
