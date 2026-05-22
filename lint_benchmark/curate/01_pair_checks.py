#!/usr/bin/env python3
"""
01_pair_checks.py
-----------------
Pairs Android Lint detector implementations with their test files.

Naming convention assumed:
    FooDetector.kt  <-->  FooDetectorTest.kt  (or .java)

Outputs:
    lint_pairs.json   -- full pairing results
    (stdout)          -- summary statistics

Usage:
    python3 01_pair_checks.py [--checks-dir PATH] [--tests-dir PATH] [--out PATH]

Defaults point at a sparse checkout of platform/tools/base cloned alongside
this script's parent directory. Override with flags as needed.
"""

import argparse
import json
from pathlib import Path

# ---------------------------------------------------------------------------
# Defaults (relative to repo root; override with --checks-dir / --tests-dir)
# ---------------------------------------------------------------------------
REPO_ROOT   = Path(__file__).resolve().parent.parent.parent / "lint_codebase" / "base"
CHECKS_DIR  = REPO_ROOT / "lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks"
TESTS_DIR   = REPO_ROOT / "lint/libs/lint-tests/src/test/java/com/android/tools/lint/checks"


def get_stems(directory: Path, suffixes=(".kt", ".java")) -> dict[str, Path]:
    """Return {stem: path} for all files with the given suffixes under directory."""
    stems: dict[str, Path] = {}
    for f in directory.rglob("*"):
        if f.suffix in suffixes:
            stems[f.stem] = f
    return stems


def classify_unpaired(names: list[str]) -> tuple[list[str], list[str]]:
    """Split unpaired check names into likely-real-detectors vs helpers/utilities."""
    keywords = ("Detector", "Check", "Lint", "Scanner", "Inspector")
    real, helpers = [], []
    for name in names:
        if any(kw in name for kw in keywords):
            real.append(name)
        else:
            helpers.append(name)
    return real, helpers


def main(checks_dir: Path, tests_dir: Path, out_path: Path) -> None:
    if not checks_dir.exists():
        raise SystemExit(f"ERROR: checks dir not found: {checks_dir}\n"
                         f"       Have you cloned the repo? See README.")
    if not tests_dir.exists():
        raise SystemExit(f"ERROR: tests dir not found: {tests_dir}")

    checks = get_stems(checks_dir)
    tests  = get_stems(tests_dir)

    paired:      list[dict] = []
    checks_only: list[str]  = []
    tests_only:  list[str]  = []

    for name, path in sorted(checks.items()):
        test_name = name + "Test"
        if test_name in tests:
            paired.append({
                "check":      name,
                "check_file": path.name,
                "check_lang": path.suffix.lstrip("."),
                "test_file":  tests[test_name].name,
                "test_lang":  tests[test_name].suffix.lstrip("."),
            })
        else:
            checks_only.append(name)

    for name in sorted(tests.keys()):
        base = name.removesuffix("Test")
        if base not in checks:
            tests_only.append(name)

    # Language breakdown
    kt_pairs   = [p for p in paired if p["check_lang"] == "kt"]
    java_pairs = [p for p in paired if p["check_lang"] == "java"]

    # Unpaired real detectors vs helpers
    real_missing, helper_missing = classify_unpaired(checks_only)

    # Print summary
    print("=" * 62)
    print(f"  Paired checks (implementation + test):  {len(paired):>4}")
    print(f"  Checks WITHOUT tests:                   {len(checks_only):>4}")
    print(f"    of which likely real detectors:       {len(real_missing):>4}")
    print(f"    of which utilities/helpers:            {len(helper_missing):>4}")
    print(f"  Tests WITHOUT matching check file:      {len(tests_only):>4}")
    print(f"  Total check files:                      {len(checks):>4}")
    print(f"  Total test files:                       {len(tests):>4}")
    print(f"  Pairing rate:                           {len(paired)/len(checks)*100:>6.1f}%")
    print("=" * 62)
    print(f"\nLanguage breakdown of paired checks:")
    print(f"  Kotlin : {len(kt_pairs)}")
    print(f"  Java   : {len(java_pairs)}")

    print(f"\nSample pairs (first 15):")
    for p in paired[:15]:
        print(f"  {p['check_file']:48s} <-> {p['test_file']}")

    if real_missing:
        print(f"\nReal detectors missing tests ({len(real_missing)}):")
        for name in sorted(real_missing):
            print(f"  {name}")

    # Persist
    result = {
        "summary": {
            "paired":       len(paired),
            "checks_only":  len(checks_only),
            "tests_only":   len(tests_only),
            "total_checks": len(checks),
            "total_tests":  len(tests),
            "pairing_rate": round(len(paired) / len(checks) * 100, 1),
            "kt_pairs":     len(kt_pairs),
            "java_pairs":   len(java_pairs),
        },
        "paired":      paired,
        "checks_only": checks_only,
        "tests_only":  tests_only,
    }
    out_path.write_text(json.dumps(result, indent=2))
    print(f"\nFull results saved to: {out_path}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--checks-dir", type=Path, default=CHECKS_DIR,
                        help="Path to lint-checks source directory")
    parser.add_argument("--tests-dir",  type=Path, default=TESTS_DIR,
                        help="Path to lint-tests source directory")
    parser.add_argument("--out",        type=Path, default=Path("data/lint_pairs.json"),
                        help="Output JSON file path")
    args = parser.parse_args()
    main(args.checks_dir, args.tests_dir, args.out)
