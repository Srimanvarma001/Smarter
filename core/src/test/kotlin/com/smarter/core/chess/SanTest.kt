package com.smarter.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SanTest {
    private fun san(fen: String, uci: String) = San.of(Position.fromFen(fen), Move.fromUci(uci)!!)

    @Test
    fun formatsMoves() {
        assertEquals("e4", san(Position.STARTING_FEN, "e2e4"))
        assertEquals("Nf3", san(Position.STARTING_FEN, "g1f3"))
        assertEquals("O-O", san("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", "e1g1"))
        assertEquals("O-O-O", san("4k3/8/8/8/8/8/8/R3K3 w Q - 0 1", "e1c1"))
        assertEquals("exf6", san("rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3", "e5f6"))
        assertEquals("e8=Q+", san("8/4P3/8/8/k7/8/8/4K3 w - - 0 1", "e7e8q"))
        assertEquals("Qxf7#", san("r1bqkbnr/pppp1ppp/2n5/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 0 1", "h5f7"))
    }

    @Test
    fun disambiguates() {
        // Knights on b1 and f3 can both reach d2.
        assertEquals("Nbd2", san("4k3/8/8/8/8/5N2/8/1N2K3 w - - 0 1", "b1d2"))
        // Rooks on a1 and a5 can both reach a3.
        assertEquals("R1a3", san("4k3/8/8/R7/8/8/8/R3K3 w - - 0 1", "a1a3"))
    }

    @Test
    fun parsesLenientInput() {
        val start = Position.initial()
        assertEquals(Move.fromUci("g1f3"), San.parse(start, "Nf3"))
        assertEquals(Move.fromUci("e2e4"), San.parse(start, "e4!?"))
        assertNull(San.parse(start, "e5"))
        val castle = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        assertEquals(Move.fromUci("e1c1"), San.parse(castle, "0-0-0"))
        val promo = Position.fromFen("8/4P3/8/8/8/8/k7/4K3 w - - 0 1")
        assertEquals(Move.fromUci("e7e8n"), San.parse(promo, "e8N"))
    }
}
