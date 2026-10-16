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
import com.smarter.core.puzzle.Puzzle
import com.smarter.core.puzzle.PuzzleSession

/**
 * The lock screen: solve the required number of puzzles to unlock a blocked app for a while.
 * Every puzzle solved makes the next one harder. Started without a package name it is a
 * practice mode that unlocks nothing.
 */
class PuzzleActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var backButton: View
    private lateinit var icon: ImageView
    private lateinit var heading: TextView
    private lateinit var counter: TextView
    private lateinit var subtitle: TextView
    private lateinit var progress: SegmentedProgress
    private lateinit var sideSwatch: View
    private lateinit var status: TextView
    private lateinit var detail: TextView
    private lateinit var rating: TextView
    private lateinit var board: ChessBoardView
    private lateinit var hintButton: TextView
    private lateinit var nextButton: TextView
    private lateinit var unlockedOverlay: LinearLayout
    private lateinit var unlockedAmount: TextView
    private lateinit var unlockedText: TextView

    /** The app being unlocked, or null in practice mode. */
    private var targetApp: String? = null
    private var required = 0
    private var unlocksToday = 0
    private var solved = 0
    private var unlocked = false
    private var session: PuzzleSession? = null
    private var prefetched: Puzzle? = null
    private var prefetchedFor = 0

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
            unlocksToday = smarter.ledger.today(app, now).unlocks
            required = settings.puzzlesRequired(unlocksToday)
            backButton.visibility = View.GONE
            icon.setImageDrawable(appIcon(app))
            icon.visibility = View.VISIBLE
            heading.text = "${appLabel(app)} is locked"
            subtitle.text = buildString {
                append("Solve $required puzzles to get ${settings.unlockMinutes} more minutes.")
                if (unlocksToday > 0) append(" Unlock ${unlocksToday + 1} today, so they start harder.")
            }
        } else {
            unlocksToday = 0
            required = 0
            backButton.visibility = View.VISIBLE
            icon.visibility = View.GONE
            heading.text = "Practice"
            subtitle.text = "Puzzles get harder as you solve them. Practice doesn't unlock anything."
        }
        renderProgress()
        loadNextPuzzle()
    }

    // --- Puzzle flow ----------------------------------------------------------------------

    /** Rating for the next puzzle: higher after every solve, and after every unlock today. */
    private fun targetRating(solvedSoFar: Int = solved): Int =
        smarter.settings.blockerSettings.targetRating(unlocksToday, solvedSoFar)

    private fun loadNextPuzzle() {
        session = null
        board.interactive = false
        board.hintSquare = -1
        nextButton.visibility = View.GONE
        hintButton.visibility = View.VISIBLE

        val target = targetRating()
        val ready = prefetched
        prefetched = null
        if (ready != null && prefetchedFor == target) {
            show(ready)
            return
        }
        setStatus("Loading puzzle…", "", R.color.text_secondary)
        sideSwatch.visibility = View.INVISIBLE
        rating.text = ""
        pendingLoad = true
        val requestedFor = generation
        smarter.puzzles.next(target) { puzzle ->
            if (isDestroyed || requestedFor != generation || !pendingLoad) return@next
            pendingLoad = false
            show(puzzle)
        }
    }

    /** Fetches the puzzle that follows a solve, so it is ready straight away. */
    private fun prefetchNextPuzzle() {
        val requestedFor = generation
        val target = targetRating(solved + 1)
        smarter.puzzles.next(target) { puzzle ->
            if (!isDestroyed && requestedFor == generation && prefetched == null) {
                prefetched = puzzle
                prefetchedFor = target
            }
        }
    }

    private fun show(puzzle: Puzzle) {
        val session = PuzzleSession(puzzle)
        this.session = session
        board.whiteAtBottom = session.solverColor == Color.WHITE
        board.setPosition(session.position, session.lastMove)
        board.interactive = true
        sideSwatch.background = roundedBackground(
            if (session.solverColor == Color.WHITE) 0xFFFFFFFF.toInt() else 0xFF1A1917.toInt(), 4, colorOf(R.color.track),
        )
        sideSwatch.visibility = View.VISIBLE
        showToMove(session)
        rating.text = "Rating ${puzzle.rating}"
        if (targetApp == null || required - solved > 1) prefetchNextPuzzle()
    }

    private fun mateIn(puzzle: Puzzle): String? =
        puzzle.themes.firstNotNullOfOrNull { Regex("mateIn(\\d)").matchEntire(it)?.groupValues?.get(1) }

    private fun showToMove(session: PuzzleSession) {
        val side = if (session.solverColor == Color.WHITE) "White" else "Black"
        val mateIn = mateIn(session.puzzle)
        val goal = when {
            session.movesFound > 0 -> "Keep going."
            mateIn != null -> "Find mate in $mateIn."
            else -> "Find the best move."
        }
        setStatus("$side to move", goal)
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
                board.mark(result.move.to, ChessBoardView.Mark.CORRECT)
                board.interactive = false
                setStatus("Best move!", "Keep going.", R.color.accent)
                handler.postDelayed({
                    board.setPosition(result.afterReply, result.reply, animate = true)
                    board.interactive = true
                    showToMove(session)
                }, REPLY_DELAY_MS)
            }

            is PuzzleSession.Result.Solved -> {
                board.hapticConfirm()
                board.setPosition(result.afterMove, result.move)
                board.mark(result.move.to, ChessBoardView.Mark.CORRECT)
                board.interactive = false
                onSolved()
            }

            is PuzzleSession.Result.Wrong -> {
                board.hapticReject()
                board.setPosition(before.play(result.move), result.move)
                board.mark(result.move.to, ChessBoardView.Mark.WRONG)
                board.interactive = false
                hintButton.visibility = View.GONE
                setStatus("Incorrect", "The answer was ${San.of(before, result.expected)}.", R.color.danger)
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
        setStatus(
            "Solved!",
            if (app != null) "$left to go. The next one is a little harder." else "That's $solved this session. Next one is harder.",
            R.color.accent,
        )
        handler.postDelayed({ loadNextPuzzle() }, NEXT_PUZZLE_DELAY_MS)
    }

    private fun unlock(app: String) {
        unlocked = true
        val settings = smarter.settings.blockerSettings
        smarter.ledger.grantUnlock(app, settings, System.currentTimeMillis())
        unlockedAmount.text = "+${settings.unlockMinutes} min"
        unlockedText.text = "${appLabel(app)} unlocked"
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
        detail.text = "Move the highlighted piece."
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

    private fun setStatus(title: String, detailText: String, color: Int = R.color.text_primary) {
        status.text = title
        status.setTextColor(colorOf(color))
        detail.text = detailText
    }

    private fun renderProgress() {
        val visibility = if (required > 0) View.VISIBLE else View.GONE
        progress.visibility = visibility
        counter.visibility = visibility
        progress.total = required
        progress.done = solved
        counter.text = "$solved / $required"
    }

    private fun buildLayout(): View {
        backButton = iconButton("←", 40, "Back", onClick = ::leave).apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(4)
        }
        icon = ImageView(this)
        heading = textView(sizeSp = 18f, weight = 600)
        counter = textView(sizeSp = 15f, color = R.color.text_secondary, weight = 600)
        subtitle = textView(sizeSp = 14f, color = R.color.text_secondary)
        progress = SegmentedProgress(this)
        sideSwatch = View(this)
        status = textView(sizeSp = 18f, weight = 700)
        detail = textView(sizeSp = 14f, color = R.color.text_secondary).apply { minHeight = dp(20) }
        rating = textView(sizeSp = 14f, color = R.color.text_secondary, weight = 500)
        board = ChessBoardView(this).apply {
            onMove = ::onUserMove
            choosePromotion = ::choosePromotion
            contentDescription = "Chessboard"
        }
        hintButton = button("Hint", ButtonStyle.SECONDARY, onClick = ::showHint)
        nextButton = button("Next puzzle", ButtonStyle.PRIMARY, onClick = ::loadNextPuzzle).apply { visibility = View.GONE }
        val leaveButton = button("Leave", ButtonStyle.SECONDARY, onClick = ::leave)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(44)
            addView(backButton)
            addView(icon, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(10) })
            addView(heading, weighted())
            addView(counter, wrap(dp(12)))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
            addView(header, matchWidth())
            addView(subtitle, matchWidth(dp(4)))
            addView(progress, matchWidth(dp(14)))
        }

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(sideSwatch, LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(10) })
            addView(status, weighted())
            addView(rating, wrap(dp(12)))
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(hintButton, weighted().apply { marginEnd = dp(10) })
            addView(nextButton, weighted().apply { marginEnd = dp(10) })
            addView(leaveButton, weighted())
        }
        val statusPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(8))
            addView(statusRow, matchWidth())
            addView(detail, matchWidth(dp(4)).apply { marginStart = dp(28) })
        }
        buttons.setPadding(dp(16), dp(8), dp(16), dp(16))

        val column = PuzzleLayout(this, top, board, statusPanel, buttons)
        fitSystemBars(column)

        unlockedAmount = title(sizeSp = 56f).apply { setTextColor(colorOf(R.color.accent)) }
        unlockedText = textView(sizeSp = 16f, color = R.color.text_secondary)
        unlockedOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(colorOf(R.color.background))
            visibility = View.GONE
            isClickable = true
            addView(unlockedAmount, wrap())
            addView(unlockedText, wrap().apply { topMargin = dp(10) })
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

/**
 * Lays out the lock screen like chess.com's puzzle view: the header, then the board spanning the
 * full width (or as large as fits, staying square, on a short screen), the status right under
 * it, and the buttons pinned to the bottom within thumb reach.
 */
private class PuzzleLayout(
    context: Context,
    private val header: View,
    private val board: View,
    private val status: View,
    private val buttons: View,
) : ViewGroup(context) {
    private val gap = context.dp(16)

    init {
        for (child in listOf(header, board, status, buttons)) addView(child)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val innerWidth = width - paddingLeft - paddingRight
        val innerHeight = height - paddingTop - paddingBottom
        val fullWidth = MeasureSpec.makeMeasureSpec(innerWidth, MeasureSpec.EXACTLY)
        val anyHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        for (child in listOf(header, status, buttons)) child.measure(fullWidth, anyHeight)
        val rest = innerHeight - header.measuredHeight - gap - status.measuredHeight - buttons.measuredHeight
        val boardSize = MeasureSpec.makeMeasureSpec(rest.coerceIn(0, innerWidth), MeasureSpec.EXACTLY)
        board.measure(boardSize, boardSize)
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val left = paddingLeft
        var y = paddingTop
        header.layout(left, y, left + header.measuredWidth, y + header.measuredHeight)
        y += header.measuredHeight + gap
        val boardLeft = left + (width - paddingLeft - paddingRight - board.measuredWidth) / 2
        board.layout(boardLeft, y, boardLeft + board.measuredWidth, y + board.measuredHeight)
        y += board.measuredHeight
        status.layout(left, y, left + status.measuredWidth, y + status.measuredHeight)
        val bottom = height - paddingBottom
        buttons.layout(left, bottom - buttons.measuredHeight, left + buttons.measuredWidth, bottom)
    }
}
