package com.smarter.core.puzzle

import com.smarter.core.chess.Color
import com.smarter.core.chess.Move
import com.smarter.core.chess.Position

/**
 * Plays through one puzzle. Like Lichess, a move is accepted if it is the expected solution
 * move or if it delivers checkmate.
 */
class PuzzleSession(val puzzle: Puzzle) {
    enum class State { PLAYING, SOLVED, FAILED }

    sealed interface Result {
        /** Right move; the opponent answered with [reply]. */
        data class Correct(val move: Move, val afterMove: Position, val reply: Move, val afterReply: Position) : Result

        /** Right move and the puzzle is complete. */
        data class Solved(val move: Move, val afterMove: Position) : Result

        /** Legal but wrong move; the puzzle is failed. [expected] was the solution move. */
        data class Wrong(val move: Move, val expected: Move) : Result

        data object Illegal : Result
    }

    var position: Position = puzzle.startPosition
        private set
    var lastMove: Move? = puzzle.lastMove
        private set
    var state: State = State.PLAYING
        private set

    /** Index into [Puzzle.solution] of the next solver move. */
    private var ply = 0

    val solverColor: Color = position.sideToMove

    /** How many of the solver's moves have been found so far. */
    val movesFound: Int get() = ply / 2

    /** The move the solver should play next, or null when the puzzle is over. */
    fun hint(): Move? = if (state == State.PLAYING) puzzle.solution[ply] else null

    fun submit(move: Move): Result {
        check(state == State.PLAYING) { "Puzzle is already $state" }
        if (!position.isLegal(move)) return Result.Illegal

        val expected = puzzle.solution[ply]
        val afterMove = position.play(move)
        if (move != expected && !afterMove.isCheckmate()) {
            state = State.FAILED
            return Result.Wrong(move, expected)
        }

        position = afterMove
        lastMove = move
        if (afterMove.isCheckmate() || ply + 1 >= puzzle.solution.size) {
            ply = puzzle.solution.size
            state = State.SOLVED
            return Result.Solved(move, afterMove)
        }

        val reply = puzzle.solution[ply + 1]
        position = afterMove.play(reply)
        lastMove = reply
        ply += 2
        return Result.Correct(move, afterMove, reply, position)
    }
}
