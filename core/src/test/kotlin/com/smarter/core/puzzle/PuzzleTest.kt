package com.smarter.core.puzzle

import com.smarter.core.chess.Move
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PuzzleTest {
    // First puzzle of the Lichess puzzle database.
    private val lichessRow = "00008,r6k/pp2r2p/4Rp1Q/3p4/8/1N1P2R1/PqP2bPP/7K b - - 0 24," +
        "f2g3 e6e7 b2b1 b3c1 b1c1 h6c1,1913,76,94,6230,crushing hangingPiece long middlegame," +
        "https://lichess.org/787zsVup/black#47,"

    private fun moves(vararg uci: String) = uci.map { Move.fromUci(it)!! }

    @Test
    fun parsesLichessCsvRow() {
        val puzzle = assertNotNull(Puzzle.fromLichessCsv(lichessRow))
        assertEquals("00008", puzzle.id)
        assertEquals(1913, puzzle.rating)
        assertEquals(Move.fromUci("f2g3"), puzzle.lastMove)
        assertEquals(moves("e6e7", "b2b1", "b3c1", "b1c1", "h6c1"), puzzle.solution)
        assertEquals(3, puzzle.solverMoveCount)
        assertEquals(listOf("crushing", "hangingPiece", "long", "middlegame"), puzzle.themes)
    }

    @Test
    fun rejectsHeaderAndBrokenRows() {
        assertNull(Puzzle.fromLichessCsv("PuzzleId,FEN,Moves,Rating,RatingDeviation,Popularity,NbPlays,Themes,GameUrl,OpeningTags"))
        assertNull(Puzzle.fromLichessCsv("x,not a fen,e2e4 e7e5,1500"))
        assertNull(Puzzle.fromLichessCsv("x,${com.smarter.core.chess.Position.STARTING_FEN},e2e5 e7e5,1500"))
    }

    @Test
    fun buildsPuzzleFromLichessApiGame() {
        val pgn = "e4 e5 Bc4 Nc6 Qh5 Nf6"
        val puzzle = assertNotNull(Puzzle.fromLichessApi("abc", pgn, 5, listOf("h5f7"), 600, listOf("mateIn1")))
        assertEquals(Move.fromUci("g8f6"), puzzle.lastMove)
        assertTrue(puzzle.startPosition.play(puzzle.solution.first()).isCheckmate())

        // Tolerates an initialPly that does not match the pgn's length.
        val shifted = assertNotNull(Puzzle.fromLichessApi("abc", pgn, 3, listOf("h5f7"), 600, emptyList()))
        assertEquals(puzzle.fen, shifted.fen)
        assertNull(Puzzle.fromLichessApi("abc", "e4 e5 Bc4 Nc6 Qh5 Nf6", 5, listOf("h5f7", "e8f7"), 600, emptyList()))
    }

    @Test
    fun sessionAcceptsSolutionAndRejectsWrongMoves() {
        val puzzle = Puzzle.fromLichessCsv(lichessRow)!!
        val session = PuzzleSession(puzzle)
        assertEquals(moves("e6e7").first(), session.hint())

        val first = assertIs<PuzzleSession.Result.Correct>(session.submit(Move.fromUci("e6e7")!!))
        assertEquals(Move.fromUci("b2b1"), first.reply)
        assertEquals(1, session.movesFound)
        assertIs<PuzzleSession.Result.Correct>(session.submit(Move.fromUci("b3c1")!!))
        assertIs<PuzzleSession.Result.Solved>(session.submit(Move.fromUci("h6c1")!!))
        assertEquals(PuzzleSession.State.SOLVED, session.state)

        val failing = PuzzleSession(puzzle)
        assertEquals(PuzzleSession.Result.Illegal, failing.submit(Move.fromUci("a1a8")!!))
        val wrong = assertIs<PuzzleSession.Result.Wrong>(failing.submit(Move.fromUci("h6h7")!!))
        assertEquals(Move.fromUci("e6e7"), wrong.expected)
        assertEquals(PuzzleSession.State.FAILED, failing.state)
    }

    @Test
    fun anyCheckmateSolvesThePuzzle() {
        // Both e8=Q and e8=R mate; the puzzle lists only the queen promotion.
        val puzzle = Puzzle("p", "6k1/p3Pppp/8/8/8/8/5PPP/6K1 w - - 0 1", moves("e7e8q"), 700)
        val session = PuzzleSession(puzzle)
        assertIs<PuzzleSession.Result.Solved>(session.submit(Move.fromUci("e7e8r")!!))
    }

    @Test
    fun pickerPrefersNearbyUnseenPuzzles() {
        val puzzles = listOf(600, 1000, 1400, 1450, 2100).mapIndexed { i, rating ->
            Puzzle("p$i", "6k1/4Pppp/8/8/8/8/5PPP/6K1 w - - 0 1", moves("e7e8q"), rating)
        }
        val picker = PuzzlePicker(puzzles, kotlin.random.Random(1))
        repeat(20) {
            assertTrue(picker.pick(1400)!!.rating in 1400..1450)
        }
        assertEquals("p3", picker.pick(1400, recentlySeen = setOf("p2"))!!.id)
        assertEquals("p4", picker.pick(2000)!!.id)
        assertNull(PuzzlePicker(emptyList()).pick(1400))
    }
}
