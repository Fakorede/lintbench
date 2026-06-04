#!/usr/bin/env python3
"""
04_stratify_difficulty.py
--------------------------
Assigns a difficulty tier (EASY / MEDIUM / HARD) to each Issue in the benchmark
by analysing the API surface complexity of its detector's source file.

Difficulty is based on a weighted signal model across five dimensions:

  Dimension               Signals detected                         Weight
  ─────────────────────── ──────────────────────────────────────── ──────
  Scope                   Single-file vs multi-file/manifest/XML      1
  AST traversal style     Simple method-call matching vs full UAST     1
  Data / value analysis   ConstantEvaluator, DataFlowAnalyzer, etc.   2
  Type / annotation       extendsClass, findAnnotation, etc.           2
  Interprocedural         PartialResult, mergeState, checkPartial…     3

Tier thresholds (weighted score):
  EASY   : 0 – 2   (simple single-scope, pattern-match checks)
  MEDIUM : 3 – 5   (type-aware, multi-scope, or light data-flow)
  HARD   : 6+      (interprocedural, deep data-flow, or CFG-level)

Additionally computes:
  - loc                  : lines of code in detector
  - num_issues           : number of Issue objects declared
  - scanner_interfaces   : which Lint scanner interfaces are implemented
  - api_surfaces         : human-readable list of detected surfaces

Input:  lint_quality.json  (from 03_audit_test_quality.py)
Output: lint_stratified.json

Usage:
    python3 04_stratify_difficulty.py \\
        [--quality PATH] [--checks-dir PATH] [--out PATH]
"""

import argparse
import json
import re
from pathlib import Path
from collections import defaultdict

REPO_ROOT   = Path(__file__).resolve().parent.parent.parent / "base"
CHECKS_DIR  = REPO_ROOT / "lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks"
QUALITY_FILE = Path("data/lint_quality.json")
OUT_FILE     = Path("data/lint_stratified.json")


# ---------------------------------------------------------------------------
# Signal definitions
# Each entry: (substring_to_search, label, weight)
# ---------------------------------------------------------------------------

SIGNALS = [
    # ── Scope signals (weight 1) ──────────────────────────────────────────
    ("EnumSet.of(Scope",            "multi_scope",          1),
    ("MANIFEST_SCOPE",              "manifest_scope",       1),
    ("ALL_RESOURCE_FILES",          "all_resources_scope",  1),
    ("RESOURCE_FILE_SCOPE",         "resource_scope",       1),
    ("GRADLE_SCOPE",                "gradle_scope",         1),

    # ── AST traversal style (weight 1) ───────────────────────────────────
    ("getApplicableMethodNames",    "method_call",          1),
    ("getApplicableConstructorTypes", "constructor_call",   1),
    ("visitMethodCall",             "method_call",          1),
    ("getApplicableReferenceNames", "field_ref",            1),
    ("visitReference",              "field_ref",            1),
    ("getApplicableElements",       "xml_element",          1),
    ("getApplicableAttributes",     "xml_attribute",        1),
    ("visitElement",                "xml_element",          1),
    ("createUastHandler",           "uast_handler",         1),
    ("getApplicableUastTypes",      "uast_types",           1),
    ("AbstractUastVisitor",         "uast_visitor",         1),
    ("visitClass",                  "class_visitor",        1),
    ("afterCheckFile",              "file_lifecycle",       1),
    ("beforeCheckFile",             "file_lifecycle",       1),
    ("applicableAnnotations",       "annotation_scanner",   1),
    ("visitAnnotationUsage",        "annotation_scanner",   1),

    # ── Data / value analysis (weight 2) ─────────────────────────────────
    ("ConstantEvaluator",           "constant_eval",        2),
    ("evaluateString",              "constant_eval",        2),
    ("evaluateInt",                 "constant_eval",        2),
    ("evaluate(",                   "constant_eval",        2),
    ("DataFlowAnalyzer",            "dataflow",             2),

    # ── Type / annotation resolution (weight 2) ───────────────────────────
    ("evaluator.extendsClass",      "type_resolution",      2),
    ("evaluator.implementsInterface", "type_resolution",    2),
    ("evaluator.getTypeClass",      "type_resolution",      2),
    ("evaluator.getQualifiedName",  "type_resolution",      2),
    ("findAnnotation",              "annotation_resolve",   2),
    ("getAnnotation",               "annotation_resolve",   2),
    ("evaluator.getAnnotation",     "annotation_resolve",   2),

    # ── Control flow (weight 2) ───────────────────────────────────────────
    ("ControlFlowGraph",            "cfg",                  2),
    ("predecessor",                 "cfg",                  2),
    ("successor",                   "cfg",                  2),

    # ── Interprocedural / partial results (weight 3) ─────────────────────
    ("PartialResult",               "interprocedural",      3),
    ("mergeState",                  "interprocedural",      3),
    ("checkPartialResults",         "interprocedural",      3),
    ("LintMap",                     "interprocedural",      3),
]

# Deduplicated label → max weight (we score each label once)
LABEL_WEIGHTS: dict[str, int] = {}
for _, label, weight in SIGNALS:
    LABEL_WEIGHTS[label] = max(LABEL_WEIGHTS.get(label, 0), weight)

SCANNER_INTERFACES = [
    "SourceCodeScanner", "XmlScanner", "ClassScanner",
    "BinaryResourceScanner", "ResourceFolderScanner",
    "GradleScanner", "OtherFileScanner",
]

# Abstract base classes that imply scanner interfaces via inheritance.
# Many detectors extend abstract base classes instead of directly implementing
# a scanner interface — the direct string search misses these.
#
# Chains resolved here (transitive):
#   LayoutDetector           → ResourceXmlDetector → XmlScanner
#   ResourceXmlDetector      → XmlScanner
#   GradleDetector           → GradleScanner
#   DependencyDetector       → GradleScanner  (also TomlScanner, mapped to GradleScanner)
#   JoinEffectDetector       → SourceCodeScanner
#   ThreadConstraintDetector → JoinEffectDetector → SourceCodeScanner
ABSTRACT_SCANNER_BASES: dict[str, list[str]] = {
    "LayoutDetector":           ["XmlScanner"],
    "ResourceXmlDetector":      ["XmlScanner"],
    "GradleDetector":           ["GradleScanner"],
    "DependencyDetector":       ["GradleScanner"],
    "JoinEffectDetector":       ["SourceCodeScanner"],
    "ThreadConstraintDetector": ["SourceCodeScanner"],
}


def _infer_scanners_from_inheritance(source: str) -> list[str]:
    """
    Return scanner interfaces implied by extending a known abstract base class
    or using a legacy interface alias.
    Handles both Java  (extends Foo)  and Kotlin  (: Foo() / : Foo,) syntax.

    Additional cases handled:
      Detector.UastScanner  — legacy alias for SourceCodeScanner
      Detector.run()        — plain Detector override = OtherFileScanner pattern
    """
    inferred: list[str] = []

    # Abstract base class → interface mapping
    for base_class, interfaces in ABSTRACT_SCANNER_BASES.items():
        # Java:   class Foo extends LayoutDetector
        # Kotlin: class Foo : LayoutDetector() / class Foo : LayoutDetector,
        pattern = rf'(?:extends\s+{base_class}\b|:\s*{base_class}\s*[(),])'
        if re.search(pattern, source):
            inferred.extend(interfaces)

    # Legacy alias: Detector.UastScanner == SourceCodeScanner
    if "Detector.UastScanner" in source and "SourceCodeScanner" not in inferred:
        inferred.append("SourceCodeScanner")

    # Plain Detector subclass overriding run(Context) = OtherFileScanner pattern
    # Java:   extends Detector  +  void run(@NonNull Context / Context ctx)
    # Kotlin: : Detector()      +  override fun run(context: Context)
    if not inferred:
        java_other = (
            re.search(r'extends\s+Detector\b', source)
            and re.search(r'void\s+run\s*\(\s*@?\w*\s*Context', source)
        )
        kotlin_other = (
            re.search(r':\s*Detector\s*\(\)', source)
            and re.search(r'override\s+fun\s+run\s*\(', source)
        )
        if java_other or kotlin_other:
            inferred.append("OtherFileScanner")

    return inferred


# ---------------------------------------------------------------------------
# Analysis
# ---------------------------------------------------------------------------

def analyse_detector(source: str) -> dict:
    """
    Analyse a detector's source and return complexity metrics.
    """
    triggered_labels: set[str] = set()
    for pattern, label, _ in SIGNALS:
        if pattern in source:
            triggered_labels.add(label)

    weighted_score = sum(LABEL_WEIGHTS[lbl] for lbl in triggered_labels)

    # Direct interface declarations (e.g. implements XmlScanner)
    scanners = [s for s in SCANNER_INTERFACES if s in source]

    # Supplement with interfaces inherited via abstract base classes
    for iface in _infer_scanners_from_inheritance(source):
        if iface not in scanners:
            scanners.append(iface)
    loc = len(source.splitlines())
    num_issues = source.count("Issue.create")

    # Human-readable API surface summary
    surfaces: list[str] = []
    surface_map = {
        "multi_scope":        "multi-scope",
        "manifest_scope":     "manifest",
        "gradle_scope":       "Gradle",
        "resource_scope":     "resources",
        "all_resources_scope":"all-resources",
        "method_call":        "method-call matching",
        "constructor_call":   "constructor matching",
        "field_ref":          "field-reference matching",
        "xml_element":        "XML element scanning",
        "xml_attribute":      "XML attribute scanning",
        "uast_handler":       "UAST handler",
        "uast_visitor":       "UAST visitor",
        "uast_types":         "UAST type filtering",
        "class_visitor":      "class visitor",
        "file_lifecycle":     "file lifecycle hooks",
        "annotation_scanner": "annotation scanning",
        "constant_eval":      "constant/value evaluation",
        "dataflow":           "data-flow analysis",
        "type_resolution":    "type resolution",
        "annotation_resolve": "annotation resolution",
        "cfg":                "control-flow graph",
        "interprocedural":    "interprocedural analysis",
    }
    for label in sorted(triggered_labels):
        if label in surface_map:
            surfaces.append(surface_map[label])

    return {
        "weighted_score":     weighted_score,
        "triggered_labels":   sorted(triggered_labels),
        "api_surfaces":       surfaces,
        "scanner_interfaces": scanners,
        "loc":                loc,
        "num_issues_declared": num_issues,
    }


def difficulty_tier(weighted_score: int) -> str:
    if weighted_score <= 2:
        return "EASY"
    elif weighted_score <= 5:
        return "MEDIUM"
    else:
        return "HARD"


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main(quality_path: Path, checks_dir: Path, out_path: Path) -> None:
    if not quality_path.exists():
        raise SystemExit(f"ERROR: quality file not found: {quality_path}\n"
                         f"       Run 03_audit_test_quality.py first.")
    if not checks_dir.exists():
        raise SystemExit(f"ERROR: checks dir not found: {checks_dir}")

    quality_data = json.loads(quality_path.read_text())
    issues       = quality_data["issues"]

    # Cache detector analyses (one per detector file, not per issue)
    detector_cache: dict[str, dict] = {}

    records = []
    for issue in issues:
        detector   = issue["detector"]
        check_file = issue["check_file"]

        if detector not in detector_cache:
            path = checks_dir / check_file
            if path.exists():
                source = path.read_text(encoding="utf-8", errors="replace")
                detector_cache[detector] = analyse_detector(source)
            else:
                detector_cache[detector] = {
                    "weighted_score": 0, "triggered_labels": [],
                    "api_surfaces": [], "scanner_interfaces": [],
                    "loc": 0, "num_issues_declared": 0,
                }

        analysis = detector_cache[detector]
        tier     = difficulty_tier(analysis["weighted_score"])

        records.append({
            # Identity
            "issue_id":          issue["issue_id"],
            "detector":          detector,
            "check_file":        check_file,
            "check_lang":        issue["check_lang"],
            "test_file":         issue["test_file"],
            # Spec
            "brief_description": issue["brief_description"],
            "category":          issue["category"],
            "severity":          issue["severity"],
            # Test quality (from previous script)
            "test_methods":      issue["test_methods"],
            "positive_cases":    issue["positive_cases"],
            "negative_cases":    issue["negative_cases"],
            "issue_covered_in_test": issue["issue_covered_in_test"],
            "quality_tier":      issue["quality_tier"],
            # Difficulty
            "difficulty":        tier,
            "weighted_score":    analysis["weighted_score"],
            "api_surfaces":      analysis["api_surfaces"],
            "scanner_interfaces": analysis["scanner_interfaces"],
            "loc":               analysis["loc"],
            "num_issues_declared": analysis["num_issues_declared"],
        })

    # ---------------------------------------------------------------------------
    # Summary
    # ---------------------------------------------------------------------------
    diff_counts = defaultdict(int)
    for r in records:
        diff_counts[r["difficulty"]] += 1

    # Cross-tab: difficulty × quality tier
    cross: dict[str, dict[str, int]] = defaultdict(lambda: defaultdict(int))
    for r in records:
        cross[r["difficulty"]][r["quality_tier"]] += 1

    # Category × difficulty
    cat_diff: dict[str, dict[str, int]] = defaultdict(lambda: defaultdict(int))
    for r in records:
        cat_diff[r["category"]][r["difficulty"]] += 1

    print("=" * 62)
    print(f"  Total issues stratified:   {len(records)}")
    print(f"  EASY   (score 0–2):        {diff_counts['EASY']:>4}")
    print(f"  MEDIUM (score 3–5):        {diff_counts['MEDIUM']:>4}")
    print(f"  HARD   (score 6+):         {diff_counts['HARD']:>4}")
    print("=" * 62)

    print("\nDifficulty × test quality cross-tab:")
    print(f"  {'':8s}  {'HIGH':>6} {'MEDIUM':>7} {'LOW':>5} {'EMPTY':>6}")
    for diff in ("EASY", "MEDIUM", "HARD"):
        row = cross[diff]
        print(f"  {diff:<8s}  {row['HIGH']:>6} {row['MEDIUM']:>7} {row['LOW']:>5} {row['EMPTY']:>6}")

    print("\nCategory breakdown (EASY / MEDIUM / HARD):")
    for cat in sorted(cat_diff.keys()):
        d = cat_diff[cat]
        total = sum(d.values())
        print(f"  {cat:<30s} E={d['EASY']:>3} M={d['MEDIUM']:>3} H={d['HARD']:>3}  total={total}")

    print("\nSample EASY issues:")
    for r in [x for x in records if x["difficulty"] == "EASY"][:5]:
        print(f"  [{r['issue_id']}] {r['brief_description'][:55]}")
        print(f"    surfaces: {r['api_surfaces']}")

    print("\nSample HARD issues:")
    for r in [x for x in records if x["difficulty"] == "HARD"][:5]:
        print(f"  [{r['issue_id']}] {r['brief_description'][:55]}")
        print(f"    surfaces: {r['api_surfaces']}")

    # Score distribution
    score_dist: dict[int, int] = defaultdict(int)
    for r in records:
        score_dist[r["weighted_score"]] += 1
    print("\nWeighted score distribution:")
    for score in sorted(score_dist.keys()):
        tier = difficulty_tier(score)
        bar  = "█" * (score_dist[score] // 3)
        print(f"  score {score:2d} [{tier:<6}]: {score_dist[score]:3d}  {bar}")

    # ---------------------------------------------------------------------------
    # Save
    # ---------------------------------------------------------------------------
    # Usable benchmark: covered issues with HIGH or MEDIUM test quality
    benchmark_ready = [
        r for r in records
        if r["issue_covered_in_test"]
        and r["quality_tier"] in ("HIGH", "MEDIUM")
    ]
    bench_diff = defaultdict(int)
    for r in benchmark_ready:
        bench_diff[r["difficulty"]] += 1

    print(f"\nBenchmark-ready corpus (covered + HIGH/MEDIUM quality):")
    print(f"  Total:  {len(benchmark_ready)}")
    print(f"  EASY:   {bench_diff['EASY']}")
    print(f"  MEDIUM: {bench_diff['MEDIUM']}")
    print(f"  HARD:   {bench_diff['HARD']}")

    output = {
        "summary": {
            "total_issues":      len(records),
            "difficulty_counts": dict(diff_counts),
            "benchmark_ready":   {
                "total":  len(benchmark_ready),
                "easy":   bench_diff["EASY"],
                "medium": bench_diff["MEDIUM"],
                "hard":   bench_diff["HARD"],
            },
            "score_distribution": {str(k): v for k, v in sorted(score_dist.items())},
        },
        "issues": records,
    }
    out_path.write_text(json.dumps(output, indent=2))
    print(f"\nFull results saved to: {out_path}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--quality",    type=Path, default=QUALITY_FILE,
                        help="lint_quality.json from 03_audit_test_quality.py")
    parser.add_argument("--checks-dir", type=Path, default=CHECKS_DIR,
                        help="Path to lint-checks source directory")
    parser.add_argument("--out",        type=Path, default=OUT_FILE,
                        help="Output JSON file path")
    args = parser.parse_args()
    main(args.quality, args.checks_dir, args.out)
