package com.smarter.core.puzzle

import com.smarter.core.chess.Move
import com.smarter.core.chess.Position
import com.smarter.core.chess.San

/**
 * A tactic to solve. [fen] is the position with the solver to move; [solution] alternates
 * solver move, opponent reply, solver move, ... and always ends with a solver move.
 */
data class Puzzle(
    val id: String,
    val fen: String,
    val solution: List<Move>,
    val rating: Int,
    val themes: List<String> = emptyList(),
    /** The opponent move that led to [fen], shown highlighted on the board. */
    val lastMove: Move? = null,
) {
    init {
        require(solution.isNotEmpty() && solution.size % 2 == 1) {
            "Puzzle $id must have an odd number of solution moves"
        }
    }

    val startPosition: Position get() = Position.fromFen(fen)

    /** Number of moves the solver has to find. */
    val solverMoveCount: Int get() = (solution.size + 1) / 2

    companion object {
        /**
         * Parses one row of the Lichess puzzle database
         * (https://database.lichess.org/#puzzles), i.e.
         * `PuzzleId,FEN,Moves,Rating,RatingDeviation,Popularity,NbPlays,Themes,GameUrl,OpeningTags`.
         *
         * In that format the FEN is the position *before* the opponent's move, and the first
         * UCI move is played by the opponent. Returns null for headers and malformed rows.
         */
        fun fromLichessCsv(line: String): Puzzle? {
            val cols = line.split(',')
            if (cols.size < 4 || cols[0] == "PuzzleId") return null
            return try {
                val moves = cols[2].trim().split(' ').filter { it.isNotEmpty() }.map { requireNotNull(Move.fromUci(it)) }
                if (moves.size < 2) return null
                val before = Position.fromFen(cols[1])
                val setup = moves.first()
                if (!before.isLegal(setup)) return null
                val start = before.play(setup)
                val solution = moves.drop(1)
                if (!start.isLegal(solution.first())) return null
                Puzzle(
                    id = cols[0],
                    fen = start.toFen(),
                    solution = solution,
                    rating = cols[3].trim().toInt(),
                    themes = cols.getOrNull(7)?.trim()?.split(' ')?.filter { it.isNotEmpty() }.orEmpty(),
                    lastMove = setup,
                )
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        /**
         * Builds a puzzle from a Lichess API response (`/api/puzzle/next`, `/api/puzzle/daily`).
         * The API gives the game as space-separated SAN moves plus `initialPly`; the puzzle
         * starts once the game has been replayed through ply `initialPly` (inclusive).
         */
        fun fromLichessApi(
            id: String,
            pgn: String,
            initialPly: Int,
            solution: List<String>,
            rating: Int,
            themes: List<String>,
        ): Puzzle? {
            val solutionMoves = solution.map { Move.fromUci(it) ?: return null }
            if (solutionMoves.size % 2 == 0) return null

            // Replay the game, remembering every position so we can pick the right start even if
            // the API's pgn has been truncated differently than initialPly suggests.
            val sans = pgn.split(' ').filter { it.isNotEmpty() && !it.endsWith('.') && !it.first().isDigit() }
            val positions = mutableListOf(Position.initial())
            val played = mutableListOf<Move>()
            for (san in sans) {
                val move = San.parse(positions.last(), san) ?: return null
                played += move
                positions += positions.last().play(move)
            }

            val candidates = listOf(initialPly + 1, played.size).distinct().filter { it in 1..played.size }
            for (plies in candidates) {
                val start = positions[plies]
                if (start.isLegal(solutionMoves.first())) {
                    return Puzzle(id, start.toFen(), solutionMoves, rating, themes, lastMove = played[plies - 1])
                }
            }
            return null
        }
    }
}
