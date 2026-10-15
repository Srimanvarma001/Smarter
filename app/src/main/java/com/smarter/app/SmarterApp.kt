package com.smarter.app

import android.app.Application
import android.content.Context
import com.smarter.app.data.PuzzleRepository
import com.smarter.app.data.SettingsRepository
import com.smarter.app.data.SharedPrefsUsageStore
import com.smarter.core.blocker.UsageLedger

class SmarterApp : Application() {
    val settings by lazy { SettingsRepository(this) }
    val ledger by lazy { UsageLedger(SharedPrefsUsageStore(this)) }
    val puzzles by lazy { PuzzleRepository(this, settings) }
}

val Context.smarter: SmarterApp get() = applicationContext as SmarterApp
