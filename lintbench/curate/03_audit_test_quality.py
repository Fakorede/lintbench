#!/usr/bin/env python3
"""
03_audit_test_quality.py
------------------------
Audits the quality of test files for each paired Android Lint check, and
cross-references which extracted Issue IDs actually have test cases written
for them.

For each test file, measures:
  - test_methods      : total number of test functions (fun test* / public void test*)
  - positive_cases    : tests that assert issues ARE reported (.expect() with issue content)
  - negative_cases    : tests that assert NO issues (.expectClean() or expect("No warnings"))
  - issue_ids_covered : which Issue IDs from the detector appear in the test file
  - lines_of_code     : raw line count of the test file

Also produces a per-Issue view: for each Issue ID in lint_specs.json, whether
any test method in its detector's test file references that issue ID.

Input:  lint_specs.json  (from 02_extract_specs.py)
        lint_pairs.json  (from 01_pair_checks.py)
Output: lint_quality.json

Usage:
    python3 03_audit_test_quality.py [--specs PATH] [--pairs PATH] [--tests-dir PATH] [--out PATH]
"""

import argparse
import json
import re
from collections import defaultdict
from pathlib import Path

REPO_ROOT  = Path(__file__).resolve().parent.parent.parent / "lint_codebase" / "base"
TESTS_DIR  = REPO_ROOT / "lint/libs/lint-tests/src/test/java/com/android/tools/lint/checks"
SPECS_FILE = Path("data/lint_specs.json")
PAIRS_FILE = Path("data/lint_pairs.json")
OUT_FILE   = Path("data/lint_quality.json")


# ---------------------------------------------------------------------------
# Patterns
# ---------------------------------------------------------------------------

# Test method declarations (Kotlin and Java)
RE_TEST_METHOD = re.compile(
    r'^\s*(?:fun\s+(test\w+)|public\s+void\s+(test\w+)\s*\()',
    re.MULTILINE
)

# Positive assertion: .expect(""" ... """) with non-empty content
# We look for .expect( followed by content that isn't just "No warnings" / clean
RE_EXPECT_POSITIVE = re.compile(
    r'\.expect\s*\(\s*(?:"""|\s*"(?!\s*(?:No warnings|""|\s*\n)))',
    re.MULTILINE
)

# Negative assertions
RE_EXPECT_CLEAN = re.compile(
    r'\.expectClean\s*\(\s*\)|\.expect\s*\(\s*""\s*\)|'
    r'\.expect\s*\(\s*"""[\s\n]*"""\s*\)|'
    r'No warnings\.?\s*(?:\\n)?["\']',
    re.MULTILINE
)

# Issue ID references in test output strings e.g. [ShortAlarm]
RE_ISSUE_REF = re.compile(r'\[([A-Z][A-Za-z0-9_]+)\]')


# ---------------------------------------------------------------------------
# Per-file audit
# ---------------------------------------------------------------------------

def audit_test_file(path: Path) -> dict:
    """Parse a single test file and return quality metrics."""
    try:
        source = path.read_text(encoding="utf-8", errors="replace")
    except Exception:
        return {}

    lines = source.splitlines()

    test_methods = [
        m.group(1) or m.group(2)
        for m in RE_TEST_METHOD.finditer(source)
    ]

    positive = len(RE_EXPECT_POSITIVE.findall(source))
    negative = len(RE_EXPECT_CLEAN.findall(source))

    # All issue IDs referenced anywhere in the file (inside [...])
    issue_ids_covered = sorted(set(RE_ISSUE_REF.findall(source)))

    return {
        "test_methods":       len(test_methods),
        "test_method_names":  test_methods,
        "positive_cases":     positive,
        "negative_cases":     negative,
        "issue_ids_covered":  issue_ids_covered,
        "lines_of_code":      len(lines),
    }


# ---------------------------------------------------------------------------
# Quality tier classification
# ---------------------------------------------------------------------------

def quality_tier(metrics: dict) -> str:
    """
    Classify a test file into a quality tier:
      HIGH   : ≥3 test methods, both positive and negative cases
      MEDIUM : ≥2 test methods, or has both positive and negative
      LOW    : 1 test method, or only positive/negative but not both
      EMPTY  : 0 test methods or file missing
    """
    if not metrics:
        return "MISSING"
    n = metrics.get("test_methods", 0)
    pos = metrics.get("positive_cases", 0)
    neg = metrics.get("negative_cases", 0)
    if n == 0:
        return "EMPTY"
    if n >= 3 and pos > 0 and neg > 0:
        return "HIGH"
    if n >= 2 or (pos > 0 and neg > 0):
        return "MEDIUM"
    return "LOW"


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main(specs_path: Path, pairs_path: Path, tests_dir: Path, out_path: Path) -> None:
    for p, label in [(specs_path, "specs"), (pairs_path, "pairs")]:
        if not p.exists():
            raise SystemExit(f"ERROR: {label} file not found: {p}\n"
                             f"       Run the earlier scripts first.")
    if not tests_dir.exists():
        raise SystemExit(f"ERROR: tests dir not found: {tests_dir}")

    specs_data = json.loads(specs_path.read_text())
    pairs_data = json.loads(pairs_path.read_text())

    issues = specs_data["issues"]
    paired = pairs_data["paired"]

    # Build detector → test_file map
    detector_to_test = {
        p["check"]: p["test_file"] for p in paired
    }

    # Audit every test file (once per detector, not per issue)
    detector_audits: dict[str, dict] = {}
    for entry in paired:
        detector = entry["check"]
        test_path = tests_dir / entry["test_file"]
        if not test_path.exists():
            detector_audits[detector] = {}
            continue
        detector_audits[detector] = audit_test_file(test_path)

    # Build per-issue records: does this issue ID appear in its test file?
    issue_records = []
    for issue in issues:
        detector  = issue["detector"]
        issue_id  = issue["issue_id"]
        audit     = detector_audits.get(detector, {})
        covered   = issue_id in audit.get("issue_ids_covered", [])
        tier      = quality_tier(audit)

        issue_records.append({
            # Identity
            "issue_id":          issue_id,
            "detector":          detector,
            "check_file":        issue["check_file"],
            "test_file":         issue.get("test_file", ""),
            "check_lang":        issue["check_lang"],
            # Spec summary
            "brief_description": issue["brief_description"],
            "category":          issue["category"],
            "severity":          issue["severity"],
            # Test quality
            "test_methods":      audit.get("test_methods", 0),
            "positive_cases":    audit.get("positive_cases", 0),
            "negative_cases":    audit.get("negative_cases", 0),
            "issue_covered_in_test": covered,
            "quality_tier":      tier,
            "test_loc":          audit.get("lines_of_code", 0),
        })

    # ---------------------------------------------------------------------------
    # Summary statistics
    # ---------------------------------------------------------------------------
    total_issues     = len(issue_records)
    covered_issues   = [r for r in issue_records if r["issue_covered_in_test"]]
    uncovered_issues = [r for r in issue_records if not r["issue_covered_in_test"]]

    tier_counts = defaultdict(int)
    for r in issue_records:
        tier_counts[r["quality_tier"]] += 1

    # Detectors by tier
    detector_tiers = {
        det: quality_tier(audit)
        for det, audit in detector_audits.items()
    }
    det_tier_counts = defaultdict(int)
    for t in detector_tiers.values():
        det_tier_counts[t] += 1

    # Category breakdown of covered vs uncovered
    cat_covered   = defaultdict(int)
    cat_uncovered = defaultdict(int)
    for r in issue_records:
        if r["issue_covered_in_test"]:
            cat_covered[r["category"]] += 1
        else:
            cat_uncovered[r["category"]] += 1

    print("=" * 62)
    print(f"  Total Issues extracted:              {total_issues:>4}")
    print(f"  Issues WITH test coverage:           {len(covered_issues):>4}  ({len(covered_issues)/total_issues*100:.1f}%)")
    print(f"  Issues WITHOUT test coverage:        {len(uncovered_issues):>4}  ({len(uncovered_issues)/total_issues*100:.1f}%)")
    print("=" * 62)
    print(f"\nIssue quality tier breakdown (by test file of detector):")
    for tier in ("HIGH", "MEDIUM", "LOW", "EMPTY", "MISSING"):
        print(f"  {tier:<10s} {tier_counts[tier]:>4} issues")
    print(f"\nDetector quality tier breakdown:")
    for tier in ("HIGH", "MEDIUM", "LOW", "EMPTY", "MISSING"):
        print(f"  {tier:<10s} {det_tier_counts[tier]:>4} detectors")

    print(f"\nCategory breakdown (covered / total):")
    all_cats = sorted(set(list(cat_covered.keys()) + list(cat_uncovered.keys())))
    for cat in all_cats:
        cov   = cat_covered.get(cat, 0)
        uncov = cat_uncovered.get(cat, 0)
        total = cov + uncov
        print(f"  {cat:<30s} {cov:>3}/{total:<3} covered")

    print(f"\nSample covered issues (first 5):")
    for r in covered_issues[:5]:
        print(f"  [{r['issue_id']}] {r['brief_description'][:55]}  "
              f"| tier={r['quality_tier']} tests={r['test_methods']} pos={r['positive_cases']} neg={r['negative_cases']}")

    print(f"\nSample uncovered issues (first 10):")
    for r in uncovered_issues[:10]:
        print(f"  [{r['issue_id']}] {r['brief_description'][:55]}  "
              f"| tier={r['quality_tier']} tests={r['test_methods']}")

    # ---------------------------------------------------------------------------
    # Save
    # ---------------------------------------------------------------------------
    output = {
        "summary": {
            "total_issues":             total_issues,
            "issues_with_coverage":     len(covered_issues),
            "issues_without_coverage":  len(uncovered_issues),
            "coverage_rate":            round(len(covered_issues) / total_issues * 100, 1),
            "issue_tier_counts":        dict(tier_counts),
            "detector_tier_counts":     dict(det_tier_counts),
        },
        "issues": issue_records,
    }
    out_path.write_text(json.dumps(output, indent=2))
    print(f"\nFull results saved to: {out_path}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--specs",      type=Path, default=SPECS_FILE,
                        help="lint_specs.json from 02_extract_specs.py")
    parser.add_argument("--pairs",      type=Path, default=PAIRS_FILE,
                        help="lint_pairs.json from 01_pair_checks.py")
    parser.add_argument("--tests-dir",  type=Path, default=TESTS_DIR,
                        help="Path to lint-tests source directory")
    parser.add_argument("--out",        type=Path, default=OUT_FILE,
                        help="Output JSON file path")
    args = parser.parse_args()
    main(args.specs, args.pairs, args.tests_dir, args.out)
