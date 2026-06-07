"""
lintgen.inference.runner — generation runner with optional RAG augmentation.

Delegates to lintbench.inference for prompting and API calls; this module
adds the RAG layer on top and writes outputs in the same layout as lintbench
so run_eval_all.sh can evaluate them unchanged.

Supported providers (auto-detected from model name):
  openrouter  provider/model syntax — e.g. anthropic/claude-sonnet-4.6,
              meta-llama/llama-3.3-70b-instruct, openai/gpt-4o
              Requires: OPENROUTER_API_KEY
  openai      Requires: OPENAI_API_KEY
  anthropic   Requires: ANTHROPIC_API_KEY
  google      Requires: GOOGLE_API_KEY
  (local)     Any OpenAI-compatible endpoint via --api-base
"""

from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

# Ensure repo root is on sys.path so `lintbench` is importable regardless of
# which Python interpreter is active. runner.py is at
# lintgen/src/lintgen/inference/runner.py → parents[4] = repo root.
_REPO_ROOT = Path(__file__).resolve().parents[3]
if str(_REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(_REPO_ROOT))

from tqdm import tqdm


def run_generation(args: argparse.Namespace) -> None:
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

    # ── Load RAG knowledge base (optional) ───────────────────────────────────
    kb = None
    if args.prompt == "api_hint_rag" and not getattr(args, "no_rag", False):
        try:
            from lintgen.rag import get_knowledge_base
            kb = get_knowledge_base()
            print("RAG knowledge base loaded.")
        except FileNotFoundError as e:
            print(f"WARNING: {e}\nFalling back to static api_hint.", file=sys.stderr)
            args.prompt = "api_hint"

    # ── Import lintbench inference primitives ─────────────────────────────────
    try:
        from lintbench.inference.prompts import build_prompt, extract_code
        from lintbench.inference.providers import (
            detect_provider, generate_sample, compute_cost,
        )
    except ImportError:
        raise SystemExit(
            "lintbench package not found. "
            "Make sure you're running from the repo root and the workspace is synced."
        )

    # ── Provider + sampling ───────────────────────────────────────────────────
    provider        = args.provider or detect_provider(args.model, getattr(args, "api_base", None))
    api_base        = getattr(args, "api_base", None)
    thinking_budget = getattr(args, "thinking_budget", None)
    samples         = getattr(args, "samples", 1)

    temperature = getattr(args, "temperature", None)
    if samples == 1 and temperature is None:
        temperature = 0.0
    elif temperature is None:
        temperature = 0.8

    max_tokens = getattr(args, "max_tokens", 32768)
    delay      = getattr(args, "delay", 0.5)
    force      = getattr(args, "force", False)

    # ── Output layout ─────────────────────────────────────────────────────────
    lintbench_prompt = args.prompt if args.prompt != "api_hint_rag" else "api_hint"
    run_id   = getattr(args, "run_id", None) or time.strftime("%Y%m%dT%H%M%SZ", time.gmtime())
    out_root = Path(args.out) / run_id / args.model / args.prompt
    out_root.mkdir(parents=True, exist_ok=True)
    log_path = out_root / "generation_log.jsonl"

    print(
        f"LintGen generate: {len(instances)} instances | model={args.model} | "
        f"provider={provider} | prompt={args.prompt} | samples={samples} | "
        f"temperature={temperature} | rag={'on' if kb else 'off'}"
        + (f" | thinking_budget={thinking_budget}" if thinking_budget else "")
    )
    print(f"Run ID : {run_id}")
    print(f"Output : {out_root}\n")

    completed = skipped = errors = 0
    total_cost_usd = 0.0
    total_calls = len(instances) * samples

    with open(log_path, "a") as log_f, \
         tqdm(total=total_calls, unit="sample", desc="generating") as pbar:
        for inst in instances:
            instance_id = inst["instance_id"]
            ext         = inst["check_lang"]
            inst_dir    = out_root / instance_id
            inst_dir.mkdir(parents=True, exist_ok=True)

            system, user = build_prompt(inst, lintbench_prompt)

            # Inject RAG context
            if kb is not None:
                rag_context = kb.format_context(inst)
                if rag_context:
                    user = f"{rag_context}\n\n{user}"

            for sample_id in range(samples):
                out_file = inst_dir / f"sample_{sample_id}.{ext}"

                if out_file.exists() and not force:
                    skipped += 1
                    pbar.update(1)
                    continue

                log_record: dict = {
                    "instance_id": instance_id,
                    "sample_id":   sample_id,
                    "model":       args.model,
                    "provider":    provider,
                    "prompt":      args.prompt,
                    "rag":         kb is not None,
                    "timestamp":   time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                }

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
                    code = extract_code(raw, ext)
                    out_file.write_text(code, encoding="utf-8")
                    log_record["success"]      = True
                    log_record["output_chars"] = len(code)
                    log_record["output_file"]  = str(out_file)
                    log_record["usage"]        = usage
                    if reasoning:
                        log_record["reasoning_content"] = reasoning
                    cost = compute_cost(args.model, usage["input_tokens"], usage["output_tokens"])
                    if cost is not None:
                        log_record["cost_usd"] = round(cost, 6)
                        total_cost_usd += cost
                    completed += 1

                except Exception as exc:
                    log_record["success"] = False
                    log_record["error"]   = str(exc)
                    errors += 1
                    tqdm.write(f"ERROR [{instance_id} s{sample_id}]: {exc}", file=sys.stderr)
                    if "rate" in str(exc).lower() or "429" in str(exc):
                        wait = min(60, 5 * errors)
                        tqdm.write(f"Rate limited — waiting {wait}s", file=sys.stderr)
                        time.sleep(wait)

                log_f.write(json.dumps(log_record) + "\n")
                log_f.flush()
                pbar.set_postfix(done=completed, skip=skipped, err=errors)
                pbar.update(1)

                if delay > 0:
                    time.sleep(delay)

    print(f"\nDone. Completed: {completed} | Skipped: {skipped} | Errors: {errors}")
    if total_cost_usd > 0:
        print(f"Cost:  ${total_cost_usd:.4f} USD")
    print(f"Log:   {log_path}")
