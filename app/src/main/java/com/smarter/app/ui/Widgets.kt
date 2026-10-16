package com.smarter.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.smarter.app.R

// Nothing-style custom widgets drawn with dots.

/** A row of dots filled left to right, like a Glyph progress bar. */
class DotsBar(context: Context) : View(context) {
    var dotCount = 16
        set(value) { field = value; requestLayout() }
    var filled = 0
        set(value) { field = value; invalidate() }
    var alert = false
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = context.dpf(7f)
    private val gap = context.dpf(4f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((dotCount * dot + (dotCount - 1) * gap).toInt() + 1, dot.toInt() + 1)
    }

    override fun onDraw(canvas: Canvas) {
        for (i in 0 until dotCount) {
            paint.color = when {
                i >= filled -> context.colorOf(R.color.track)
                alert -> context.colorOf(R.color.accent)
                else -> context.colorOf(R.color.text_primary)
            }
            canvas.drawCircle(i * (dot + gap) + dot / 2, dot / 2, dot / 2, paint)
        }
    }
}

/** Puzzle progress: solved dots are filled, the current one pulses red, the rest are rings. */
class ProgressDots(context: Context) : View(context) {
    var total = 3
        set(value) { field = value; requestLayout() }
    var done = 0
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val size = context.dpf(16f)
    private val gap = context.dpf(10f)
    private val stroke = context.dpf(1.5f)
    private var pulse = 0f
    private val pulseAnimator = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
        duration = 1400
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            pulse = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        pulseAnimator.start()
    }

    override fun onDetachedFromWindow() {
        pulseAnimator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val halo = context.dpf(5f)
        setMeasuredDimension((total * size + (total - 1).coerceAtLeast(0) * gap + 2 * halo).toInt(), (size + 2 * halo).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val halo = context.dpf(5f)
        val r = size / 2
        for (i in 0 until total) {
            val cx = halo + i * (size + gap) + r
            val cy = halo + r
            when {
                i < done -> {
                    paint.style = Paint.Style.FILL
                    paint.color = context.colorOf(R.color.text_primary)
                    canvas.drawCircle(cx, cy, r, paint)
                }
                i == done -> {
                    paint.style = Paint.Style.FILL
                    paint.color = context.colorOf(R.color.accent)
                    paint.alpha = (64 * pulse).toInt()
                    canvas.drawCircle(cx, cy, r + halo * pulse, paint)
                    paint.alpha = 255
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = stroke
                    canvas.drawCircle(cx, cy, r - stroke / 2, paint)
                }
                else -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = stroke
                    paint.color = context.colorOf(R.color.outline)
                    canvas.drawCircle(cx, cy, r - stroke / 2, paint)
                }
            }
        }
    }
}

/** An on/off switch: white track with a dark knob when on, grey when off. */
class ToggleView(context: Context) : View(context) {
    var checked = false
        private set
    var onChange: ((Boolean) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var position = 0f
    private var animator: ValueAnimator? = null

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener {
            setChecked(!checked, animate = true)
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onChange?.invoke(checked)
        }
    }

    fun setChecked(value: Boolean, animate: Boolean = false) {
        checked = value
        contentDescription = if (value) "On" else "Off"
        animator?.cancel()
        val target = if (value) 1f else 0f
        if (!animate) {
            position = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(position, target).apply {
            duration = 180
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                position = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(context.dp(52), context.dp(30))
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        rect.set(0f, 0f, width.toFloat(), h)
        paint.color = blend(context.colorOf(R.color.track), context.colorOf(R.color.text_primary), position)
        canvas.drawRoundRect(rect, h / 2, h / 2, paint)
        val inset = context.dpf(4f)
        val knob = h - 2 * inset
        val x = inset + (width - 2 * inset - knob) * position + knob / 2
        paint.color = blend(context.colorOf(R.color.text_secondary), context.colorOf(R.color.background), position)
        canvas.drawCircle(x, h / 2, knob / 2, paint)
    }

    private fun blend(from: Int, to: Int, t: Float): Int {
        fun channel(shift: Int) = (((from shr shift) and 0xFF) * (1 - t) + ((to shr shift) and 0xFF) * t).toInt() shl shift
        return channel(24) or channel(16) or channel(8) or channel(0)
    }
}

/** Selection marker for the app picker: an empty ring, or a red dot when picked. */
class CheckDot(context: Context) : View(context) {
    var checked = false
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(context.dp(24), context.dp(24))
    }

    override fun onDraw(canvas: Canvas) {
        val c = width / 2f
        val stroke = context.dpf(1.5f)
        if (checked) {
            paint.style = Paint.Style.FILL
            paint.color = context.colorOf(R.color.accent)
            canvas.drawCircle(c, c, c, paint)
            paint.color = context.colorOf(R.color.on_accent)
            canvas.drawCircle(c, c, context.dpf(4f), paint)
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.color = context.colorOf(R.color.text_secondary)
            canvas.drawCircle(c, c, c - stroke / 2, paint)
        }
    }
}
