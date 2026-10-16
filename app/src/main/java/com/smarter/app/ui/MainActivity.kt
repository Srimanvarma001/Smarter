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
    private lateinit var statusPill: LinearLayout
    private lateinit var blockerOffCard: LinearLayout
    private lateinit var solvedToday: TextView
    private lateinit var usedToday: TextView
    private lateinit var unlocksToday: TextView
    private lateinit var solvedAll: TextView
    private lateinit var appsCard: LinearLayout
    private lateinit var rulesCard: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        statusPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBackground(colorOf(R.color.background), 99, colorOf(R.color.outline))
            setPadding(dp(12), dp(8), dp(14), dp(8))
        }
        blockerOffCard = card(strokeColor = blendAccent())
        appsCard = card()
        rulesCard = card().apply { setPadding(dp(18), dp(4), dp(18), dp(4)) }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(dotoText("SMARTER", 44f), weighted())
            addView(statusPill, wrap())
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(28))
            addView(header, matchWidth())
            addView(label("Earn your screen time"), matchWidth(dp(6)))
            addView(blockerOffCard, matchWidth(dp(18)))
            addView(buildHero(), matchWidth(dp(18)))
            addView(section("Blocked apps", pillButton("Edit", ButtonStyle.OUTLINE, small = true) { openPicker() }), matchWidth(dp(26)))
            addView(appsCard, matchWidth(dp(10)))
            addView(section("Rules"), matchWidth(dp(26)))
            addView(rulesCard, matchWidth(dp(10)))
            addView(pillButton("Practice a puzzle") {
                startActivity(PuzzleActivity.practiceIntent(this@MainActivity))
            }, matchWidth(dp(24)))
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

    /** A red-tinted outline for the warning card. */
    private fun blendAccent(): Int {
        val a = colorOf(R.color.accent)
        val o = colorOf(R.color.outline)
        fun mix(shift: Int) = ((((a shr shift) and 0xFF) * 45 + ((o shr shift) and 0xFF) * 55) / 100) shl shift
        return (0xFF shl 24) or mix(16) or mix(8) or mix(0)
    }

    private fun section(title: String, action: View? = null): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), 0, 0, 0)
        addView(label(title), weighted())
        if (action != null) addView(action, wrap())
    }

    private fun buildHero(): View {
        solvedToday = dotoText("0", 76f)
        usedToday = monoValue("0m", 20f)
        unlocksToday = monoValue("0", 20f)
        solvedAll = monoValue("0", 20f)
        fun stat(name: String, value: TextView, first: Boolean) = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            if (!first) setPadding(dp(12), 0, 0, 0)
            addView(label(name))
            addView(value, matchWidth(dp(2)))
        }
        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(stat("Used", usedToday, true), weighted())
            addView(divider(vertical = true))
            addView(stat("Unlocks", unlocksToday, false), weighted())
            addView(divider(vertical = true))
            addView(stat("All-time", solvedAll, false), weighted())
        }
        return card(dotGrid = true).apply {
            setPadding(dp(20), dp(20), dp(20), dp(18))
            addView(label("Puzzles solved today"))
            addView(solvedToday, matchWidth(dp(14)))
            addView(divider(), matchWidth(dp(18)))
            addView(stats, matchWidth(dp(14)))
        }
    }

    private fun renderStatus() {
        val on = BlockerService.isEnabled(this)
        statusPill.removeAllViews()
        statusPill.addView(led(red = !on))
        statusPill.addView(label(if (on) "On" else "Off", color = R.color.text_primary), wrap(dp(8)))
        statusPill.contentDescription = if (on) "Blocker is on" else "Blocker is off"
        statusPill.setOnClickListener { openAccessibilitySettings() }

        blockerOffCard.visibility = if (on) View.GONE else View.VISIBLE
        if (on) return
        blockerOffCard.removeAllViews()
        blockerOffCard.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(led(red = true))
            addView(label("Blocker is off", 13f, R.color.text_primary), wrap(dp(10)))
        })
        blockerOffCard.addView(
            body(
                "Turn on \"${getString(R.string.accessibility_service_label)}\" in Accessibility settings so Smarter can tell " +
                    "which app is open. It never reads your screen.",
                15f, R.color.text_secondary,
            ),
            matchWidth(dp(8)),
        )
        blockerOffCard.addView(pillButton("Turn on blocker") { openAccessibilitySettings() }, matchWidth(dp(16)))
        blockerOffCard.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(body("Greyed out? In App info, open the three-dot menu and allow restricted settings first.", 13f, R.color.text_secondary), weighted())
            addView(pillButton("App info", ButtonStyle.OUTLINE, small = true) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            }, wrap(dp(12)))
        }, matchWidth(dp(12)))
    }

    private fun renderStats() {
        val now = System.currentTimeMillis()
        val days = smarter.settings.blockedApps.map { smarter.ledger.today(it, now) }
        solvedToday.text = smarter.settings.solvedToday().toString()
        usedToday.text = "${days.sumOf { it.usedMs } / UsageLedger.MINUTE_MS}m"
        unlocksToday.text = days.sumOf { it.unlocks }.toString()
        solvedAll.text = smarter.settings.solvedAllTime.toString()
    }

    private fun renderApps() {
        appsCard.removeAllViews()
        val blocked = smarter.settings.blockedApps.sortedBy { appLabel(it).lowercase() }
        if (blocked.isEmpty()) {
            appsCard.addView(body("No apps blocked yet. Pick the ones that eat your time.", 15f, R.color.text_secondary))
            appsCard.addView(pillButton("Choose apps", ButtonStyle.OUTLINE) { openPicker() }, matchWidth(dp(14)))
            return
        }
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
            val bar = DotsBar(this).apply {
                filled = if (limitMs == 0L) dotCount else ((day.usedMs * dotCount + limitMs / 2) / limitMs).toInt().coerceIn(0, dotCount)
                alert = locked
            }
            val name = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(body(appLabel(app), 16f, bold = true))
                if (locked) addView(redTag("Locked"), wrap(dp(8)))
            }
            val text = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(name)
                addView(label("${minOf(day.usedMs, limitMs) / UsageLedger.MINUTE_MS} / ${limitMs / UsageLedger.MINUTE_MS} min$unlocks"), matchWidth(dp(3)))
                addView(bar, wrap().apply { topMargin = dp(9) })
            }
            appsCard.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(12), 0, dp(12))
                addView(ImageView(context).apply { setImageDrawable(appIcon(app)) }, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
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
            stepper("Free time per app daily", "${s.dailyAllowanceMinutes} min",
                { update { it.copy(dailyAllowanceMinutes = (it.dailyAllowanceMinutes - 5).coerceAtLeast(0)) } },
                { update { it.copy(dailyAllowanceMinutes = (it.dailyAllowanceMinutes + 5).coerceAtMost(600)) } }),
            stepper("Puzzles per unlock", "${s.puzzlesPerUnlock}",
                { update { it.copy(puzzlesPerUnlock = (it.puzzlesPerUnlock - 1).coerceAtLeast(1)) } },
                { update { it.copy(puzzlesPerUnlock = (it.puzzlesPerUnlock + 1).coerceAtMost(10)) } }),
            stepper("Time per unlock", "${s.unlockMinutes} min",
                { update { it.copy(unlockMinutes = (it.unlockMinutes - 5).coerceAtLeast(5)) } },
                { update { it.copy(unlockMinutes = (it.unlockMinutes + 5).coerceAtMost(120)) } }),
            stepper("Difficulty", s.difficulty.label,
                { update { it.copy(difficulty = it.difficulty.harder(-1)) } },
                { update { it.copy(difficulty = it.difficulty.harder(1)) } }),
            toggle(
                "Harder after each unlock",
                "+1 puzzle (up to +${BlockerSettings.MAX_EXTRA_PUZZLES}) and harder each time.",
                s.escalate,
            ) { on -> update(rerenderRules = false) { it.copy(escalate = on) } },
            toggle(
                "Fresh puzzles from Lichess",
                "Online when possible, built-in set offline.",
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

    private fun stepper(title: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            addView(body(title, 15f), weighted())
            addView(circleButton("−", 36, "Decrease ${title.lowercase()}", onMinus))
            addView(monoValue(value.uppercase(), 14f).apply {
                gravity = Gravity.CENTER
                minWidth = dp(80)
            })
            addView(circleButton("+", 36, "Increase ${title.lowercase()}", onPlus))
        }

    private fun toggle(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, dp(14))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(body(title, 15f))
                addView(body(description, 13f, R.color.text_secondary), matchWidth(dp(2)))
            }, weighted())
            addView(ToggleView(context).apply {
                setChecked(checked)
                this.onChange = onChange
            }, wrap(dp(12)))
        }
}
