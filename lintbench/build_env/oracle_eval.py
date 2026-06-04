#!/usr/bin/env python3
"""
oracle_eval.py — Audit benchmark instances using real AOSP detectors as oracle.

For every instance in lintbench.jsonl, substitutes the real AOSP detector
source as the "generated" file and runs the full build-env pipeline
(compile → test).  Results classify each instance as:

  pass          — real detector compiles and all targeted tests pass
  test_fail     — compiles but ≥1 targeted test fails (harness / stub gap)
  compile_fail  — real detector references AOSP-internal APIs not in maven
  timeout       — container exceeded --timeout seconds
  error         — unexpected subprocess / harness error

This audit identifies which instances are:
  • directly usable in the benchmark (pass)
  • fixable with stubs (test_fail — inspect failure_output)
  • hard by construction / LLM must use public-API equivalent (compile_fail)

Usage (from repo root):
    python lintbench/build_env/oracle_eval.py [options]

    # Full run — all 439 instances, 4 parallel workers
    python lintbench/build_env/oracle_eval.py \
        --out validation/oracle_results.json \
        --log-dir validation/oracle_logs \
        --workers 4

Options:
    --out PATH          Write JSON results (default: oracle_results.json)
    --log-dir PATH      Write per-instance compile/test logs (default: none)
    --split SPLIT       Filter by benchmark_split (easy|medium|hard)
    --difficulty LEVEL  Filter by difficulty (EASY|MEDIUM|HARD)
    --limit N           Stop after N instances
    --workers N         Parallel Docker containers (default: 4)
    --timeout SECS      Per-instance timeout (default: 180)
    --no-docker         Dry-run: print what would be run, no containers
    --rebuild-dataset   Reconstruct dataset.jsonl from lintbench.jsonl using
                        existing passing IDs — no Docker. Use after re-running
                        curate steps 4 and 5 to propagate metadata fixes.
"""

import argparse
import json
import os
import shutil
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
BENCHMARK   = HERE.parent / "data" / "lintbench.jsonl"
RUN_SH      = HERE / "run.sh"
ORACLE_SRC  = HERE / "src" / "oracle"          # persistent staging dir for detector files
RESULTS_DIR = HERE.parent / "results" / "oracle"
DATASET_OUT = HERE.parent / "data" / "dataset.jsonl"
CHECKS_SRC  = (
    REPO_ROOT
    / "lint_codebase/base/lint/libs/lint-checks/src/main/java"
    / "com/android/tools/lint/checks"
)

# ---------------------------------------------------------------------------
# Status labels (ordered for the summary table)
# ---------------------------------------------------------------------------
ALL_STATUSES = ["pass", "test_fail", "compile_fail", "timeout", "error", "missing"]

STATUS_LABEL = {
    "pass":         "pass         ",
    "test_fail":    "test_fail    ",
    "compile_fail": "compile_fail ",
    "timeout":      "timeout      ",
    "error":        "error        ",
    "missing":      "missing      ",
}

# Colour codes (disabled when stdout is not a tty)
_USE_COLOUR = sys.stdout.isatty()
COLOUR = {
    "pass":         "\033[32m",   # green
    "test_fail":    "\033[33m",   # yellow
    "compile_fail": "\033[34m",   # blue
    "timeout":      "\033[35m",   # magenta
    "error":        "\033[31m",   # red
    "missing":      "\033[31m",   # red
    "reset":        "\033[0m",
} if _USE_COLOUR else {k: "" for k in ["pass","test_fail","compile_fail","timeout","error","missing","reset"]}


# ---------------------------------------------------------------------------
# Core runner
# ---------------------------------------------------------------------------

def run_instance(instance: dict, log_dir: Optional[str], timeout: int, dry_run: bool) -> dict:
    """Run one instance and return a result dict."""
    iid        = instance["instance_id"]
    check_file = instance["check_file"]
    test_class = "com.android.tools.lint.checks." + instance["test_file"].replace(".kt", "").replace(".java", "")
    methods    = ",".join(instance["tests_to_run"])
    src_path   = CHECKS_SRC / check_file

    result_base = {
        "instance_id":  iid,
        "detector":     instance["detector"],
        "difficulty":   instance["difficulty"],
        "split":        instance.get("benchmark_split", ""),
        "check_lang":   instance["check_lang"],
        "tests_to_run": instance["tests_to_run"],
    }

    if not src_path.exists():
        return {**result_base, "status": "missing",
                "tests_passed": [], "tests_failed": instance["tests_to_run"],
                "compile_errors": [], "failure_output": f"Source not found: {src_path}"}

    if dry_run:
        print(f"  [dry-run] {iid}")
        return {**result_base, "status": "pass",
                "tests_passed": instance["tests_to_run"], "tests_failed": [],
                "compile_errors": [], "failure_output": ""}

    # Place the real detector in a persistent staging dir named after the instance
    # so run.sh picks up the instance label from dirname(generated_file).
    inst_dir  = ORACLE_SRC / iid
    if inst_dir.exists():
        shutil.rmtree(inst_dir)
    inst_dir.mkdir(parents=True)
    dest_name = f"detector.{instance['check_lang']}"
    shutil.copy(src_path, inst_dir / dest_name)

    env = {**os.environ, "LINTBENCH_TIMEOUT": str(timeout)}
    if log_dir:
        env["LINTBENCH_LOG_DIR"] = log_dir

    t0 = time.monotonic()
    try:
        proc = subprocess.run(
            ["bash", str(RUN_SH),
             str(inst_dir / dest_name),
             test_class,
             methods],
            capture_output=True, text=True,
            timeout=timeout + 30,   # outer safety margin
            env=env,
        )
        elapsed = round(time.monotonic() - t0, 1)
        raw_out = proc.stdout.strip()

        # run.sh writes JSON to stdout; stderr is progress lines
        raw = json.loads(raw_out) if raw_out else {}
    except subprocess.TimeoutExpired:
        return {**result_base, "status": "timeout",
                "tests_passed": [], "tests_failed": instance["tests_to_run"],
                "compile_errors": [], "failure_output": "Container timed out",
                "elapsed": timeout}
    except Exception as exc:
        return {**result_base, "status": "error",
                "tests_passed": [], "tests_failed": instance["tests_to_run"],
                "compile_errors": [], "failure_output": str(exc),
                "elapsed": 0}

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
# Summary helpers
# ---------------------------------------------------------------------------

def _pct(n: int, total: int) -> str:
    return f"{n/total*100:5.1f}%" if total else "  n/a"


def print_summary(results: "list[dict]") -> None:
    total = len(results)
    by_status: dict[str, list] = {s: [] for s in ALL_STATUSES}
    for r in results:
        by_status[r["status"]].append(r)

    print("\n" + "─" * 70)
    print(f"  Oracle eval summary — {total} instances")
    print("─" * 70)

    for s in ALL_STATUSES:
        grp = by_status[s]
        if not grp:
            continue
        c = COLOUR[s]
        rst = COLOUR["reset"]
        print(f"  {c}{STATUS_LABEL[s]}{rst}  {len(grp):4d}  {_pct(len(grp), total)}")

    # By difficulty × status
    print("\n  By difficulty:")
    diffs = ["EASY", "MEDIUM", "HARD"]
    header = f"  {'':8s}" + "".join(f"  {d:<8}" for d in diffs)
    print(header)
    for s in ALL_STATUSES:
        grp = by_status[s]
        if not grp:
            continue
        row = f"  {STATUS_LABEL[s][:8]:<8}"
        by_diff = {d: sum(1 for r in grp if r["difficulty"] == d) for d in diffs}
        for d in diffs:
            n = by_diff[d]
            row += f"  {n:<8}" if n else "  -       "
        print(row)

    # Actionable: test_fail instances need stubs
    test_fail = by_status["test_fail"]
    if test_fail:
        print(f"\n  ── {len(test_fail)} test_fail instances (may need stubs) ──")
        for r in test_fail[:20]:
            snippet = (r["failure_output"] or "").split("\n")[0][:60]
            print(f"    {r['instance_id']:<45}  {snippet}")
        if len(test_fail) > 20:
            print(f"    ... and {len(test_fail)-20} more (see results JSON)")

    print("─" * 70 + "\n")


def save_dataset(results: "list[dict]", out_path: Path) -> None:
    """Write a .jsonl of passing instances, sourced from the original benchmark file."""
    passing_ids = {r["instance_id"] for r in results if r["status"] == "pass"}
    if not passing_ids:
        print("  No passing instances — dataset.jsonl not written")
        return
    out_path.parent.mkdir(parents=True, exist_ok=True)
    written = 0
    with open(BENCHMARK) as src, open(out_path, "w") as dst:
        for line in src:
            inst = json.loads(line)
            if inst.get("instance_id") in passing_ids:
                dst.write(line)
                written += 1
    print(f"  Dataset written to {out_path}  ({written} passing instances)")


def save_results(results: "list[dict]", out_path: Path) -> None:
    total = len(results)
    by_status: dict[str, list] = {s: [] for s in ALL_STATUSES}
    for r in results:
        by_status[r["status"]].append(r)

    summary = {
        "total": total,
        "by_status": {s: len(g) for s, g in by_status.items() if g},
        "by_difficulty": {},
    }
    for d in ["EASY", "MEDIUM", "HARD"]:
        sub = [r for r in results if r["difficulty"] == d]
        if sub:
            summary["by_difficulty"][d] = {
                s: sum(1 for r in sub if r["status"] == s)
                for s in ALL_STATUSES
                if any(r["status"] == s for r in sub)
            }

    output = {"summary": summary, "instances": results}
    out_path.write_text(json.dumps(output, indent=2))
    print(f"  Results written to {out_path}")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def rebuild_dataset(out_path: Path) -> None:
    """
    Reconstruct dataset.jsonl from the current lintbench.jsonl without
    re-running any Docker containers.

    Reads the instance IDs already in dataset.jsonl (the oracle-passing set),
    then pulls the corresponding records from lintbench.jsonl — which may have
    updated fields (e.g. scanner_interfaces fixed by re-running curate steps
    4 and 5).  Overwrites dataset.jsonl in-place.

    Use this after:
      python run_curate.py --only 4 5
    to propagate metadata fixes without re-running oracle/stub eval.
    """
    if not out_path.exists():
        raise SystemExit(f"ERROR: {out_path} not found — run oracle_eval first")
    if not BENCHMARK.exists():
        raise SystemExit(f"ERROR: {BENCHMARK} not found")

    # Read the instance IDs that already passed oracle eval
    passing_ids: set[str] = set()
    with open(out_path) as f:
        for line in f:
            inst = json.loads(line)
            passing_ids.add(inst["instance_id"])

    print(f"  Rebuilding dataset.jsonl from {len(passing_ids)} passing instance IDs")
    print(f"  Source: {BENCHMARK}")

    written = 0
    tmp = out_path.with_suffix(".tmp")
    with open(BENCHMARK) as src, open(tmp, "w") as dst:
        for line in src:
            inst = json.loads(line)
            if inst.get("instance_id") in passing_ids:
                dst.write(line)
                written += 1
    tmp.replace(out_path)
    print(f"  dataset.jsonl rebuilt: {written} instances → {out_path}")

    # Warn if the counts differ (instance was in dataset but not in lintbench)
    if written != len(passing_ids):
        missing = len(passing_ids) - written
        print(f"  WARNING: {missing} instance ID(s) in dataset.jsonl not found in lintbench.jsonl")


def main() -> None:
    ap = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    ap.add_argument("--out",        default=None,
                    help="Output JSON path (default: results/oracle/oracle_results.json)")
    ap.add_argument("--log-dir",    default=None,
                    help="Directory for per-instance compile/test logs (default: results/oracle/logs)")
    ap.add_argument("--split",      default=None,
                    help="Filter by benchmark_split (easy|medium|hard)")
    ap.add_argument("--difficulty", default=None,
                    help="Filter by difficulty (EASY|MEDIUM|HARD)")
    ap.add_argument("--limit",      type=int, default=None,
                    help="Stop after N instances")
    ap.add_argument("--workers",    type=int, default=4,
                    help="Parallel Docker containers (default: 4)")
    ap.add_argument("--timeout",    type=int, default=180,
                    help="Per-instance timeout in seconds (default: 180)")
    ap.add_argument("--no-docker",  action="store_true",
                    help="Dry-run: print what would run, skip containers")
    ap.add_argument("--rebuild-dataset", action="store_true",
                    help="Reconstruct dataset.jsonl from the current lintbench.jsonl "
                         "using the instance IDs already in dataset.jsonl. "
                         "No Docker containers are started. Use after re-running "
                         "curate steps 4 and 5 to propagate metadata fixes.")
    args = ap.parse_args()

    # --rebuild-dataset: metadata-only refresh, no Docker
    if args.rebuild_dataset:
        rebuild_dataset(DATASET_OUT)
        return

    # Load and filter instances
    instances = []
    with open(BENCHMARK) as f:
        for line in f:
            d = json.loads(line)
            if not d.get("in_benchmark"):
                continue
            if args.split and d.get("benchmark_split") != args.split:
                continue
            if args.difficulty and d.get("difficulty") != args.difficulty.upper():
                continue
            instances.append(d)

    # Deduplicate by instance_id, keeping first occurrence.
    seen: set = set()
    deduped = []
    for inst in instances:
        if inst["instance_id"] not in seen:
            seen.add(inst["instance_id"])
            deduped.append(inst)
    if len(deduped) < len(instances):
        print(f"  Warning: dropped {len(instances)-len(deduped)} duplicate instance_id(s)")
    instances = deduped

    if args.limit:
        instances = instances[: args.limit]

    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    ORACLE_SRC.mkdir(parents=True, exist_ok=True)
    out_path = Path(args.out) if args.out else RESULTS_DIR / "oracle_results.json"
    log_dir  = args.log_dir if args.log_dir else str(RESULTS_DIR / "logs")
    Path(log_dir).mkdir(parents=True, exist_ok=True)

    n = len(instances)
    print(f"\nOracle eval: {n} instances  |  workers={args.workers}  |  timeout={args.timeout}s")
    if args.no_docker:
        print("  (dry-run mode — no containers will be started)\n")

    # Use list index as key so duplicate instance_ids never collide.
    results: "list[Optional[dict]]" = [None] * n

    done = 0
    t_start = time.monotonic()

    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {
            pool.submit(run_instance, inst, log_dir, args.timeout, args.no_docker): (i, inst)
            for i, inst in enumerate(instances)
        }
        for fut in as_completed(futures):
            idx, inst = futures[fut]
            result    = fut.result()
            results[idx] = result
            done += 1

            s   = result["status"]
            c   = COLOUR[s]
            rst = COLOUR["reset"]
            passed  = len(result["tests_passed"])
            total_t = len(result["tests_to_run"])
            elapsed = result.get("elapsed", 0)
            print(
                f"  [{done:3d}/{n}] {c}{s:<13}{rst}  "
                f"{result['instance_id']:<45}  "
                f"{passed}/{total_t} passed  "
                f"({elapsed:.0f}s)"
            )

    total_elapsed = round(time.monotonic() - t_start, 1)
    print(f"\n  Finished {n} instances in {total_elapsed:.1f}s")

    # Filter out any None slots (shouldn't happen after dedup, but be safe).
    completed = [r for r in results if r is not None]
    if len(completed) < n:
        print(f"  Warning: {n - len(completed)} result slot(s) were empty")

    print_summary(completed)
    save_results(completed, out_path)
    save_dataset(completed, DATASET_OUT)


if __name__ == "__main__":
    main()
