package com.smarter.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PositionTest {
    private fun perft(position: Position, depth: Int): Long {
        if (depth == 0) return 1
        val moves = position.legalMoves()
        if (depth == 1) return moves.size.toLong()
        return moves.sumOf { perft(position.play(it), depth - 1) }
    }

    // Reference counts from https://www.chessprogramming.org/Perft_Results
    @Test
    fun perftStartingPosition() {
        val start = Position.initial()
        assertEquals(listOf(20L, 400L, 8_902L, 197_281L), (1..4).map { perft(start, it) })
    }

    @Test
    fun perftKiwipete() {
        val pos = Position.fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        assertEquals(listOf(48L, 2_039L, 97_862L), (1..3).map { perft(pos, it) })
    }

    @Test
    fun perftEndgameWithEnPassantPins() {
        val pos = Position.fromFen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1")
        assertEquals(listOf(14L, 191L, 2_812L, 43_238L), (1..4).map { perft(pos, it) })
    }

    @Test
    fun perftPromotionsAndCastling() {
        val pos4 = Position.fromFen("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1")
        assertEquals(listOf(6L, 264L, 9_467L), (1..3).map { perft(pos4, it) })
        val pos5 = Position.fromFen("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8")
        assertEquals(listOf(44L, 1_486L, 62_379L), (1..3).map { perft(pos5, it) })
    }

    @Test
    fun fenRoundTrips() {
        val fens = listOf(
            Position.STARTING_FEN,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2",
        )
        for (fen in fens) assertEquals(fen, Position.fromFen(fen).toFen())
    }

    @Test
    fun playUpdatesCastlingEnPassantAndClocks() {
        var pos = Position.initial()
        for (uci in listOf("e2e4", "d7d5", "e4e5", "f7f5")) pos = pos.play(Move.fromUci(uci)!!)
        assertEquals("rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3", pos.toFen())
        pos = pos.play(Move.fromUci("e5f6")!!) // en passant
        assertEquals(null, pos[Square.parse("f5")!!])

        val castle = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1").play(Move.fromUci("e1g1")!!)
        assertEquals("r3k2r/8/8/8/8/8/8/R4RK1 b kq - 1 1", castle.toFen())
    }

    @Test
    fun detectsMateAndStalemate() {
        val foolsMate = Position.fromFen("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3")
        assertTrue(foolsMate.isCheckmate())
        val stalemate = Position.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertTrue(stalemate.isStalemate())
        assertFalse(stalemate.isCheckmate())
    }

    @Test
    fun cannotCastleThroughCheck() {
        val pos = Position.fromFen("r3k2r/8/8/8/8/8/5r2/R3K2R w KQkq - 0 1")
        assertTrue(pos.isCheck().not())
        assertFalse(pos.isLegal(Move.fromUci("e1g1")!!), "f1 is attacked")
        assertTrue(pos.isLegal(Move.fromUci("e1c1")!!))
    }
}
