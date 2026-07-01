#!/usr/bin/env python3
"""
04_stratify_difficulty.py
--------------------------
Assigns a difficulty tier (EASY / MEDIUM / HARD) to every benchmark instance.

Difficulty is a property of the *detector*, not the individual Issue: an
detector's issues share a single reference implementation, so they cannot be
told apart by any source-level measure of complexity. We therefore stratify
per detector and let every instance inherit its detector's tier.

Each detector is scored on three axes of its reference check, each measured
relative to the per-detector median across the benchmark:

  Axis                 Measure                                Source
  -------------------- -------------------------------------- ----------------
  Implementation size  logical SLoC of the detector source    compute_sloc
  Interface complexity number of Lint scanner interfaces       scanner_interfaces
  Test density         number of test methods (whole detector) test_methods

A detector's score is the number of axes on which it strictly exceeds the
benchmark median. Tiers are symmetric in the median:

  EASY    score 0     (below the median on all three axes)
  MEDIUM  score 1-2   (above the median on one or two axes)
  HARD    score 3     (above the median on all three: large, multi-interface, test-heavy)

The three retained axes each track real generation difficulty
(see Section~ref{sec:results}); the combined score is monotonic in pass rate.

Usage:
    python3 04_stratify_difficulty.py [--dataset PATH] [--checks-dir PATH]
        [--out PATH] [--write] [--drop-task-fields]
"""

import argparse
import json
import re
import statistics
from collections import Counter, defaultdict
from pathlib import Path

ROOT       = Path(__file__).resolve().parent.parent.parent
DATASET    = ROOT / "data" / "dataset.jsonl"
CHECKS_DIR = ROOT / "base" / "lint" / "libs" / "lint-checks" / "src" / "main" / "java" / "com" / "android" / "tools" / "lint" / "checks"

# Axes used for the median rubric and the deprecated fields to strip.
AXES            = ("sloc", "n_scanners", "n_test_methods")
ALWAYS_DROP     = ("weighted_score",)
TASK_FIELDS     = ("methods_to_generate", "quality_tier")


def logical_sloc(path: Path) -> int:
    """Logical SLoC: a line counts unless it is blank, a comment, or made up
    only of structural punctuation. (Canonical definition from
    scripts/compute_sloc.py.)"""
    txt = path.read_text(encoding="utf-8", errors="ignore")
    txt = re.sub(r"/\*.*?\*/", "", txt, flags=re.S)        # strip block comments
    n = 0
    for line in txt.split("\n"):
        s = line.strip()
        if not s:                           continue        # blank
        if s.startswith(("//", "*", "/*")): continue        # comment
        if set(s) <= set("{}();,"):         continue        # punctuation only
        n += 1
    return n


def tier_for(score: int) -> str:
    # Symmetric in the median: EASY = below median on all axes (score 0),
    # HARD = above median on all axes (score 3), MEDIUM = mixed (1-2).
    return "EASY" if score == 0 else ("HARD" if score == 3 else "MEDIUM")


def main(dataset: Path, checks_dir: Path, out: Path,
         write: bool, drop_task_fields: bool) -> None:
    rows = [json.loads(l) for l in dataset.read_text().splitlines() if l.strip()]

    # ---- per-detector features -------------------------------------------
    by_det: dict[str, list[dict]] = defaultdict(list)
    for r in rows:
        by_det[r["detector"]].append(r)

    sloc_cache: dict[str, int] = {}
    feats: dict[str, dict] = {}
    for det, insts in by_det.items():
        cf = insts[0]["check_file"]
        if cf not in sloc_cache:
            p = checks_dir / cf
            if not p.exists():
                raise SystemExit(f"ERROR: detector source not found: {p}")
            sloc_cache[cf] = logical_sloc(p)
        # test density: methods across the detector's distinct test files
        per_test = {r["test_file"]: r["test_methods"] for r in insts}
        feats[det] = {
            "sloc":           sloc_cache[cf],
            "n_scanners":     len(insts[0]["scanner_interfaces"]),
            "n_test_methods": sum(per_test.values()),
            "n_instances":    len(insts),
        }

    medians = {ax: statistics.median([feats[d][ax] for d in feats]) for ax in AXES}
    for det in feats:
        score = sum(1 for ax in AXES if feats[det][ax] > medians[ax])
        feats[det]["score"] = score
        # Normalized difficulty score in [0,1]: the fraction of axes above the
        # benchmark median. Takes values {0, 1/3, 2/3, 1}, so the tier cutpoints
        # 0.33 / 0.67 reproduce the score-0 / score-1,2 / score-3 split exactly.
        feats[det]["difficulty_score"] = round(score / len(AXES), 4)
        feats[det]["tier"]  = tier_for(score)

    # ---- report ----------------------------------------------------------
    print(f"detectors: {len(feats)}   instances: {len(rows)}")
    print("medians:", {ax: round(medians[ax], 1) for ax in AXES})
    det_counts  = Counter(feats[d]["tier"] for d in feats)
    inst_counts = Counter()
    for d in feats:
        inst_counts[feats[d]["tier"]] += feats[d]["n_instances"]
    pct = lambda n: f"{n} ({100*n/len(rows):.0f}%)"
    for t in ("EASY", "MEDIUM", "HARD"):
        print(f"  {t:6s} detectors={det_counts[t]:3d}  instances={pct(inst_counts[t])}")

    # ---- rewrite rows ----------------------------------------------------
    drop = set(ALWAYS_DROP) | (set(TASK_FIELDS) if drop_task_fields else set())
    for r in rows:
        f = feats[r["detector"]]
        r["sloc"] = f["sloc"]
        r.pop("loc", None)
        r["difficulty"] = f["tier"]
        r["difficulty_score"] = f["difficulty_score"]
        if r.get("in_benchmark", True):
            r["benchmark_split"] = f["tier"].lower()
        for k in drop:
            r.pop(k, None)

    if write:
        with out.open("w") as fh:
            for r in rows:
                fh.write(json.dumps(r) + "\n")
        print(f"\nwrote {len(rows)} rows -> {out}")
        if drop_task_fields:
            print("dropped task fields (methods_to_generate, quality_tier): "
                  "inference/eval harness must be updated before re-running.")
    else:
        print("\n[report only] re-run with --write to update the dataset.")


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dataset",    type=Path, default=DATASET)
    ap.add_argument("--checks-dir", type=Path, default=CHECKS_DIR)
    ap.add_argument("--out",        type=Path, default=None,
                    help="output path (default: overwrite --dataset)")
    ap.add_argument("--write", action="store_true",
                    help="write output (otherwise report only)")
    ap.add_argument("--drop-task-fields", action="store_true",
                    help="also drop methods_to_generate and quality_tier "
                         "(breaks the harness until it is updated)")
    args = ap.parse_args()
    main(args.dataset, args.checks_dir, args.out or args.dataset,
         args.write, args.drop_task_fields)
