package com.smarter.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.smarter.core.puzzle.Difficulty
import com.smarter.core.puzzle.Puzzle
import com.smarter.core.puzzle.PuzzlePicker
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.Executors

/**
 * Serves puzzles from the Lichess API when online, falling back to the bundled pack
 * (assets/puzzles.csv, in Lichess puzzle-database format).
 */
class PuzzleRepository(context: Context, private val settings: SettingsRepository) {
    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private val mainThread = Handler(Looper.getMainLooper())
    private val offline: PuzzlePicker by lazy { loadBundledPack() }

    /** Loads a puzzle off the main thread and delivers it on the main thread. */
    fun next(difficulty: Difficulty, callback: (Puzzle) -> Unit) {
        executor.execute {
            val recent = settings.recentPuzzleIds
            val puzzle = (if (settings.onlinePuzzles) fetchOnline(difficulty, recent) else null)
                ?: offline.pick(difficulty, recent)
                ?: error("The bundled puzzle pack is empty")
            settings.rememberPuzzle(puzzle.id)
            mainThread.post { callback(puzzle) }
        }
    }

    private fun fetchOnline(difficulty: Difficulty, recent: Set<String>): Puzzle? {
        repeat(2) {
            val puzzle = fetchLichessPuzzle(difficulty) ?: return null
            if (puzzle.id !in recent) return puzzle
        }
        return null
    }

    private fun fetchLichessPuzzle(difficulty: Difficulty): Puzzle? {
        val connection = URI("$LICHESS_NEXT_PUZZLE?difficulty=${difficulty.apiName}").toURL().openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Smarter-Android (chess puzzle app blocker)")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val game = json.getJSONObject("game")
            val puzzle = json.getJSONObject("puzzle")
            val solution = puzzle.getJSONArray("solution")
            val themes = puzzle.optJSONArray("themes")
            Puzzle.fromLichessApi(
                id = puzzle.getString("id"),
                pgn = game.getString("pgn"),
                initialPly = puzzle.getInt("initialPly"),
                solution = List(solution.length()) { solution.getString(it) },
                rating = puzzle.optInt("rating", difficulty.targetRating),
                themes = if (themes == null) emptyList() else List(themes.length()) { themes.getString(it) },
            )
        } catch (e: Exception) {
            // Offline, timed out, rate limited or an unexpected response: use the bundled pack.
            Log.i(TAG, "Lichess puzzle unavailable: $e")
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun loadBundledPack(): PuzzlePicker {
        val puzzles = appContext.assets.open(PACK_ASSET).bufferedReader().useLines { lines ->
            lines.mapNotNull { Puzzle.fromLichessCsv(it) }.toList()
        }
        return PuzzlePicker(puzzles)
    }

    private companion object {
        const val TAG = "PuzzleRepository"
        const val LICHESS_NEXT_PUZZLE = "https://lichess.org/api/puzzle/next"
        const val PACK_ASSET = "puzzles.csv"
        const val TIMEOUT_MS = 5_000
    }
}
