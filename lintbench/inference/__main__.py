#!/usr/bin/env python3
"""
inference — LintBench model inferencing
----------------------------------------
Sends benchmark instances to an LLM API and saves generated detector files
in the layout expected by the eval module.

USAGE
─────────────────────────────────────────────────────────────────────────
# Run from lintbench/ root:

# Pass@1 (greedy):
python -m inference \\
    --model      claude-sonnet-4-5 \\
    --prompt     zero_shot \\
    --out        generated/

# Pass@k (5 independent samples, temperature > 0):
python -m inference \\
    --model      gpt-4o \\
    --prompt     zero_shot \\
    --samples    5 \\
    --temperature 0.8 \\
    --out        generated/

# OpenRouter (any model with one key):
python -m inference \\
    --model    meta-llama/llama-3.3-70b-instruct \\
    --provider openrouter \\
    --out      generated/

# Restrict to one split or cap instances:
python -m inference --model gpt-4o --split easy --limit 10 --out generated/

# Compile-repair pass (compile_repair_1):
#   After running eval, feed compile failures back for one repair round.
python -m inference \\
    --model       claude-sonnet-4-5 \\
    --repair-from results/sonnet_zero_shot.json \\
    --generated   generated/claude-sonnet-4-5/zero_shot/ \\
    --out         generated/

OUTPUT LAYOUT
─────────────────────────────────────────────────────────────────────────
generated/
  <model>/<prompt>/
    <instance_id>/
      sample_0.<kt|java>
      sample_1.<kt|java>
      ...
    generation_log.jsonl     one record per API call (for auditing)

PROMPT VARIANTS
─────────────────────────────────────────────────────────────────────────
  zero_shot                  NL spec only (baseline)
  structured_zero_shot       zero_shot + strict output contract
  api_hint                   zero_shot + scanner-interface API guidance
  skeleton                   zero_shot + pre-filled class skeleton
  few_shot                   NL spec + one worked example (language-matched)
  few_shot_surface_matched   few_shot with example matched to scanner type
  few_shot_surface_matched_cot  few_shot_surface_matched + CoT

COMPILE REPAIR (compile_repair_1)
─────────────────────────────────────────────────────────────────────────
  --repair-from RESULTS_JSON   path to eval results JSON
  --generated   GENERATED_DIR  directory containing the original samples

  Reads instances with failure_mode=compilation_failed, sends the original code
  + compiler errors back to the model, overwrites the sample file in-place.
  Logged separately in generation_log.jsonl with "repair_round": 1.

SUPPORTED PROVIDERS & MODELS
─────────────────────────────────────────────────────────────────────────
  openai      gpt-4o, gpt-4o-mini, gpt-4-turbo, o1, o3-mini, ...
  anthropic   claude-opus-4-5, claude-sonnet-4-5, claude-haiku-4-5, ...
  google      gemini-2.5-flash, gemini-2.5-pro, ...
  openrouter  openai/gpt-4o, anthropic/claude-opus-4-5,
              meta-llama/llama-3.3-70b-instruct, ... (any openrouter.ai model)
  (local)     any OpenAI-compatible endpoint via --api-base + --api-key

API KEYS
─────────────────────────────────────────────────────────────────────────
Set via environment variables:
  OPENAI_API_KEY
  ANTHROPIC_API_KEY
  GOOGLE_API_KEY
  OPENROUTER_API_KEY
"""

import argparse
import json
import sys
import time
from pathlib import Path

from dotenv import load_dotenv
from tqdm import tqdm

from .prompts import ALL_VARIANTS, build_prompt, build_repair_prompt, extract_code, stub_detector
from .providers import compute_cost, detect_provider, generate_sample

# Load .env from the project root (two levels up from inference/)
load_dotenv(Path(__file__).resolve().parent.parent.parent / ".env")


# ---------------------------------------------------------------------------
# Standard generation
# ---------------------------------------------------------------------------

def run_generation(args: argparse.Namespace) -> None:
    benchmark_path = Path(args.benchmark)
    if not benchmark_path.exists():
        raise SystemExit(f"ERROR: benchmark not found: {benchmark_path}")

    if benchmark_path.suffix == ".jsonl":
        instances = [
            json.loads(line) for line in benchmark_path.read_text().splitlines() if line.strip()
        ]
        if args.split:
            instances = [i for i in instances if i.get("benchmark_split") == args.split]
    else:
        data = json.loads(benchmark_path.read_text())
        splits = [args.split] if args.split else ["easy", "medium", "hard"]
        instances = [
            inst
            for split in splits
            for inst in data["splits"].get(split, [])
        ]

    if args.instance_id:
        id_filter = set(args.instance_id)
        instances = [i for i in instances if i["instance_id"] in id_filter]
    if args.limit:
        instances = instances[: args.limit]

    run_id   = args.run_id or time.strftime("%Y%m%dT%H%M%SZ", time.gmtime())
    out_root = Path(args.out) / run_id / args.model / args.prompt
    out_root.mkdir(parents=True, exist_ok=True)
    log_path = out_root / "generation_log.jsonl"

    provider        = args.provider or detect_provider(args.model, args.api_base)
    api_base        = args.api_base or None
    thinking_budget = args.thinking_budget or None
    temperature     = args.temperature
    if args.samples == 1 and temperature is None:
        temperature = 0.0
    elif temperature is None:
        temperature = 0.8

    print(
        f"Generating {len(instances)} instances | "
        f"model={args.model} | provider={provider} | "
        f"prompt={args.prompt} | samples={args.samples} | "
        f"temperature={temperature}"
        + (f" | thinking_budget={thinking_budget}" if thinking_budget else "")
    )
    print(f"Run ID : {run_id}")
    print(f"Output : {out_root}\n")

    completed = skipped = errors = 0
    total_cost_usd: float = 0.0
    total_calls = len(instances) * args.samples

    with open(log_path, "a") as log_f, \
         tqdm(total=total_calls, unit="sample", desc="generating") as pbar:
        for inst in instances:
            instance_id = inst["instance_id"]
            ext         = inst["check_lang"]
            inst_dir    = out_root / instance_id
            inst_dir.mkdir(parents=True, exist_ok=True)

            system, user = build_prompt(inst, args.prompt)

            for sample_id in range(args.samples):
                out_file = inst_dir / f"sample_{sample_id}.{ext}"

                if out_file.exists() and not args.force:
                    skipped += 1
                    pbar.update(1)
                    continue

                log_record: dict = {
                    "instance_id": instance_id,
                    "sample_id":   sample_id,
                    "model":       args.model,
                    "prompt":      args.prompt,
                    "timestamp":   time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                }

                try:
                    if args.stub:
                        code      = stub_detector(inst)
                        usage     = {"input_tokens": 0, "output_tokens": 0}
                        reasoning = None
                    else:
                        raw, usage, reasoning = generate_sample(
                            model=args.model,
                            provider=provider,
                            system=system,
                            user=user,
                            temperature=temperature,
                            max_tokens=args.max_tokens,
                            api_base=api_base,
                            thinking_budget=thinking_budget,
                        )
                        code = extract_code(raw, ext)

                    out_file.write_text(code, encoding="utf-8")
                    log_record["success"]      = True
                    log_record["output_chars"] = len(code)
                    log_record["output_file"]  = str(out_file)
                    log_record["usage"]        = usage
                    if reasoning is not None:
                        log_record["reasoning_content"] = reasoning
                        # Also dump to a readable file for per-sample inspection.
                        (inst_dir / f"sample_{sample_id}_thinking.txt").write_text(
                            reasoning, encoding="utf-8"
                        )
                    cost = compute_cost(args.model, usage["input_tokens"], usage["output_tokens"])
                    if cost is not None:
                        log_record["cost_usd"] = round(cost, 6)
                        total_cost_usd += cost
                    completed += 1

                except Exception as e:
                    log_record["success"] = False
                    log_record["error"]   = str(e)
                    errors += 1
                    tqdm.write(f"ERROR [{instance_id} s{sample_id}]: {e}", file=sys.stderr)
                    if "rate" in str(e).lower() or "429" in str(e):
                        wait = min(60, 5 * errors)
                        tqdm.write(f"Rate limited — waiting {wait}s", file=sys.stderr)
                        time.sleep(wait)

                log_f.write(json.dumps(log_record) + "\n")
                log_f.flush()
                pbar.set_postfix(done=completed, skip=skipped, err=errors)
                pbar.update(1)

                if args.delay > 0:
                    time.sleep(args.delay)

    print(f"\nDone. Generated: {completed} | Skipped: {skipped} | Errors: {errors}")
    if total_cost_usd > 0:
        print(f"Cost:  ${total_cost_usd:.4f} USD")
    print(f"Log: {log_path}")
    print(f"\nNext step:")
    print(f"  python run_eval.py \\")
    print(f"    --generated {out_root} \\")
    print(f"    --model     {args.model} \\")
    print(f"    --prompt    {args.prompt} \\")
    print(f"    --out       results/{args.model}_{args.prompt}.json \\")
    print(f"    --build-env build_env/run.sh \\")
    print(f"    --samples   {args.samples}")


# ---------------------------------------------------------------------------
# Compile-repair pass (compile_repair_1)
# ---------------------------------------------------------------------------

def run_repair(args: argparse.Namespace) -> None:
    """
    Compile-repair pass: for every instance with failure_mode=compilation_failed
    in the eval results, load the original generated file, send it back with the
    compiler errors, and write the repaired file to a separate output directory.

    Output layout:
      <generated>_repair1/
        <instance_id>/sample_N.<ext>   ← repaired files only
        generation_log.jsonl
    """
    results_path   = Path(args.repair_from)
    generated_root = Path(args.generated)

    if not results_path.exists():
        raise SystemExit(f"ERROR: results file not found: {results_path}")
    if not generated_root.exists():
        raise SystemExit(f"ERROR: generated directory not found: {generated_root}")

    results = json.loads(results_path.read_text())
    instances_results = results.get("instances", [])

    # Load dataset for instance metadata
    dataset_path = Path(args.benchmark)
    if not dataset_path.exists():
        raise SystemExit(f"ERROR: benchmark not found: {dataset_path}")
    dataset: dict[str, dict] = {}
    for line in dataset_path.read_text().splitlines():
        if line.strip():
            inst = json.loads(line)
            dataset[inst["instance_id"]] = inst

    # Collect repair targets: instances with at least one compile-failed sample
    repair_targets: list[tuple[dict, int, list[str], Path]] = []
    instance_filter = set(args.instance) if args.instance else None
    for inst_result in instances_results:
        iid = inst_result.get("instance_id", "")
        if iid not in dataset:
            continue
        if instance_filter and iid not in instance_filter:
            continue
        for sample in inst_result.get("samples", []):
            if sample.get("failure_mode") != "compilation_failed":
                continue
            sample_id      = sample.get("sample_id", 0)
            compile_errors = sample.get("compile_errors", [])
            ext            = dataset[iid]["check_lang"]
            src_file = generated_root / iid / f"sample_{sample_id}.{ext}"
            if not src_file.exists():
                tqdm.write(f"WARN: sample file not found: {src_file}", file=sys.stderr)
                continue
            repair_targets.append((dataset[iid], sample_id, compile_errors, src_file))

    if not repair_targets:
        print("No compilation failures found in results — nothing to repair.")
        return

    provider        = args.provider or detect_provider(args.model, args.api_base)
    api_base        = args.api_base or None
    thinking_budget = args.thinking_budget or None
    temperature     = args.temperature if args.temperature is not None else 0.0

    # Output directory: <generated>_repair1/ (sibling of original, never overwrites originals)
    repair_root = Path(str(generated_root).rstrip("/") + "_repair1")
    repair_root.mkdir(parents=True, exist_ok=True)
    log_path = repair_root / "generation_log.jsonl"

    print(
        f"Compile repair: {len(repair_targets)} samples | "
        f"model={args.model} | provider={provider} | temperature={temperature}"
    )
    print(f"Results source: {results_path}")
    print(f"Original files: {generated_root}")
    print(f"Repaired files: {repair_root}\n")

    completed = errors = 0
    total_cost_usd: float = 0.0

    with open(log_path, "a") as log_f, \
         tqdm(total=len(repair_targets), unit="sample", desc="repairing") as pbar:
        for inst, sample_id, compile_errors, src_file in repair_targets:
            instance_id   = inst["instance_id"]
            ext           = inst["check_lang"]
            original_code = src_file.read_text(encoding="utf-8")

            out_file = repair_root / instance_id / f"sample_{sample_id}.{ext}"
            out_file.parent.mkdir(parents=True, exist_ok=True)

            system, user = build_repair_prompt(inst, original_code, compile_errors)

            log_record: dict = {
                "instance_id":  instance_id,
                "sample_id":    sample_id,
                "model":        args.model,
                "prompt":       "compile_repair_1",
                "repair_round": 1,
                "timestamp":    time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            }

            try:
                raw, usage, reasoning = generate_sample(
                    model=args.model,
                    provider=provider,
                    system=system,
                    user=user,
                    temperature=temperature,
                    max_tokens=args.max_tokens,
                    api_base=api_base,
                    thinking_budget=thinking_budget,
                )
                code = extract_code(raw, ext)
                out_file.write_text(code, encoding="utf-8")

                log_record["success"]      = True
                log_record["output_chars"] = len(code)
                log_record["output_file"]  = str(out_file)
                log_record["usage"]        = usage
                if reasoning is not None:
                    log_record["reasoning_content"] = reasoning
                cost = compute_cost(args.model, usage["input_tokens"], usage["output_tokens"])
                if cost is not None:
                    log_record["cost_usd"] = round(cost, 6)
                    total_cost_usd += cost
                completed += 1

            except Exception as e:
                log_record["success"] = False
                log_record["error"]   = str(e)
                errors += 1
                tqdm.write(f"ERROR [{instance_id} s{sample_id}]: {e}", file=sys.stderr)
                if "rate" in str(e).lower() or "429" in str(e):
                    wait = min(60, 5 * errors)
                    tqdm.write(f"Rate limited — waiting {wait}s", file=sys.stderr)
                    time.sleep(wait)

            log_f.write(json.dumps(log_record) + "\n")
            log_f.flush()
            pbar.set_postfix(done=completed, err=errors)
            pbar.update(1)

            if args.delay > 0:
                time.sleep(args.delay)

    print(f"\nRepair done. Fixed: {completed} | Errors: {errors}")
    if total_cost_usd > 0:
        print(f"Cost:  ${total_cost_usd:.4f} USD")
    print(f"Log: {log_path}")
    print(f"\nRe-run eval on the repair directory to measure compile_repair_1 pass rate:")
    print(f"  python run_eval.py \\")
    print(f"    --generated {repair_root} \\")
    print(f"    --model     {args.model} \\")
    print(f"    --out       results/... \\")


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--benchmark", default="data/dataset.jsonl",
        help="Path to benchmark JSONL (default: data/dataset.jsonl)",
    )
    parser.add_argument(
        "--model", required=True,
        help="Model name (e.g. gpt-4o, claude-sonnet-4-5, openai/gpt-4o)",
    )
    parser.add_argument(
        "--provider", default=None,
        choices=["openai", "anthropic", "google", "openrouter"],
        help="Force provider (auto-detected from model name if omitted)",
    )
    parser.add_argument(
        "--prompt", default="zero_shot",
        choices=ALL_VARIANTS,
        help=f"Prompt variant (default: zero_shot)",
    )
    parser.add_argument(
        "--out", default="generated",
        help="Root output directory (default: generated/)",
    )
    parser.add_argument(
        "--run-id", default=None,
        help="Run identifier inserted between --out and model path "
             "(e.g. 'run_001'). Auto-generates a UTC timestamp if omitted.",
    )
    parser.add_argument(
        "--samples", type=int, default=1,
        help="Samples per instance for pass@k (default: 1)",
    )
    parser.add_argument(
        "--temperature", type=float, default=None,
        help="Sampling temperature. Default: 0.0 for k=1, 0.8 for k>1",
    )
    parser.add_argument(
        "--max-tokens", type=int, default=4096,
        help="Max output tokens (default: 4096)",
    )
    parser.add_argument(
        "--thinking-budget", type=int, default=None,
        help=(
            "Enable extended thinking with this token budget. "
            "For anthropic/* models via OpenRouter, forces temperature=1 as required. "
            "For openai/* reasoning models, thinking is always on; this arg is ignored."
        ),
    )
    parser.add_argument(
        "--instance-id", action="append", default=None, metavar="INSTANCE_ID",
        help="Run only this instance. Repeatable: --instance-id A --instance-id B",
    )
    parser.add_argument(
        "--split", choices=["easy", "medium", "hard"],
        help="Restrict to one difficulty split",
    )
    parser.add_argument(
        "--limit", type=int, default=None,
        help="Cap number of instances (for testing)",
    )
    parser.add_argument(
        "--delay", type=float, default=0.5,
        help="Seconds between API calls (default: 0.5)",
    )
    parser.add_argument(
        "--api-base", default=None,
        help="Custom API base URL for local/compatible endpoints",
    )
    parser.add_argument(
        "--force", action="store_true",
        help="Regenerate even if output file already exists",
    )
    parser.add_argument(
        "--stub", action="store_true",
        help="Write minimal placeholder detectors instead of calling APIs (smoke testing)",
    )

    # compile_repair_1 mode
    repair_group = parser.add_argument_group("compile_repair_1 mode")
    repair_group.add_argument(
        "--repair-from", default=None, metavar="RESULTS_JSON",
        help="Eval results JSON. When set, runs compile-repair instead of normal generation.",
    )
    repair_group.add_argument(
        "--generated", default=None, metavar="GENERATED_DIR",
        help="Directory containing original generated samples (required with --repair-from)",
    )
    repair_group.add_argument(
        "--instance", action="append", default=None, metavar="INSTANCE_ID",
        help="Restrict repair to specific instance(s). Repeatable: --instance A --instance B",
    )

    args = parser.parse_args()

    if args.repair_from:
        if not args.generated:
            parser.error("--repair-from requires --generated <path-to-generated-samples-dir>")
        run_repair(args)
    else:
        run_generation(args)


if __name__ == "__main__":
    main()
