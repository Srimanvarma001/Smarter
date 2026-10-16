package com.smarter.app.ui

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.smarter.app.R
import com.smarter.app.smarter
import java.util.concurrent.Executors

/** Lists launchable apps so the user can pick which ones to limit. */
class AppPickerActivity : Activity() {
    private class AppEntry(val packageName: String, val label: String, val icon: Drawable)

    private var allApps: List<AppEntry> = emptyList()
    private var shown: List<AppEntry> = emptyList()
    private var query = ""
    private lateinit var adapter: AppAdapter
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = AppAdapter()
        emptyText = body("Loading apps…", 15f, R.color.text_secondary).apply { gravity = Gravity.CENTER }

        val search = EditText(this).apply {
            hint = "Search apps"
            setSingleLine()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(colorOf(R.color.text_primary))
            setHintTextColor(colorOf(R.color.text_secondary))
            background = roundedBackground(colorOf(R.color.surface), 24, colorOf(R.color.outline))
            setPadding(dp(18), 0, dp(18), 0)
            minHeight = dp(48)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString().orEmpty()
                    applyFilter()
                }
            })
        }
        val list = ListView(this).apply {
            adapter = this@AppPickerActivity.adapter
            divider = null
            selector = getDrawable(android.R.color.transparent)
            isVerticalScrollBarEnabled = false
            emptyView = emptyText
            setOnItemClickListener { _, view, position, _ -> toggle(shown[position].packageName, view) }
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(circleButton("←", 40, "Back") { finish() })
            addView(dotoText("CHOOSE APPS", 30f), wrap(dp(12)))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorOf(R.color.background))
            setPadding(dp(16), dp(12), dp(16), 0)
            addView(header, matchWidth())
            addView(
                body("Each app you pick gets its own daily free time. After that, it takes chess puzzles to unlock.", 15f, R.color.text_secondary),
                matchWidth(dp(14)),
            )
            addView(search, matchWidth(dp(16)))
            addView(emptyText, matchWidth(dp(24)))
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(6) })
        }
        fitSystemBars(root)
        setContentView(root)
        loadApps()
    }

    private fun loadApps() {
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            val apps = packageManager.queryIntentActivities(launcherIntent, 0)
                .map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != packageName }
                .map { AppEntry(it.packageName, it.loadLabel(packageManager).toString(), it.loadIcon(packageManager)) }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                val blocked = smarter.settings.blockedApps
                // Already-blocked apps first, then alphabetical.
                allApps = apps.sortedWith(compareBy<AppEntry> { it.packageName !in blocked }.thenBy { it.label.lowercase() })
                emptyText.text = "No apps match."
                applyFilter()
            }
            executor.shutdown()
        }
    }

    private fun applyFilter() {
        shown = if (query.isBlank()) allApps else allApps.filter { it.label.contains(query.trim(), ignoreCase = true) }
        adapter.notifyDataSetChanged()
    }

    private fun toggle(packageName: String, row: View) {
        val blocked = smarter.settings.blockedApps
        smarter.settings.blockedApps = if (packageName in blocked) blocked - packageName else blocked + packageName
        row.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        adapter.notifyDataSetChanged()
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView as? LinearLayout ?: createRow()
            val entry = shown[position]
            val blocked = entry.packageName in smarter.settings.blockedApps
            (row.getChildAt(0) as ImageView).setImageDrawable(entry.icon)
            (row.getChildAt(1) as TextView).text = entry.label
            (row.getChildAt(2) as CheckDot).checked = blocked
            row.contentDescription = entry.label + if (blocked) ", limited" else ", not limited"
            return row
        }

        private fun createRow() = LinearLayout(this@AppPickerActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(66)
            setPadding(dp(2), dp(10), dp(4), dp(10))
            addView(ImageView(context), LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(14) })
            addView(body(sizeSp = 16f, bold = true), weighted())
            addView(CheckDot(context))
        }
    }
}
