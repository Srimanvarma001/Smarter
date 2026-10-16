package com.smarter.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.smarter.app.R
import com.smarter.core.chess.Color
import com.smarter.core.chess.Move
import com.smarter.core.chess.Piece
import com.smarter.core.chess.PieceType
import com.smarter.core.chess.Position
import com.smarter.core.chess.Square

/**
 * A chessboard you can play on by tapping or dragging, styled like chess.com's default board:
 * green and cream squares, yellow last-move highlights, soft move dots, coordinates inside the
 * edge squares, and a tick or cross badge on the square of a right or wrong move. Pieces are the
 * cburnett set as vector drawables, so they are sharp at any size.
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

    enum class Mark { CORRECT, WRONG }

    /** Badge on the destination square of the last move: right or wrong. */
    private var mark: Mark? = null
    private var markSquare = -1

    fun mark(square: Int, mark: Mark?) {
        this.mark = mark
        markSquare = square
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
    /** Square under the finger while dragging, outlined like on chess.com. */
    private var hoverSquare = -1

    private var animatedMove: Move? = null
    private var animationProgress = 1f
    private var animator: ValueAnimator? = null

    private val lightSquare = context.colorOf(R.color.square_light)
    private val darkSquare = context.colorOf(R.color.square_dark)
    private val lightHighlight = context.colorOf(R.color.square_light_highlight)
    private val darkHighlight = context.colorOf(R.color.square_dark_highlight)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val coordinatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = typeface(700) }
    private val badgePath = Path()
    private val pieces = HashMap<Piece, Drawable>()

    private val squareSize get() = width / 8f

    fun setPosition(position: Position, lastMove: Move?, animate: Boolean = false) {
        animator?.cancel()
        this.position = position
        this.lastMove = lastMove
        mark = null
        markSquare = -1
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
            val light = isLight(sq)
            val highlighted = sq == selected || lastMove?.let { sq == it.from || sq == it.to } == true
            paint.color = when {
                highlighted && light -> lightHighlight
                highlighted -> darkHighlight
                light -> lightSquare
                else -> darkSquare
            }
            canvas.drawRect(left, top, left + size, top + size, paint)
        }

        drawCoordinates(canvas)
        drawCheck(canvas)
        drawHint(canvas)
        drawHover(canvas)

        for (sq in 0 until 64) {
            val piece = position[sq] ?: continue
            if (dragging && dragMoved && sq == selected) continue
            val move = animatedMove
            if (move != null && sq == move.to && animationProgress < 1f) continue
            drawPiece(canvas, piece, squareLeft(sq), squareTop(sq), size)
        }
        // Move dots go over the pieces so capture rings show around the piece to take.
        drawTargets(canvas)

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
        drawMark(canvas)
    }

    private fun isLight(sq: Int) = (Square.file(sq) + Square.rank(sq)) % 2 == 1

    private fun drawCoordinates(canvas: Canvas) {
        val size = squareSize
        coordinatePaint.textSize = size * 0.2f
        for (i in 0..7) {
            // Rank numbers in the top-left of the left column, file letters in the bottom-right
            // of the bottom row, each in the other square colour.
            val rankSq = if (whiteAtBottom) Square.of(0, i) else Square.of(7, i)
            coordinatePaint.color = if (isLight(rankSq)) darkSquare else lightSquare
            canvas.drawText("${i + 1}", squareLeft(rankSq) + size * 0.06f, squareTop(rankSq) + size * 0.22f, coordinatePaint)

            val fileSq = if (whiteAtBottom) Square.of(i, 0) else Square.of(i, 7)
            coordinatePaint.color = if (isLight(fileSq)) darkSquare else lightSquare
            val letter = "${'a' + i}"
            val textWidth = coordinatePaint.measureText(letter)
            canvas.drawText(letter, squareLeft(fileSq) + size - textWidth - size * 0.06f, squareTop(fileSq) + size * 0.93f, coordinatePaint)
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
            cx, cy, squareSize * 0.72f,
            intArrayOf(CHECK_RED, CHECK_RED, withAlpha(CHECK_RED, 0xA0), withAlpha(CHECK_RED, 0)),
            floatArrayOf(0f, 0.25f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(left, top, left + squareSize, top + squareSize, paint)
        paint.shader = null
    }

    private fun drawHint(canvas: Canvas) {
        if (hintSquare < 0) return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = squareSize * 0.07f
        paint.color = AndroidColor.WHITE
        val inset = paint.strokeWidth / 2
        canvas.drawRect(
            squareLeft(hintSquare) + inset, squareTop(hintSquare) + inset,
            squareLeft(hintSquare) + squareSize - inset, squareTop(hintSquare) + squareSize - inset,
            paint,
        )
    }

    private fun drawHover(canvas: Canvas) {
        if (!(dragging && dragMoved) || hoverSquare < 0) return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = squareSize * 0.06f
        paint.color = AndroidColor.argb(0xB0, 0xFF, 0xFF, 0xFF)
        val inset = paint.strokeWidth / 2
        canvas.drawRect(
            squareLeft(hoverSquare) + inset, squareTop(hoverSquare) + inset,
            squareLeft(hoverSquare) + squareSize - inset, squareTop(hoverSquare) + squareSize - inset,
            paint,
        )
    }

    private fun drawTargets(canvas: Canvas) {
        paint.color = AndroidColor.argb(0x24, 0, 0, 0)
        for (to in targets.map { it.to }.distinct()) {
            val cx = squareLeft(to) + squareSize / 2
            val cy = squareTop(to) + squareSize / 2
            if (position[to] == null) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, squareSize * 0.165f, paint)
            } else {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = squareSize * 0.09f
                canvas.drawCircle(cx, cy, squareSize * 0.5f - paint.strokeWidth / 2, paint)
            }
        }
    }

    /** A round tick or cross badge in the top-right corner of the marked square. */
    private fun drawMark(canvas: Canvas) {
        val mark = mark ?: return
        if (markSquare < 0) return
        val r = squareSize * 0.17f
        val cx = squareLeft(markSquare) + squareSize - r * 1.05f
        val cy = squareTop(markSquare) + r * 1.05f
        paint.style = Paint.Style.FILL
        paint.color = AndroidColor.argb(0x40, 0, 0, 0)
        canvas.drawCircle(cx, cy + r * 0.08f, r * 1.04f, paint)
        paint.color = if (mark == Mark.CORRECT) CORRECT_GREEN else WRONG_RED
        canvas.drawCircle(cx, cy, r, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.28f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = AndroidColor.WHITE
        badgePath.reset()
        if (mark == Mark.CORRECT) {
            badgePath.moveTo(cx - r * 0.45f, cy + r * 0.02f)
            badgePath.lineTo(cx - r * 0.12f, cy + r * 0.35f)
            badgePath.lineTo(cx + r * 0.48f, cy - r * 0.3f)
        } else {
            val d = r * 0.38f
            badgePath.moveTo(cx - d, cy - d)
            badgePath.lineTo(cx + d, cy + d)
            badgePath.moveTo(cx + d, cy - d)
            badgePath.lineTo(cx - d, cy + d)
        }
        canvas.drawPath(badgePath, paint)
        paint.strokeCap = Paint.Cap.BUTT
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
                    hoverSquare = sq
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
                hoverSquare = squareAt(event.x, event.y)
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

    private companion object {
        const val CHECK_RED = 0xFFFF0000.toInt()
        const val CORRECT_GREEN = 0xFF81B64C.toInt()
        const val WRONG_RED = 0xFFFA412D.toInt()
    }
}
