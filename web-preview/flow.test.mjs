// Browser test for web-preview/: plays the whole flow with real taps at Nothing Phone (4a) size
// (408 x 907 CSS px at 3x = 1224 x 2720). Needs Playwright (npm i -D playwright).
// Serve the repo root first:  python3 -m http.server 8765
// Then run:                   node web-preview/flow.test.mjs <screenshot dir> [dark|light]
import { chromium } from 'playwright';
const out = process.argv[2];
const scheme = process.argv[3] || 'dark';
const browser = await chromium.launch();
const ctx = await browser.newContext({ viewport: { width: 408, height: 907 }, deviceScaleFactor: 3, isMobile: true, hasTouch: true, colorScheme: scheme });
const page = await ctx.newPage();
const errors = [];
page.on('pageerror', e => errors.push(e.message));
const log = (...a) => console.log(...a);
const check = (cond, msg) => { if (!cond) { errors.push('ASSERT: ' + msg); log('FAIL', msg); } else log('ok  ', msg); };

await page.goto('http://localhost:8765/web-preview/');
await page.waitForSelector('body[data-ready="1"]');

// Blocker off: opening an app only shows a toast.
await page.tap('[data-open="com.instagram.android"]');
check(await page.isVisible('#home'), 'stays home while blocker is off');
await page.tap('#enable');
check(await page.textContent('#status-text') === 'On', 'blocker turns on');
// Bundled puzzles only, so the rating checks below don't depend on the network.
await page.tap('[data-t="online"]');
check(await page.evaluate(() => window.smarter.state.settings.online === false), 'online puzzles off');

// Shorten the allowance to 2 minutes with the stepper (30 -> 0 then +2 steps of 5? use -) .
for (let i = 0; i < 6; i++) await page.tap('[data-k="allowance"][data-d="-1"]');
check((await page.textContent('#rules')).includes('0 min'), 'allowance stepper goes down to 0');
await page.tap('[data-k="allowance"][data-d="1"]');
check((await page.textContent('#rules')).includes('5 min'), 'allowance 5 min');
await page.screenshot({ path: `${out}/1-home-${scheme}.png` });

// Use Instagram until it locks (1 s = 1 min). The 1-minute toast should appear.
await page.tap('[data-open="com.instagram.android"]');
check(await page.isVisible('#appsim'), 'simulated app opens');
await page.screenshot({ path: `${out}/2-app-${scheme}.png` });
const warnedOk = await page.waitForFunction(() => document.querySelector('#toast.show')?.textContent.includes('1 minute left'), null, { timeout: 8000 }).then(() => true, () => false);
check(warnedOk, 'one-minute warning');
await page.waitForSelector('#puzzle.active', { timeout: 8000 });
check((await page.textContent('#lock-sub')).startsWith('Solve 3 puzzles'), 'lock screen asks for 3 puzzles');
const firstRating = await page.evaluate(() => window.smarter.lock.session.puzzle.rating);
check(firstRating >= 1300 && firstRating <= 1500, `first puzzle is rated 1300-1500 (${firstRating})`);
await page.waitForFunction(() => window.smarter.lock.session);
await page.waitForTimeout(300);
await page.screenshot({ path: `${out}/3-lock-${scheme}.png` });

// The board stays square and the lock screen fits without scrolling; on a short screen the
// board shrinks instead of being squashed.
const isSquare = () => page.$eval('#board', b => { const r = b.getBoundingClientRect(); return r.width > 200 && Math.abs(r.width - r.height) < 1; });
const fits = () => page.$eval('#puzzle', s => s.scrollHeight <= s.clientHeight);
check(await isSquare() && await fits(), 'board is square and lock screen fits');
await page.setViewportSize({ width: 408, height: 640 });
check(await isSquare() && await fits(), 'board shrinks but stays square on a short screen');
await page.setViewportSize({ width: 408, height: 907 });

// Helper: tap a square on the board.
async function tapSquare(sq) {
  const box = await page.locator('#board').boundingBox();
  const white = await page.evaluate(() => window.smarter.board.white);
  const f = sq.charCodeAt(0) - 97, r = +sq[1] - 1;
  const col = white ? f : 7 - f, row = white ? 7 - r : r;
  await page.touchscreen.tap(box.x + (col + .5) * box.width / 8, box.y + (row + .5) * box.height / 8);
}
async function playHintByTaps() {
  const uci = await page.evaluate(() => window.smarter.lock.session.hint());
  await tapSquare(uci.slice(0, 2));
  await page.waitForTimeout(80);
  await tapSquare(uci.slice(2, 4));
  if (uci.length === 5) { await page.waitForSelector('.promo'); await page.tap(`.promo [data-p="${uci[4]}"]`); }
  return uci;
}

// Selecting a piece shows its legal moves.
const first = await page.evaluate(() => window.smarter.lock.session.hint());
await tapSquare(first.slice(0, 2));
await page.waitForTimeout(150);
check(await page.locator('#board .hl.target').count() > 0, 'tapping a piece shows target dots');
await page.screenshot({ path: `${out}/4-selected-${scheme}.png` });
await tapSquare(first.slice(0, 2)); // deselect

// Hint highlights the from-square.
await page.tap('#hint-btn');
check(await page.locator('#board .hl.hint').count() === 1, 'hint highlights one square');

// Solve puzzle 1 by real taps (all moves).
async function solveCurrent() {
  const startSolved = await page.evaluate(() => window.smarter.lock.solved);
  for (let i = 0; i < 6; i++) {
    await page.waitForFunction(() => window.smarter.board.interactive || window.smarter.lock.session?.state !== 'playing');
    const st = await page.evaluate(() => window.smarter.lock.session?.state);
    if (st !== 'playing') break;
    await playHintByTaps();
    await page.waitForTimeout(700);
    if (await page.evaluate(n => window.smarter.lock.solved > n, startSolved)) break;
  }
}
await solveCurrent();
check(await page.evaluate(() => window.smarter.lock.solved) === 1, 'first puzzle solved by tapping');
check(await page.locator('#progress i.done').count() === 1, 'progress shows one solved');
await page.screenshot({ path: `${out}/5-solved-${scheme}.png` });

// Next puzzle: play a wrong move on purpose.
await page.waitForFunction(() => window.smarter.lock.session && window.smarter.board.interactive);
const wrong = await page.evaluate(() => {
  const s = window.smarter.lock.session, hint = s.hint();
  const moves = s.chess.moves({ verbose: true }).filter(m => m.lan !== hint);
  const c = moves.find(m => { const x = new s.chess.constructor(s.chess.fen()); x.move(m.lan); return !x.isCheckmate(); });
  return c.lan;
});
await tapSquare(wrong.slice(0, 2)); await page.waitForTimeout(80); await tapSquare(wrong.slice(2, 4));
check(await page.locator('#board .badge.bad').count() === 1, 'wrong move gets a red badge');
await page.waitForSelector('#next-btn:visible', { timeout: 3000 });
check((await page.textContent('#status')) === 'Incorrect' && (await page.textContent('#detail')).startsWith('The answer was'), 'wrong move is rejected and answer shown');
check(await page.evaluate(() => window.smarter.lock.solved) === 1, 'failed puzzle does not count');
const secondRating = await page.evaluate(() => window.smarter.lock.session.puzzle.rating);
check(secondRating >= 1350 && secondRating <= 1550, `after one solve the target climbs to 1450 (${secondRating})`);
await page.screenshot({ path: `${out}/6-wrong-${scheme}.png` });
await page.tap('#next-btn');

// Solve the remaining two.
await page.waitForFunction(() => window.smarter.lock.session && window.smarter.board.interactive);
await solveCurrent();
await page.waitForFunction(() => window.smarter.lock.session && window.smarter.board.interactive);
await solveCurrent();
await page.waitForSelector('#unlocked.show', { timeout: 3000 });
check((await page.textContent('#unlocked-num')) === '+10 min', 'unlock overlay shows +10 min');
await page.screenshot({ path: `${out}/7-unlocked-${scheme}.png` });
await page.waitForSelector('#appsim.active', { timeout: 4000 });
check(Number(await page.textContent('#sim-left')) >= 9, 'app reopens with ~10 minutes');
await page.tap('#close-app');
const info = await page.textContent('#apps');
check(info.includes('1 unlock'), 'home shows one unlock');
check((await page.textContent('#solved-today')) === '3', 'stats count 3 solved today');
await page.screenshot({ path: `${out}/8-home-after-${scheme}.png` });

// Second unlock needs 4 puzzles (escalation).
await page.tap('[data-open="com.instagram.android"]');
await page.waitForSelector('#puzzle.active', { timeout: 15000 });
check((await page.textContent('#lock-sub')).startsWith('Solve 4 puzzles'), 'second unlock needs 4 puzzles');
await page.waitForFunction(() => window.smarter.lock.session);
const escalated = await page.evaluate(() => window.smarter.lock.session.puzzle.rating);
check(escalated >= 1450 && escalated <= 1650, `second unlock starts harder, around 1550 (${escalated})`);
await page.tap('#leave-btn');
check(await page.isVisible('#home'), 'leave goes home');

// Picker: search and toggle.
await page.tap('#edit-apps');
await page.fill('#search', 'tik');
check(await page.locator('[data-pick]').count() === 1, 'search filters apps');
await page.tap('[data-pick="com.zhiliaoapp.musically"]');
await page.fill('#search', '');
await page.screenshot({ path: `${out}/9-picker-${scheme}.png` });
await page.tap('[data-go="home"]');
check((await page.textContent('#apps')).includes('TikTok'), 'picked app shows on home');

// Practice mode.
await page.tap('#practice');
await page.waitForFunction(() => window.smarter.lock.session);
check((await page.textContent('#lock-title')) === 'Practice', 'practice mode');
await page.waitForTimeout(300);
await page.screenshot({ path: `${out}/10-practice-${scheme}.png` });

console.log('page errors:', JSON.stringify(errors.filter(e => !e.startsWith('ASSERT'))));
console.log(errors.some(e => e.startsWith('ASSERT')) ? 'RESULT: FAILED' : 'RESULT: PASSED');
await browser.close();
