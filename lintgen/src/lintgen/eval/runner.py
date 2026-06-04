"""
lintgen.eval.runner — delegates evaluation to lintbench's run_eval_all.sh.

LintGen outputs the same generated/ layout as lintbench, so the existing
eval pipeline works unchanged. This module is a thin subprocess wrapper
so `lintgen eval` stays a single command.
"""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path


def run_eval(args: argparse.Namespace) -> None:
    lintbench_root = Path(__file__).resolve().parents[6] / "lintbench"
    eval_script    = lintbench_root / "run_eval_all.sh"

    if not eval_script.exists():
        raise SystemExit(
            f"run_eval_all.sh not found at {eval_script}. "
            "Make sure lintbench/ is at the repo root."
        )

    cmd = [
        "bash", str(eval_script),
        "--generated", args.generated,
        "--benchmark", args.benchmark,
        "--results",   args.out,
    ]

    print("Running:", " ".join(cmd))
    result = subprocess.run(cmd, cwd=str(lintbench_root))
    sys.exit(result.returncode)
