"""
lintgen.agent.runner — iterative compile-and-test repair loop.

Pipeline (per instance, inspired by AutoChecker TDCD):
  1. Generate initial detector from NL spec + knowledge context (any prompt
     variant, optional RAG — same setup as the inference runner).
  2. Validate via build_env (Docker): compile + run original test suite.
  3. If all tests pass → done (final).
  4. Else extract feedback:
       - Compile failure  → compile_errors (up to 20 lines)
       - Test failure     → failure_output (assertion messages from the harness)
  5. Build repair prompt: original user message + last code + feedback.
     RAG context is re-retrieved each iteration with an error-augmented
     query: unresolved symbols for compile failures; scanner interface +
     scope constants extracted from the generated code for test failures.
  6. Generate repaired detector.
  7. Goto 2, up to --max-iter times.

Tests are never shown to the model. The harness runs them as a held-out
oracle; only their execution output (error messages, assertion failures) is
fed back — matching the paper's knowledge-gap framing.

Trajectory layout (per instance):
  <out_root>/<instance_id>/
    trajectory.jsonl     one JSONL record per iteration
    iter_0.<ext>         generated code at iteration 0 (initial)
    iter_1.<ext>         generated code at iteration 1 (first repair)
    ...
    final.<ext>          symlink or copy of the best attempt:
                         last passing iteration, else last iteration
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
from pathlib import Path
from typing import Optional

from tqdm import tqdm

# Ensure the repo root is on sys.path so `lintbench` is importable regardless
# of which Python interpreter is active (uv venv, conda, system, etc.).
# runner.py lives at lintgen/src/lintgen/agent/runner.py → parents[4] = repo root.
_REPO_ROOT = Path(__file__).resolve().parents[4]
if str(_REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(_REPO_ROOT))


# ---------------------------------------------------------------------------
# Feedback extraction
# ---------------------------------------------------------------------------

def _extract_feedback(result: dict) -> str:
    """
    Format build_env output into a concise feedback string for the repair prompt.

    Prioritises compile errors (most actionable); falls back to test failure
    output from assertions / harness messages.
    """
    if not result.get("compiled", False):
        errors = result.get("compile_errors", [])
        failure = result.get("failure_output", "")
        parts = []
        if errors:
            parts.append("Compilation failed:\n" + "\n".join(errors))
        if failure and not errors:
            # Docker / harness-level failure, no structured errors
            parts.append("Build error:\n" + failure[:800])
        return "\n\n".join(parts) or "Compilation failed (no error details available)."

    # Compiled but tests failed
    failed = result.get("tests_failed", [])
    output = result.get("failure_output", "").strip()
    parts = []
    if failed:
        parts.append(f"Tests failed: {', '.join(failed)}")
    if output:
        parts.append(output[:1200])
    return "\n\n".join(parts) or "Tests failed (no failure details available)."


# ---------------------------------------------------------------------------
# Repair prompt construction
# ---------------------------------------------------------------------------

_REPAIR_HEADER = """\
Your previous attempt failed. Here is the code you generated:

```{ext}
{prev_code}
```

The harness reported the following errors:

{feedback}

Fix the detector so that it compiles cleanly and all tests pass.
Output ONLY the corrected source file — no explanation, no markdown fences."""


def _build_repair_user(
    original_user: str,
    prev_code: str,
    feedback: str,
    ext: str,
) -> str:
    """
    Build the user message for a repair iteration.

    Structure:
      <original user message (NL spec + knowledge context)>
      ---
      <repair block: last code + harness feedback>
    """
    repair_block = _REPAIR_HEADER.format(
        ext=ext,
        prev_code=prev_code.strip(),
        feedback=feedback.strip(),
    )
    return f"{original_user}\n\n---\n\n{repair_block}"


# ---------------------------------------------------------------------------
# Error-augmented RAG re-retrieval
# ---------------------------------------------------------------------------

_UNRESOLVED_RE = re.compile(
    r"[Uu]nresolved reference:\s*(\w+)"
    r"|error: cannot find symbol.*?(\w+)"
    r"|error: (\w+) is not defined",
    re.IGNORECASE,
)

# Extracts scanner interface names and Scope constants from generated code.
# Used when the detector compiles but produces no warnings — the error signal
# contains no symbol names, so we use what the model already tried as the query.
_SCANNER_RE = re.compile(
    r"implements\s+((?:[\w]+Scanner)(?:\s*,\s*[\w]+Scanner)*)"  # Java: implements BinaryResourceScanner
    r"|:\s*Detector\(\),\s*([\w]+Scanner)"                       # Kotlin: : Detector(), SourceCodeScanner
    r"|Scope\.([\w_]+)"                                          # Scope.BINARY_RESOURCE_FILE etc.
)


def _augment_rag_query(instance: dict, feedback: str, prev_code: str = "") -> dict:
    """
    Return a copy of instance with nl_spec augmented by error-salient terms
    so that RAG re-retrieval is biased toward relevant API entries.

    Two strategies:
      1. Compile failure: extract unresolved symbol names from the error log.
         These name exactly what the model got wrong; retrieving their docs
         gives the model the correct signatures on the next attempt.
      2. Test failure (compiled but no warnings): extract the scanner interface
         and Scope constants the model already used from prev_code. Retrieving
         docs for those surfaces correct usage — appliesTo contracts, callback
         lifecycle, scope registration — which is what silent failures require.
    """
    extra_terms: set[str] = set()

    # Strategy 1: unresolved symbols from compile errors
    for m in _UNRESOLVED_RE.finditer(feedback):
        term = next(filter(None, m.groups()), None)
        if term:
            extra_terms.add(term)

    # Strategy 2: scanner interface + scope from generated code (test failures)
    if not extra_terms and prev_code:
        for m in _SCANNER_RE.finditer(prev_code):
            for group in m.groups():
                if group:
                    # Split comma-separated interface lists (Java multi-implements)
                    for term in re.split(r"\s*,\s*", group.strip()):
                        if term:
                            extra_terms.add(term)

    if not extra_terms:
        return instance

    augmented = dict(instance)
    augmented["nl_spec"] = (
        instance.get("nl_spec", "") + " " + " ".join(sorted(extra_terms))
    )
    return augmented


# ---------------------------------------------------------------------------
# Main entry point
# ---------------------------------------------------------------------------

def run_agent(args: argparse.Namespace) -> None:
    # ── Load benchmark ────────────────────────────────────────────────────────
    benchmark_path = Path(args.benchmark)
    if not benchmark_path.exists():
        raise SystemExit(f"Benchmark not found: {benchmark_path}")

    instances = [
        json.loads(line)
        for line in benchmark_path.read_text().splitlines()
        if line.strip()
    ]
    if getattr(args, "split", None):
        instances = [i for i in instances if i.get("benchmark_split") == args.split]
    if getattr(args, "instance_ids", None):
        id_filter = set(args.instance_ids)
        instances = [i for i in instances if i["instance_id"] in id_filter]
    if args.limit:
        instances = instances[: args.limit]

    # ── Build environment ─────────────────────────────────────────────────────
    build_env_script: Optional[Path] = None
    if getattr(args, "build_env", None):
        build_env_script = Path(args.build_env)
        if not build_env_script.exists():
            raise SystemExit(f"build_env script not found: {build_env_script}")

    # ── RAG knowledge base (optional) ─────────────────────────────────────────
    _RAG_PROMPTS = {"base+apis", "base+apis+docs", "base+docs"}
    _RAG_TIERS = {
        "base+apis":      frozenset({1, 2, 4, 5}),
        "base+apis+docs": frozenset({1, 2, 3, 4, 5}),
        "base+docs":      frozenset({3}),
    }
    _RAG_K = {
        "base+apis":      5,
        "base+apis+docs": 5,
        "base+docs":      20,
    }

    kb = None
    if args.prompt in _RAG_PROMPTS and not getattr(args, "no_rag", False):
        try:
            from lintgen.rag import get_knowledge_base
            kb = get_knowledge_base()
            print("RAG knowledge base loaded.")
        except FileNotFoundError as e:
            print(f"WARNING: {e}\nFalling back without RAG.", file=sys.stderr)

    # ── Import lintbench primitives ───────────────────────────────────────────
    try:
        from lintbench.inference.prompts import build_prompt, extract_code
        from lintbench.inference.providers import (
            detect_provider, generate_sample, compute_cost,
        )
        from lintbench.eval.__main__ import run_build_env, run_stub
    except ImportError as exc:
        raise SystemExit(
            f"lintbench package not found ({exc}). "
            "Make sure you're running from the repo root and the workspace is synced."
        )

    # ── Provider + sampling params ────────────────────────────────────────────
    provider        = args.provider or detect_provider(args.model, getattr(args, "api_base", None))
    api_base        = getattr(args, "api_base", None)
    thinking_budget = getattr(args, "thinking_budget", None)
    temperature     = getattr(args, "temperature", 0.2)  # slight stochasticity helps repair
    max_tokens      = getattr(args, "max_tokens", 32768)
    delay           = getattr(args, "delay", 0.5)
    force           = getattr(args, "force", False)
    max_iter        = getattr(args, "max_iter", 10)
    timeout_s       = getattr(args, "timeout", 180)
    stub_mode       = getattr(args, "stub_mode", "all_fail")
    # RAG prompts use zero_shot as the base template; RAG context is injected by the loop
    lintbench_prompt = "zero_shot" if args.prompt in _RAG_PROMPTS else args.prompt
    rag_tiers = _RAG_TIERS.get(args.prompt, frozenset({1, 2, 3, 4, 5}))
    rag_k     = _RAG_K.get(args.prompt, 5)

    # ── Output layout ─────────────────────────────────────────────────────────
    run_id   = getattr(args, "run_id", None) or time.strftime("%Y%m%dT%H%M%SZ", time.gmtime())
    out_root = Path(args.out) / run_id / args.model / f"{args.prompt}_agent"
    out_root.mkdir(parents=True, exist_ok=True)
    run_log_path = out_root / "run_log.jsonl"

    print(
        f"LintGen agent: {len(instances)} instances | model={args.model} | "
        f"provider={provider} | prompt={args.prompt} | max_iter={max_iter} | "
        f"rag={'on' if kb else 'off'} | build_env={'real' if build_env_script else 'stub'}"
    )
    print(f"Run ID : {run_id}")
    print(f"Output : {out_root}\n")

    total_passed = total_errors = 0
    total_cost   = 0.0

    with open(run_log_path, "a") as run_log_f, \
         tqdm(instances, unit="instance", desc="agent") as pbar:

        for inst in pbar:
            instance_id = inst["instance_id"]
            ext         = inst["check_lang"]
            inst_dir    = out_root / instance_id
            inst_dir.mkdir(parents=True, exist_ok=True)

            traj_path  = inst_dir / "trajectory.jsonl"
            final_path = inst_dir / f"final.{ext}"

            # Skip if already fully done (final.* exists and not --force)
            if final_path.exists() and not force:
                pbar.write(f"[skip] {instance_id}")
                continue

            # ── Build initial prompt ──────────────────────────────────────────
            system, raw_user = build_prompt(inst, lintbench_prompt)

            # Inject RAG context (initial retrieval).
            # raw_user is kept without RAG so repair iterations can substitute
            # (not stack) the error-augmented context.
            if kb is not None:
                rag_context = kb.format_context(inst, k=rag_k, include_tiers=rag_tiers)
                original_user = f"{rag_context}\n\n{raw_user}" if rag_context else raw_user
            else:
                original_user = raw_user

            # ── Repair loop ───────────────────────────────────────────────────
            prev_code:  Optional[str] = None
            best_code:  Optional[str] = None   # last passing code
            last_code:  Optional[str] = None   # most recent code regardless
            inst_cost   = 0.0
            passed_iter = None

            traj_records: list[dict] = []

            for iteration in range(max_iter):
                # Build user message for this iteration
                if iteration == 0:
                    user = original_user
                else:
                    feedback = _extract_feedback(last_result)  # type: ignore[name-defined]

                    # Re-retrieve RAG with error-augmented query if available.
                    # Use raw_user (without any prior RAG context) as the base
                    # so the new context replaces rather than stacks on iter_0's.
                    if kb is not None:
                        augmented_inst = _augment_rag_query(inst, feedback, prev_code or "")
                        rag_context = kb.format_context(augmented_inst, k=rag_k, include_tiers=rag_tiers)
                        aug_base = f"{rag_context}\n\n{raw_user}" if rag_context else raw_user
                    else:
                        aug_base = raw_user

                    user = _build_repair_user(
                        original_user=aug_base,
                        prev_code=prev_code,  # type: ignore[arg-type]
                        feedback=feedback,
                        ext=ext,
                    )

                # ── Generate ─────────────────────────────────────────────────
                iter_file      = inst_dir / f"iter_{iteration}.{ext}"
                iter_prompt    = inst_dir / f"iter_{iteration}_prompt.txt"
                iter_raw       = inst_dir / f"iter_{iteration}_raw.txt"
                gen_record: dict = {
                    "instance_id": instance_id,
                    "iteration":   iteration,
                    "model":       args.model,
                    "provider":    provider,
                    "prompt":      args.prompt,
                    "timestamp":   time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                    "prompt_file": str(iter_prompt),
                    "raw_file":    str(iter_raw),
                }

                # Save prompt to disk for inspection
                iter_prompt.write_text(
                    f"=== SYSTEM ===\n{system}\n\n=== USER ===\n{user}",
                    encoding="utf-8",
                )

                try:
                    raw, usage, reasoning = generate_sample(
                        model=args.model,
                        provider=provider,
                        system=system,
                        user=user,
                        temperature=temperature,
                        max_tokens=max_tokens,
                        api_base=api_base,
                        thinking_budget=thinking_budget,
                    )
                    iter_raw.write_text(raw, encoding="utf-8")
                    code = extract_code(raw, ext)
                    iter_file.write_text(code, encoding="utf-8")
                    prev_code = last_code = code
                    gen_record["generated"] = True
                    gen_record["output_chars"] = len(code)
                    gen_record["usage"] = usage
                    if reasoning:
                        gen_record["reasoning_content"] = reasoning
                    cost = compute_cost(args.model, usage["input_tokens"], usage["output_tokens"])
                    if cost is not None:
                        gen_record["cost_usd"] = round(cost, 6)
                        inst_cost += cost
                        total_cost += cost
                except Exception as exc:
                    gen_record["generated"] = False
                    gen_record["error"] = str(exc)
                    iter_raw.write_text(str(exc), encoding="utf-8")
                    total_errors += 1
                    tqdm.write(f"ERROR [{instance_id} iter{iteration}]: {exc}", file=sys.stderr)
                    if "rate" in str(exc).lower() or "429" in str(exc):
                        wait = min(60, 5 * (iteration + 1))
                        tqdm.write(f"Rate limited — waiting {wait}s", file=sys.stderr)
                        time.sleep(wait)
                    traj_records.append(gen_record)
                    break

                # ── Validate ──────────────────────────────────────────────────
                tests_to_run    = inst["tests_to_run"]
                test_file       = inst["test_file"]
                test_class      = test_file.replace(".kt", "").replace(".java", "")
                from lintbench.eval.metrics import TEST_PACKAGE
                test_class_fqn  = f"{TEST_PACKAGE}.{test_class}"

                iter_log_dir = inst_dir / f"iter_{iteration}_logs"
                if build_env_script is not None:
                    last_result = run_build_env(
                        build_env_script, iter_file, test_class_fqn,
                        tests_to_run, timeout_s=timeout_s, log_dir=iter_log_dir,
                    )
                    # Replace sparse compile_errors list with the full compile.log
                    # so the model sees the exact symbol names, not just "cannot find symbol".
                    _compile_log = iter_log_dir / instance_id / "compile.log"
                    if _compile_log.exists():
                        full_log = _compile_log.read_text(encoding="utf-8", errors="replace").strip()
                        if full_log:
                            last_result = dict(last_result)
                            last_result["compile_errors"] = full_log.splitlines()
                else:
                    last_result = run_stub(instance_id, iter_file, test_class_fqn,
                                          tests_to_run, stub_mode)

                compiled       = last_result.get("compiled", False)
                compile_errors = last_result.get("compile_errors", [])
                tests_passed   = last_result.get("tests_passed", [])
                tests_failed   = last_result.get("tests_failed", [])
                all_pass       = compiled and set(tests_to_run).issubset(set(tests_passed))

                gen_record.update({
                    "compiled":        compiled,
                    "compile_errors":  compile_errors,
                    "tests_passed":    tests_passed,
                    "tests_failed":    tests_failed,
                    "all_pass":        all_pass,
                    "failure_output":  last_result.get("failure_output", ""),
                })
                traj_records.append(gen_record)

                if all_pass:
                    best_code   = code
                    passed_iter = iteration
                    break

                if delay > 0:
                    time.sleep(delay)

            # ── Save trajectory ───────────────────────────────────────────────
            with open(traj_path, "w") as tf:
                for rec in traj_records:
                    tf.write(json.dumps(rec) + "\n")

            # ── Write final ───────────────────────────────────────────────────
            chosen = best_code or last_code
            if chosen:
                final_path.write_text(chosen, encoding="utf-8")

            # ── Run log entry ─────────────────────────────────────────────────
            summary = {
                "instance_id":  instance_id,
                "passed":       passed_iter is not None,
                "passed_iter":  passed_iter,
                "iterations":   len(traj_records),
                "cost_usd":     round(inst_cost, 6),
            }
            run_log_f.write(json.dumps(summary) + "\n")
            run_log_f.flush()

            if passed_iter is not None:
                total_passed += 1

            pbar.set_postfix(
                passed=total_passed,
                err=total_errors,
                cost=f"${total_cost:.3f}",
            )

    print(f"\nDone. Passed: {total_passed}/{len(instances)} | Errors: {total_errors}")
    if total_cost > 0:
        print(f"Cost:  ${total_cost:.4f} USD")
    print(f"Log:   {run_log_path}")
