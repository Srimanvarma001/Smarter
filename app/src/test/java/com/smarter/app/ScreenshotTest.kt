package com.smarter.app

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import com.smarter.app.ui.AppPickerActivity
import com.smarter.app.ui.MainActivity
import com.smarter.app.ui.PuzzleActivity
import com.smarter.core.blocker.UsageLedger
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/**
 * Renders the real screens on the JVM at the Nothing Phone (4a)'s resolution
 * (1224 × 2720 px, 6.78″, 20:9; 3× density → 408 × 906 dp) and saves them to
 * app/build/screenshots. Doubles as a smoke test: any crash while creating a screen fails it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w408dp-h906dp-long-notround-port-xxhdpi-night")
class ScreenshotTest {
    private val app get() = RuntimeEnvironment.getApplication() as SmarterApp

    @Before
    fun setUp() {
        app.settings.onlinePuzzles = false // never hit the network from tests
        for ((pkg, label) in APPS) installLauncherApp(pkg, label)
        app.settings.blockedApps = APPS.keys.take(3).toSet()
    }

    @Test
    fun dashboard() {
        val now = System.currentTimeMillis()
        app.ledger.addUsage(INSTAGRAM, 31 * UsageLedger.MINUTE_MS, now) // used up: locked
        app.ledger.addUsage(YOUTUBE, 12 * UsageLedger.MINUTE_MS, now)
        repeat(4) { app.settings.recordSolved() }
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        save(activity, "1-dashboard")
    }

    @Test
    @Config(qualifiers = "+notnight")
    fun dashboardLight() {
        app.ledger.addUsage(YOUTUBE, 12 * UsageLedger.MINUTE_MS, System.currentTimeMillis())
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        save(activity, "5-dashboard-light")
    }

    @Test
    fun lockScreen() {
        app.ledger.addUsage(INSTAGRAM, 31 * UsageLedger.MINUTE_MS, System.currentTimeMillis())
        val controller = Robolectric.buildActivity(PuzzleActivity::class.java, PuzzleActivity.lockIntent(app, INSTAGRAM)).setup()
        waitUntil { controller.get().isShowingPuzzle }
        save(controller.get(), "2-lock-screen")
    }

    @Test
    fun practice() {
        val controller = Robolectric.buildActivity(PuzzleActivity::class.java, PuzzleActivity.practiceIntent(app)).setup()
        waitUntil { controller.get().isShowingPuzzle }
        save(controller.get(), "3-practice")
    }

    @Test
    fun appPicker() {
        val activity = Robolectric.buildActivity(AppPickerActivity::class.java).setup().get()
        repeat(10) { idle(100) } // apps load on a background thread
        save(activity, "4-app-picker")
    }

    private fun installLauncherApp(pkg: String, label: String) {
        val pm = shadowOf(app.packageManager)
        pm.installPackage(PackageInfo().apply {
            packageName = pkg
            applicationInfo = ApplicationInfo().apply {
                packageName = pkg
                nonLocalizedLabel = label
            }
        })
        val component = ComponentName(pkg, "$pkg.MainActivity")
        pm.addActivityIfNotPresent(component)
        pm.addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
    }

    private fun idle(ms: Long = 0) {
        if (ms > 0) Thread.sleep(ms)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(maxOf(ms, 16)))
    }

    private fun waitUntil(condition: () -> Boolean) {
        repeat(100) {
            if (condition()) return
            idle(50)
        }
        assertTrue("timed out waiting for the screen to load", condition())
    }

    private fun save(activity: Activity, name: String) {
        idle(300) // let entry animations settle
        val view = activity.window.decorView
        assertTrue("screen was not laid out", view.width > 0 && view.height > 0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val INSTAGRAM = "com.instagram.android"
        const val YOUTUBE = "com.google.android.youtube"
        val APPS = linkedMapOf(
            INSTAGRAM to "Instagram",
            YOUTUBE to "YouTube",
            "com.zhiliaoapp.musically" to "TikTok",
            "com.reddit.frontpage" to "Reddit",
            "com.spotify.music" to "Spotify",
            "com.whatsapp" to "WhatsApp",
        )
    }
}
