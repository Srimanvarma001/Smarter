# Smarter

An Android app blocker that makes you **earn screen time with chess puzzles**.

Pick the apps that eat your day (Instagram, YouTube, TikTok…). Each one gets a daily free
allowance. When it runs out, the app is covered by a lock screen with a chess puzzle. Solve
enough puzzles and you get a few more minutes. Every further unlock that day needs more,
harder puzzles.

## How it works

```
 open Instagram ──► BlockerService (Accessibility) notices the app in front
                         │ counts time once a second while it stays in front
                         ▼
               time left? ── yes ──► keep counting (toast at 1 minute left)
                         │ no
                         ▼
               PuzzleActivity: "Instagram is locked — solve 3 puzzles"
                         │ solve them all
                         ▼
               +10 minutes for Instagram today, app reopens
```

* **Default rules** (all adjustable on the main screen): 30 free minutes per app per day,
  3 puzzles per unlock, 10 minutes per unlock, Normal difficulty. With *Harder after each
  unlock* on, each extra unlock of the same app adds a puzzle (up to +3) and steps the
  difficulty up. Usage resets at local midnight.
* **Wrong move = failed puzzle.** The answer is shown and you get a new puzzle; only solved
  puzzles count. Like on Lichess, any move that gives checkmate is accepted.
* **Puzzles** come from the [Lichess puzzle API](https://lichess.org/api#tag/Puzzles) when
  online, matched to the difficulty. Offline, the app uses the bundled pack in
  `app/src/main/assets/puzzles.csv`.
* **Privacy:** the accessibility service only receives "window changed" events and the
  package name of the app in front. It cannot read screen content
  (`canRetrieveWindowContent="false"`). Everything is stored on the device.

## Install it on your phone

**Option A: download a build.** Every push runs the GitHub Actions workflow
(`.github/workflows/build.yml`), which runs the tests and builds a debug APK. Open the
latest run in the repo's **Actions** tab, download the `smarter-debug-apk` artifact, unzip
it and install the APK on your phone (allow "install unknown apps" for your browser or file
manager).

**Option B: build it yourself.** Open the project in Android Studio and press Run, or
build it from the command line:

```sh
./gradlew :app:assembleDebug     # APK in app/build/outputs/apk/debug/
./gradlew :core:test             # unit tests for chess rules, puzzles and time budgets
```

Then in the app:

1. Tap **Turn on blocker** and enable *Smarter app blocker* in Accessibility settings.
   On Android 13+ a sideloaded app's accessibility switch is greyed out at first: open
   **App info → ⋮ → Allow restricted settings**, then try again.
2. Tap **Choose apps** and tick the apps to limit.
3. Adjust the rules, or try **Practice a puzzle**.

> Each CI build is signed with a fresh debug key, so to install a newer CI build over an
> older one you must uninstall the old one first (your settings are reset).

## A bigger offline puzzle pack

The bundled pack is a small hand-made set of mate-in-1 and mate-in-2 puzzles. The unit tests
prove each one has a unique solution. For thousands of real puzzles, grab the free
(CC0) Lichess puzzle database and sample it:

```sh
curl -O https://database.lichess.org/lichess_db_puzzle.csv.zst
python3 tools/make_puzzle_pack.py lichess_db_puzzle.csv.zst > app/src/main/assets/puzzles.csv
./gradlew :core:test   # checks every puzzle in the new pack parses and replays legally
```

The default of 300 puzzles per 200-point rating band gives about 3,300 puzzles (≈450 KB).

## Project layout

| Path | What's there |
| --- | --- |
| `core/` | Plain Kotlin, no Android: chess rules (`chess/`: legal moves, FEN, SAN), puzzles (`puzzle/`: Lichess formats, puzzle session, offline picker) and the time budget (`blocker/`: usage ledger, unlock rules, `BlockerEngine`). Unit-tested on the JVM; the move generator is checked against the standard perft counts. |
| `app/` | The Android app, built with framework APIs only (no AndroidX): `BlockerService` (accessibility service), `PuzzleActivity` (lock screen), `ChessBoardView` (tap or drag to move), `MainActivity` (dashboard and rules), `AppPickerActivity`. |
| `tools/make_puzzle_pack.py` | Builds `puzzles.csv` from the Lichess puzzle database. |

## Limitations

* It's a self-control tool, not parental controls. You can always turn off the
  accessibility service or uninstall the app.
* Google Play restricts apps that use the Accessibility API. For a Play Store release this
  would need Play's accessibility declaration, or the UsageStats API plus an overlay
  instead. Sideloading works as is.
* Time is measured while a blocked app's window is in front and the screen is on, so
  picture-in-picture video or floating chat heads aren't counted.
