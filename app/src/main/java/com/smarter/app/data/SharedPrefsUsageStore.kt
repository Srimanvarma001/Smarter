package com.smarter.app.data

import android.content.Context
import com.smarter.core.blocker.AppDay
import com.smarter.core.blocker.UsageStore
import java.time.LocalDate

/** Stores each app's [AppDay] as "date|usedMs|bonusMs|unlocks". */
class SharedPrefsUsageStore(context: Context) : UsageStore {
    private val prefs = context.getSharedPreferences("usage", Context.MODE_PRIVATE)

    override fun load(packageName: String): AppDay? {
        val parts = prefs.getString(packageName, null)?.split('|') ?: return null
        if (parts.size != 4) return null
        return try {
            AppDay(LocalDate.parse(parts[0]), parts[1].toLong(), parts[2].toLong(), parts[3].toInt())
        } catch (e: RuntimeException) {
            null
        }
    }

    override fun save(packageName: String, day: AppDay) {
        prefs.edit().putString(packageName, "${day.date}|${day.usedMs}|${day.bonusMs}|${day.unlocks}").apply()
    }
}
