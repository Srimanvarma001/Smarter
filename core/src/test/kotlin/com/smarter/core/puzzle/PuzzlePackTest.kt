package com.smarter.core.puzzle

import com.smarter.core.chess.Move
import com.smarter.core.chess.Position
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Checks the offline puzzle pack bundled with the app. Every puzzle must parse and replay
 * legally. The hand-made puzzles (ids starting with "smr") are also proven sound: the line ends
 * in mate, and each solver move is the only one that forces mate in time (other moves would be
 * marked wrong, so a second solution would be unfair). Puzzles from the Lichess database are
 * already verified by Lichess with Stockfish.
 */
class PuzzlePackTest {
    private val packFile = File(System.getProperty("puzzlePack") ?: "../app/src/main/assets/puzzles.csv")

    private fun loadPack(): List<Puzzle> {
        val lines = packFile.readLines().drop(1).filter { it.isNotBlank() }
        return lines.map { Puzzle.fromLichessCsv(it) ?: fail("Unparseable puzzle row: $it") }
    }

    @Test
    fun packIsWellFormed() {
        val puzzles = loadPack()
        assertTrue(puzzles.size >= 10, "pack has only ${puzzles.size} puzzles")
        assertEquals(puzzles.size, puzzles.map { it.id }.toSet().size, "duplicate ids")
        for (puzzle in puzzles) {
            var pos = puzzle.startPosition
            for (move in puzzle.solution) {
                assertTrue(pos.isLegal(move), "${puzzle.id}: $move is illegal in ${pos.toFen()}")
                pos = pos.play(move)
            }
        }
    }

    @Test
    fun matePuzzlesAreSoundAndUnique() {
        loadPack().filter { it.id.startsWith("smr") && mateLength(it) != null }.forEach(::assertSoundMate)
    }

    @Test
    fun rejectsPuzzleWithTwoSolutions() {
        // Labelled mate in 2, but Ra8# and Rb8# both mate at once.
        val puzzle = Puzzle.fromLichessCsv("x,6k1/5ppp/8/8/8/8/R7/1R4K1 b - - 0 1,g8h8 a2a7 h8g8 a7a8,1000,0,0,0,mateIn2,,")!!
        val error = runCatching { assertSoundMate(puzzle) }.exceptionOrNull()
        assertTrue(error is AssertionError, "expected the checker to reject ${puzzle.id}")
    }

    private fun mateLength(puzzle: Puzzle): Int? =
        puzzle.themes.firstNotNullOfOrNull { Regex("mateIn(\\d)").matchEntire(it)?.groupValues?.get(1)?.toInt() }

    private fun assertSoundMate(puzzle: Puzzle) {
        val n = requireNotNull(mateLength(puzzle))
        assertEquals(n, puzzle.solverMoveCount, "${puzzle.id}: solution length vs theme")

        var pos = puzzle.startPosition
        for ((i, move) in puzzle.solution.withIndex()) {
            if (i % 2 == 0) {
                val left = n - i / 2
                if (left > 1) {
                    assertTrue(mateMoves(pos, left - 1).isEmpty(), "${puzzle.id}: shorter mate exists at ply $i")
                    assertEquals(listOf(move), mateMoves(pos, left), "${puzzle.id}: solver moves at ply $i")
                }
            }
            pos = pos.play(move)
        }
        assertTrue(pos.isCheckmate(), "${puzzle.id}: line does not end in mate")
    }

    /** Moves for the side to move that force mate within [n] moves. */
    private fun mateMoves(pos: Position, n: Int): List<Move> = pos.legalMoves().filter { forcesMate(pos.play(it), n - 1) }

    /** True if, with the defender to move in [pos], the attacker mates within [n] more moves. */
    private fun forcesMate(pos: Position, n: Int): Boolean {
        if (pos.isCheckmate()) return true
        if (n == 0) return false
        val replies = pos.legalMoves()
        if (replies.isEmpty()) return false // stalemate
        return replies.all { reply -> mateMoves(pos.play(reply), n).isNotEmpty() }
    }
}
