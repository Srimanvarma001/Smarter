package com.smarter.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import com.smarter.app.R
import com.smarter.core.chess.Color
import com.smarter.core.chess.Move
import com.smarter.core.chess.Piece
import com.smarter.core.chess.PieceType
import com.smarter.core.chess.Position
import com.smarter.core.chess.Square

/**
 * A chessboard you can play on by tapping or dragging. Monochrome squares with red
 * highlights, and the chessnut piece set as vector drawables so it is sharp at any size.
 */
class ChessBoardView(context: Context) : View(context) {
    /** Called with a legal move made by the user. */
    var onMove: ((Move) -> Unit)? = null

    /** Asks the user which piece to promote to; must eventually call back with one of the moves. */
    var choosePromotion: ((List<Move>, (Move) -> Unit) -> Unit)? = null

    /** When false the board only displays the position. */
    var interactive = false
        set(value) {
            field = value
            if (!value) clearSelection()
        }

    var whiteAtBottom = true
        set(value) {
            field = value
            invalidate()
        }

    /** Square to highlight as a hint, or -1. */
    var hintSquare = -1
        set(value) {
            field = value
            invalidate()
        }

    var position: Position = Position.initial()
        private set
    private var lastMove: Move? = null
    private var selected = -1
    private var targets: List<Move> = emptyList()
    private var dragging = false
    private var dragMoved = false
    /** A second tap on the selected piece (without dragging it away) deselects it. */
    private var deselectOnTap = false
    private var dragX = 0f
    private var dragY = 0f

    private var animatedMove: Move? = null
    private var animationProgress = 1f
    private var animator: ValueAnimator? = null

    private val lightSquare = context.colorOf(R.color.square_light)
    private val darkSquare = context.colorOf(R.color.square_dark)
    private val accent = context.colorOf(R.color.accent)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val coordinatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.monoBold(context) }
    private val pieces = HashMap<Piece, Drawable>()

    private val squareSize get() = width / 8f

    init {
        // Rounded corners, clipped by the view outline.
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, context.dpf(10f))
            }
        }
        clipToOutline = true
    }

    fun setPosition(position: Position, lastMove: Move?, animate: Boolean = false) {
        animator?.cancel()
        this.position = position
        this.lastMove = lastMove
        clearSelection()
        if (animate && lastMove != null && isLaidOut) {
            animatedMove = lastMove
            animationProgress = 0f
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 220
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    animationProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            animatedMove = null
            animationProgress = 1f
        }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val size = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) width else minOf(width, height)
        setMeasuredDimension(size, size)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    // --- Geometry -------------------------------------------------------------------------

    private fun squareLeft(sq: Int): Float {
        val file = Square.file(sq)
        return (if (whiteAtBottom) file else 7 - file) * squareSize
    }

    private fun squareTop(sq: Int): Float {
        val rank = Square.rank(sq)
        return (if (whiteAtBottom) 7 - rank else rank) * squareSize
    }

    private fun squareAt(x: Float, y: Float): Int {
        if (x < 0 || y < 0 || x >= width || y >= height) return -1
        val col = (x / squareSize).toInt().coerceIn(0, 7)
        val row = (y / squareSize).toInt().coerceIn(0, 7)
        return if (whiteAtBottom) Square.of(col, 7 - row) else Square.of(7 - col, row)
    }

    // --- Drawing --------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val size = squareSize
        paint.style = Paint.Style.FILL
        paint.shader = null
        for (sq in 0 until 64) {
            val left = squareLeft(sq)
            val top = squareTop(sq)
            val light = (Square.file(sq) + Square.rank(sq)) % 2 == 1
            paint.color = if (light) lightSquare else darkSquare
            canvas.drawRect(left, top, left + size, top + size, paint)

            val overlayAlpha = when {
                sq == selected -> 0x80
                lastMove?.let { sq == it.from || sq == it.to } == true -> 0x48
                else -> 0
            }
            if (overlayAlpha > 0) {
                paint.color = withAlpha(accent, overlayAlpha)
                canvas.drawRect(left, top, left + size, top + size, paint)
            }
        }

        drawCoordinates(canvas)
        drawCheck(canvas)
        drawHint(canvas)
        drawTargets(canvas)

        for (sq in 0 until 64) {
            val piece = position[sq] ?: continue
            if (dragging && dragMoved && sq == selected) continue
            val move = animatedMove
            if (move != null && sq == move.to && animationProgress < 1f) continue
            drawPiece(canvas, piece, squareLeft(sq), squareTop(sq), size)
        }

        val move = animatedMove
        if (move != null && animationProgress < 1f) {
            position[move.to]?.let { piece ->
                val x = squareLeft(move.from) + (squareLeft(move.to) - squareLeft(move.from)) * animationProgress
                val y = squareTop(move.from) + (squareTop(move.to) - squareTop(move.from)) * animationProgress
                drawPiece(canvas, piece, x, y, size)
            }
        }
        if (dragging && dragMoved && selected >= 0) {
            // The dragged piece is drawn larger and above the finger so it stays visible.
            val big = size * 1.25f
            position[selected]?.let { drawPiece(canvas, it, dragX - big / 2, dragY - big / 2 - size * 0.45f, big) }
        }
    }

    private fun drawCoordinates(canvas: Canvas) {
        val size = squareSize
        coordinatePaint.textSize = size * 0.19f
        for (i in 0..7) {
            // Rank numbers down the left edge, file letters along the bottom edge.
            val rankSq = if (whiteAtBottom) Square.of(0, i) else Square.of(7, i)
            coordinatePaint.color = if ((Square.file(rankSq) + i) % 2 == 1) darkSquare else lightSquare
            canvas.drawText("${i + 1}", squareLeft(rankSq) + size * 0.06f, squareTop(rankSq) + size * 0.22f, coordinatePaint)

            val fileSq = if (whiteAtBottom) Square.of(i, 0) else Square.of(i, 7)
            coordinatePaint.color = if ((i + Square.rank(fileSq)) % 2 == 1) darkSquare else lightSquare
            val letter = "${'a' + i}"
            val textWidth = coordinatePaint.measureText(letter)
            canvas.drawText(letter, squareLeft(fileSq) + size - textWidth - size * 0.06f, squareTop(fileSq) + size * 0.92f, coordinatePaint)
        }
    }

    private fun drawCheck(canvas: Canvas) {
        if (!position.isCheck()) return
        val king = position.kingSquare(position.sideToMove)
        val left = squareLeft(king)
        val top = squareTop(king)
        val cx = left + squareSize / 2
        val cy = top + squareSize / 2
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx, cy, squareSize * 0.7f,
            intArrayOf(withAlpha(accent, 0xFF), withAlpha(accent, 0xB0), withAlpha(accent, 0)),
            floatArrayOf(0f, 0.3f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(left, top, left + squareSize, top + squareSize, paint)
        paint.shader = null
    }

    private fun drawHint(canvas: Canvas) {
        if (hintSquare < 0) return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = squareSize * 0.08f
        paint.color = accent
        val inset = paint.strokeWidth / 2
        canvas.drawRect(
            squareLeft(hintSquare) + inset, squareTop(hintSquare) + inset,
            squareLeft(hintSquare) + squareSize - inset, squareTop(hintSquare) + squareSize - inset,
            paint,
        )
    }

    private fun drawTargets(canvas: Canvas) {
        paint.color = AndroidColor.argb(0x48, 0, 0, 0)
        for (to in targets.map { it.to }.distinct()) {
            val cx = squareLeft(to) + squareSize / 2
            val cy = squareTop(to) + squareSize / 2
            if (position[to] == null) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, squareSize * 0.15f, paint)
            } else {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = squareSize * 0.08f
                canvas.drawCircle(cx, cy, squareSize * 0.44f - paint.strokeWidth / 2, paint)
            }
        }
    }

    private fun drawPiece(canvas: Canvas, piece: Piece, left: Float, top: Float, size: Float) {
        val drawable = pieces.getOrPut(piece) { requireNotNull(context.getDrawable(pieceDrawable(piece))).mutate() }
        drawable.setBounds(left.toInt(), top.toInt(), (left + size).toInt(), (top + size).toInt())
        drawable.draw(canvas)
    }

    private fun pieceDrawable(piece: Piece): Int = if (piece.color == Color.WHITE) {
        when (piece.type) {
            PieceType.KING -> R.drawable.piece_wk
            PieceType.QUEEN -> R.drawable.piece_wq
            PieceType.ROOK -> R.drawable.piece_wr
            PieceType.BISHOP -> R.drawable.piece_wb
            PieceType.KNIGHT -> R.drawable.piece_wn
            PieceType.PAWN -> R.drawable.piece_wp
        }
    } else {
        when (piece.type) {
            PieceType.KING -> R.drawable.piece_bk
            PieceType.QUEEN -> R.drawable.piece_bq
            PieceType.ROOK -> R.drawable.piece_br
            PieceType.BISHOP -> R.drawable.piece_bb
            PieceType.KNIGHT -> R.drawable.piece_bn
            PieceType.PAWN -> R.drawable.piece_bp
        }
    }

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)

    // --- Input ----------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactive) return false
        val sq = squareAt(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                if (selected >= 0 && targets.any { it.to == sq }) {
                    tryMove(sq)
                    return true
                }
                val piece = if (sq >= 0) position[sq] else null
                val moves = if (sq >= 0) position.legalMovesFrom(sq) else emptyList()
                if (piece != null && piece.color == position.sideToMove && moves.isNotEmpty()) {
                    deselectOnTap = sq == selected
                    selected = sq
                    targets = moves
                    dragging = true
                    dragMoved = false
                    dragX = event.x
                    dragY = event.y
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                } else {
                    clearSelection()
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> if (dragging) {
                if (!dragMoved && Math.hypot((event.x - dragX).toDouble(), (event.y - dragY).toDouble()) > squareSize * 0.15f) {
                    dragMoved = true
                }
                dragX = event.x
                dragY = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP -> if (dragging) {
                dragging = false
                when {
                    sq != selected && targets.any { it.to == sq } -> tryMove(sq)
                    sq == selected && deselectOnTap && !dragMoved -> clearSelection()
                }
                performClick()
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun tryMove(to: Int) {
        val candidates = targets.filter { it.to == to }
        clearSelection()
        when {
            candidates.size == 1 -> onMove?.invoke(candidates.single())
            candidates.size > 1 -> {
                val chooser = choosePromotion
                if (chooser != null) chooser(candidates) { onMove?.invoke(it) }
                else onMove?.invoke(candidates.first { it.promotion == PieceType.QUEEN })
            }
        }
    }

    private fun clearSelection() {
        selected = -1
        targets = emptyList()
        dragging = false
        invalidate()
    }
}
