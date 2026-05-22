#!/usr/bin/env bash
# smoke_test.sh
#
# End-to-end smoke test for LintBench. No real API calls, no Docker required.
#
# What it does:
#   1. Downloads lintbench.json from HuggingFace
#   2. Runs inference with --stub (writes minimal placeholder detectors)
#   3. Runs eval with --stub (fake pass/fail, no compilation)
#   4. Validates the results JSON looks sane
#
# Usage (from repo root):
#   bash smoke_test.sh
#
# Prerequisites:
#   uv sync && source lint_benchmark/.venv/bin/activate

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LB="${REPO_ROOT}/lint_benchmark"
TMP="${REPO_ROOT}/.smoke"
HF_REPO="lintbench/lintbench"   # TODO: update to real HuggingFace repo path

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

pass() { echo -e "${GREEN}✓ $*${NC}"; }
fail() { echo -e "${RED}✗ $*${NC}"; exit 1; }

echo "=== LintBench smoke test ==="
echo

# ---------------------------------------------------------------------------
# 1. Download benchmark from HuggingFace
# ---------------------------------------------------------------------------
echo "[1/3] Downloading benchmark from HuggingFace (${HF_REPO})..."

python3 - <<PYEOF
from huggingface_hub import hf_hub_download
import shutil, pathlib

dest = pathlib.Path("${LB}/data/lintbench.json")
dest.parent.mkdir(parents=True, exist_ok=True)

path = hf_hub_download(
    repo_id="${HF_REPO}",
    filename="lintbench.json",
    repo_type="dataset",
)
shutil.copy(path, dest)
print(f"  Saved to {dest}")
PYEOF

[[ -f "${LB}/data/lintbench.json" ]] || fail "lintbench.json not found after download"
pass "Benchmark downloaded"

# ---------------------------------------------------------------------------
# 2. Run inference (stub — no API calls)
# ---------------------------------------------------------------------------
echo
echo "[2/3] Running inference (stub mode, 5 instances)..."

rm -rf "${TMP}/generated"

python "${LB}/run_inference.py" \
    --benchmark "${LB}/data/lintbench.json" \
    --model     smoke-test-model \
    --prompt    zero_shot \
    --out       "${TMP}/generated" \
    --limit     5 \
    --stub

GENERATED_DIR="${TMP}/generated/smoke-test-model/zero_shot"
[[ -d "${GENERATED_DIR}" ]] || fail "Generated directory not created"

N_FILES=$(find "${GENERATED_DIR}" -name "sample_0.*" | wc -l | tr -d ' ')
[[ "${N_FILES}" -eq 5 ]] || fail "Expected 5 generated files, got ${N_FILES}"
pass "Inference produced ${N_FILES} detector files"

# ---------------------------------------------------------------------------
# 3. Run eval (stub — no Docker)
# ---------------------------------------------------------------------------
echo
echo "[3/3] Running eval (stub mode, mixed pass/fail)..."

rm -rf "${TMP}/results"
mkdir -p "${TMP}/results"

python "${LB}/run_eval.py" \
    --benchmark "${LB}/data/lintbench.json" \
    --generated "${GENERATED_DIR}" \
    --model     smoke-test-model \
    --prompt    zero_shot \
    --out       "${TMP}/results/smoke.json" \
    --limit     5 \
    --stub \
    --stub-mode mixed

RESULTS="${TMP}/results/smoke.json"
[[ -f "${RESULTS}" ]] || fail "Results file not created"

# Validate results JSON has expected top-level keys
python3 - <<PYEOF
import json, sys

with open("${RESULTS}") as f:
    r = json.load(f)

required = ["pass_at_1", "compilation_rate", "by_difficulty", "instances"]
missing  = [k for k in required if k not in r]
if missing:
    print(f"Missing keys in results: {missing}", file=sys.stderr)
    sys.exit(1)

n = len(r["instances"])
print(f"  {n} instances evaluated")
print(f"  pass@1 = {r['pass_at_1']:.1%}")
print(f"  compile_rate = {r['compilation_rate']:.1%}")
PYEOF

pass "Results JSON is valid"

# ---------------------------------------------------------------------------
# Done
# ---------------------------------------------------------------------------
echo
echo "=== Smoke test passed ==="
echo "Results: ${RESULTS}"
echo "Generated files: ${GENERATED_DIR}"
