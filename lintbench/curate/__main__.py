#!/usr/bin/env python3
"""
curate — LintBench dataset construction pipeline
-------------------------------------------------
Runs the five curation steps in order:

  01  pair_checks         Match detector files to their test files
  02  extract_specs       Extract NL specs from Issue.create() calls
  03  audit_test_quality  Score test quality for each pair
  04  stratify_difficulty Assign EASY / MEDIUM / HARD labels
  05  build_benchmark     Assemble the final lintbench.json

All steps read from and write to ../data/. Run from lintbench/
or from the repo root via:  python lintbench/run_curate.py

USAGE
-----
  python -m curate
  python -m curate --skip-existing
  python -m curate --only 1 2 3
"""

import argparse
import os
import runpy
import sys
import time
from pathlib import Path

_CURATE_DIR = Path(__file__).resolve().parent
_LINT_BENCHMARK_DIR = _CURATE_DIR.parent

_STEPS = [
    ("01_pair_checks",        "data/lint_pairs.json"),
    ("02_extract_specs",      "data/lint_specs.json"),
    ("03_audit_test_quality", "data/lint_quality.json"),
    ("04_stratify_difficulty","data/lint_stratified.json"),
    ("05_build_benchmark",    "data/lintbench.json"),
]


def run_step(number: int, module_file: str, output: str, skip_existing: bool) -> None:
    out_path = Path(output)
    label = f"[{number}/5] {module_file}"

    if skip_existing and out_path.exists():
        print(f"  SKIP  {label}  (output exists: {output})")
        return

    print(f"\n{'='*60}")
    print(f"  STEP  {label}")
    print(f"{'='*60}")

    script = str(_CURATE_DIR / f"{module_file}.py")
    old_argv = sys.argv
    sys.argv = [script]
    t0 = time.time()
    try:
        runpy.run_path(script, run_name="__main__")
    finally:
        sys.argv = old_argv

    print(f"\n  Done in {time.time() - t0:.1f}s  →  {output}")


def main() -> None:
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--skip-existing", action="store_true",
        help="Skip steps whose output file already exists",
    )
    parser.add_argument(
        "--only", type=int, nargs="+", choices=range(1, 6), metavar="N",
        help="Run only the listed step numbers (1–5)",
    )
    args = parser.parse_args()

    only = set(args.only) if args.only else set(range(1, 6))

    # All scripts use relative paths (data/...) — must run from lintbench/
    os.chdir(_LINT_BENCHMARK_DIR)

    for i, (module_file, output) in enumerate(_STEPS, start=1):
        if i not in only:
            continue
        try:
            run_step(i, module_file, output, args.skip_existing)
        except SystemExit as e:
            if e.code not in (None, 0):
                sys.exit(f"Pipeline stopped at step {i} (exit code {e.code})")
        except Exception as e:
            sys.exit(f"Pipeline stopped at step {i}: {e}")

    print("\nPipeline complete. Final benchmark: data/lintbench.json")


if __name__ == "__main__":
    main()
