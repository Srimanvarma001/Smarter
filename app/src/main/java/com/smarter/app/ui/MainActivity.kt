package com.smarter.app.ui

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.smarter.app.R
import com.smarter.app.blocker.BlockerService
import com.smarter.app.smarter
import com.smarter.core.blocker.BlockerSettings
import com.smarter.core.blocker.UsageLedger

/** Dashboard: blocker status, today's usage per blocked app, and the unlock rules. */
class MainActivity : Activity() {
    private lateinit var statusCard: LinearLayout
    private lateinit var appsList: LinearLayout
    private lateinit var rulesCard: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        statusCard = card()
        appsList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rulesCard = card()

        val appsCard = card().apply {
            addView(label("Blocked apps", 18f, bold = true))
            addView(appsList, matchWidth())
            addView(pillButton("Choose apps", ButtonStyle.SECONDARY) {
                startActivity(Intent(this@MainActivity, AppPickerActivity::class.java))
            }, matchWidth(dp(14)))
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(28))
            addView(label("Smarter", 30f, bold = true))
            addView(label("Earn your screen time by solving chess puzzles.", 16f, R.color.text_secondary), matchWidth(dp(2)))
            addView(statusCard, matchWidth(dp(20)))
            addView(appsCard, matchWidth(dp(14)))
            addView(rulesCard, matchWidth(dp(14)))
            addView(pillButton("Practice a puzzle") {
                startActivity(PuzzleActivity.practiceIntent(this@MainActivity))
            }, matchWidth(dp(20)))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(colorOf(R.color.background))
            isFillViewport = true
            addView(content)
        }
        fitSystemBars(scroll)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        renderStatus()
        renderApps()
        renderRules()
    }

    private fun renderStatus() {
        statusCard.removeAllViews()
        if (BlockerService.isEnabled(this)) {
            statusCard.addView(label("✓ Blocker is on", 18f, R.color.accent, bold = true))
            statusCard.addView(
                label("Blocked apps lock when their daily time runs out. Solve puzzles to get more time.", 14f, R.color.text_secondary),
                matchWidth(dp(4)),
            )
            return
        }
        statusCard.addView(label("Blocker is off", 18f, R.color.danger, bold = true))
        statusCard.addView(
            label(
                "Turn on \"${getString(R.string.accessibility_service_label)}\" in Accessibility settings so Smarter can tell " +
                    "which app is open. It never reads what's on your screen.",
                14f, R.color.text_secondary,
            ),
            matchWidth(dp(4)),
        )
        statusCard.addView(pillButton("Turn on blocker") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, matchWidth(dp(14)))
        statusCard.addView(
            label(
                "Greyed out? On Android 13 and newer, open App info, tap ⋮ and choose \"Allow restricted settings\" first.",
                13f, R.color.text_secondary,
            ),
            matchWidth(dp(12)),
        )
        statusCard.addView(pillButton("Open App info", ButtonStyle.SECONDARY) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }, matchWidth(dp(8)))
    }

    private fun renderApps() {
        appsList.removeAllViews()
        val blocked = smarter.settings.blockedApps.sortedBy { appLabel(it).lowercase() }
        if (blocked.isEmpty()) {
            appsList.addView(label("No apps blocked yet.", 14f, R.color.text_secondary), matchWidth(dp(8)))
            return
        }
        val settings = smarter.settings.blockerSettings
        val now = System.currentTimeMillis()
        for (app in blocked) {
            val day = smarter.ledger.today(app, now)
            val limitMs = settings.dailyAllowanceMinutes * UsageLedger.MINUTE_MS + day.bonusMs
            val usedMinutes = day.usedMs / UsageLedger.MINUTE_MS
            val limitMinutes = limitMs / UsageLedger.MINUTE_MS
            val unlocks = when (day.unlocks) {
                0 -> ""
                1 -> " · 1 unlock"
                else -> " · ${day.unlocks} unlocks"
            }
            val locked = day.usedMs >= limitMs

            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000
                progress = if (limitMs == 0L) 1000 else (day.usedMs * 1000 / limitMs).toInt().coerceIn(0, 1000)
                progressTintList = ColorStateList.valueOf(colorOf(if (locked) R.color.danger else R.color.accent))
                progressBackgroundTintList = ColorStateList.valueOf(colorOf(R.color.track))
            }
            val text = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(appLabel(app), 16f, bold = true))
                addView(
                    label(
                        (if (locked) "Locked · " else "") + "$usedMinutes of $limitMinutes min today$unlocks",
                        13f, R.color.text_secondary,
                    ),
                )
                addView(bar, matchWidth(dp(6)))
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(ImageView(context).apply { setImageDrawable(appIcon(app)) }, LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                    marginEnd = dp(14)
                })
                addView(text, weighted())
            }
            appsList.addView(row, matchWidth(dp(14)))
        }
    }

    private fun renderRules() {
        rulesCard.removeAllViews()
        val s = smarter.settings.blockerSettings
        rulesCard.addView(label("Rules", 18f, bold = true))
        rulesCard.addView(stepper("Free time per app each day", "${s.dailyAllowanceMinutes} min",
            { update(s.copy(dailyAllowanceMinutes = (s.dailyAllowanceMinutes - 5).coerceAtLeast(0))) },
            { update(s.copy(dailyAllowanceMinutes = (s.dailyAllowanceMinutes + 5).coerceAtMost(600))) }))
        rulesCard.addView(stepper("Puzzles per unlock", "${s.puzzlesPerUnlock}",
            { update(s.copy(puzzlesPerUnlock = (s.puzzlesPerUnlock - 1).coerceAtLeast(1))) },
            { update(s.copy(puzzlesPerUnlock = (s.puzzlesPerUnlock + 1).coerceAtMost(10))) }))
        rulesCard.addView(stepper("Time per unlock", "${s.unlockMinutes} min",
            { update(s.copy(unlockMinutes = (s.unlockMinutes - 5).coerceAtLeast(5))) },
            { update(s.copy(unlockMinutes = (s.unlockMinutes + 5).coerceAtMost(120))) }))
        rulesCard.addView(stepper("Difficulty", s.difficulty.label,
            { update(s.copy(difficulty = s.difficulty.harder(-1))) },
            { update(s.copy(difficulty = s.difficulty.harder(1))) }))
        rulesCard.addView(toggle(
            "Harder after each unlock",
            "Each extra unlock of the same app on the same day needs one more puzzle (up to " +
                "+${BlockerSettings.MAX_EXTRA_PUZZLES}) and harder ones.",
            s.escalate,
        ) { update(s.copy(escalate = it)) })
        rulesCard.addView(toggle(
            "Fresh puzzles from Lichess",
            "Uses lichess.org when online. Offline, a small built-in set is used.",
            smarter.settings.onlinePuzzles,
        ) { smarter.settings.onlinePuzzles = it })
    }

    private fun update(settings: BlockerSettings) {
        smarter.settings.blockerSettings = settings
        renderRules()
        renderApps()
    }

    private fun stepper(title: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = matchWidth(dp(12))
            addView(label(title, 15f), weighted())
            addView(roundButton("−", onMinus))
            addView(label(value, 15f, bold = true).apply {
                gravity = Gravity.CENTER
                minWidth = dp(72)
            })
            addView(roundButton("+", onPlus))
        }

    private fun roundButton(text: String, onClick: () -> Unit): TextView =
        pillButton(text, ButtonStyle.SECONDARY, onClick).apply {
            minHeight = dp(40)
            minWidth = dp(44)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            contentDescription = if (text == "+") "Increase" else "Decrease"
        }

    private fun toggle(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = matchWidth(dp(16))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(title, 15f))
                addView(label(description, 13f, R.color.text_secondary))
            }, weighted())
            addView(Switch(context).apply {
                isChecked = checked
                setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
            })
        }
}
