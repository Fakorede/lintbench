"""
eval/metrics.py
---------------
Data classes, pass@k estimator, failure classifier, and aggregation helpers
for LintBench evaluation results.
"""

import re
from collections import defaultdict
from dataclasses import dataclass, field
from math import comb
from typing import Optional


# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

TEST_PACKAGE = "com.android.tools.lint.checks"

FAILURE_MODES: dict[str, str] = {
    "pass":               "All targeted tests passed",
    "compilation_failed": "Generated file did not compile",
    "wrong_imports":      "Missing or wrong Lint API imports",
    "wrong_scanner":      "Wrong Lint scanner interface implemented",
    "too_narrow":         "Only positive tests failed (under-detection)",
    "too_broad":          "Only negative/clean tests failed (over-detection)",
    "wrong_logic":        "Mixed test failures (logic error)",
    "init_error":         "JVM class-init failure (missing Issue fields in multi-issue detector)",
    "timeout":            "Build or test timed out",
    "harness_error":      "Internal harness error",
    "no_file":            "Generated file not found for this instance",
}


# ---------------------------------------------------------------------------
# Data classes
# ---------------------------------------------------------------------------

@dataclass
class SampleResult:
    """Result for one generated sample (one of k candidates)."""
    sample_id:       int
    generated_file:  Optional[str]
    compiled:        bool
    compile_errors:  list[str]
    tests_run:       list[str]
    tests_passed:    list[str]
    tests_failed:    list[str]
    failure_output:  str
    failure_mode:    str
    passed:          bool
    duration_s:      float


@dataclass
class InstanceResult:
    """Aggregated result across all k samples for one benchmark instance."""
    instance_id:          str
    issue_id:             str
    detector:             str
    difficulty:           str
    category:             str
    quality_tier:         str
    tests_to_run:         list[str]
    methods_to_generate:  list[str]
    n_samples:            int
    n_passed:             int
    n_compiled:           int
    pass_at_1:            float
    pass_at_k:            float
    compilation_rate:     float
    dominant_failure:     str
    samples:              list[SampleResult]


@dataclass
class EvalResults:
    """Top-level results file."""
    model:              str
    prompt_variant:     str
    benchmark_version:  str
    split:              Optional[str]
    n_instances:        int
    n_samples_per_inst: int
    timestamp:          str
    pass_at_1:          float
    pass_at_k:          float
    compilation_rate:   float
    by_difficulty:      dict
    by_category:        dict
    by_api_surface:     dict
    failure_distribution: dict
    instances:          list[InstanceResult]


# ---------------------------------------------------------------------------
# pass@k
# ---------------------------------------------------------------------------

def estimate_pass_at_k(n: int, c: int, k: int) -> float:
    """
    Unbiased pass@k estimator (Chen et al. HumanEval):
        pass@k = 1 - C(n-c, k) / C(n, k)
    """
    if k > n:
        return 0.0
    if n - c < k:
        return 1.0
    return 1.0 - comb(n - c, k) / comb(n, k)


# ---------------------------------------------------------------------------
# Failure classification
# ---------------------------------------------------------------------------

_NEGATIVE_PATTERN = re.compile(
    r"(?i)(clean|correct|nowarning|nowarn|noerror|valid|ok|pass|negative)"
)


def classify_failure(
    compiled: bool,
    compile_errors: list[str],
    tests_passed: list[str],
    tests_failed: list[str],
    all_tests: list[str],
    failure_output: str,
) -> str:
    if not compiled:
        joined = "\n".join(compile_errors)
        if "import" in joined.lower() or "unresolved reference" in joined.lower():
            return "wrong_imports"
        return "compilation_failed"

    if not tests_failed:
        return "pass"

    # JVM class-init cascade — registry couldn't initialize due to missing
    # Issue fields in a multi-issue detector (inject_stubs failed or was skipped).
    if "NoClassDefFoundError" in failure_output or "ExceptionInInitializerError" in failure_output:
        return "init_error"

    failed_negative = [t for t in tests_failed if _NEGATIVE_PATTERN.search(t)]
    failed_positive = [t for t in tests_failed if not _NEGATIVE_PATTERN.search(t)]

    if failed_positive and not failed_negative:
        return "too_narrow"
    if failed_negative and not failed_positive:
        return "too_broad"

    if "NoSuchMethodError" in failure_output or "AbstractMethodError" in failure_output:
        return "wrong_scanner"

    return "wrong_logic"


# ---------------------------------------------------------------------------
# Aggregation
# ---------------------------------------------------------------------------

def aggregate_metrics(instance_results: list[InstanceResult], k: int) -> dict:
    n = len(instance_results)
    if n == 0:
        return {}

    overall_pass1   = round(sum(r.pass_at_1 for r in instance_results) / n, 4)
    overall_passk   = round(sum(r.pass_at_k for r in instance_results) / n, 4)
    overall_compile = round(sum(r.compilation_rate for r in instance_results) / n, 4)

    by_diff: dict[str, dict] = {}
    for tier in ("EASY", "MEDIUM", "HARD"):
        subset = [r for r in instance_results if r.difficulty == tier]
        if subset:
            by_diff[tier] = {
                "n":               len(subset),
                "pass_at_1":       round(sum(r.pass_at_1 for r in subset) / len(subset), 4),
                "pass_at_k":       round(sum(r.pass_at_k for r in subset) / len(subset), 4),
                "compilation_rate": round(sum(r.compilation_rate for r in subset) / len(subset), 4),
            }

    by_cat: dict[str, dict] = {}
    for cat in sorted(set(r.category for r in instance_results)):
        subset = [r for r in instance_results if r.category == cat]
        by_cat[cat] = {
            "n":         len(subset),
            "pass_at_1": round(sum(r.pass_at_1 for r in subset) / len(subset), 4),
        }

    failure_dist: dict[str, int] = defaultdict(int)
    for r in instance_results:
        for s in r.samples:
            failure_dist[s.failure_mode] += 1

    return {
        "pass_at_1":          overall_pass1,
        "pass_at_k":          overall_passk,
        "compilation_rate":   overall_compile,
        "by_difficulty":      by_diff,
        "by_category":        by_cat,
        "failure_distribution": dict(sorted(failure_dist.items(), key=lambda x: -x[1])),
    }


def aggregate_by_api_surface(
    instance_results: list[InstanceResult],
    instance_map: dict[str, dict],
) -> dict:
    surface_pass: dict[str, list[float]] = defaultdict(list)
    for r in instance_results:
        for surface in instance_map.get(r.instance_id, {}).get("api_surfaces", []):
            surface_pass[surface].append(r.pass_at_1)
    return {
        surface: {
            "n":         len(vals),
            "pass_at_1": round(sum(vals) / len(vals), 4),
        }
        for surface, vals in sorted(surface_pass.items())
        if len(vals) >= 3
    }


def print_summary(metrics: dict, model: str, prompt: str, k: int) -> None:
    print("=" * 62)
    print(f"  Model:         {model}")
    print(f"  Prompt:        {prompt}")
    print(f"  Instances:     {sum(v['n'] for v in metrics['by_difficulty'].values())}")
    print("=" * 62)
    print(f"\n  pass@1:            {metrics['pass_at_1']:.1%}")
    if k > 1:
        print(f"  pass@{k}:            {metrics['pass_at_k']:.1%}")
    print(f"  compilation rate:  {metrics['compilation_rate']:.1%}")

    print(f"\nStratified pass@1:")
    for tier in ("EASY", "MEDIUM", "HARD"):
        d = metrics["by_difficulty"].get(tier, {})
        if d:
            print(f"  {tier:<8} n={d['n']:<4}  pass@1={d['pass_at_1']:.1%}  "
                  f"compile={d['compilation_rate']:.1%}")

    print(f"\nCategory pass@1 (top 5 by n):")
    cats = sorted(metrics["by_category"].items(), key=lambda x: -x[1]["n"])
    for cat, d in cats[:5]:
        print(f"  {cat:<25} n={d['n']:<4}  pass@1={d['pass_at_1']:.1%}")

    print(f"\nFailure distribution:")
    for mode, count in list(metrics["failure_distribution"].items())[:6]:
        desc = FAILURE_MODES.get(mode, mode)
        print(f"  {mode:<22} {count:>4}   {desc}")
