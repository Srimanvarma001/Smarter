package com.smarter.app.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.window.OnBackInvokedDispatcher
import com.smarter.app.R
import com.smarter.app.smarter
import com.smarter.core.chess.Color
import com.smarter.core.chess.Move
import com.smarter.core.chess.PieceType
import com.smarter.core.chess.San
import com.smarter.core.puzzle.Difficulty
import com.smarter.core.puzzle.Puzzle
import com.smarter.core.puzzle.PuzzleSession

/**
 * The lock screen: solve the required number of puzzles to unlock a blocked app for a while.
 * Started without a package name it is a practice mode that unlocks nothing.
 */
class PuzzleActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var icon: ImageView
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var progress: LinearLayout
    private lateinit var status: TextView
    private lateinit var details: TextView
    private lateinit var board: ChessBoardView
    private lateinit var hintButton: TextView
    private lateinit var nextButton: TextView

    /** The app being unlocked, or null in practice mode. */
    private var targetApp: String? = null
    private var required = 0
    private var difficulty = Difficulty.NORMAL
    private var solved = 0
    private var unlocked = false
    private var session: PuzzleSession? = null
    private var prefetched: Puzzle? = null

    /** Bumped whenever a new unlock starts, so late puzzle deliveries for an old one are dropped. */
    private var generation = 0
    private var pendingLoad = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { leave() }
        }
        start(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        start(intent)
    }

    @Deprecated("Replaced by OnBackInvokedCallback on Android 13+")
    override fun onBackPressed() = leave()

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun start(intent: Intent) {
        val app = intent.getStringExtra(EXTRA_PACKAGE)
        // Opening the blocked app again while its puzzles are in progress keeps the progress.
        if (app != null && app == targetApp && !unlocked && (session != null || pendingLoad)) return

        val settings = smarter.settings.blockerSettings
        val now = System.currentTimeMillis()
        if (app != null && smarter.ledger.remainingMs(app, settings, now) > 0) {
            // Not actually out of time (e.g. the settings changed); nothing to unlock.
            finish()
            return
        }

        generation++
        handler.removeCallbacksAndMessages(null)
        targetApp = app
        solved = 0
        unlocked = false
        prefetched = null
        session = null
        pendingLoad = false

        if (app != null) {
            val unlocks = smarter.ledger.today(app, now).unlocks
            required = settings.puzzlesRequired(unlocks)
            difficulty = settings.difficultyFor(unlocks)
            icon.setImageDrawable(appIcon(app))
            icon.visibility = View.VISIBLE
            title.text = "${appLabel(app)} is locked"
            val plural = if (required == 1) "puzzle" else "puzzles"
            subtitle.text = buildString {
                append("Your time is up. Solve $required $plural to unlock ${settings.unlockMinutes} more minutes.")
                if (unlocks > 0) append(" Unlock #${unlocks + 1} today, so the puzzles get harder.")
            }
        } else {
            required = 0
            difficulty = settings.difficulty
            icon.visibility = View.GONE
            title.text = "Puzzle practice"
            subtitle.text = "Warm up. Practice puzzles don't unlock anything."
        }
        renderProgress()
        loadNextPuzzle()
    }

    // --- Puzzle flow ----------------------------------------------------------------------

    private fun loadNextPuzzle() {
        session = null
        board.interactive = false
        board.hintSquare = -1
        nextButton.visibility = View.GONE
        hintButton.visibility = View.VISIBLE

        val ready = prefetched
        if (ready != null) {
            prefetched = null
            show(ready)
            return
        }
        setStatus("Loading puzzle…", R.color.text_secondary)
        details.text = ""
        pendingLoad = true
        val requestedFor = generation
        smarter.puzzles.next(difficulty) { puzzle ->
            if (isDestroyed || requestedFor != generation || !pendingLoad) return@next
            pendingLoad = false
            show(puzzle)
        }
    }

    private fun prefetchNextPuzzle() {
        val requestedFor = generation
        smarter.puzzles.next(difficulty) { puzzle ->
            if (!isDestroyed && requestedFor == generation && prefetched == null) prefetched = puzzle
        }
    }

    private fun show(puzzle: Puzzle) {
        val session = PuzzleSession(puzzle)
        this.session = session
        board.whiteAtBottom = session.solverColor == Color.WHITE
        board.setPosition(session.position, session.lastMove)
        board.interactive = true
        setStatus(toMoveText(session), R.color.text_primary)
        details.text = "Rating ${puzzle.rating}" + if (puzzle.id.startsWith("smr")) "" else " · lichess.org/training/${puzzle.id}"
        if (targetApp == null || required - solved > 1) prefetchNextPuzzle()
    }

    private fun toMoveText(session: PuzzleSession): String {
        val side = if (session.solverColor == Color.WHITE) "White" else "Black"
        val mateIn = session.puzzle.themes.firstNotNullOfOrNull { Regex("mateIn(\\d)").matchEntire(it)?.groupValues?.get(1) }
        val goal = when {
            session.movesFound > 0 -> "keep going"
            mateIn != null -> "find mate in $mateIn"
            else -> "find the best move"
        }
        return "$side to move: $goal."
    }

    private fun onUserMove(move: Move) {
        val session = session ?: return
        val before = session.position
        board.hintSquare = -1
        when (val result = session.submit(move)) {
            PuzzleSession.Result.Illegal -> board.setPosition(before, session.lastMove)

            is PuzzleSession.Result.Correct -> {
                board.setPosition(result.afterMove, result.move)
                board.interactive = false
                setStatus("✓ Good move!", R.color.accent)
                handler.postDelayed({
                    board.setPosition(result.afterReply, result.reply, animate = true)
                    board.interactive = true
                    setStatus(toMoveText(session), R.color.text_primary)
                }, REPLY_DELAY_MS)
            }

            is PuzzleSession.Result.Solved -> {
                board.setPosition(result.afterMove, result.move)
                board.interactive = false
                onSolved()
            }

            is PuzzleSession.Result.Wrong -> {
                board.setPosition(before.play(result.move), result.move)
                board.interactive = false
                hintButton.visibility = View.GONE
                setStatus("✗ Not quite. The answer was ${San.of(before, result.expected)}.", R.color.danger)
                handler.postDelayed({
                    board.setPosition(before.play(result.expected), result.expected, animate = true)
                    nextButton.visibility = View.VISIBLE
                }, SHOW_ANSWER_DELAY_MS)
            }
        }
    }

    private fun onSolved() {
        solved++
        renderProgress()
        hintButton.visibility = View.GONE
        val app = targetApp
        if (app != null && solved >= required) {
            unlock(app)
            return
        }
        val left = required - solved
        setStatus(if (app != null) "✓ Solved! $left to go." else "✓ Solved! That's $solved this session.", R.color.accent)
        handler.postDelayed({ loadNextPuzzle() }, NEXT_PUZZLE_DELAY_MS)
    }

    private fun unlock(app: String) {
        unlocked = true
        val settings = smarter.settings.blockerSettings
        smarter.ledger.grantUnlock(app, settings, System.currentTimeMillis())
        setStatus("Unlocked! Enjoy ${settings.unlockMinutes} more minutes of ${appLabel(app)}.", R.color.accent)
        handler.postDelayed({
            packageManager.getLaunchIntentForPackage(app)?.let {
                startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            finish()
        }, UNLOCK_DELAY_MS)
    }

    private fun showHint() {
        val move = session?.hint() ?: return
        board.hintSquare = move.from
        setStatus("Hint: move the highlighted piece.", R.color.text_primary)
    }

    private fun leave() {
        if (targetApp != null && !unlocked) {
            // Go to the home screen rather than back to the blocked app.
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }

    private fun choosePromotion(moves: List<Move>, choose: (Move) -> Unit) {
        val types = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)
            .filter { type -> moves.any { it.promotion == type } }
        AlertDialog.Builder(this)
            .setTitle("Promote to")
            .setItems(types.map { it.name.lowercase().replaceFirstChar(Char::uppercase) }.toTypedArray()) { _, which ->
                choose(moves.first { it.promotion == types[which] })
            }
            .show()
    }

    // --- Layout ---------------------------------------------------------------------------

    private fun setStatus(text: String, color: Int) {
        status.text = text
        status.setTextColor(colorOf(color))
    }

    private fun renderProgress() {
        progress.removeAllViews()
        progress.visibility = if (required > 0) View.VISIBLE else View.GONE
        repeat(required) { i ->
            progress.addView(
                View(this).apply {
                    background = roundedBackground(colorOf(if (i < solved) R.color.accent else R.color.track), 4)
                },
                LinearLayout.LayoutParams(0, dp(8), 1f).apply {
                    if (i > 0) marginStart = dp(6)
                },
            )
        }
    }

    private fun buildLayout(): View {
        icon = ImageView(this)
        title = label(sizeSp = 22f, bold = true)
        subtitle = label(sizeSp = 15f, color = R.color.text_secondary)
        progress = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        status = label(sizeSp = 18f, bold = true)
        details = label(sizeSp = 13f, color = R.color.text_secondary)
        board = ChessBoardView(this).apply {
            onMove = ::onUserMove
            choosePromotion = ::choosePromotion
            contentDescription = "Chessboard"
        }
        hintButton = pillButton("Hint", ButtonStyle.SECONDARY, ::showHint)
        nextButton = pillButton("Next puzzle", ButtonStyle.PRIMARY, ::loadNextPuzzle).apply { visibility = View.GONE }
        val leaveButton = pillButton("Leave", ButtonStyle.SECONDARY, ::leave)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(14) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(title)
                addView(subtitle, matchWidth(dp(2)))
            }, weighted())
        }
        val boardFrame = FrameLayout(this).apply {
            addView(board, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(hintButton, weighted().apply { marginEnd = dp(10) })
            addView(nextButton, weighted().apply { marginEnd = dp(10) })
            addView(leaveButton, weighted())
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorOf(R.color.background))
            setPadding(dp(20), dp(16), dp(20), dp(16))
            addView(header, matchWidth())
            addView(progress, matchWidth(dp(16)))
            addView(status, matchWidth(dp(18)))
            addView(details, matchWidth(dp(2)))
            addView(boardFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(12) })
            addView(buttons, matchWidth(dp(16)))
        }
        fitSystemBars(root)
        return root
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val REPLY_DELAY_MS = 450L
        private const val SHOW_ANSWER_DELAY_MS = 700L
        private const val NEXT_PUZZLE_DELAY_MS = 900L
        private const val UNLOCK_DELAY_MS = 1_200L

        fun lockIntent(context: Context, packageName: String): Intent =
            Intent(context, PuzzleActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun practiceIntent(context: Context): Intent = Intent(context, PuzzleActivity::class.java)
    }
}
