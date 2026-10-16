package com.smarter.app.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
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

// Small helpers for building the Nothing-style UI in code (the app has no AndroidX
// dependencies). Display text uses the dot-matrix Doto font, labels use Space Mono.

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

fun Context.dpf(value: Float): Float = value * resources.displayMetrics.density

fun Context.colorOf(id: Int): Int = getColor(id)

fun Context.appLabel(packageName: String): String = try {
    packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
} catch (e: Exception) {
    packageName
}

/** The app's icon, or a letter badge if it can't be loaded (e.g. the app was uninstalled). */
fun Context.appIcon(packageName: String): Drawable = try {
    packageManager.getApplicationInfo(packageName, 0).loadIcon(packageManager)
} catch (e: Exception) {
    LetterIconDrawable(appLabel(packageName).first().uppercaseChar(), colorOf(R.color.track), colorOf(R.color.text_primary), Fonts.monoBold(this))
}

object Fonts {
    /** Doto's variable axes: heavy weight and fully round dots, like Nothing's NDot. */
    const val DOTO_AXES = "'wght' 800, 'ROND' 100"

    fun doto(context: Context): Typeface = context.resources.getFont(R.font.doto)
    fun mono(context: Context): Typeface = context.resources.getFont(R.font.space_mono)
    fun monoBold(context: Context): Typeface = context.resources.getFont(R.font.space_mono_bold)
}

/** Body text in the system font. */
fun Context.body(
    text: CharSequence = "",
    sizeSp: Float = 15f,
    color: Int = R.color.text_primary,
    bold: Boolean = false,
): TextView = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    setTextColor(colorOf(color))
    setLineSpacing(0f, 1.15f)
    if (bold) {
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.DEFAULT, 600, false) else Typeface.DEFAULT_BOLD
    }
}

/** Big dot-matrix display text. */
fun Context.dotoText(text: CharSequence = "", sizeSp: Float, color: Int = R.color.text_primary): TextView =
    TextView(this).apply {
        this.text = text
        typeface = Fonts.doto(context)
        fontVariationSettings = Fonts.DOTO_AXES
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(colorOf(color))
        letterSpacing = 0.02f
        includeFontPadding = false
    }

/** Small uppercase monospace label, e.g. section titles and stats. */
fun Context.label(text: CharSequence = "", sizeSp: Float = 11f, color: Int = R.color.text_secondary): TextView =
    TextView(this).apply {
        this.text = text.toString().uppercase()
        typeface = Fonts.monoBold(context)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(colorOf(color))
        letterSpacing = 0.14f
        isAllCaps = true
    }

/** Monospace value text (numbers in stats and steppers). */
fun Context.monoValue(text: CharSequence = "", sizeSp: Float = 14f, color: Int = R.color.text_primary): TextView =
    TextView(this).apply {
        this.text = text
        typeface = Fonts.monoBold(context)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(colorOf(color))
    }

fun Context.roundedBackground(fill: Int, radiusDp: Int = 16, strokeColor: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeColor != null) setStroke(dp(1), strokeColor)
    }

fun Context.circleBackground(fill: Int, strokeColor: Int? = null, strokeDp: Float = 1f): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
        if (strokeColor != null) setStroke(dpf(strokeDp).roundToInt().coerceAtLeast(1), strokeColor)
    }

enum class ButtonStyle { PRIMARY, OUTLINE, RED }

fun Context.pillButton(
    text: String,
    style: ButtonStyle = ButtonStyle.PRIMARY,
    small: Boolean = false,
    onClick: () -> Unit,
): TextView = TextView(this).apply {
    this.text = text.uppercase()
    typeface = Fonts.monoBold(context)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, if (small) 11f else 13f)
    letterSpacing = 0.1f
    gravity = Gravity.CENTER
    val height = if (small) 36 else 52
    minHeight = dp(height)
    minimumHeight = dp(height)
    setPadding(dp(if (small) 16 else 22), 0, dp(if (small) 16 else 22), 0)
    val (fill, textColor, stroke) = when (style) {
        ButtonStyle.PRIMARY -> Triple(R.color.primary_bg, R.color.primary_fg, null)
        ButtonStyle.OUTLINE -> Triple(R.color.surface, R.color.text_primary, R.color.outline)
        ButtonStyle.RED -> Triple(R.color.accent, R.color.on_accent, null)
    }
    setTextColor(colorOf(textColor))
    background = RippleDrawable(
        ColorStateList.valueOf(colorOf(R.color.track)),
        roundedBackground(colorOf(fill), height / 2, stroke?.let { colorOf(it) }),
        null,
    )
    isClickable = true
    isFocusable = true
    setOnClickListener {
        it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        onClick()
    }
}

/** A round outlined icon button (back arrow, stepper −/+). */
fun Context.circleButton(text: String, sizeDp: Int = 40, description: String, onClick: () -> Unit): TextView =
    TextView(this).apply {
        this.text = text
        typeface = Fonts.mono(context)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setTextColor(colorOf(R.color.text_primary))
        gravity = Gravity.CENTER
        includeFontPadding = false
        contentDescription = description
        background = RippleDrawable(
            ColorStateList.valueOf(colorOf(R.color.track)),
            circleBackground(colorOf(R.color.surface), colorOf(R.color.outline)),
            null,
        )
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        isClickable = true
        setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onClick()
        }
    }

/** A status light: white when fine, glowing red when something needs attention. */
fun Context.led(red: Boolean, sizeDp: Int = 8): View = View(this).apply {
    background = circleBackground(colorOf(if (red) R.color.accent else R.color.text_primary))
    layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
}

/** Red pill tag such as "LOCKED". */
fun Context.redTag(text: String): TextView = TextView(this).apply {
    this.text = text.uppercase()
    typeface = Fonts.monoBold(context)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
    letterSpacing = 0.12f
    setTextColor(colorOf(R.color.on_accent))
    background = roundedBackground(colorOf(R.color.accent), 99)
    setPadding(dp(7), dp(2), dp(7), dp(2))
}

fun Context.card(dotGrid: Boolean = false, strokeColor: Int = colorOf(R.color.outline)): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = CardDrawable(
            fill = colorOf(R.color.surface),
            stroke = strokeColor,
            radius = dpf(24f),
            strokeWidth = dpf(1f),
            dotColor = if (dotGrid) colorOf(R.color.dot_grid) else null,
            dotSpacing = dpf(9f),
            dotRadius = dpf(1.1f),
        )
        setPadding(dp(18), dp(16), dp(18), dp(16))
    }

fun Context.divider(vertical: Boolean = false): View = View(this).apply {
    setBackgroundColor(colorOf(R.color.outline))
    layoutParams = if (vertical) {
        LinearLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT)
    } else {
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
    }
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

/** Confirm / reject haptics; Nothing phones have a strong, crisp haptic motor. */
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

/** Rounded card background, optionally with Nothing's dot-grid texture. */
class CardDrawable(
    private val fill: Int,
    private val stroke: Int,
    private val radius: Float,
    private val strokeWidth: Float,
    private val dotColor: Int?,
    private val dotSpacing: Float,
    private val dotRadius: Float,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val clip = Path()

    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        rect.inset(strokeWidth / 2, strokeWidth / 2)
        paint.style = Paint.Style.FILL
        paint.color = fill
        canvas.drawRoundRect(rect, radius, radius, paint)

        if (dotColor != null) {
            clip.reset()
            clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            paint.color = dotColor
            var y = bounds.top + dotSpacing / 2
            while (y < bounds.bottom) {
                var x = bounds.left + dotSpacing / 2
                while (x < bounds.right) {
                    canvas.drawCircle(x, y, dotRadius, paint)
                    x += dotSpacing
                }
                y += dotSpacing
            }
            canvas.restore()
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        paint.color = stroke
        canvas.drawRoundRect(rect, radius, radius, paint)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
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
        val radius = rect.width() * 0.3f
        paint.color = background
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.color = foreground
        paint.textSize = rect.height() * 0.45f
        canvas.drawText(letter.toString(), rect.centerX(), rect.centerY() - (paint.descent() + paint.ascent()) / 2, paint)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
