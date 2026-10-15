package com.smarter.core.chess

/**
 * An immutable chess position with full legal-move generation (castling, en passant, promotion).
 * Standard chess only; puzzles never need Chess960 castling.
 */
class Position private constructor(
    private val board: Array<Piece?>,
    val sideToMove: Color,
    /** Bit set of [WHITE_KINGSIDE], [WHITE_QUEENSIDE], [BLACK_KINGSIDE], [BLACK_QUEENSIDE]. */
    val castling: Int,
    /** Square a pawn may capture onto en passant, or -1. */
    val epSquare: Int,
    val halfmoveClock: Int,
    val fullmoveNumber: Int,
) {
    operator fun get(sq: Int): Piece? = board[sq]

    fun kingSquare(color: Color): Int =
        board.indices.firstOrNull { board[it] == Piece(color, PieceType.KING) } ?: -1

    fun isCheck(): Boolean = isKingAttacked(sideToMove)

    fun isCheckmate(): Boolean = isCheck() && legalMoves().isEmpty()

    fun isStalemate(): Boolean = !isCheck() && legalMoves().isEmpty()

    private var cachedLegalMoves: List<Move>? = null

    fun legalMoves(): List<Move> {
        cachedLegalMoves?.let { return it }
        val moves = pseudoLegalMoves().filter { !play(it).isKingAttacked(sideToMove) }
        cachedLegalMoves = moves
        return moves
    }

    fun legalMovesFrom(sq: Int): List<Move> = legalMoves().filter { it.from == sq }

    fun isLegal(move: Move): Boolean = move in legalMoves()

    /** Plays [move] without checking legality and returns the resulting position. */
    fun play(move: Move): Position {
        val next = board.copyOf()
        val piece = requireNotNull(board[move.from]) { "No piece on ${Square.name(move.from)}" }
        val captured = board[move.to]
        val fromFile = Square.file(move.from)
        val toFile = Square.file(move.to)

        next[move.from] = null
        next[move.to] = if (move.promotion != null) Piece(piece.color, move.promotion) else piece

        if (piece.type == PieceType.PAWN && move.to == epSquare && captured == null) {
            val capturedPawnSq = if (piece.color == Color.WHITE) move.to - 8 else move.to + 8
            next[capturedPawnSq] = null
        }
        if (piece.type == PieceType.KING && kotlin.math.abs(toFile - fromFile) == 2) {
            val rank = Square.rank(move.from)
            val (rookFrom, rookTo) = if (toFile == 6) Square.of(7, rank) to Square.of(5, rank)
            else Square.of(0, rank) to Square.of(3, rank)
            next[rookTo] = next[rookFrom]
            next[rookFrom] = null
        }

        // Moving a king or rook, or capturing a rook on its home square, removes castling rights.
        val rights = castling and castlingRightsLostAt(move.from).inv() and castlingRightsLostAt(move.to).inv()

        val isDoublePush = piece.type == PieceType.PAWN && kotlin.math.abs(move.to - move.from) == 16
        val ep = if (isDoublePush) (move.from + move.to) / 2 else -1
        val resetsClock = piece.type == PieceType.PAWN || captured != null

        return Position(
            board = next,
            sideToMove = sideToMove.opposite,
            castling = rights,
            epSquare = ep,
            halfmoveClock = if (resetsClock) 0 else halfmoveClock + 1,
            fullmoveNumber = if (sideToMove == Color.BLACK) fullmoveNumber + 1 else fullmoveNumber,
        )
    }

    fun isKingAttacked(color: Color): Boolean {
        val king = kingSquare(color)
        return king >= 0 && isAttacked(king, color.opposite)
    }

    /** True if any piece of color [by] attacks [sq]. */
    fun isAttacked(sq: Int, by: Color): Boolean {
        val file = Square.file(sq)
        val rank = Square.rank(sq)

        // A pawn of color `by` attacks sq from one rank behind (from its own point of view).
        val pawnRank = if (by == Color.WHITE) rank - 1 else rank + 1
        for (df in intArrayOf(-1, 1)) {
            if (pieceAt(file + df, pawnRank) == Piece(by, PieceType.PAWN)) return true
        }
        for ((df, dr) in KNIGHT_STEPS) {
            if (pieceAt(file + df, rank + dr) == Piece(by, PieceType.KNIGHT)) return true
        }
        for ((df, dr) in KING_STEPS) {
            if (pieceAt(file + df, rank + dr) == Piece(by, PieceType.KING)) return true
        }
        for ((df, dr) in ROOK_DIRS) {
            val p = firstPieceInDirection(file, rank, df, dr)
            if (p != null && p.color == by && (p.type == PieceType.ROOK || p.type == PieceType.QUEEN)) return true
        }
        for ((df, dr) in BISHOP_DIRS) {
            val p = firstPieceInDirection(file, rank, df, dr)
            if (p != null && p.color == by && (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN)) return true
        }
        return false
    }

    private fun pieceAt(file: Int, rank: Int): Piece? =
        if (Square.isValid(file, rank)) board[Square.of(file, rank)] else null

    private fun firstPieceInDirection(file: Int, rank: Int, df: Int, dr: Int): Piece? {
        var f = file + df
        var r = rank + dr
        while (Square.isValid(f, r)) {
            board[Square.of(f, r)]?.let { return it }
            f += df
            r += dr
        }
        return null
    }

    private fun pseudoLegalMoves(): List<Move> {
        val moves = ArrayList<Move>(48)
        for (from in 0 until 64) {
            val piece = board[from] ?: continue
            if (piece.color != sideToMove) continue
            when (piece.type) {
                PieceType.PAWN -> addPawnMoves(from, moves)
                PieceType.KNIGHT -> addStepMoves(from, KNIGHT_STEPS, moves)
                PieceType.BISHOP -> addSlideMoves(from, BISHOP_DIRS, moves)
                PieceType.ROOK -> addSlideMoves(from, ROOK_DIRS, moves)
                PieceType.QUEEN -> addSlideMoves(from, ROOK_DIRS + BISHOP_DIRS, moves)
                PieceType.KING -> {
                    addStepMoves(from, KING_STEPS, moves)
                    addCastlingMoves(from, moves)
                }
            }
        }
        return moves
    }

    private fun addPawnMoves(from: Int, moves: MutableList<Move>) {
        val file = Square.file(from)
        val rank = Square.rank(from)
        val dir = if (sideToMove == Color.WHITE) 1 else -1
        val startRank = if (sideToMove == Color.WHITE) 1 else 6
        val lastRank = if (sideToMove == Color.WHITE) 7 else 0

        fun add(to: Int) {
            if (Square.rank(to) == lastRank) {
                for (promo in PROMOTIONS) moves += Move(from, to, promo)
            } else {
                moves += Move(from, to)
            }
        }

        val oneRank = rank + dir
        if (!Square.isValid(file, oneRank)) return
        val one = Square.of(file, oneRank)
        if (board[one] == null) {
            add(one)
            if (rank == startRank) {
                val two = Square.of(file, rank + 2 * dir)
                if (board[two] == null) moves += Move(from, two)
            }
        }
        for (df in intArrayOf(-1, 1)) {
            if (!Square.isValid(file + df, oneRank)) continue
            val to = Square.of(file + df, oneRank)
            val target = board[to]
            if ((target != null && target.color != sideToMove) || to == epSquare) add(to)
        }
    }

    private fun addStepMoves(from: Int, steps: List<Pair<Int, Int>>, moves: MutableList<Move>) {
        val file = Square.file(from)
        val rank = Square.rank(from)
        for ((df, dr) in steps) {
            val f = file + df
            val r = rank + dr
            if (!Square.isValid(f, r)) continue
            val to = Square.of(f, r)
            if (board[to]?.color != sideToMove) moves += Move(from, to)
        }
    }

    private fun addSlideMoves(from: Int, dirs: List<Pair<Int, Int>>, moves: MutableList<Move>) {
        val file = Square.file(from)
        val rank = Square.rank(from)
        for ((df, dr) in dirs) {
            var f = file + df
            var r = rank + dr
            while (Square.isValid(f, r)) {
                val to = Square.of(f, r)
                val target = board[to]
                if (target == null) {
                    moves += Move(from, to)
                } else {
                    if (target.color != sideToMove) moves += Move(from, to)
                    break
                }
                f += df
                r += dr
            }
        }
    }

    private fun addCastlingMoves(from: Int, moves: MutableList<Move>) {
        val (kingside, queenside, home) = if (sideToMove == Color.WHITE) {
            Triple(WHITE_KINGSIDE, WHITE_QUEENSIDE, E1)
        } else {
            Triple(BLACK_KINGSIDE, BLACK_QUEENSIDE, E8)
        }
        if (from != home || isKingAttacked(sideToMove)) return
        val enemy = sideToMove.opposite
        val rook = Piece(sideToMove, PieceType.ROOK)
        if (castling and kingside != 0 && board[home + 3] == rook &&
            board[home + 1] == null && board[home + 2] == null &&
            !isAttacked(home + 1, enemy)
        ) {
            moves += Move(home, home + 2)
        }
        if (castling and queenside != 0 && board[home - 4] == rook &&
            board[home - 1] == null && board[home - 2] == null && board[home - 3] == null &&
            !isAttacked(home - 1, enemy)
        ) {
            moves += Move(home, home - 2)
        }
    }

    fun toFen(): String = buildString {
        for (rank in 7 downTo 0) {
            var empty = 0
            for (file in 0..7) {
                val p = board[Square.of(file, rank)]
                if (p == null) {
                    empty++
                } else {
                    if (empty > 0) append(empty).also { empty = 0 }
                    append(p.fenChar)
                }
            }
            if (empty > 0) append(empty)
            if (rank > 0) append('/')
        }
        append(if (sideToMove == Color.WHITE) " w " else " b ")
        val rights = buildString {
            if (castling and WHITE_KINGSIDE != 0) append('K')
            if (castling and WHITE_QUEENSIDE != 0) append('Q')
            if (castling and BLACK_KINGSIDE != 0) append('k')
            if (castling and BLACK_QUEENSIDE != 0) append('q')
        }
        append(rights.ifEmpty { "-" })
        append(' ')
        append(if (epSquare >= 0) Square.name(epSquare) else "-")
        append(' ').append(halfmoveClock).append(' ').append(fullmoveNumber)
    }

    override fun toString(): String = toFen()

    companion object {
        const val WHITE_KINGSIDE = 1
        const val WHITE_QUEENSIDE = 2
        const val BLACK_KINGSIDE = 4
        const val BLACK_QUEENSIDE = 8

        const val STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        private const val A1 = 0
        private const val E1 = 4
        private const val H1 = 7
        private const val A8 = 56
        private const val E8 = 60
        private const val H8 = 63

        private val PROMOTIONS = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)
        private val KNIGHT_STEPS = listOf(1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2)
        private val KING_STEPS = listOf(1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)
        private val ROOK_DIRS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        private val BISHOP_DIRS = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)

        private fun castlingRightsLostAt(sq: Int): Int = when (sq) {
            E1 -> WHITE_KINGSIDE or WHITE_QUEENSIDE
            E8 -> BLACK_KINGSIDE or BLACK_QUEENSIDE
            H1 -> WHITE_KINGSIDE
            A1 -> WHITE_QUEENSIDE
            H8 -> BLACK_KINGSIDE
            A8 -> BLACK_QUEENSIDE
            else -> 0
        }

        fun initial(): Position = fromFen(STARTING_FEN)

        /** Parses a FEN string. Move counters are optional, as in EPD-style strings. */
        fun fromFen(fen: String): Position {
            val parts = fen.trim().split(Regex("\\s+"))
            require(parts.size >= 2) { "Invalid FEN: $fen" }

            val board = arrayOfNulls<Piece>(64)
            val rows = parts[0].split('/')
            require(rows.size == 8) { "Invalid FEN board: ${parts[0]}" }
            for ((i, row) in rows.withIndex()) {
                val rank = 7 - i
                var file = 0
                for (c in row) {
                    if (c.isDigit()) {
                        file += c - '0'
                    } else {
                        val piece = requireNotNull(Piece.fromFenChar(c)) { "Invalid FEN piece '$c'" }
                        require(file < 8) { "Invalid FEN row: $row" }
                        board[Square.of(file, rank)] = piece
                        file++
                    }
                }
                require(file == 8) { "Invalid FEN row: $row" }
            }

            val side = when (parts[1]) {
                "w" -> Color.WHITE
                "b" -> Color.BLACK
                else -> throw IllegalArgumentException("Invalid side to move: ${parts[1]}")
            }
            var castling = 0
            parts.getOrNull(2)?.forEach {
                castling = castling or when (it) {
                    'K' -> WHITE_KINGSIDE
                    'Q' -> WHITE_QUEENSIDE
                    'k' -> BLACK_KINGSIDE
                    'q' -> BLACK_QUEENSIDE
                    else -> 0
                }
            }
            val ep = parts.getOrNull(3)?.let { Square.parse(it) } ?: -1
            val halfmove = parts.getOrNull(4)?.toIntOrNull() ?: 0
            val fullmove = parts.getOrNull(5)?.toIntOrNull() ?: 1

            for (color in Color.entries) {
                require(board.count { it == Piece(color, PieceType.KING) } == 1) {
                    "FEN must have exactly one ${color.name.lowercase()} king: $fen"
                }
            }
            return Position(board, side, castling, ep, halfmove, fullmove)
        }
    }
}
