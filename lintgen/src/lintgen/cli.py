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
    gen.add_argument("--benchmark",  default="../lintbench/data/dataset.jsonl")
    gen.add_argument("--model",      required=True, help="Model name or HPC served-model-name")
    gen.add_argument("--prompt",     default="api_hint_rag",
                     choices=["api_hint_rag", "zero_shot", "api_hint", "skeleton",
                               "few_shot_surface_matched"])
    gen.add_argument("--out",        default="generated/")
    gen.add_argument("--limit",      type=int, default=None)
    gen.add_argument("--instance-id", action="append", dest="instance_ids")
    gen.add_argument("--no-rag",     action="store_true", help="Disable RAG (ablation baseline)")

    # ── build-index ───────────────────────────────────────────────────────────
    idx = sub.add_parser("build-index", help="Build / rebuild the Lint API FAISS index")
    idx.add_argument("--source",  default="../android-custom-lint-rules/",
                     help="Path to android-custom-lint-rules source for Tier 2 auto-construction")
    idx.add_argument("--out-dir", default="src/lintgen/rag/index/")

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
