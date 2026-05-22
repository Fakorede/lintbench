#!/usr/bin/env python3
"""
generate — LintBench model inferencing
---------------------------------------
Sends benchmark instances to an LLM API and saves the generated detector
files in the layout expected by the eval module.

USAGE
─────────────────────────────────────────────────────────────────────────
# Run from lint_benchmark/ root:

# Pass@1 (single sample per instance, greedy):
python -m generate \\
    --benchmark  data/lintbench.json \\
    --model      gpt-4o \\
    --prompt     zero_shot \\
    --out        generated/

# Pass@k (5 samples per instance, temperature > 0):
python -m generate \\
    --benchmark  data/lintbench.json \\
    --model      gpt-4o \\
    --prompt     zero_shot \\
    --out        generated/ \\
    --samples    5 \\
    --temperature 0.8

# OpenRouter (access any model with one key):
python -m generate \\
    --model      openai/gpt-4o \\
    --provider   openrouter

# Restrict to one split or cap instances:
python -m generate --split easy --limit 20 ...

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
  zero_shot   NL spec only, no examples
  few_shot    NL spec + one worked example in the same language
  cot         NL spec + chain-of-thought instruction before code

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

from .prompts import build_prompt, extract_code
from .providers import detect_provider, generate_sample

# Load .env from the project root (two levels up from generate/)
load_dotenv(Path(__file__).resolve().parent.parent.parent / ".env")


def main(args: argparse.Namespace) -> None:
    benchmark_path = Path(args.benchmark)
    if not benchmark_path.exists():
        raise SystemExit(f"ERROR: benchmark not found: {benchmark_path}")

    data = json.loads(benchmark_path.read_text())

    splits = [args.split] if args.split else ["easy", "medium", "hard"]
    instances = [
        inst
        for split in splits
        for inst in data["splits"].get(split, [])
    ]
    if args.limit:
        instances = instances[:args.limit]

    out_root = Path(args.out) / args.model / args.prompt
    out_root.mkdir(parents=True, exist_ok=True)
    log_path = out_root / "generation_log.jsonl"

    provider = args.provider or detect_provider(args.model, args.api_base)
    api_base = args.api_base or None

    temperature = args.temperature
    if args.samples == 1 and temperature is None:
        temperature = 0.0
    elif temperature is None:
        temperature = 0.8

    print(f"Generating {len(instances)} instances | "
          f"model={args.model} | provider={provider} | "
          f"prompt={args.prompt} | samples={args.samples} | "
          f"temperature={temperature}")
    print(f"Output: {out_root}")
    print()

    completed = skipped = errors = 0

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
                    raw, usage = generate_sample(
                        model=args.model,
                        provider=provider,
                        system=system,
                        user=user,
                        temperature=temperature,
                        max_tokens=args.max_tokens,
                        api_base=api_base,
                    )
                    code = extract_code(raw, ext)
                    out_file.write_text(code, encoding="utf-8")

                    log_record["success"]      = True
                    log_record["output_chars"] = len(code)
                    log_record["output_file"]  = str(out_file)
                    log_record["usage"]        = usage
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
    print(f"Log: {log_path}")
    print(f"\nNext step:")
    print(f"  python -m eval \\")
    print(f"    --benchmark {args.benchmark} \\")
    print(f"    --generated {out_root} \\")
    print(f"    --model     {args.model} \\")
    print(f"    --prompt    {args.prompt} \\")
    print(f"    --out       results/{args.model}_{args.prompt}.json \\")
    print(f"    --build-env build_env/run.sh \\")
    print(f"    --samples   {args.samples}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--benchmark",   default="data/lintbench.json",
                        help="Path to benchmark JSON file")
    parser.add_argument("--model",       required=True,
                        help="Model name (e.g. gpt-4o, claude-sonnet-4-5, openai/gpt-4o)")
    parser.add_argument("--provider",    default=None,
                        choices=["openai", "anthropic", "google", "openrouter"],
                        help="Force provider (auto-detected from model name if omitted)")
    parser.add_argument("--prompt",      default="zero_shot",
                        choices=["zero_shot", "few_shot", "cot"],
                        help="Prompt variant (default: zero_shot)")
    parser.add_argument("--out",         default="generated",
                        help="Root output directory (default: generated/)")
    parser.add_argument("--samples",     type=int, default=1,
                        help="Samples per instance for pass@k (default: 1)")
    parser.add_argument("--temperature", type=float, default=None,
                        help="Sampling temperature. Default: 0.0 for k=1, 0.8 for k>1")
    parser.add_argument("--max-tokens",  type=int, default=4096,
                        help="Max output tokens (default: 4096)")
    parser.add_argument("--split",       choices=["easy", "medium", "hard"],
                        help="Restrict to one difficulty split")
    parser.add_argument("--limit",       type=int, default=None,
                        help="Cap number of instances (for testing)")
    parser.add_argument("--delay",       type=float, default=0.5,
                        help="Seconds to wait between API calls (default: 0.5)")
    parser.add_argument("--api-base",    default=None,
                        help="Custom API base URL for local/compatible endpoints")
    parser.add_argument("--force",       action="store_true",
                        help="Regenerate even if output file already exists")
    main(parser.parse_args())
