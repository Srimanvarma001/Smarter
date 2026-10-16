package com.smarter.app.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.TextView
import com.smarter.app.R
import kotlin.math.roundToInt

// Small helpers for building the UI in code (the app has no AndroidX dependencies).
// The look is deliberately plain: the system font, sentence case, flat surfaces and one
// green accent that matches the board.

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

fun Context.dpf(value: Float): Float = value * resources.displayMetrics.density

fun Context.colorOf(id: Int): Int = getColor(id)

fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

fun Context.appLabel(packageName: String): String = try {
    packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
} catch (e: Exception) {
    packageName
}

/** The app's icon, or a letter badge if it can't be loaded (e.g. the app was uninstalled). */
fun Context.appIcon(packageName: String): Drawable = try {
    packageManager.getApplicationInfo(packageName, 0).loadIcon(packageManager)
} catch (e: Exception) {
    LetterIconDrawable(appLabel(packageName).first().uppercaseChar(), colorOf(R.color.surface_2), colorOf(R.color.text_primary), typeface(600))
}

/** The system sans-serif at [weight] (400 regular, 500 medium, 600 semibold, 700 bold). */
fun typeface(weight: Int): Typeface = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> Typeface.create(Typeface.DEFAULT, weight, false)
    weight >= 700 -> Typeface.DEFAULT_BOLD
    weight >= 500 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
    else -> Typeface.DEFAULT
}

fun Context.textView(
    text: CharSequence = "",
    sizeSp: Float = 15f,
    color: Int = R.color.text_primary,
    weight: Int = 400,
): TextView = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    setTextColor(colorOf(color))
    typeface = typeface(weight)
    setLineSpacing(0f, 1.15f)
}

/** Screen title, e.g. "Smarter" or "Choose apps". */
fun Context.title(text: CharSequence = "", sizeSp: Float = 26f): TextView =
    textView(text, sizeSp, weight = 700).apply {
        letterSpacing = -0.01f
        includeFontPadding = false
    }

/** Quiet heading above a group of rows. */
fun Context.sectionTitle(text: CharSequence): TextView = textView(text, 14f, R.color.text_secondary, weight = 600)

fun Context.roundedBackground(fill: Int, radiusDp: Int = 12, strokeColor: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeColor != null) setStroke(dp(1), strokeColor)
    }

fun Context.circleBackground(fill: Int): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
    }

private fun Context.ripple(content: Drawable?, mask: Drawable? = null): RippleDrawable =
    RippleDrawable(ColorStateList.valueOf(withAlpha(colorOf(R.color.text_primary), 0x1F)), content, mask)

enum class ButtonStyle { PRIMARY, SECONDARY, TEXT }

fun Context.button(
    text: String,
    style: ButtonStyle = ButtonStyle.PRIMARY,
    small: Boolean = false,
    onClick: () -> Unit,
): TextView = TextView(this).apply {
    this.text = text
    typeface = typeface(600)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, if (small) 14f else 16f)
    gravity = Gravity.CENTER
    val height = if (small) 36 else 52
    minHeight = dp(height)
    minimumHeight = dp(height)
    setPadding(dp(if (small) 12 else 20), 0, dp(if (small) 12 else 20), 0)
    val textColor = when (style) {
        ButtonStyle.PRIMARY -> R.color.on_accent
        ButtonStyle.SECONDARY -> R.color.text_primary
        ButtonStyle.TEXT -> R.color.accent
    }
    setTextColor(colorOf(textColor))
    val radius = if (small) 10 else 12
    background = when (style) {
        ButtonStyle.PRIMARY -> ripple(roundedBackground(colorOf(R.color.accent), radius))
        ButtonStyle.SECONDARY -> ripple(roundedBackground(colorOf(R.color.surface_2), radius))
        ButtonStyle.TEXT -> ripple(null, roundedBackground(colorOf(R.color.text_primary), radius))
    }
    isClickable = true
    isFocusable = true
    setOnClickListener {
        it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        onClick()
    }
}

/** A round, borderless icon button (back arrow, stepper −/+). */
fun Context.iconButton(
    symbol: String,
    sizeDp: Int = 40,
    description: String,
    filled: Boolean = false,
    onClick: () -> Unit,
): TextView = TextView(this).apply {
    text = symbol
    typeface = typeface(500)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, if (filled) 18f else 22f)
    setTextColor(colorOf(R.color.text_primary))
    gravity = Gravity.CENTER
    includeFontPadding = false
    contentDescription = description
    background = if (filled) ripple(circleBackground(colorOf(R.color.surface_2))) else ripple(null, circleBackground(colorOf(R.color.text_primary)))
    layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    isClickable = true
    setOnClickListener {
        it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        onClick()
    }
}

/** A small tinted pill such as "Locked" or "On". */
fun Context.tag(text: String, color: Int, dot: Boolean = false): TextView = TextView(this).apply {
    val tint = colorOf(color)
    this.text = if (dot) "●  $text" else text
    typeface = typeface(600)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
    setTextColor(tint)
    background = roundedBackground(withAlpha(tint, 0x24), 99)
    setPadding(dp(10), dp(4), dp(10), dp(4))
}

/** A flat rounded surface grouping related rows. */
fun Context.card(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = roundedBackground(colorOf(R.color.surface), 16, colorOf(R.color.outline))
    setPadding(dp(16), dp(4), dp(16), dp(4))
}

fun Context.divider(): View = View(this).apply {
    setBackgroundColor(colorOf(R.color.outline))
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
}

fun matchWidth(topMarginPx: Int = 0): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin = topMarginPx
    }

fun weighted(weight: Float = 1f): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)

fun wrap(startMarginPx: Int = 0): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        marginStart = startMarginPx
    }

/** Confirm / reject haptics for right and wrong moves. */
fun View.hapticConfirm() = performHapticFeedback(
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
)

fun View.hapticReject() = performHapticFeedback(
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS,
)

/** Pads [root] so content stays clear of the status bar, camera cutout and navigation bar. */
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

/** A rounded-square badge with one letter, used when an app icon is unavailable. */
class LetterIconDrawable(
    private val letter: Char,
    private val background: Int,
    private val foreground: Int,
    typeface: Typeface,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        val radius = rect.width() * 0.28f
        paint.color = background
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.color = foreground
        paint.textSize = rect.height() * 0.42f
        canvas.drawText(letter.toString(), rect.centerX(), rect.centerY() - (paint.descent() + paint.ascent()) / 2, paint)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
