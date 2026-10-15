package com.smarter.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.smarter.core.chess.Color
import com.smarter.core.chess.Move
import com.smarter.core.chess.Piece
import com.smarter.core.chess.PieceType
import com.smarter.core.chess.Position
import com.smarter.core.chess.Square

/**
 * A chessboard you can play on by tapping or dragging. Pieces are drawn with the Unicode chess
 * glyphs, so no image assets are needed.
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

    private var position: Position = Position.initial()
    private var lastMove: Move? = null
    private var selected = -1
    private var targets: List<Move> = emptyList()
    private var dragging = false
    /** A second tap on the selected piece (without dragging it away) deselects it. */
    private var deselectOnTap = false
    private var dragX = 0f
    private var dragY = 0f

    private var animatedMove: Move? = null
    private var animationProgress = 1f
    private var animator: ValueAnimator? = null

    private val squarePaint = Paint()
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val coordinatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }
    private val glyphBounds = Rect()

    private val squareSize get() = width / 8f

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
        for (sq in 0 until 64) {
            val left = squareLeft(sq)
            val top = squareTop(sq)
            val light = (Square.file(sq) + Square.rank(sq)) % 2 == 1
            squarePaint.color = if (light) LIGHT_SQUARE else DARK_SQUARE
            canvas.drawRect(left, top, left + size, top + size, squarePaint)

            val highlight = when {
                sq == selected -> SELECTED
                lastMove?.let { sq == it.from || sq == it.to } == true -> LAST_MOVE
                else -> null
            }
            if (highlight != null) {
                overlayPaint.style = Paint.Style.FILL
                overlayPaint.shader = null
                overlayPaint.color = highlight
                canvas.drawRect(left, top, left + size, top + size, overlayPaint)
            }
        }

        drawCoordinates(canvas)
        drawCheck(canvas)
        drawHint(canvas)

        for (sq in 0 until 64) {
            val piece = position[sq] ?: continue
            if (dragging && sq == selected) continue
            val move = animatedMove
            if (move != null && sq == move.to && animationProgress < 1f) continue
            drawPiece(canvas, piece, squareLeft(sq) + size / 2, squareTop(sq) + size / 2, size)
        }

        drawTargets(canvas)

        val move = animatedMove
        if (move != null && animationProgress < 1f) {
            position[move.to]?.let { piece ->
                val x = squareLeft(move.from) + (squareLeft(move.to) - squareLeft(move.from)) * animationProgress
                val y = squareTop(move.from) + (squareTop(move.to) - squareTop(move.from)) * animationProgress
                drawPiece(canvas, piece, x + size / 2, y + size / 2, size)
            }
        }
        if (dragging && selected >= 0) {
            position[selected]?.let { drawPiece(canvas, it, dragX, dragY - size * 0.4f, size * 1.25f) }
        }
    }

    private fun drawCoordinates(canvas: Canvas) {
        val size = squareSize
        coordinatePaint.textSize = size * 0.2f
        for (i in 0..7) {
            // Rank numbers down the left edge, file letters along the bottom edge.
            val rankSq = if (whiteAtBottom) Square.of(0, i) else Square.of(7, i)
            coordinatePaint.color = if ((Square.file(rankSq) + i) % 2 == 1) DARK_SQUARE else LIGHT_SQUARE
            canvas.drawText("${i + 1}", squareLeft(rankSq) + size * 0.06f, squareTop(rankSq) + size * 0.24f, coordinatePaint)

            val fileSq = if (whiteAtBottom) Square.of(i, 0) else Square.of(i, 7)
            coordinatePaint.color = if ((i + Square.rank(fileSq)) % 2 == 1) DARK_SQUARE else LIGHT_SQUARE
            val letter = "${'a' + i}"
            val textWidth = coordinatePaint.measureText(letter)
            canvas.drawText(letter, squareLeft(fileSq) + size - textWidth - size * 0.06f, squareTop(fileSq) + size * 0.94f, coordinatePaint)
        }
    }

    private fun drawCheck(canvas: Canvas) {
        if (!position.isCheck()) return
        val king = position.kingSquare(position.sideToMove)
        val cx = squareLeft(king) + squareSize / 2
        val cy = squareTop(king) + squareSize / 2
        overlayPaint.style = Paint.Style.FILL
        overlayPaint.shader = RadialGradient(
            cx, cy, squareSize * 0.7f,
            intArrayOf(AndroidColor.argb(255, 255, 0, 0), AndroidColor.argb(170, 231, 0, 0), AndroidColor.argb(0, 169, 0, 0)),
            floatArrayOf(0f, 0.3f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(squareLeft(king), squareTop(king), squareLeft(king) + squareSize, squareTop(king) + squareSize, overlayPaint)
        overlayPaint.shader = null
    }

    private fun drawHint(canvas: Canvas) {
        if (hintSquare < 0) return
        overlayPaint.style = Paint.Style.STROKE
        overlayPaint.strokeWidth = squareSize * 0.08f
        overlayPaint.color = HINT
        val inset = squareSize * 0.06f
        canvas.drawRect(
            squareLeft(hintSquare) + inset, squareTop(hintSquare) + inset,
            squareLeft(hintSquare) + squareSize - inset, squareTop(hintSquare) + squareSize - inset,
            overlayPaint,
        )
    }

    private fun drawTargets(canvas: Canvas) {
        overlayPaint.color = TARGET
        for (to in targets.map { it.to }.distinct()) {
            val cx = squareLeft(to) + squareSize / 2
            val cy = squareTop(to) + squareSize / 2
            if (position[to] == null) {
                overlayPaint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, squareSize * 0.16f, overlayPaint)
            } else {
                overlayPaint.style = Paint.Style.STROKE
                overlayPaint.strokeWidth = squareSize * 0.08f
                canvas.drawCircle(cx, cy, squareSize * 0.44f, overlayPaint)
            }
        }
    }

    private fun drawPiece(canvas: Canvas, piece: Piece, cx: Float, cy: Float, size: Float) {
        val solid = SOLID_GLYPHS.getValue(piece.type)
        glyphPaint.textSize = size * 0.82f
        glyphPaint.getTextBounds(solid, 0, solid.length, glyphBounds)
        val x = cx - glyphBounds.exactCenterX()
        val y = cy - glyphBounds.exactCenterY()

        glyphPaint.style = Paint.Style.FILL
        if (piece.color == Color.WHITE) {
            // Fill the silhouette white, then draw the outlined glyph on top for the details.
            glyphPaint.color = AndroidColor.WHITE
            canvas.drawText(solid, x, y, glyphPaint)
            glyphPaint.color = PIECE_OUTLINE
            canvas.drawText(OUTLINE_GLYPHS.getValue(piece.type), x, y, glyphPaint)
        } else {
            glyphPaint.color = BLACK_PIECE
            canvas.drawText(solid, x, y, glyphPaint)
        }
    }

    // --- Input ----------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactive) return false
        val sq = squareAt(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (selected >= 0 && targets.any { it.to == sq }) {
                    tryMove(sq)
                    return true
                }
                val piece = if (sq >= 0) position[sq] else null
                val moves = if (sq >= 0) position.legalMovesFrom(sq) else emptyList()
                if (piece != null && piece.color == position.sideToMove && moves.isNotEmpty()) {
                    val wasSelected = sq == selected
                    selected = sq
                    targets = moves
                    dragging = true
                    dragX = event.x
                    dragY = event.y
                    deselectOnTap = wasSelected
                } else {
                    clearSelection()
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> if (dragging) {
                dragX = event.x
                dragY = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP -> if (dragging) {
                dragging = false
                when {
                    sq != selected && targets.any { it.to == sq } -> tryMove(sq)
                    sq == selected && deselectOnTap -> clearSelection()
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
        val LIGHT_SQUARE = AndroidColor.rgb(0xF0, 0xD9, 0xB5)
        val DARK_SQUARE = AndroidColor.rgb(0xB5, 0x88, 0x63)
        val LAST_MOVE = AndroidColor.argb(0x69, 0x9B, 0xC7, 0x00)
        val SELECTED = AndroidColor.argb(0x80, 0x14, 0x55, 0x1E)
        val TARGET = AndroidColor.argb(0x66, 0x14, 0x55, 0x1E)
        val HINT = AndroidColor.rgb(0x2F, 0x80, 0xED)
        val PIECE_OUTLINE = AndroidColor.rgb(0x1A, 0x1A, 0x1A)
        val BLACK_PIECE = AndroidColor.rgb(0x1A, 0x1A, 0x1A)

        // U+FE0E asks for the text (not emoji) presentation of the glyph.
        val SOLID_GLYPHS = mapOf(
            PieceType.KING to "♚︎", PieceType.QUEEN to "♛︎", PieceType.ROOK to "♜︎",
            PieceType.BISHOP to "♝︎", PieceType.KNIGHT to "♞︎", PieceType.PAWN to "♟︎",
        )
        val OUTLINE_GLYPHS = mapOf(
            PieceType.KING to "♔︎", PieceType.QUEEN to "♕︎", PieceType.ROOK to "♖︎",
            PieceType.BISHOP to "♗︎", PieceType.KNIGHT to "♘︎", PieceType.PAWN to "♙︎",
        )
    }
}
