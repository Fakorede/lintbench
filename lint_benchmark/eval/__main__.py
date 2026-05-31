#!/usr/bin/env python3
"""
eval — LintBench evaluation runner
------------------------------------
Reads lintbench.json, iterates over benchmark instances, calls the build
environment to compile and test each model-generated detector, and writes
structured results.

USAGE
─────────────────────────────────────────────────────────────────────────
# Run from lint_benchmark/ root:

# Stub mode (no compilation — for testing the pipeline):
python -m eval \\
    --benchmark   data/lintbench.json \\
    --generated   path/to/generated_detectors/ \\
    --model       gpt-4o \\
    --prompt      zero_shot \\
    --out         results/gpt4o_zero_shot.json \\
    --stub

# Real evaluation against the Docker build environment:
python -m eval \\
    --benchmark   data/lintbench.json \\
    --generated   path/to/generated_detectors/ \\
    --model       gpt-4o \\
    --prompt      zero_shot \\
    --out         results/gpt4o_zero_shot.json \\
    --build-env   eval/build_env/run.sh \\
    --samples     5

GENERATED FILES LAYOUT
─────────────────────────────────────────────────────────────────────────
generated/
  <model>/<prompt>/
    <instance_id>/
      sample_0.<kt|java>
      sample_1.<kt|java>

RESULTS SCHEMA
─────────────────────────────────────────────────────────────────────────
See metrics.py — SampleResult, InstanceResult, EvalResults dataclasses.
"""

import argparse
import datetime
import json
import os
import subprocess
import time
from collections import defaultdict
from dataclasses import asdict
from pathlib import Path
from typing import Optional

from tqdm import tqdm

from .metrics import (
    TEST_PACKAGE,
    SampleResult,
    InstanceResult,
    estimate_pass_at_k,
    classify_failure,
    aggregate_metrics,
    aggregate_by_api_surface,
    print_summary,
)


# ---------------------------------------------------------------------------
# Build environment interface
# ---------------------------------------------------------------------------

def run_stub(
    instance_id: str,
    generated_file: Path,
    test_class_fqn: str,
    tests_to_run: list[str],
    stub_mode: str = "all_fail",
) -> dict:
    """
    Stub build environment for pipeline development.

    stub_mode options:
      "all_pass"      — every sample passes
      "all_fail"      — every sample fails at compilation (default)
      "mixed"         — alternates pass/fail by hash of instance+file
      "compile_only"  — compiles but tests fail
    """
    time.sleep(0.01)

    if stub_mode == "all_pass":
        return {
            "compiled": True, "compile_errors": [],
            "tests_run": tests_to_run,
            "tests_passed": tests_to_run, "tests_failed": [],
            "failure_output": "",
        }
    if stub_mode == "compile_only":
        return {
            "compiled": True, "compile_errors": [],
            "tests_run": tests_to_run,
            "tests_passed": [], "tests_failed": tests_to_run,
            "failure_output": "Expected: 1 error\nActual: 0 errors",
        }
    if stub_mode == "mixed":
        if hash(f"{instance_id}{generated_file}") % 2 == 0:
            return {
                "compiled": True, "compile_errors": [],
                "tests_run": tests_to_run,
                "tests_passed": tests_to_run, "tests_failed": [],
                "failure_output": "",
            }
        return {
            "compiled": True, "compile_errors": [],
            "tests_run": tests_to_run,
            "tests_passed": [], "tests_failed": tests_to_run,
            "failure_output": "Expected: 2 warnings\nActual: 0 warnings",
        }
    # all_fail
    return {
        "compiled": False,
        "compile_errors": ["error: unresolved reference: JavaContext"],
        "tests_run": [], "tests_passed": [], "tests_failed": [],
        "failure_output": "",
    }


def run_build_env(
    build_env_script: Path,
    generated_file: Path,
    test_class_fqn: str,
    tests_to_run: list[str],
    timeout_s: int = 120,
    log_dir: Optional[Path] = None,
) -> dict:
    """
    Call the real build environment subprocess.

    Interface: run.sh <generated_file> <test_class_fqn> <comma_methods>
    Returns the JSON dict written to stdout by run_inner.sh.
    """
    cmd = [
        str(build_env_script),
        str(generated_file),
        test_class_fqn,
        ",".join(tests_to_run),
    ]
    env = os.environ.copy()
    if log_dir:
        env["LINTBENCH_LOG_DIR"] = str(log_dir)
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout_s, env=env)
        if proc.returncode != 0 and not proc.stdout.strip():
            return {
                "compiled": False,
                "compile_errors": [proc.stderr[:500]],
                "tests_run": [], "tests_passed": [], "tests_failed": [],
                "failure_output": proc.stderr[:500],
            }
        return json.loads(proc.stdout)
    except subprocess.TimeoutExpired:
        return {
            "compiled": False, "compile_errors": ["TIMEOUT"],
            "tests_run": [], "tests_passed": [], "tests_failed": [],
            "failure_output": "TIMEOUT",
        }
    except Exception as e:
        return {
            "compiled": False, "compile_errors": [f"HARNESS_ERROR: {e}"],
            "tests_run": [], "tests_passed": [], "tests_failed": [],
            "failure_output": str(e),
        }


# ---------------------------------------------------------------------------
# Per-instance evaluation
# ---------------------------------------------------------------------------

def evaluate_instance(
    instance: dict,
    generated_dir: Path,
    n_samples: int,
    k: int,
    build_env_script: Optional[Path],
    stub_mode: str,
    timeout_s: int,
    log_dir: Optional[Path] = None,
) -> InstanceResult:
    instance_id  = instance["instance_id"]
    tests_to_run = instance["tests_to_run"]
    test_file    = instance["test_file"]
    check_lang   = instance["check_lang"]
    detector     = instance["detector"]

    test_class     = test_file.replace(".kt", "").replace(".java", "")
    test_class_fqn = f"{TEST_PACKAGE}.{test_class}"

    inst_dir = generated_dir / instance_id
    sample_results: list[SampleResult] = []

    for sample_id in range(n_samples):
        candidate = inst_dir / f"sample_{sample_id}.{check_lang}"

        if not candidate.exists():
            sample_results.append(SampleResult(
                sample_id=sample_id, generated_file=None,
                compiled=False, compile_errors=["File not found"],
                tests_run=[], tests_passed=[], tests_failed=[],
                failure_output="", failure_mode="no_file",
                passed=False, duration_s=0.0,
            ))
            continue

        t0 = time.monotonic()

        if build_env_script is not None:
            raw = run_build_env(build_env_script, candidate, test_class_fqn,
                                tests_to_run, timeout_s, log_dir)
        else:
            raw = run_stub(instance_id, candidate, test_class_fqn,
                           tests_to_run, stub_mode)

        duration       = time.monotonic() - t0
        compiled       = raw.get("compiled", False)
        compile_errors = raw.get("compile_errors", [])
        tests_run      = raw.get("tests_run", [])
        tests_passed   = raw.get("tests_passed", [])
        tests_failed   = raw.get("tests_failed", [])
        failure_output = raw.get("failure_output", "")

        passed = compiled and set(tests_to_run).issubset(set(tests_passed))
        failure_mode = classify_failure(
            compiled, compile_errors, tests_passed, tests_failed,
            tests_to_run, failure_output,
        )

        sample_results.append(SampleResult(
            sample_id=sample_id,
            generated_file=str(candidate),
            compiled=compiled,
            compile_errors=compile_errors,
            tests_run=tests_run,
            tests_passed=tests_passed,
            tests_failed=tests_failed,
            failure_output=failure_output[:1000],
            failure_mode=failure_mode,
            passed=passed,
            duration_s=round(duration, 3),
        ))

    n_passed   = sum(1 for s in sample_results if s.passed)
    n_compiled = sum(1 for s in sample_results if s.compiled)
    n          = len(sample_results)

    failure_counts: dict[str, int] = defaultdict(int)
    for s in sample_results:
        if not s.passed:
            failure_counts[s.failure_mode] += 1
    dominant_failure = (
        max(failure_counts, key=lambda x: failure_counts[x])
        if failure_counts else "pass"
    )

    return InstanceResult(
        instance_id=instance_id,
        issue_id=instance["issue_id"],
        detector=detector,
        difficulty=instance["difficulty"],
        category=instance["category"],
        quality_tier=instance["quality_tier"],
        tests_to_run=tests_to_run,
        methods_to_generate=instance["methods_to_generate"],
        n_samples=n,
        n_passed=n_passed,
        n_compiled=n_compiled,
        pass_at_1=estimate_pass_at_k(n, n_passed, 1),
        pass_at_k=estimate_pass_at_k(n, n_passed, k),
        compilation_rate=round(n_compiled / n, 3) if n else 0.0,
        dominant_failure=dominant_failure,
        samples=sample_results,
    )


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main(args: argparse.Namespace) -> None:
    benchmark_path = Path(args.benchmark)
    generated_dir  = Path(args.generated)
    out_path       = Path(args.out)
    build_env      = Path(args.build_env) if args.build_env else None
    log_dir        = Path(args.log_dir) if args.log_dir else None
    if log_dir:
        log_dir.mkdir(parents=True, exist_ok=True)

    if not benchmark_path.exists():
        raise SystemExit(f"ERROR: benchmark file not found: {benchmark_path}")
    if not generated_dir.exists():
        raise SystemExit(f"ERROR: generated directory not found: {generated_dir}")
    if build_env and not build_env.exists():
        raise SystemExit(f"ERROR: build env script not found: {build_env}")

    if benchmark_path.suffix == ".jsonl":
        all_instances = [
            json.loads(line) for line in benchmark_path.read_text().splitlines() if line.strip()
        ]
        if args.split:
            all_instances = [i for i in all_instances if i.get("benchmark_split") == args.split]
        benchmark_version = "1.0.0"
    else:
        data = json.loads(benchmark_path.read_text())
        splits_to_run = [args.split] if args.split else ["easy", "medium", "hard"]
        all_instances = [
            inst
            for split in splits_to_run
            for inst in data["splits"].get(split, [])
        ]
        benchmark_version = data.get("version", "1.0.0")

    if args.instance:
        instance_filter = set(args.instance)
        all_instances = [i for i in all_instances if i["instance_id"] in instance_filter]

    # Skip instances with no generated files on disk (partial runs)
    all_instances = [
        i for i in all_instances
        if any(
            (generated_dir / i["instance_id"] / f"sample_{s}.{i['check_lang']}").exists()
            for s in range(args.samples)
        )
    ]

    if args.limit:
        all_instances = all_instances[:args.limit]

    instance_map = {inst["instance_id"]: inst for inst in all_instances}

    print(f"Evaluating {len(all_instances)} instances | "
          f"model={args.model} | prompt={args.prompt} | "
          f"k={args.samples} | {'STUB' if not build_env else 'REAL'}")
    print()

    instance_results: list[InstanceResult] = []
    with tqdm(all_instances, unit="instance", desc="evaluating") as pbar:
        for inst in pbar:
            result = evaluate_instance(
                instance=inst,
                generated_dir=generated_dir,
                n_samples=args.samples,
                k=args.samples,
                build_env_script=build_env,
                stub_mode=args.stub_mode,
                timeout_s=args.timeout,
                log_dir=log_dir,
            )
            instance_results.append(result)
            n_pass = sum(1 for r in instance_results if r.n_passed > 0)
            compile_rate = sum(r.compilation_rate for r in instance_results) / len(instance_results)
            pbar.set_postfix(passing=n_pass, compile=f"{compile_rate:.1%}")

    metrics = aggregate_metrics(instance_results, k=args.samples)
    metrics["by_api_surface"] = aggregate_by_api_surface(instance_results, instance_map)

    print_summary(metrics, args.model, args.prompt, args.samples)

    out_path.parent.mkdir(parents=True, exist_ok=True)
    output = {
        "model":              args.model,
        "prompt_variant":     args.prompt,
        "benchmark_version":  benchmark_version,
        "split":              args.split,
        "n_instances":        len(instance_results),
        "n_samples_per_inst": args.samples,
        "timestamp":          datetime.datetime.now(datetime.timezone.utc).isoformat().replace("+00:00", "Z"),
        "pass_at_1":          metrics["pass_at_1"],
        "pass_at_k":          metrics["pass_at_k"],
        "compilation_rate":   metrics["compilation_rate"],
        "by_difficulty":      metrics["by_difficulty"],
        "by_category":        metrics["by_category"],
        "by_api_surface":     metrics["by_api_surface"],
        "failure_distribution": metrics["failure_distribution"],
        "instances": [asdict(r) for r in instance_results],
    }

    out_path.write_text(json.dumps(output, indent=2))
    print(f"\nResults saved to: {out_path}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--benchmark",  default="data/lintbench.jsonl",
                        help="Path to benchmark file (.jsonl or .json)")
    parser.add_argument("--generated",  required=True,
                        help="Directory containing generated detector files")
    parser.add_argument("--model",      required=True,
                        help="Model name (for result labelling)")
    parser.add_argument("--prompt",     default="zero_shot",
                        help="Prompt variant label (zero_shot / few_shot / cot)")
    parser.add_argument("--out",        required=True,
                        help="Output results JSON file path")
    parser.add_argument("--build-env",  default=None,
                        help="Path to build environment script (build_env/run.sh). "
                             "Omit to use stub mode.")
    parser.add_argument("--samples",    type=int, default=1,
                        help="Number of samples per instance (n in pass@k). Default: 1")
    parser.add_argument("--split",      default=None,
                        choices=["easy", "medium", "hard"],
                        help="Restrict evaluation to one difficulty split")
    parser.add_argument("--instance",   action="append", default=None, metavar="INSTANCE_ID",
                        help="Restrict eval to specific instance(s). Repeatable.")
    parser.add_argument("--limit",      type=int, default=None,
                        help="Cap number of instances (useful for testing)")
    parser.add_argument("--timeout",    type=int, default=120,
                        help="Per-instance timeout in seconds (default: 120)")
    parser.add_argument("--log-dir",    default=None,
                        help="Directory to save per-instance compile/test logs from Docker")
    parser.add_argument("--stub",       action="store_true",
                        help="Use stub build environment (no real compilation)")
    parser.add_argument("--stub-mode",  default="all_fail",
                        choices=["all_pass", "all_fail", "compile_only", "mixed"],
                        help="Stub behaviour (default: all_fail)")
    args = parser.parse_args()

    if args.stub and args.build_env:
        raise SystemExit("ERROR: --stub and --build-env are mutually exclusive")

    main(args)
