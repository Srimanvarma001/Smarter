#!/usr/bin/env python3
"""Build the app's offline puzzle pack from the Lichess puzzle database.

Download lichess_db_puzzle.csv.zst from https://database.lichess.org/#puzzles (CC0), then:

    python3 tools/make_puzzle_pack.py lichess_db_puzzle.csv.zst > app/src/main/assets/puzzles.csv

It keeps well-rated, often-played puzzles and samples an even spread across rating bands, so
every difficulty level has puzzles. Reading a .zst file needs the `zstd` command; a plain .csv
(or "-" for stdin) works without it. Run the core tests afterwards (./gradlew :core:test) to
check that every puzzle in the new pack parses.
"""
import argparse
import csv
import random
import subprocess
import sys

HEADER = "PuzzleId,FEN,Moves,Rating,RatingDeviation,Popularity,NbPlays,Themes,GameUrl,OpeningTags"


def open_rows(path):
    if path == "-":
        return sys.stdin
    if path.endswith(".zst"):
        proc = subprocess.Popen(["zstd", "-dc", path], stdout=subprocess.PIPE, text=True)
        return proc.stdout
    return open(path, newline="")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source", help="lichess_db_puzzle.csv(.zst), or - for stdin")
    parser.add_argument("--per-band", type=int, default=300, help="puzzles per 200-point rating band (default 300)")
    parser.add_argument("--min-rating", type=int, default=400)
    parser.add_argument("--max-rating", type=int, default=2600)
    parser.add_argument("--min-popularity", type=int, default=85, help="Lichess popularity, -100..100 (default 85)")
    parser.add_argument("--min-plays", type=int, default=1000)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    rng = random.Random(args.seed)
    bands = {}  # band index -> reservoir of rows
    seen = {}  # band index -> rows considered

    for row in csv.reader(open_rows(args.source)):
        if len(row) < 8 or row[0] == "PuzzleId":
            continue
        try:
            rating, popularity, plays = int(row[3]), int(row[5]), int(row[6])
        except ValueError:
            continue
        if not (args.min_rating <= rating < args.max_rating):
            continue
        if popularity < args.min_popularity or plays < args.min_plays:
            continue
        band = (rating - args.min_rating) // 200
        reservoir = bands.setdefault(band, [])
        seen[band] = seen.get(band, 0) + 1
        # Reservoir sampling keeps a uniform sample of each band in one pass.
        if len(reservoir) < args.per_band:
            reservoir.append(row)
        else:
            slot = rng.randrange(seen[band])
            if slot < args.per_band:
                reservoir[slot] = row

    out = csv.writer(sys.stdout, lineterminator="\n")
    print(HEADER)
    total = 0
    for band in sorted(bands):
        for row in sorted(bands[band], key=lambda r: int(r[3])):
            out.writerow(row[:10] + [""] * (10 - len(row[:10])))
            total += 1
    print(f"Wrote {total} puzzles in {len(bands)} rating bands", file=sys.stderr)


if __name__ == "__main__":
    main()
