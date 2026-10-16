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

    private lateinit var backButton: View
    private lateinit var icon: ImageView
    private lateinit var appName: TextView
    private lateinit var lockedTag: TextView
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var progress: ProgressDots
    private lateinit var status: TextView
    private lateinit var metaLeft: TextView
    private lateinit var metaRight: TextView
    private lateinit var board: ChessBoardView
    private lateinit var hintButton: TextView
    private lateinit var nextButton: TextView
    private lateinit var unlockedOverlay: LinearLayout
    private lateinit var unlockedAmount: TextView
    private lateinit var unlockedText: TextView

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

    /** True once a puzzle is on the board (used by tests). */
    internal val isShowingPuzzle: Boolean get() = session != null

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
        unlockedOverlay.visibility = View.GONE

        if (app != null) {
            val unlocks = smarter.ledger.today(app, now).unlocks
            required = settings.puzzlesRequired(unlocks)
            difficulty = settings.difficultyFor(unlocks)
            backButton.visibility = View.GONE
            icon.setImageDrawable(appIcon(app))
            icon.visibility = View.VISIBLE
            appName.text = appLabel(app).uppercase()
            lockedTag.visibility = View.VISIBLE
            title.text = "SOLVE $required TO UNLOCK"
            subtitle.text = buildString {
                append("Time's up. Earn ${settings.unlockMinutes} more minutes.")
                if (unlocks > 0) append(" Unlock #${unlocks + 1} today, so they get harder.")
            }
        } else {
            required = 0
            difficulty = settings.difficulty
            backButton.visibility = View.VISIBLE
            icon.visibility = View.GONE
            appName.text = "PRACTICE"
            lockedTag.visibility = View.GONE
            title.text = "WARM UP"
            subtitle.text = "Practice puzzles don't unlock anything."
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
        metaLeft.text = ""
        metaRight.text = ""
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
        val mateIn = mateIn(puzzle)
        metaLeft.text = "RATING ${puzzle.rating}" + if (mateIn != null) " · MATE IN $mateIn" else ""
        metaRight.text = if (puzzle.id.startsWith("smr")) "BUILT-IN" else "LICHESS"
        if (targetApp == null || required - solved > 1) prefetchNextPuzzle()
    }

    private fun mateIn(puzzle: Puzzle): String? =
        puzzle.themes.firstNotNullOfOrNull { Regex("mateIn(\\d)").matchEntire(it)?.groupValues?.get(1) }

    private fun toMoveText(session: PuzzleSession): String {
        val side = if (session.solverColor == Color.WHITE) "White" else "Black"
        val mateIn = mateIn(session.puzzle)
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
                board.hapticConfirm()
                board.setPosition(result.afterMove, result.move)
                board.interactive = false
                setStatus("✓ Good move!", R.color.text_primary)
                handler.postDelayed({
                    board.setPosition(result.afterReply, result.reply, animate = true)
                    board.interactive = true
                    setStatus(toMoveText(session), R.color.text_primary)
                }, REPLY_DELAY_MS)
            }

            is PuzzleSession.Result.Solved -> {
                board.hapticConfirm()
                board.setPosition(result.afterMove, result.move)
                board.interactive = false
                onSolved()
            }

            is PuzzleSession.Result.Wrong -> {
                board.hapticReject()
                board.setPosition(before.play(result.move), result.move)
                board.interactive = false
                hintButton.visibility = View.GONE
                setStatus("✗ Not quite. The answer was ${San.of(before, result.expected)}.", R.color.accent)
                handler.postDelayed({
                    board.setPosition(before.play(result.expected), result.expected, animate = true)
                    nextButton.visibility = View.VISIBLE
                }, SHOW_ANSWER_DELAY_MS)
            }
        }
    }

    private fun onSolved() {
        solved++
        smarter.settings.recordSolved()
        renderProgress()
        hintButton.visibility = View.GONE
        val app = targetApp
        if (app != null && solved >= required) {
            unlock(app)
            return
        }
        val left = required - solved
        setStatus(if (app != null) "✓ Solved! $left to go." else "✓ Solved! That's $solved this session.", R.color.text_primary)
        handler.postDelayed({ loadNextPuzzle() }, NEXT_PUZZLE_DELAY_MS)
    }

    private fun unlock(app: String) {
        unlocked = true
        val settings = smarter.settings.blockerSettings
        smarter.ledger.grantUnlock(app, settings, System.currentTimeMillis())
        unlockedAmount.text = "+${settings.unlockMinutes}"
        unlockedText.text = "MIN UNLOCKED · ${appLabel(app).uppercase()}"
        unlockedOverlay.alpha = 0f
        unlockedOverlay.visibility = View.VISIBLE
        unlockedOverlay.animate().alpha(1f).setDuration(220).start()
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
        progress.visibility = if (required > 0) View.VISIBLE else View.GONE
        progress.total = required
        progress.done = solved
    }

    private fun buildLayout(): View {
        backButton = circleButton("←", 40, "Back", ::leave).apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(12)
        }
        icon = ImageView(this)
        appName = label(color = R.color.text_secondary)
        lockedTag = redTag("Locked")
        title = dotoText(sizeSp = 34f)
        subtitle = body(sizeSp = 15f, color = R.color.text_secondary)
        progress = ProgressDots(this)
        status = body(sizeSp = 17f, bold = true).apply { minHeight = dp(24) }
        metaLeft = label()
        metaRight = label()
        board = ChessBoardView(this).apply {
            onMove = ::onUserMove
            choosePromotion = ::choosePromotion
            contentDescription = "Chessboard"
        }
        hintButton = pillButton("Hint", ButtonStyle.OUTLINE, onClick = ::showHint)
        nextButton = pillButton("Next puzzle", ButtonStyle.PRIMARY, onClick = ::loadNextPuzzle).apply { visibility = View.GONE }
        val leaveButton = pillButton("Leave", ButtonStyle.OUTLINE, onClick = ::leave)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(40)
            addView(backButton)
            addView(icon, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(12) })
            addView(appName, wrap())
            addView(lockedTag, wrap(dp(10)))
        }
        val meta = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(2), 0, dp(2), 0)
            addView(metaLeft, weighted())
            addView(metaRight, wrap())
        }
        // The board takes the remaining height; on tall 20:9 screens like the Nothing Phone
        // it fills the width and the buttons sit at the bottom, within thumb reach.
        val boardFrame = FrameLayout(this).apply {
            addView(board, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(hintButton, weighted().apply { marginEnd = dp(10) })
            addView(nextButton, weighted().apply { marginEnd = dp(10) })
            addView(leaveButton, weighted())
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
            addView(header, matchWidth())
            addView(title, matchWidth(dp(18)))
            addView(subtitle, matchWidth(dp(8)))
            addView(progress, wrap().apply { topMargin = dp(10); marginStart = -dp(5) })
            addView(status, matchWidth(dp(12)))
            addView(meta, matchWidth(dp(10)))
            addView(boardFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(10) })
            addView(buttons, matchWidth(dp(16)))
        }
        fitSystemBars(column)

        unlockedAmount = dotoText(sizeSp = 120f, color = R.color.accent)
        unlockedText = label()
        unlockedOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(colorOf(R.color.background))
            visibility = View.GONE
            isClickable = true
            addView(unlockedAmount, wrap())
            addView(unlockedText, wrap().apply { topMargin = dp(16) })
        }

        return FrameLayout(this).apply {
            setBackgroundColor(colorOf(R.color.background))
            addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(unlockedOverlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val REPLY_DELAY_MS = 450L
        private const val SHOW_ANSWER_DELAY_MS = 700L
        private const val NEXT_PUZZLE_DELAY_MS = 900L
        private const val UNLOCK_DELAY_MS = 1_400L

        fun lockIntent(context: Context, packageName: String): Intent =
            Intent(context, PuzzleActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun practiceIntent(context: Context): Intent = Intent(context, PuzzleActivity::class.java)
    }
}
