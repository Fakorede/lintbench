#!/usr/bin/env python3
"""
05_build_benchmark.py
---------------------
Assembles the final LintBench benchmark JSON by joining all prior pipeline
outputs into a single, clean, evaluation-ready artifact.

Each benchmark instance represents one Android Lint Issue and contains:

  IDENTITY
    instance_id          Unique stable ID: "<detector>:<issue_id>"
    issue_id             Lint issue ID (e.g. "ShortAlarm")
    detector             Detector class name (e.g. "AlarmDetector")
    check_file           Source filename of the detector
    check_lang           "kt" or "java"
    test_file            Source filename of the test
    check_url            AOSP Gitiles URL to the detector source
    test_url             AOSP Gitiles URL to the test source

  NATURAL LANGUAGE SPECIFICATION  (the LLM prompt input)
    brief_description    One-line summary from Issue.create()
    explanation          Full explanation from Issue.create()
    nl_spec              Combined prompt-ready spec (brief + explanation)
    more_info_urls       Reference URLs from the issue declaration

  METADATA
    category             Lint category (CORRECTNESS, SECURITY, …)
    severity             ERROR / WARNING / FATAL / INFORMATIONAL
    priority             Integer 1–10

  EVALUATION
    test_file            Test file to run for pass/fail signal
    issue_covered_in_test  Whether the issue ID appears in test assertions

  QUALITY & DIFFICULTY
    quality_tier         HIGH / MEDIUM / LOW / EMPTY
    test_methods         Number of test methods in test file
    positive_cases       Tests asserting issues ARE reported
    negative_cases       Tests asserting NO issues (expectClean)
    difficulty           EASY / MEDIUM / HARD
    weighted_score       Raw difficulty score (0–18+)
    api_surfaces         Human-readable list of Lint API surfaces used
    scanner_interfaces   Lint scanner interfaces implemented
    loc                  Lines of code in detector

  BENCHMARK INCLUSION
    in_benchmark         True if benchmark-ready (covered + HIGH/MEDIUM quality)
    benchmark_split      "easy" / "medium" / "hard" / null (if excluded)

Inputs:
    lint_stratified.json  (from 04_stratify_difficulty.py)
    lint_specs.json       (from 02_extract_specs.py)

Output:
    lintbench.json        The final benchmark artifact

Usage:
    python3 05_build_benchmark.py \\
        [--stratified PATH] [--specs PATH] [--out PATH]
"""

import argparse
import json
import re
from collections import defaultdict
from pathlib import Path


# Known Lint API callback names — used as a fallback for Java detectors
# that omit the @Override annotation (which is optional in Java).
_LINT_API_METHODS = {
    "getApplicableMethodNames", "getApplicableConstructorTypes",
    "getApplicableReferenceNames", "getApplicableElements",
    "getApplicableAttributes", "getApplicableUastTypes",
    "getApplicableCallNames", "applicableAnnotations", "applicableSuperClasses",
    "visitMethodCall", "visitConstructor", "visitReference",
    "visitElement", "visitAttribute", "visitAnnotationUsage",
    "visitClass", "createUastHandler",
    "afterCheckFile", "beforeCheckFile", "afterCheckEachProject",
    "afterCheckRootProject", "checkPartialResults", "mergeState",
    "filterIncident", "appliesTo", "run",
}

# Java method declaration pattern (without requiring @Override)
_JAVA_METHOD_RE = re.compile(
    r"(?:public|protected)\s+"
    r"(?:(?:static|final|synchronized)\s+)*"
    r"(?:@\w+(?:\([^)]*\))?\s+)*"
    r"(?:[\w<>\[\],\s?]+?\s+)"
    r"(\w+)\s*\(",
    re.MULTILINE | re.DOTALL,
)


def extract_methods_to_generate(source: str, lang: str) -> list[str]:
    """
    Extract Lint API override methods from a detector source file.
    Private helpers are excluded (oracle implementation detail).

    Kotlin: `override` keyword is mandatory — use it as the sole signal.
    Java:   `@Override` is optional in the Lint codebase, so combine:
            (a) methods annotated with @Override, plus
            (b) public/protected methods whose name is in _LINT_API_METHODS.
    """
    seen: set[str] = set()
    overrides: list[str] = []

    if lang == "kt":
        pattern = re.compile(r"override\s+fun\s+(\w+)\s*\(", re.MULTILINE)
        for m in pattern.finditer(source):
            name = m.group(1)
            if name not in seen:
                seen.add(name)
                overrides.append(name)

    else:  # java
        # (a) @Override-annotated methods
        annotated_re = re.compile(
            r"@Override\s+"
            r"(?:@\w+(?:\([^)]*\))?\s+)*"
            r"(?:public|protected|private)?"
            r"(?:\s+(?:static|final|synchronized))*"
            r"\s+(?:[\w<>\[\],\s]+?\s+)"
            r"(\w+)\s*\(",
            re.MULTILINE | re.DOTALL,
        )
        for m in annotated_re.finditer(source):
            name = m.group(1)
            if name not in seen:
                seen.add(name)
                overrides.append(name)

        # (b) known Lint API names that lack @Override
        for m in _JAVA_METHOD_RE.finditer(source):
            name = m.group(1)
            if name not in seen and name in _LINT_API_METHODS:
                seen.add(name)
                overrides.append(name)

    return overrides


def extract_tests_for_issue(
    test_source: str,
    issue_id: str,
) -> list[str]:
    """
    Return the test method names in the test file that are relevant to
    the given issue_id.

    Strategy (in priority order):
      1. Methods whose body contains [IssueId] in an .expect() assertion
         — these explicitly test this issue.
      2. If no explicit matches, fall back to ALL test methods in the file
         (used for single-issue detectors where the issue ID may not appear
         literally in every test method's body).
    """
    issue_ref = re.compile(r"\[" + re.escape(issue_id) + r"\]")
    method_pattern = re.compile(
        r"(?:fun|public void)\s+(test\w+)\s*\(",
        re.MULTILINE,
    )

    matches = list(method_pattern.finditer(test_source))
    explicit: list[str] = []

    for idx, m in enumerate(matches):
        name = m.group(1)
        start = m.start()
        end = matches[idx + 1].start() if idx + 1 < len(matches) else len(test_source)
        body = test_source[start:end]
        if issue_ref.search(body):
            explicit.append(name)

    if explicit:
        return explicit

    # Fallback: return all test methods (single-issue detector)
    return [m.group(1) for m in matches]

STRATIFIED_FILE = Path("data/lint_stratified.json")
SPECS_FILE      = Path("data/lint_specs.json")
OUT_FILE        = Path("data/lintbench.json")

_REPO_ROOT  = Path(__file__).resolve().parent.parent.parent / "lint_codebase" / "base"
CHECKS_DIR  = _REPO_ROOT / "lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks"
TESTS_DIR   = _REPO_ROOT / "lint/libs/lint-tests/src/test/java/com/android/tools/lint/checks"

_AOSP_BASE = (
    "https://android.googlesource.com/platform/tools/base"
    "/+/refs/heads/mirror-goog-studio-main"
)
_CHECKS_URL_PREFIX = (
    f"{_AOSP_BASE}/lint/libs/lint-checks/src/main/java"
    "/com/android/tools/lint/checks"
)
_TESTS_URL_PREFIX = (
    f"{_AOSP_BASE}/lint/libs/lint-tests/src/test/java"
    "/com/android/tools/lint/checks"
)


def source_urls(check_file: str, test_file: str) -> tuple[str, str]:
    """Return (check_url, test_url) AOSP Gitiles URLs for the given filenames."""
    return (
        f"{_CHECKS_URL_PREFIX}/{check_file}",
        f"{_TESTS_URL_PREFIX}/{test_file}",
    )


def main(stratified_path: Path, specs_path: Path, out_path: Path,
         checks_dir: Path = CHECKS_DIR, tests_dir: Path = TESTS_DIR) -> None:
    for p, label in [(stratified_path, "stratified"), (specs_path, "specs")]:
        if not p.exists():
            raise SystemExit(f"ERROR: {label} file not found: {p}\n"
                             f"       Run the earlier pipeline scripts first.")

    stratified_data = json.loads(stratified_path.read_text())
    specs_data      = json.loads(specs_path.read_text())

    # Build lookup: (issue_id, detector) → spec fields
    spec_lookup: dict[tuple, dict] = {}
    for s in specs_data["issues"]:
        key = (s["issue_id"], s["detector"])
        spec_lookup[key] = {
            "nl_spec":        s.get("nl_spec", ""),
            "explanation":    s.get("explanation", ""),
            "more_info_urls": s.get("more_info_urls", []),
            "priority":       s.get("priority"),
        }

    # Caches: one read per detector file, not per issue
    detector_methods_cache: dict[str, list[str]] = {}
    test_source_cache:      dict[str, str]        = {}

    # Assemble benchmark instances
    instances = []
    for issue in stratified_data["issues"]:
        issue_id = issue["issue_id"]
        detector = issue["detector"]
        key      = (issue_id, detector)
        spec     = spec_lookup.get(key, {})

        # Benchmark inclusion criteria:
        #   - Issue ID explicitly covered in test assertions
        #   - Test quality is HIGH or MEDIUM
        in_benchmark = (
            issue["issue_covered_in_test"]
            and issue["quality_tier"] in ("HIGH", "MEDIUM")
        )
        benchmark_split = issue["difficulty"].lower() if in_benchmark else None

        instance_id = f"{detector}:{issue_id}"

        # --- methods to generate ---
        check_file = issue["check_file"]
        lang       = issue["check_lang"]
        if check_file not in detector_methods_cache:
            p = checks_dir / check_file
            src = p.read_text(encoding="utf-8", errors="replace") if p.exists() else ""
            detector_methods_cache[check_file] = extract_methods_to_generate(src, lang)
        methods_to_generate = detector_methods_cache[check_file]

        # --- tests to run ---
        test_file = issue["test_file"]
        if test_file not in test_source_cache:
            p = tests_dir / test_file
            test_source_cache[test_file] = p.read_text(encoding="utf-8", errors="replace") if p.exists() else ""
        tests_to_run = extract_tests_for_issue(test_source_cache[test_file], issue_id)

        check_url, test_url = source_urls(issue["check_file"], issue["test_file"])

        instances.append({
            # Identity
            "instance_id":    instance_id,
            "issue_id":       issue_id,
            "detector":       detector,
            "check_file":     issue["check_file"],
            "check_lang":     issue["check_lang"],
            "test_file":      issue["test_file"],
            "check_url":      check_url,
            "test_url":       test_url,
            # Methods the model must implement
            "methods_to_generate": methods_to_generate,
            # Test methods to run for pass/fail evaluation
            "tests_to_run":        tests_to_run,
            # NL specification
            "brief_description": issue["brief_description"],
            "explanation":    spec.get("explanation", ""),
            "nl_spec":        spec.get("nl_spec", issue["brief_description"]),
            "more_info_urls": spec.get("more_info_urls", []),
            # Metadata
            "category":       issue["category"],
            "severity":       issue["severity"],
            "priority":       spec.get("priority"),
            # Test quality
            "quality_tier":        issue["quality_tier"],
            "test_methods":        issue["test_methods"],
            "positive_cases":      issue["positive_cases"],
            "negative_cases":      issue["negative_cases"],
            "issue_covered_in_test": issue["issue_covered_in_test"],
            # Difficulty
            "difficulty":          issue["difficulty"],
            "weighted_score":      issue["weighted_score"],
            "api_surfaces":        issue["api_surfaces"],
            "scanner_interfaces":  issue["scanner_interfaces"],
            "loc":                 issue["loc"],
            # Benchmark flags
            "in_benchmark":        in_benchmark,
            "benchmark_split":     benchmark_split,
        })

    # Sort: benchmark-ready first, then by difficulty score desc, then alpha
    instances.sort(key=lambda x: (
        0 if x["in_benchmark"] else 1,
        -x["weighted_score"],
        x["instance_id"],
    ))

    # ---------------------------------------------------------------------------
    # Statistics
    # ---------------------------------------------------------------------------
    total          = len(instances)
    in_bench       = [i for i in instances if i["in_benchmark"]]
    excluded       = [i for i in instances if not i["in_benchmark"]]

    split_counts: dict[str, int] = defaultdict(int)
    for i in in_bench:
        split_counts[i["benchmark_split"]] += 1

    cat_counts: dict[str, int] = defaultdict(int)
    for i in in_bench:
        cat_counts[i["category"]] += 1

    lang_counts: dict[str, int] = defaultdict(int)
    for i in in_bench:
        lang_counts[i["check_lang"]] += 1

    sev_counts: dict[str, int] = defaultdict(int)
    for i in in_bench:
        sev_counts[i["severity"]] += 1

    print("=" * 62)
    print(f"  Total instances assembled:   {total}")
    print(f"  Benchmark-ready:             {len(in_bench)}  ({len(in_bench)/total*100:.1f}%)")
    print(f"  Excluded (quality/coverage): {len(excluded)}")
    print("=" * 62)

    print(f"\nBenchmark split:")
    for split in ("easy", "medium", "hard"):
        print(f"  {split.upper():<8} {split_counts[split]:>4}")

    print(f"\nLanguage breakdown (benchmark-ready):")
    for lang, count in sorted(lang_counts.items()):
        print(f"  {lang:<6} {count:>4}")

    print(f"\nSeverity breakdown (benchmark-ready):")
    for sev, count in sorted(sev_counts.items(), key=lambda x: -x[1]):
        print(f"  {sev:<15} {count:>4}")

    print(f"\nCategory breakdown (benchmark-ready):")
    for cat, count in sorted(cat_counts.items(), key=lambda x: -x[1]):
        print(f"  {cat:<30s} {count:>4}")

    print(f"\nSample benchmark instances:")
    for split in ("easy", "medium", "hard"):
        examples = [i for i in in_bench if i["benchmark_split"] == split][:2]
        print(f"\n  [{split.upper()}]")
        for ex in examples:
            print(f"    {ex['instance_id']}")
            print(f"    Spec: {ex['brief_description']}")
            print(f"    Surfaces: {ex['api_surfaces']}")
            print(f"    Tests: {ex['test_methods']} methods "
                  f"(+{ex['positive_cases']} pos / -{ex['negative_cases']} neg)")

    # ---------------------------------------------------------------------------
    # Save
    # ---------------------------------------------------------------------------
    benchmark_instances = [i for i in instances if i["in_benchmark"]]

    output = {
        "name":        "LintBench",
        "description": (
            "A benchmark of Android Lint checks with natural language specifications "
            "and executable test suites, for evaluating LLMs on static analysis "
            "tool generation. Each instance pairs an NL spec (briefDescription + "
            "explanation from Issue.create()) with the corresponding Lint Detector "
            "implementation and its test file for automated pass/fail evaluation."
        ),
        "source":      "https://android.googlesource.com/platform/tools/base",
        "license":     "Apache-2.0",
        "version":     "1.0.0",
        "summary": {
            "total_instances":   len(benchmark_instances),
            "split_counts":      dict(split_counts),
            "category_counts":   dict(sorted(cat_counts.items(), key=lambda x: -x[1])),
            "language_counts":   dict(lang_counts),
            "severity_counts":   dict(sorted(sev_counts.items(), key=lambda x: -x[1])),
            "total_raw_issues":  total,
            "excluded_count":    len(excluded),
        },
        "splits": {
            "easy":   [i for i in benchmark_instances if i["benchmark_split"] == "easy"],
            "medium": [i for i in benchmark_instances if i["benchmark_split"] == "medium"],
            "hard":   [i for i in benchmark_instances if i["benchmark_split"] == "hard"],
        },
        # Full list including excluded (for analysis and ablations)
        "all_instances": instances,
    }

    out_path.write_text(json.dumps(output, indent=2))

    # Also write a compact version with benchmark-only instances (no all_instances)
    compact_path = out_path.with_name(out_path.stem + "_compact.json")
    compact = {k: v for k, v in output.items() if k != "all_instances"}
    compact_path.write_text(json.dumps(compact, indent=2))

    # JSONL — one instance per line, flat, benchmark-ready instances only
    # This is the preferred input format for the generate and eval modules.
    jsonl_path = out_path.with_suffix(".jsonl")
    with jsonl_path.open("w", encoding="utf-8") as f:
        for inst in benchmark_instances:
            f.write(json.dumps(inst) + "\n")

    size_full    = out_path.stat().st_size / 1024
    size_compact = compact_path.stat().st_size / 1024
    size_jsonl   = jsonl_path.stat().st_size / 1024
    print(f"\nSaved: {out_path}         ({size_full:.0f} KB)")
    print(f"Saved: {compact_path}  ({size_compact:.0f} KB)")
    print(f"Saved: {jsonl_path}         ({size_jsonl:.0f} KB)")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--stratified", type=Path, default=STRATIFIED_FILE,
                        help="lint_stratified.json from 04_stratify_difficulty.py")
    parser.add_argument("--specs",      type=Path, default=SPECS_FILE,
                        help="lint_specs.json from 02_extract_specs.py")
    parser.add_argument("--out",        type=Path, default=OUT_FILE,
                        help="Output benchmark JSON file path")
    parser.add_argument("--checks-dir", type=Path, default=CHECKS_DIR,
                        help="Path to lint-checks source directory")
    parser.add_argument("--tests-dir",  type=Path, default=TESTS_DIR,
                        help="Path to lint-tests source directory")
    args = parser.parse_args()
    main(args.stratified, args.specs, args.out, args.checks_dir, args.tests_dir)
