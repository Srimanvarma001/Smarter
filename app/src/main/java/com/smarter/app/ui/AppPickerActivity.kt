package com.smarter.app.ui

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
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
        emptyText = label("Loading apps…", 15f, R.color.text_secondary).apply { gravity = Gravity.CENTER }

        val search = EditText(this).apply {
            hint = "Search apps"
            setSingleLine()
            setTextColor(colorOf(R.color.text_primary))
            setHintTextColor(colorOf(R.color.text_secondary))
            background = roundedBackground(colorOf(R.color.surface), 24, colorOf(R.color.outline))
            setPadding(dp(18), dp(12), dp(18), dp(12))
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
            emptyView = emptyText
            setOnItemClickListener { _, _, position, _ -> toggle(shown[position].packageName) }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorOf(R.color.background))
            setPadding(dp(20), dp(16), dp(20), 0)
            addView(label("Choose apps to limit", 24f, bold = true))
            addView(
                label("Each app you pick gets its own daily free time. After that, it takes chess puzzles to unlock.", 14f, R.color.text_secondary),
                matchWidth(dp(4)),
            )
            addView(search, matchWidth(dp(16)))
            addView(emptyText, matchWidth(dp(24)))
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) })
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

    private fun toggle(packageName: String) {
        val blocked = smarter.settings.blockedApps
        smarter.settings.blockedApps = if (packageName in blocked) blocked - packageName else blocked + packageName
        adapter.notifyDataSetChanged()
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView as? LinearLayout ?: createRow()
            val entry = shown[position]
            (row.getChildAt(0) as ImageView).setImageDrawable(entry.icon)
            (row.getChildAt(1) as TextView).text = entry.label
            (row.getChildAt(2) as CheckBox).isChecked = entry.packageName in smarter.settings.blockedApps
            return row
        }

        private fun createRow() = LinearLayout(this@AppPickerActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(60)
            setPadding(dp(4), dp(8), dp(4), dp(8))
            addView(ImageView(context), LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(14) })
            addView(label(sizeSp = 16f), weighted())
            // The row handles clicks; the checkbox only shows the state.
            addView(CheckBox(context).apply {
                isClickable = false
                isFocusable = false
            })
        }
    }
}
