#!/usr/bin/env python3
"""
stub_eval.py — Validate benchmark instances by running tests against stubbed detectors.

For every instance in dataset.jsonl, generates a minimal stub implementation
(correct class structure, Issue declarations intact, all method bodies emptied)
and runs the full build-env pipeline.  Every test should fail — a passing test
means the instance has no discriminating power and is removed from the dataset.

Results:
  test_fail     — expected; stub produces no warnings, test correctly rejects it
  pass          — bad; stub somehow satisfies the test → remove from dataset
  compile_fail  — stub failed to compile (stub generator bug or unusual API usage)
  timeout / error — infrastructure issues

Usage (from repo root):
    # 1. Build the stub generator (once):
    #    cd lint_benchmark/stub_generator && ./gradlew shadowJar

    # 2. Run stub eval:
    python lint_benchmark/build_env/stub_eval.py [options]

Options:
    --out PATH      Write JSON results (default: results/stub/stub_results.json)
    --log-dir PATH  Per-instance logs (default: results/stub/logs)
    --limit N       Stop after N instances
    --workers N     Parallel Docker containers (default: 4)
    --timeout SECS  Per-instance timeout (default: 180)
    --no-docker     Dry-run: print what would run, skip containers
"""

import argparse
import json
import os
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Optional

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------
HERE        = Path(__file__).parent.resolve()
REPO_ROOT   = HERE.parent.parent
DATASET     = HERE.parent / "data" / "dataset.jsonl"
RUN_SH      = HERE / "run.sh"
STUB_SRC    = HERE / "src" / "stub"          # persistent staging dir for stub files
RESULTS_DIR = HERE.parent / "results" / "stub"
STUB_JAR    = HERE.parent / "stub_generator" / "build" / "libs" / "stub-generator.jar"
CHECKS_SRC  = (
    REPO_ROOT
    / "lint_codebase/base/lint/libs/lint-checks/src/main/java"
    / "com/android/tools/lint/checks"
)

# ---------------------------------------------------------------------------
# Status labels
# ---------------------------------------------------------------------------
ALL_STATUSES = ["pass", "test_fail", "compile_fail", "timeout", "error", "missing"]

STATUS_LABEL = {s: f"{s:<13}" for s in ALL_STATUSES}

_USE_COLOUR = sys.stdout.isatty()
COLOUR = {
    "pass":         "\033[31m",   # red   — bad: stub should NOT pass
    "test_fail":    "\033[32m",   # green — good: stub correctly rejected
    "compile_fail": "\033[34m",   # blue
    "timeout":      "\033[35m",   # magenta
    "error":        "\033[31m",   # red
    "missing":      "\033[31m",   # red
    "reset":        "\033[0m",
} if _USE_COLOUR else {k: "" for k in [*ALL_STATUSES, "reset"]}


# ---------------------------------------------------------------------------
# Stub generation
# ---------------------------------------------------------------------------

def generate_stubs(instances: "list[dict]") -> "dict[str, Path]":
    """
    Run the stub generator JAR over all instances in one JVM invocation.
    Returns {instance_id: stub_dest_path} for instances whose stub was written.
    """
    if not STUB_JAR.exists():
        sys.exit(
            f"\nERROR: stub-generator JAR not found at {STUB_JAR}\n"
            f"Build it first:\n"
            f"  cd {STUB_JAR.parent.parent.parent} && ./gradlew shadowJar\n"
        )

    manifest_lines = []
    stub_paths: dict[str, Path] = {}

    for inst in instances:
        iid      = inst["instance_id"]
        lang     = inst["check_lang"]
        src_path = CHECKS_SRC / inst["check_file"]
        if not src_path.exists():
            continue
        dst_dir  = STUB_SRC / iid
        dst_dir.mkdir(parents=True, exist_ok=True)
        dst_path = dst_dir / f"detector.{lang}"
        manifest_lines.append(f"{src_path}\t{dst_path}")
        stub_paths[iid] = dst_path

    manifest_path = STUB_SRC / "manifest.txt"
    manifest_path.write_text("\n".join(manifest_lines))

    print(f"  Generating {len(manifest_lines)} stubs via JAR …")
    t0 = time.monotonic()
    proc = subprocess.run(
        ["java", "-jar", str(STUB_JAR), "--manifest", str(manifest_path)],
        capture_output=True, text=True,
    )
    elapsed = round(time.monotonic() - t0, 1)
    if proc.returncode != 0:
        print(f"  Stub generator stderr:\n{proc.stderr[:2000]}", file=sys.stderr)
        sys.exit(f"  Stub generator exited with code {proc.returncode}")
    print(f"  Stubs generated in {elapsed}s")

    # Return only paths for which a file was actually written
    return {iid: p for iid, p in stub_paths.items() if p.exists()}


# ---------------------------------------------------------------------------
# Per-instance runner (identical pipeline to oracle_eval)
# ---------------------------------------------------------------------------

def run_instance(
    instance: dict,
    stub_path: Path,
    log_dir: Optional[str],
    timeout: int,
    dry_run: bool,
) -> dict:
    iid        = instance["instance_id"]
    test_class = "com.android.tools.lint.checks." + instance["test_file"].replace(".kt", "").replace(".java", "")
    methods    = ",".join(instance["tests_to_run"])

    result_base = {
        "instance_id":  iid,
        "detector":     instance["detector"],
        "difficulty":   instance["difficulty"],
        "split":        instance.get("benchmark_split", ""),
        "check_lang":   instance["check_lang"],
        "tests_to_run": instance["tests_to_run"],
    }

    if dry_run:
        print(f"  [dry-run] {iid}")
        return {**result_base, "status": "test_fail",
                "tests_passed": [], "tests_failed": instance["tests_to_run"],
                "compile_errors": [], "failure_output": "", "elapsed": 0}

    env = {**os.environ, "LINTBENCH_TIMEOUT": str(timeout)}
    if log_dir:
        env["LINTBENCH_LOG_DIR"] = log_dir

    t0 = time.monotonic()
    try:
        proc = subprocess.run(
            ["bash", str(RUN_SH), str(stub_path), test_class, methods],
            capture_output=True, text=True,
            timeout=timeout + 30,
            env=env,
        )
        elapsed  = round(time.monotonic() - t0, 1)
        raw_out  = proc.stdout.strip()
        raw      = json.loads(raw_out) if raw_out else {}
    except subprocess.TimeoutExpired:
        return {**result_base, "status": "timeout",
                "tests_passed": [], "tests_failed": instance["tests_to_run"],
                "compile_errors": [], "failure_output": "Container timed out",
                "elapsed": timeout}
    except Exception as exc:
        return {**result_base, "status": "error",
                "tests_passed": [], "tests_failed": instance["tests_to_run"],
                "compile_errors": [], "failure_output": str(exc), "elapsed": 0}

    if not raw.get("compiled", False):
        status = "compile_fail"
    elif raw.get("tests_failed"):
        status = "test_fail"
    else:
        status = "pass"

    return {
        **result_base,
        "status":         status,
        "tests_passed":   raw.get("tests_passed", []),
        "tests_failed":   raw.get("tests_failed", []),
        "compile_errors": raw.get("compile_errors", [])[:5],
        "failure_output": (raw.get("failure_output") or "")[:500],
        "elapsed":        elapsed,
    }


# ---------------------------------------------------------------------------
# Summary / output
# ---------------------------------------------------------------------------

def _pct(n: int, total: int) -> str:
    return f"{n/total*100:5.1f}%" if total else "  n/a"


def print_summary(results: "list[dict]") -> None:
    total = len(results)
    by_status: dict[str, list] = {s: [] for s in ALL_STATUSES}
    for r in results:
        by_status[r["status"]].append(r)

    print("\n" + "─" * 70)
    print(f"  Stub eval summary — {total} instances")
    print("─" * 70)

    for s in ALL_STATUSES:
        grp = by_status[s]
        if not grp:
            continue
        c   = COLOUR[s]
        rst = COLOUR["reset"]
        note = "  ← BAD (remove from dataset)" if s == "pass" else ""
        print(f"  {c}{STATUS_LABEL[s]}{rst}  {len(grp):4d}  {_pct(len(grp), total)}{note}")

    # Flag the bad ones explicitly
    bad = by_status["pass"]
    if bad:
        print(f"\n  ── {len(bad)} instances passed with stub (insufficient test coverage) ──")
        for r in bad[:20]:
            print(f"    {r['instance_id']}")
        if len(bad) > 20:
            print(f"    … and {len(bad) - 20} more (see results JSON)")

    print("─" * 70 + "\n")


def save_results(results: "list[dict]", out_path: Path) -> None:
    total     = len(results)
    by_status = {s: sum(1 for r in results if r["status"] == s) for s in ALL_STATUSES}
    output    = {"summary": {"total": total, "by_status": {s: n for s, n in by_status.items() if n}},
                 "instances": results}
    out_path.write_text(json.dumps(output, indent=2))
    print(f"  Results written to {out_path}")


def save_validated_dataset(results: "list[dict]", dataset_path: Path) -> None:
    """
    Rewrite dataset.jsonl keeping only instances where the stub correctly
    failed (status == test_fail).  Instances where the stub passed are dropped.
    """
    keep_ids = {r["instance_id"] for r in results if r["status"] in ("test_fail", "compile_fail", "timeout", "error")}
    drop_ids = {r["instance_id"] for r in results if r["status"] == "pass"}

    if drop_ids:
        print(f"\n  Dropping {len(drop_ids)} instance(s) where stub passed:")
        for iid in sorted(drop_ids):
            print(f"    {iid}")

    lines_kept = 0
    tmp = dataset_path.with_suffix(".tmp")
    with open(dataset_path) as src, open(tmp, "w") as dst:
        for line in src:
            inst = json.loads(line)
            if inst.get("instance_id") in keep_ids:
                dst.write(line)
                lines_kept += 1
    tmp.replace(dataset_path)
    print(f"  dataset.jsonl updated: {lines_kept} validated instances")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    ap = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    ap.add_argument("--out",      default=None,
                    help="Output JSON path (default: results/stub/stub_results.json)")
    ap.add_argument("--log-dir",  default=None,
                    help="Directory for per-instance logs (default: results/stub/logs)")
    ap.add_argument("--limit",    type=int, default=None)
    ap.add_argument("--workers",  type=int, default=4)
    ap.add_argument("--timeout",  type=int, default=180)
    ap.add_argument("--no-docker", action="store_true")
    args = ap.parse_args()

    # Load dataset (already filtered to oracle-passing instances)
    instances = []
    with open(DATASET) as f:
        for line in f:
            instances.append(json.loads(line))

    if args.limit:
        instances = instances[: args.limit]

    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    STUB_SRC.mkdir(parents=True, exist_ok=True)
    out_path = Path(args.out) if args.out else RESULTS_DIR / "stub_results.json"
    log_dir  = args.log_dir if args.log_dir else str(RESULTS_DIR / "logs")
    Path(log_dir).mkdir(parents=True, exist_ok=True)

    n = len(instances)
    print(f"\nStub eval: {n} instances  |  workers={args.workers}  |  timeout={args.timeout}s")
    if args.no_docker:
        print("  (dry-run mode)\n")

    # Pre-generate all stubs in a single JVM invocation
    if not args.no_docker:
        stub_paths = generate_stubs(instances)
    else:
        stub_paths = {inst["instance_id"]: STUB_SRC / inst["instance_id"] / f"detector.{inst['check_lang']}"
                      for inst in instances}

    results: "list[Optional[dict]]" = [None] * n
    done = 0
    t_start = time.monotonic()

    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {}
        for i, inst in enumerate(instances):
            iid = inst["instance_id"]
            stub_path = stub_paths.get(iid)
            if stub_path is None and not args.no_docker:
                # Stub generation failed for this instance
                results[i] = {
                    "instance_id":  iid,
                    "detector":     inst["detector"],
                    "difficulty":   inst["difficulty"],
                    "split":        inst.get("benchmark_split", ""),
                    "check_lang":   inst["check_lang"],
                    "tests_to_run": inst["tests_to_run"],
                    "status":       "error",
                    "tests_passed": [],
                    "tests_failed": inst["tests_to_run"],
                    "compile_errors": [],
                    "failure_output": "Stub generation failed",
                    "elapsed": 0,
                }
                done += 1
                continue
            futures[pool.submit(
                run_instance, inst, stub_path, log_dir, args.timeout, args.no_docker
            )] = i

        for fut in as_completed(futures):
            idx    = futures[fut]
            result = fut.result()
            results[idx] = result
            done  += 1

            s       = result["status"]
            c       = COLOUR[s]
            rst     = COLOUR["reset"]
            passed  = len(result["tests_passed"])
            total_t = len(result["tests_to_run"])
            elapsed = result.get("elapsed", 0)
            flag    = " ← BAD" if s == "pass" else ""
            print(
                f"  [{done:3d}/{n}] {c}{s:<13}{rst}  "
                f"{result['instance_id']:<45}  "
                f"{passed}/{total_t} passed  ({elapsed:.0f}s){flag}"
            )

    total_elapsed = round(time.monotonic() - t_start, 1)
    print(f"\n  Finished {n} instances in {total_elapsed:.1f}s")

    completed = [r for r in results if r is not None]
    print_summary(completed)
    save_results(completed, out_path)

    # Update dataset.jsonl to drop instances where the stub passed
    if not args.no_docker:
        save_validated_dataset(completed, DATASET)


if __name__ == "__main__":
    main()
