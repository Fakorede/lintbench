#!/usr/bin/env python3
"""
hpc_inference.py — programmatic interface for HPC / local-vLLM inference
-------------------------------------------------------------------------
Wraps the lintbench.inference pipeline so you can call it from Python
or a Jupyter notebook without subprocess indirection.

Supported models (--served-model-name in vLLM):
  qwen25-32b             Qwen/Qwen2.5-32B-Instruct                 (instruct)
  llama3.1-8b-instruct   meta-llama/Llama-3.1-8B-Instruct          (instruct)
  deepseek-r1-32b        deepseek-ai/DeepSeek-R1-Distill-Qwen-32B  (reasoning)
  gemma4-26b             google/gemma-4-26B-A4B-it                 (reasoning)

Usage (CLI)
-----------
  # From lintbench/:
  python -m inference.hpc.hpc_inference \\
      --model  qwen3-coder-30b \\
      --host   gpu-node-01 \\
      --port   8000 \\
      --prompt zero_shot

  # Dry-run (stub mode, no GPU needed):
  python -m inference.hpc.hpc_inference --model qwen3-coder-30b --stub

Usage (library)
---------------
  from lintbench.inference.hpc.hpc_inference import HPCInferenceConfig, run_hpc_inference

  cfg = HPCInferenceConfig(model="qwen3-coder-30b", host="gpu-node-01", port=8000)
  run_hpc_inference(cfg)
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

from ..prompts import ALL_VARIANTS, build_prompt, extract_code, stub_detector
from ..providers import compute_cost

try:
    from tqdm import tqdm
except ImportError:
    def tqdm(iterable, **kwargs):  # type: ignore[misc]
        return iterable


# ---------------------------------------------------------------------------
# HPC model registry
# ---------------------------------------------------------------------------

HPC_MODELS: dict[str, dict] = {
    "qwen25-32b": {
        "hf_id":        "Qwen/Qwen2.5-32B-Instruct",
        "default_port":  8000,
        "gpus":          2,
        "slurm_script":  "slurm_vllm_qwen25_32b.sh",
        "reasoning":     False,
    },
    "llama3.1-8b-instruct": {
        "hf_id":        "meta-llama/Llama-3.1-8B-Instruct",
        "default_port":  8001,
        "gpus":          1,
        "slurm_script":  "slurm_vllm_llama3.1_8b.sh",
        "reasoning":     False,
    },
    "deepseek-r1-32b": {
        "hf_id":        "deepseek-ai/DeepSeek-R1-Distill-Qwen-32B",
        "default_port":  8002,
        "gpus":          4,
        "slurm_script":  "slurm_vllm_deepseek_r1_32b.sh",
        "reasoning":     True,
    },
    "gemma4-26b": {
        "hf_id":        "google/gemma-4-26B-A4B-it",
        "default_port":  8003,
        "gpus":          1,
        "slurm_script":  "slurm_vllm_gemma4_26b.sh",
        "reasoning":     True,
    },
}


# ---------------------------------------------------------------------------
# Config dataclass
# ---------------------------------------------------------------------------

@dataclass
class HPCInferenceConfig:
    model:             str
    host:              str             = "localhost"
    port:              int             = 8000
    prompt:            str             = "zero_shot"
    out:               str             = "generated"
    benchmark:         str             = "data/dataset.jsonl"
    run_id:            Optional[str]   = None
    samples:           int             = 1
    temperature:       Optional[float] = None
    max_tokens:        int             = 32768
    limit:             Optional[int]   = None
    split:             Optional[str]   = None
    instance_ids:      list[str]       = field(default_factory=list)
    delay:             float           = 0.2
    force:             bool            = False
    stub:              bool            = False
    capture_reasoning: bool            = True

    @property
    def api_base(self) -> str:
        return f"http://{self.host}:{self.port}/v1"


# ---------------------------------------------------------------------------
# vLLM-specific call (captures reasoning_content via --enable-reasoning)
# ---------------------------------------------------------------------------

def call_vllm(
    model: str,
    api_base: str,
    system: str,
    user: str,
    temperature: float,
    max_tokens: int,
) -> tuple[str, dict, str | None]:
    """
    Call a local vLLM server (OpenAI-compatible).

    For reasoning models launched with --enable-reasoning --reasoning-parser deepseek_r1,
    vLLM strips <think>...</think> from text and returns it as reasoning_content.
    Works for Qwen3-Coder, DeepSeek-R1, and Gemma 4.
    For non-reasoning models (LLaMA 3), reasoning_content will be None.
    """
    try:
        import openai
    except ImportError:
        raise SystemExit("openai package not installed. Run: pip install openai")

    client = openai.OpenAI(
        api_key=os.environ.get("OPENAI_API_KEY", "local"),
        base_url=api_base,
    )
    resp = client.chat.completions.create(
        model=model,
        messages=[
            {"role": "system", "content": system},
            {"role": "user",   "content": user},
        ],
        temperature=temperature,
        max_tokens=max_tokens,
    )
    msg  = resp.choices[0].message
    text = msg.content or ""

    # vLLM reasoning parsers (deepseek_r1, gemma4) return reasoning_content
    # as a non-standard field; the openai SDK surfaces it via model_extra.
    msg_extra = getattr(msg, "model_extra", None) or {}
    reasoning: str | None = (
        getattr(msg, "reasoning_content", None)
        or msg_extra.get("reasoning_content")
        or None
    )

    usage = {
        "input_tokens":  resp.usage.prompt_tokens,
        "output_tokens": resp.usage.completion_tokens,
    }
    return text, usage, reasoning


# ---------------------------------------------------------------------------
# Core runner
# ---------------------------------------------------------------------------

def run_hpc_inference(cfg: HPCInferenceConfig) -> dict:
    """Run inference for all instances and return a summary dict."""
    benchmark_path = Path(cfg.benchmark)
    if not benchmark_path.exists():
        raise FileNotFoundError(f"Benchmark not found: {benchmark_path}")

    if benchmark_path.suffix == ".jsonl":
        instances = [
            json.loads(line)
            for line in benchmark_path.read_text().splitlines()
            if line.strip()
        ]
        if cfg.split:
            instances = [i for i in instances if i.get("benchmark_split") == cfg.split]
    else:
        data = json.loads(benchmark_path.read_text())
        splits = [cfg.split] if cfg.split else ["easy", "medium", "hard"]
        instances = [
            inst
            for split in splits
            for inst in data["splits"].get(split, [])
        ]

    if cfg.instance_ids:
        id_filter = set(cfg.instance_ids)
        instances = [i for i in instances if i["instance_id"] in id_filter]
    if cfg.limit:
        instances = instances[: cfg.limit]

    run_id   = cfg.run_id or time.strftime("%Y%m%dT%H%M%SZ", time.gmtime())
    out_root = Path(cfg.out) / run_id / cfg.model / cfg.prompt
    out_root.mkdir(parents=True, exist_ok=True)
    log_path = out_root / "generation_log.jsonl"

    temperature = cfg.temperature
    if cfg.samples == 1 and temperature is None:
        temperature = 0.0
    elif temperature is None:
        temperature = 0.8

    os.environ.setdefault("OPENAI_API_KEY", "local")

    print(
        f"HPC inference: {len(instances)} instances | model={cfg.model} | "
        f"prompt={cfg.prompt} | samples={cfg.samples} | temperature={temperature}"
    )
    print(f"API Base : {cfg.api_base}")
    print(f"Run ID   : {run_id}\n")

    completed = skipped = errors = 0
    total_cost_usd = 0.0
    total_calls = len(instances) * cfg.samples

    with open(log_path, "a") as log_f, \
         tqdm(total=total_calls, unit="sample", desc="generating") as pbar:
        for inst in instances:
            instance_id = inst["instance_id"]
            ext         = inst["check_lang"]
            inst_dir    = out_root / instance_id
            inst_dir.mkdir(parents=True, exist_ok=True)

            system, user = build_prompt(inst, cfg.prompt)

            for sample_id in range(cfg.samples):
                out_file = inst_dir / f"sample_{sample_id}.{ext}"

                if out_file.exists() and not cfg.force:
                    skipped += 1
                    pbar.update(1)
                    continue

                log_record: dict = {
                    "instance_id": instance_id,
                    "sample_id":   sample_id,
                    "model":       cfg.model,
                    "prompt":      cfg.prompt,
                    "api_base":    cfg.api_base,
                    "timestamp":   time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                }

                try:
                    if cfg.stub:
                        code      = stub_detector(inst)
                        usage     = {"input_tokens": 0, "output_tokens": 0}
                        reasoning = None
                    else:
                        raw, usage, reasoning = call_vllm(
                            model=cfg.model,
                            api_base=cfg.api_base,
                            system=system,
                            user=user,
                            temperature=temperature,
                            max_tokens=cfg.max_tokens,
                        )
                        code = extract_code(raw, ext)

                    out_file.write_text(code, encoding="utf-8")
                    log_record["success"]      = True
                    log_record["output_chars"] = len(code)
                    log_record["output_file"]  = str(out_file)
                    log_record["usage"]        = usage
                    if reasoning and cfg.capture_reasoning:
                        log_record["reasoning_content"] = reasoning
                    cost = compute_cost(cfg.model, usage["input_tokens"], usage["output_tokens"])
                    if cost is not None:
                        log_record["cost_usd"] = round(cost, 6)
                        total_cost_usd += cost
                    completed += 1

                except Exception as exc:
                    log_record["success"] = False
                    log_record["error"]   = str(exc)
                    errors += 1
                    print(f"ERROR [{instance_id} s{sample_id}]: {exc}", file=sys.stderr)

                log_f.write(json.dumps(log_record) + "\n")
                log_f.flush()
                pbar.set_postfix(done=completed, skip=skipped, err=errors)
                pbar.update(1)

                if cfg.delay > 0:
                    time.sleep(cfg.delay)

    print(f"\nDone. Completed: {completed} | Skipped: {skipped} | Errors: {errors}")
    if total_cost_usd > 0:
        print(f"Cost:  ${total_cost_usd:.4f} USD")
    print(f"Log:   {log_path}")

    return {
        "completed": completed,
        "skipped":   skipped,
        "errors":    errors,
        "log_path":  str(log_path),
        "out_root":  str(out_root),
        "run_id":    run_id,
    }


def wait_for_server(host: str, port: int, timeout: int = 300, interval: int = 5) -> bool:
    """Poll the vLLM /health endpoint until it responds or timeout is reached."""
    import urllib.request
    url = f"http://{host}:{port}/health"
    waited = 0
    print(f"Waiting for vLLM server at {url} ...")
    while waited < timeout:
        try:
            urllib.request.urlopen(url, timeout=3)  # noqa: S310
            print(f"Server ready after {waited}s.")
            return True
        except Exception:
            time.sleep(interval)
            waited += interval
            print(f"  ... {waited}s elapsed")
    print(f"ERROR: server not ready after {timeout}s.", file=sys.stderr)
    return False


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--model",       required=True)
    parser.add_argument("--host",        default="localhost")
    parser.add_argument("--port",        type=int, default=8000)
    parser.add_argument("--prompt",      default="zero_shot", choices=ALL_VARIANTS)
    parser.add_argument("--out",         default="generated")
    parser.add_argument("--benchmark",   default="data/dataset.jsonl")
    parser.add_argument("--run-id",      default=None)
    parser.add_argument("--samples",     type=int, default=1)
    parser.add_argument("--temperature", type=float, default=None)
    parser.add_argument("--max-tokens",  type=int, default=32768)
    parser.add_argument("--limit",       type=int, default=None)
    parser.add_argument("--split",       choices=["easy", "medium", "hard"], default=None)
    parser.add_argument("--instance-id", action="append", default=None, dest="instance_ids")
    parser.add_argument("--delay",       type=float, default=0.2)
    parser.add_argument("--force",       action="store_true")
    parser.add_argument("--stub",        action="store_true")
    parser.add_argument("--wait",        action="store_true",
                        help="Poll server health before running")
    parser.add_argument("--no-reasoning", action="store_true",
                        help="Do not capture chain-of-thought reasoning blocks")

    args = parser.parse_args()

    if args.wait:
        if not wait_for_server(args.host, args.port):
            sys.exit(1)

    cfg = HPCInferenceConfig(
        model=args.model,
        host=args.host,
        port=args.port,
        prompt=args.prompt,
        out=args.out,
        benchmark=args.benchmark,
        run_id=args.run_id,
        samples=args.samples,
        temperature=args.temperature,
        max_tokens=args.max_tokens,
        limit=args.limit,
        split=args.split,
        instance_ids=args.instance_ids or [],
        delay=args.delay,
        force=args.force,
        stub=args.stub,
        capture_reasoning=not args.no_reasoning,
    )
    run_hpc_inference(cfg)


if __name__ == "__main__":
    main()
