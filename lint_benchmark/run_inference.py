#!/usr/bin/env python3
"""
run_inference.py — LintBench model inferencing entry point.

Thin shim that invokes the inference package. All logic lives in inference/__main__.py.

Run from lint_benchmark/ root:

    python run_inference.py \\
        --model gpt-4o --prompt zero_shot \\
        --out generated/

See python run_inference.py --help for full usage.
"""
import runpy
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
runpy.run_module("inference", run_name="__main__", alter_sys=True)
