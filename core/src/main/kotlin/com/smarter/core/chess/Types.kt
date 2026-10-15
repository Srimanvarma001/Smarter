package com.smarter.core.chess

enum class Color {
    WHITE, BLACK;

    val opposite: Color get() = if (this == WHITE) BLACK else WHITE
}

enum class PieceType(val letter: Char) {
    PAWN('p'), KNIGHT('n'), BISHOP('b'), ROOK('r'), QUEEN('q'), KING('k');

    companion object {
        fun fromLetter(c: Char): PieceType? = entries.firstOrNull { it.letter == c.lowercaseChar() }
    }
}

data class Piece(val color: Color, val type: PieceType) {
    /** FEN letter: uppercase for white, lowercase for black. */
    val fenChar: Char get() = if (color == Color.WHITE) type.letter.uppercaseChar() else type.letter

    companion object {
        fun fromFenChar(c: Char): Piece? {
            val type = PieceType.fromLetter(c) ?: return null
            return Piece(if (c.isUpperCase()) Color.WHITE else Color.BLACK, type)
        }
    }
}

/** Squares are 0..63 with a1 = 0, b1 = 1, ..., h8 = 63. */
object Square {
    fun of(file: Int, rank: Int): Int = rank * 8 + file
    fun file(sq: Int): Int = sq and 7
    fun rank(sq: Int): Int = sq shr 3
    fun isValid(file: Int, rank: Int): Boolean = file in 0..7 && rank in 0..7

    fun name(sq: Int): String = "${'a' + file(sq)}${'1' + rank(sq)}"

    fun parse(name: String): Int? {
        if (name.length != 2) return null
        val file = name[0] - 'a'
        val rank = name[1] - '1'
        return if (isValid(file, rank)) of(file, rank) else null
    }
}

data class Move(val from: Int, val to: Int, val promotion: PieceType? = null) {
    fun uci(): String = Square.name(from) + Square.name(to) + (promotion?.letter ?: "")

    override fun toString(): String = uci()

    companion object {
        fun fromUci(uci: String): Move? {
            if (uci.length !in 4..5) return null
            val from = Square.parse(uci.substring(0, 2)) ?: return null
            val to = Square.parse(uci.substring(2, 4)) ?: return null
            val promotion = if (uci.length == 5) {
                PieceType.fromLetter(uci[4])?.takeIf { it != PieceType.PAWN && it != PieceType.KING } ?: return null
            } else {
                null
            }
            return Move(from, to, promotion)
        }
    }
}
