#!/usr/bin/env python3
"""
generate.py — LintBench model inferencing entry point.

Thin shim that invokes the generate package. All logic lives in generate/__main__.py.

Run from lint_benchmark/ root:

    python generate.py \\
        --model gpt-4o --prompt zero_shot \\
        --out generated/

See python generate.py --help for full usage.
"""
import runpy
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
runpy.run_module("generate", run_name="__main__", alter_sys=True)
