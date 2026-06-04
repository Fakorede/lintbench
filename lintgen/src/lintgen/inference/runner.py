"""
lintgen.inference.runner — generation runner with optional RAG augmentation.

Delegates to lintbench.inference for prompting and API calls; this module
adds the RAG layer on top and writes outputs in the same layout as lintbench
so run_eval_all.sh can evaluate them unchanged.
"""

from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

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
    # lintbench must be on sys.path (it is when installed as a workspace member).
    try:
        from lintbench.inference.prompts import build_prompt, extract_code
        from lintbench.inference.providers import detect_provider, generate_sample, compute_cost
    except ImportError:
        raise SystemExit(
            "lintbench package not found. "
            "Make sure you're running from the repo root and the workspace is synced."
        )

    # ── Resolve prompt variant ────────────────────────────────────────────────
    lintbench_prompt = args.prompt if args.prompt != "api_hint_rag" else "api_hint"

    run_id   = time.strftime("%Y%m%dT%H%M%SZ", time.gmtime())
    out_root = Path(args.out) / run_id / args.model / args.prompt
    out_root.mkdir(parents=True, exist_ok=True)
    log_path = out_root / "generation_log.jsonl"

    provider    = detect_provider(args.model)
    temperature = 0.0

    print(
        f"LintGen generate: {len(instances)} instances | model={args.model} | "
        f"prompt={args.prompt} | rag={'on' if kb else 'off'}"
    )
    print(f"Output: {out_root}\n")

    completed = errors = 0

    with open(log_path, "a") as log_f, \
         tqdm(total=len(instances), unit="instance") as pbar:
        for inst in instances:
            instance_id = inst["instance_id"]
            ext         = inst["check_lang"]
            inst_dir    = out_root / instance_id
            inst_dir.mkdir(parents=True, exist_ok=True)
            out_file = inst_dir / f"sample_0.{ext}"

            system, user = build_prompt(inst, lintbench_prompt)

            # Inject RAG context into the user prompt
            if kb is not None:
                rag_context = kb.format_context(inst)
                if rag_context:
                    user = f"{rag_context}\n\n{user}"

            log_record: dict = {
                "instance_id": instance_id,
                "model":       args.model,
                "prompt":      args.prompt,
                "rag":         kb is not None,
                "timestamp":   time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            }

            try:
                raw, usage, _ = generate_sample(
                    model=args.model,
                    provider=provider,
                    system=system,
                    user=user,
                    temperature=temperature,
                    max_tokens=32768,
                )
                code = extract_code(raw, ext)
                out_file.write_text(code, encoding="utf-8")
                log_record["success"]      = True
                log_record["output_chars"] = len(code)
                log_record["usage"]        = usage
                completed += 1
            except Exception as exc:
                log_record["success"] = False
                log_record["error"]   = str(exc)
                errors += 1
                tqdm.write(f"ERROR [{instance_id}]: {exc}", file=sys.stderr)

            log_f.write(json.dumps(log_record) + "\n")
            log_f.flush()
            pbar.set_postfix(done=completed, err=errors)
            pbar.update(1)

    print(f"\nDone. Completed: {completed} | Errors: {errors}")
    print(f"Log: {log_path}")
