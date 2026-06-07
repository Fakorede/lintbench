"""
lintgen.cli — command-line entry point.

Usage:
  lintgen generate --instance <id> --model <model> --prompt api_hint_rag
  lintgen build-index
  lintgen eval --generated <dir>
"""

from __future__ import annotations

import argparse
import sys


def main() -> None:
    parser = argparse.ArgumentParser(
        prog="lintgen",
        description="LintGen: RAG-augmented Android Lint detector generation",
    )
    sub = parser.add_subparsers(dest="command", required=True)

    # ── generate ──────────────────────────────────────────────────────────────
    gen = sub.add_parser("generate", help="Run RAG-augmented inference on benchmark instances")
    gen.add_argument("--benchmark",   default="../lintbench/data/dataset.jsonl")
    gen.add_argument("--model",       required=True,
                     help="Model name (e.g. anthropic/claude-sonnet-4.6, "
                          "openai/gpt-4o, meta-llama/llama-3.3-70b-instruct). "
                          "provider/model syntax is routed via OpenRouter automatically.")
    gen.add_argument("--provider",    default=None,
                     choices=["openai", "anthropic", "google", "openrouter"],
                     help="Force provider (auto-detected from model name if omitted). "
                          "Use 'openrouter' to access any model with a single OPENROUTER_API_KEY.")
    gen.add_argument("--prompt",      default="api_hint_rag",
                     choices=["api_hint_rag", "zero_shot", "skeleton",
                               "few_shot_surface_matched"])
    gen.add_argument("--out",         default="generated/")
    gen.add_argument("--run-id",      default=None,
                     help="Run identifier inserted into the output path. "
                          "Auto-generates a UTC timestamp if omitted.")
    gen.add_argument("--samples",     type=int, default=1,
                     help="Samples per instance for pass@k (default: 1)")
    gen.add_argument("--temperature", type=float, default=None,
                     help="Sampling temperature. Default: 0.0 for k=1, 0.8 for k>1")
    gen.add_argument("--max-tokens",  type=int, default=32768)
    gen.add_argument("--thinking-budget", type=int, default=None,
                     help="Enable extended thinking (OpenRouter reasoning models). "
                          "Sets budget_tokens; forces temperature=1 for Anthropic models.")
    gen.add_argument("--split",       choices=["easy", "hard"], default=None)
    gen.add_argument("--limit",       type=int, default=None)
    gen.add_argument("--instance-id", action="append", dest="instance_ids")
    gen.add_argument("--delay",       type=float, default=0.5,
                     help="Seconds between API calls (default: 0.5)")
    gen.add_argument("--api-base",    default=None,
                     help="Custom API base URL (local vLLM or other OpenAI-compatible endpoint)")
    gen.add_argument("--force",       action="store_true",
                     help="Regenerate even if output file already exists")
    gen.add_argument("--no-rag",      action="store_true",
                     help="Disable RAG retrieval (ablation baseline)")

    # ── build-index ───────────────────────────────────────────────────────────
    idx = sub.add_parser("build-index", help="Build / rebuild the Lint API FAISS index")
    idx.add_argument("--docs-dir",     default=None,
                     help="Path to android-custom-lint-rules/docs/ for Tier 3 guide chunking "
                          "(default: auto-detected)")
    idx.add_argument("--intellij-dir", default=None,
                     help="Path to intellij-community checkout for Tier 4 UAST/PSI extraction "
                          "(default: ../intellij-community)")
    idx.add_argument("--common-jar",   default=None,
                     help="Path to common-31.7.0.jar for Tier 5 SdkConstants extraction "
                          "(default: ~/.gradle/caches/.../common-31.7.0.jar)")
    idx.add_argument("--out-dir",      default="src/lintgen/rag/index/")

    # ── eval ──────────────────────────────────────────────────────────────────
    ev = sub.add_parser("eval", help="Evaluate generated detectors")
    ev.add_argument("--generated", required=True)
    ev.add_argument("--benchmark", default="../lintbench/data/dataset.jsonl")
    ev.add_argument("--out",       default="results/")

    args = parser.parse_args()

    if args.command == "generate":
        from lintgen.inference.runner import run_generation
        run_generation(args)
    elif args.command == "build-index":
        from lintgen.rag.build_corpus import build_index
        build_index(args)
    elif args.command == "eval":
        from lintgen.eval.runner import run_eval
        run_eval(args)
    else:
        parser.print_help()
        sys.exit(1)
