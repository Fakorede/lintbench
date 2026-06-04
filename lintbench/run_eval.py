#!/usr/bin/env python3
"""
run_eval.py — LintBench evaluation runner entry point.

Thin shim that invokes the eval package. All logic lives in eval/__main__.py.

Run from lintbench/ root:

    python run_eval.py \\
        --benchmark data/lintbench.json \\
        --generated generated/gpt-4o/zero_shot/ \\
        --model gpt-4o --prompt zero_shot \\
        --out results/gpt4o_zero_shot.json \\
        --build-env build_env/run.sh

See python run_eval.py --help for full usage.
"""
import runpy
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
runpy.run_module("eval", run_name="__main__", alter_sys=True)
