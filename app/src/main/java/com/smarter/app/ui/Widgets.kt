package com.smarter.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.smarter.app.R

// Small custom widgets drawn on a canvas.

/** A thin rounded progress bar: how much of an app's time is used. Red once it's locked. */
class UsageBar(context: Context) : View(context) {
    var fraction = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }
    var alert = false
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), context.dp(4))
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        rect.set(0f, 0f, width.toFloat(), h)
        paint.color = context.colorOf(R.color.track)
        canvas.drawRoundRect(rect, h / 2, h / 2, paint)
        if (fraction <= 0f) return
        rect.right = (width * fraction).coerceAtLeast(h)
        paint.color = context.colorOf(if (alert) R.color.danger else R.color.accent)
        canvas.drawRoundRect(rect, h / 2, h / 2, paint)
    }
}

/** Puzzle progress: one segment per puzzle, filled green once solved. */
class SegmentedProgress(context: Context) : View(context) {
    var total = 3
        set(value) { field = value; invalidate() }
    var done = 0
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val gap = context.dpf(4f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), context.dp(4))
    }

    override fun onDraw(canvas: Canvas) {
        if (total <= 0) return
        val h = height.toFloat()
        val segment = (width - gap * (total - 1)) / total
        for (i in 0 until total) {
            val left = i * (segment + gap)
            rect.set(left, 0f, left + segment, h)
            paint.color = context.colorOf(if (i < done) R.color.accent else R.color.track)
            canvas.drawRoundRect(rect, h / 2, h / 2, paint)
        }
    }
}

/** An on/off switch: green track when on, grey when off, with a white knob. */
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
            duration = 160
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                position = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(context.dp(48), context.dp(28))
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        rect.set(0f, 0f, width.toFloat(), h)
        paint.color = blend(context.colorOf(R.color.track), context.colorOf(R.color.accent), position)
        canvas.drawRoundRect(rect, h / 2, h / 2, paint)
        val inset = context.dpf(3f)
        val knob = h - 2 * inset
        val x = inset + (width - 2 * inset - knob) * position + knob / 2
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(x, h / 2, knob / 2, paint)
    }

    private fun blend(from: Int, to: Int, t: Float): Int {
        fun channel(shift: Int) = (((from shr shift) and 0xFF) * (1 - t) + ((to shr shift) and 0xFF) * t).toInt() shl shift
        return channel(24) or channel(16) or channel(8) or channel(0)
    }
}

/** Selection marker for the app picker: an empty ring, or a green disc with a tick. */
class CheckMark(context: Context) : View(context) {
    var checked = false
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tick = Path()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(context.dp(24), context.dp(24))
    }

    override fun onDraw(canvas: Canvas) {
        val size = width.toFloat()
        val c = size / 2
        val stroke = context.dpf(2f)
        if (checked) {
            paint.style = Paint.Style.FILL
            paint.color = context.colorOf(R.color.accent)
            canvas.drawCircle(c, c, c, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = context.colorOf(R.color.on_accent)
            tick.reset()
            tick.moveTo(size * 0.29f, size * 0.52f)
            tick.lineTo(size * 0.44f, size * 0.66f)
            tick.lineTo(size * 0.72f, size * 0.37f)
            canvas.drawPath(tick, paint)
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.color = context.colorOf(R.color.track)
            canvas.drawCircle(c, c, c - stroke / 2, paint)
        }
    }
}
