# Third-party assets

| Asset | Where | Author | License |
| --- | --- | --- | --- |
| cburnett chess pieces (Lichess's default set) | `app/src/main/res/drawable/piece_*.xml`, `web-preview/pieces/` (converted from SVG by `tools/svg_to_vector.py`; launcher icon pawn) | [Colin M.L. Burnett](https://en.wikipedia.org/wiki/User:Cburnett), via [lichess-org/lila](https://github.com/lichess-org/lila/tree/master/public/piece/cburnett) | GPLv2 or later (`cburnett-GPL-2.0.txt`) |
| chess.js (browser preview only) | `web-preview/vendor/chess.js` | [Jeff Hlywa](https://github.com/jhlywa/chess.js) | BSD 2-Clause (`chess.js-LICENSE.txt`) |
| Offline puzzle pack, online puzzles | `app/src/main/assets/puzzles.csv` (sampled by `tools/make_puzzle_pack.py`), Lichess puzzle API | [lichess.org](https://database.lichess.org/#puzzles) | CC0 |

The board colours follow chess.com's default green board; colours aren't copyrightable and no
chess.com artwork is used.
