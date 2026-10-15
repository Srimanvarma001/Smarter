package com.smarter.app.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.TextView
import com.smarter.app.R
import kotlin.math.roundToInt

// Small helpers for building the UI in code (the app has no AndroidX dependencies).

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

fun Context.colorOf(id: Int): Int = getColor(id)

fun Context.appLabel(packageName: String): String = try {
    packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
} catch (e: Exception) {
    packageName
}

fun Context.appIcon(packageName: String): Drawable? = try {
    packageManager.getApplicationIcon(packageName)
} catch (e: Exception) {
    null
}

fun Context.label(
    text: CharSequence = "",
    sizeSp: Float = 16f,
    color: Int = R.color.text_primary,
    bold: Boolean = false,
): TextView = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    setTextColor(colorOf(color))
    if (bold) typeface = Typeface.DEFAULT_BOLD
}

fun Context.roundedBackground(fill: Int, radiusDp: Int = 16, strokeColor: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeColor != null) setStroke(dp(1), strokeColor)
    }

enum class ButtonStyle { PRIMARY, SECONDARY }

fun Context.pillButton(text: String, style: ButtonStyle = ButtonStyle.PRIMARY, onClick: () -> Unit): TextView =
    label(text, 16f, if (style == ButtonStyle.PRIMARY) R.color.on_accent else R.color.text_primary, bold = true).apply {
        gravity = Gravity.CENTER
        minHeight = dp(48)
        setPadding(dp(20), dp(12), dp(20), dp(12))
        val fill = if (style == ButtonStyle.PRIMARY) colorOf(R.color.accent) else colorOf(R.color.surface)
        val stroke = if (style == ButtonStyle.SECONDARY) colorOf(R.color.outline) else null
        background = RippleDrawable(
            ColorStateList.valueOf(colorOf(R.color.outline)),
            roundedBackground(fill, 24, stroke),
            null,
        )
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

fun Context.card(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = roundedBackground(colorOf(R.color.surface), 20, colorOf(R.color.outline))
    setPadding(dp(18), dp(16), dp(18), dp(18))
}

fun matchWidth(topMarginPx: Int = 0): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin = topMarginPx
    }

fun weighted(weight: Float = 1f): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)

/** Pads [root] so content stays clear of the status and navigation bars (edge-to-edge on Android 15+). */
fun Activity.fitSystemBars(root: View) {
    val left = root.paddingLeft
    val top = root.paddingTop
    val right = root.paddingRight
    val bottom = root.paddingBottom
    root.setOnApplyWindowInsetsListener { view, insets ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom)
        } else {
            @Suppress("DEPRECATION")
            view.setPadding(
                left + insets.systemWindowInsetLeft,
                top + insets.systemWindowInsetTop,
                right + insets.systemWindowInsetRight,
                bottom + insets.systemWindowInsetBottom,
            )
        }
        insets
    }
    root.requestApplyInsets()
}
