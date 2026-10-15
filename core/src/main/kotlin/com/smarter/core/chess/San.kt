package com.smarter.core.chess

/** Standard Algebraic Notation (e.g. "Nbd7", "exd6", "O-O", "e8=Q#"). */
object San {
    fun of(position: Position, move: Move): String {
        val base = withoutSuffix(position, move)
        val after = position.play(move)
        return when {
            after.isCheckmate() -> "$base#"
            after.isCheck() -> "$base+"
            else -> base
        }
    }

    /** Returns the legal move matching [san], or null. Tolerates "0-0", missing "=", and "+#!?" annotations. */
    fun parse(position: Position, san: String): Move? {
        val wanted = normalize(san)
        if (wanted.isEmpty()) return null
        return position.legalMoves().firstOrNull { normalize(withoutSuffix(position, it)) == wanted }
    }

    private fun normalize(san: String): String =
        san.trim().trimEnd('+', '#', '!', '?').replace("0", "O").replace("=", "")

    private fun withoutSuffix(position: Position, move: Move): String {
        val piece = requireNotNull(position[move.from]) { "No piece on ${Square.name(move.from)}" }
        val fileDelta = Square.file(move.to) - Square.file(move.from)

        if (piece.type == PieceType.KING && kotlin.math.abs(fileDelta) == 2) {
            return if (fileDelta > 0) "O-O" else "O-O-O"
        }

        val isCapture = position[move.to] != null ||
            (piece.type == PieceType.PAWN && move.to == position.epSquare)

        return buildString {
            if (piece.type == PieceType.PAWN) {
                if (isCapture) append('a' + Square.file(move.from)).append('x')
                append(Square.name(move.to))
                move.promotion?.let { append('=').append(it.letter.uppercaseChar()) }
            } else {
                append(piece.type.letter.uppercaseChar())
                append(disambiguation(position, move, piece))
                if (isCapture) append('x')
                append(Square.name(move.to))
            }
        }
    }

    private fun disambiguation(position: Position, move: Move, piece: Piece): String {
        val rivals = position.legalMoves().filter {
            it.to == move.to && it.from != move.from && position[it.from] == piece
        }
        if (rivals.isEmpty()) return ""
        val file = Square.file(move.from)
        val rank = Square.rank(move.from)
        return when {
            rivals.none { Square.file(it.from) == file } -> "${'a' + file}"
            rivals.none { Square.rank(it.from) == rank } -> "${'1' + rank}"
            else -> Square.name(move.from)
        }
    }
}
